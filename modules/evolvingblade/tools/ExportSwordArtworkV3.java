import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * <b>v3 简洁像素风导出器</b>：把 {@code art/v3/masters/stage0..7.png} 导出为
 * Minecraft 原生 <b>32×32 物品贴图</b>；中后期（stage2+）额外导出
 * <b>PNG + mcmeta 帧动画</b>——只让<b>剑身内部</b>材质缓慢流动，
 * 边缘/轮廓/护手/握柄逐像素固定。
 *
 * <h2>可复现参数（全部是常量，改这里就改输出）</h2>
 * <ul>
 *   <li>{@link #OUT}=32：逻辑分辨率（可像原版 16px 那样粗像素）；</li>
 *   <li>{@link #ALPHA_THRESHOLD}=128：alpha 硬量化，输出只有 0 / 255，绝无半透明白底噪点；</li>
 *   <li>{@link #MAX_PALETTE}=12：颜色上限（只从母版自身颜色里挑，不发明新色）；</li>
 *   <li>{@link #FRAMES_STATIC}=1（stage0/1 静态）、{@link #FRAMES_EARLY}=6（stage2..4）、
 *       {@link #FRAMES_LATE}=8（stage5..7）；</li>
 *   <li>{@link #FRAMETIME}=5 tick、{@code interpolate=false}（缓慢、像素级跳变，不闪烁）。</li>
 * </ul>
 *
 * <h2>动的是哪一块</h2>
 * 沿"护手 → 剑尖"的对角轴把不透明像素投影到 {@code u∈[0,1]}：
 * {@code u} 小的是握柄/护手，大的是剑尖。动区 = 不透明轮廓<b>腐蚀 1px</b>（去掉描边）
 * ∩ {@code u ≥ }{@link #BLADE_START}（默认 0.42）。若存在
 * {@code art/v3/masks/stageN.png}，则直接用该图的亮色像素作为动区（Codex 可精确指定）。
 * 动区之外的所有像素在每一帧都<b>逐字节相同</b>。
 *
 * <p>纯 JDK（{@code javax.imageio} + {@code java.util.zip}），不依赖任何图片库。
 *
 * <p>用法：{@code java tools/ExportSwordArtworkV3.java [项目根]}
 */
public final class ExportSwordArtworkV3 {

    /** 逻辑分辨率。 */
    static final int OUT = 32;
    /** alpha 硬量化阈值：低于它一律透明，否则不透明。 */
    static final int ALPHA_THRESHOLD = 128;
    /** 颜色上限（含透明）。 */
    static final int MAX_PALETTE = 12;
    /** stage0/1 静态。 */
    static final int FRAMES_STATIC = 1;
    /** stage2..4 帧数。 */
    static final int FRAMES_EARLY = 6;
    /** stage5..7 帧数。 */
    static final int FRAMES_LATE = 8;
    /** 每帧持续 tick。 */
    static final int FRAMETIME = 5;
    /** 动区沿对角轴的起点（0=握柄端，1=剑尖端）。 */
    static final double BLADE_START = 0.42D;
    /** 稀疏星砂的出现概率（"极少青紫颗粒"）。 */
    static final double ACCENT_CHANCE = 0.02D;
    /** 接触表放大倍数。 */
    static final int SHEET_ZOOM = 4;
    /** 帧序列图放大倍数。 */
    static final int FRAME_GRID_ZOOM = 2;
    private static final int STAGES = 8;

    private ExportSwordArtworkV3() {}

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path masters = root.resolve("art").resolve("v3").resolve("masters");
        Path masks = root.resolve("art").resolve("v3").resolve("masks");
        Path exports = root.resolve("art").resolve("v3").resolve("exports");
        Path textures = root.resolve("src").resolve("main").resolve("resources")
                .resolve("assets").resolve("evolvingblade").resolve("textures").resolve("item");

        List<Path> missing = new ArrayList<>();
        for (int stage = 0; stage < STAGES; stage++) {
            Path file = masters.resolve("stage" + stage + ".png");
            if (!Files.isRegularFile(file)) {
                missing.add(file);
            }
        }
        if (!missing.isEmpty()) {
            System.err.println("[v3] 母版不齐（" + missing.size() + "/" + STAGES + " 缺失），"
                    + "本次不导出、不改动任何工程贴图：");
            for (Path file : missing) {
                System.err.println("  - " + file);
            }
            System.err.println("[v3] 请让 Codex 把八张母版放到 art/v3/masters/stage0..7.png 后重跑本工具。");
            System.exit(2);
            return;
        }

        Files.createDirectories(exports);
        Files.createDirectories(textures);

        StringBuilder report = new StringBuilder();
        report.append("# v3 像素材质导出报告（tools/ExportSwordArtworkV3.java）\n\n");
        report.append("| 参数 | 值 |\n|---|---|\n");
        report.append("| 逻辑分辨率 | ").append(OUT).append("×").append(OUT).append(" |\n");
        report.append("| alpha 硬阈值 | ").append(ALPHA_THRESHOLD).append("（输出仅 0/255） |\n");
        report.append("| 颜色上限 | ").append(MAX_PALETTE).append(" |\n");
        report.append("| 帧数 | stage0/1=").append(FRAMES_STATIC).append("，stage2..4=")
                .append(FRAMES_EARLY).append("，stage5..7=").append(FRAMES_LATE).append(" |\n");
        report.append("| frametime | ").append(FRAMETIME).append(" tick，interpolate=false |\n");
        report.append("| 动区起点 u | ").append(BLADE_START)
                .append("（沿护手→剑尖对角轴，腐蚀 1px 后再取该段） |\n\n");
        report.append("| 阶段 | 帧数 | 调色板色数 | 动区像素 | 静态像素 | 输出文件 | 输出字节 | alpha 掩码 SHA-256 |\n");
        report.append("|---|---|---|---|---|---|---|---|\n");

        int[][][] sheet = new int[8][][];
        List<int[][]> firstFrames = new ArrayList<>();
        List<int[][]> maskSheets = new ArrayList<>();
        List<List<int[][]>> allFrames = new ArrayList<>();
        boolean ok = true;

        for (int stage = 0; stage < STAGES; stage++) {
            BufferedImage master = ImageIO.read(masters.resolve("stage" + stage + ".png").toFile());
            if (master == null) {
                System.err.println("[v3] 无法读取 " + masters.resolve("stage" + stage + ".png"));
                ok = false;
                break;
            }
            int[][] base = quantize(downscale(master));
            int[] palette = paletteOf(base);
            int[][] mask = bladeMask(base, masks.resolve("stage" + stage + ".png"));
            double[][] uMap = buildUMap(base, mask);
            int[][] materialSplit = splitMaterials(palette);
            int[] darkMaterials = materialSplit[0];
            int starColor = materialSplit[1][0];
            int frames = framesFor(stage);
            List<int[][]> list = new ArrayList<>();
            for (int frame = 0; frame < frames; frame++) {
                list.add(animate(base, mask, palette, darkMaterials, starColor, frame, frames, uMap));
            }
            allFrames.add(list);
            firstFrames.add(list.get(0));
            maskSheets.add(mask);
            sheet[stage] = list.get(0);

            int animated = count(mask, true);
            int staticPixels = count(base, false) - animated;
            // 静态阶段写单帧；动画阶段写纵向帧条（Minecraft 原生 mcmeta 布局）。
            int[][] filmstrip = frames == 1 ? list.get(0) : stack(list);
            Path gameFile = textures.resolve("star_eater_stage" + stage + ".png");
            writePng(filmstrip, gameFile);
            Path mcmeta = textures.resolve("star_eater_stage" + stage + ".png.mcmeta");
            if (frames == 1) {
                Files.deleteIfExists(mcmeta);
            } else {
                Files.writeString(mcmeta, mcmetaJson(), StandardCharsets.UTF_8);
            }
            // 导出目录留一份同内容副本，便于独立核对。
            writePng(filmstrip, exports.resolve("star_eater_stage" + stage + ".png"));

            report.append("| ").append(stage).append(" | ").append(frames)
                    .append(" | ").append(palette.length)
                    .append(" | ").append(animated)
                    .append(" | ").append(staticPixels)
                    .append(" | `").append(gameFile.getFileName()).append(frames > 1 ? "` + `.mcmeta" : "`")
                    .append(" | ").append(Files.size(gameFile))
                    .append(" | `").append(sha256(maskDigestInput(mask))).append("` |\n");
            System.out.println("stage" + stage + " frames=" + frames + " palette=" + palette.length
                    + " animated=" + animated + " -> " + gameFile);
        }

        if (!ok) {
            report.append("\n**导出中断**：存在无法读取的母版；工程贴图未被本工具修改（详见 stderr）。\n");
            Files.writeString(exports.resolve("EXPORT-REPORT.md"), report.toString());
            System.exit(2);
            return;
        }

        writePng(buildContactSheet(firstFrames), exports.resolve("contact-sheet.png"));
        writePng(buildFrameGrid(allFrames), exports.resolve("animation-frames.png"));
        writePng(buildMaskSheet(maskSheets), exports.resolve("blade-mask.png"));
        report.append("\n## 预览文件\n\n");
        report.append("- `art/v3/exports/contact-sheet.png`：八阶段<b>单帧横排</b>，每张 ")
                .append(SHEET_ZOOM).append("×，中灰棋盘底。\n");
        report.append("- `art/v3/exports/animation-frames.png`：<b>可查看的帧序列</b>，行=阶段、列=帧，每张 ")
                .append(FRAME_GRID_ZOOM).append("×；静态阶段只有一列。\n");
        report.append("- `art/v3/exports/blade-mask.png`：动区可视化（青=会流动的剑身内部，灰=逐帧固定的边缘/护手/握柄）。\n");
        report.append("- `art/v3/exports/star_eater_stage0..7.png`：与工程贴图同内容副本（动画阶段是纵向帧条）。\n");
        report.append("\n> 每阶段 alpha 掩码在所有帧中完全一致；动区之外像素逐帧相同。\n");
        Files.writeString(exports.resolve("EXPORT-REPORT.md"), report.toString());
        System.out.println("预览 -> " + exports.resolve("contact-sheet.png"));
        System.out.println("报告 -> " + exports.resolve("EXPORT-REPORT.md"));
    }

    // ------------------------------------------------------------------
    // 母版 → 32px 基图
    // ------------------------------------------------------------------

    /** 等比最近邻缩放 + 边距，输出 32×32 的 ARGB（alpha 先硬量化）。 */
    static int[][] downscale(BufferedImage master) {
        int w = master.getWidth();
        int h = master.getHeight();
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((master.getRGB(x, y) >>> 24) & 0xFF) >= ALPHA_THRESHOLD) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        int[][] out = new int[OUT][OUT];
        if (maxX < minX || maxY < minY) {
            return out;
        }
        int margin = 2;
        int usable = OUT - margin * 2;
        int sw = maxX - minX + 1;
        int sh = maxY - minY + 1;
        double scale = Math.min((double) usable / sw, (double) usable / sh);
        int dw = Math.max(1, (int) Math.round(sw * scale));
        int dh = Math.max(1, (int) Math.round(sh * scale));
        int ox = (OUT - dw) / 2;
        int oy = (OUT - dh) / 2;
        for (int y = 0; y < dh; y++) {
            int sy = minY + Math.min(sh - 1, (int) ((y + 0.5D) / scale));
            for (int x = 0; x < dw; x++) {
                int sx = minX + Math.min(sw - 1, (int) ((x + 0.5D) / scale));
                int argb = master.getRGB(sx, sy);
                if (((argb >>> 24) & 0xFF) < ALPHA_THRESHOLD) {
                    continue;
                }
                out[oy + y][ox + x] = 0xFF000000 | (argb & 0x00FFFFFF);
            }
        }
        return out;
    }

    /** 把不透明像素量化到 ≤ {@link #MAX_PALETTE} 个颜色（按出现频率挑代表色）。 */
    static int[][] quantize(int[][] pixels) {
        Map<Integer, Integer> histogram = new LinkedHashMap<>();
        for (int[] row : pixels) {
            for (int argb : row) {
                if ((argb >>> 24) >= ALPHA_THRESHOLD) {
                    histogram.merge(argb & 0x00FFFFFF, 1, Integer::sum);
                }
            }
        }
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(histogram.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Integer> chosen = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : sorted) {
            if (chosen.size() >= MAX_PALETTE) {
                break;
            }
            boolean close = false;
            for (int existing : chosen) {
                if (distance(existing, entry.getKey()) < 24) {
                    close = true;
                    break;
                }
            }
            if (!close) {
                chosen.add(entry.getKey());
            }
        }
        if (chosen.isEmpty()) {
            chosen.add(0x000000);
        }
        int[][] out = new int[pixels.length][pixels[0].length];
        for (int y = 0; y < pixels.length; y++) {
            for (int x = 0; x < pixels[y].length; x++) {
                int argb = pixels[y][x];
                if ((argb >>> 24) < ALPHA_THRESHOLD) {
                    continue;
                }
                out[y][x] = 0xFF000000 | nearest(chosen, argb & 0x00FFFFFF);
            }
        }
        return out;
    }

    private static int distance(int rgbA, int rgbB) {
        int dr = ((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF);
        int dg = ((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF);
        int db = (rgbA & 0xFF) - (rgbB & 0xFF);
        return (int) Math.sqrt((double) (dr * dr + dg * dg + db * db));
    }

    private static int nearest(List<Integer> colors, int rgb) {
        int best = colors.get(0);
        int bestDistance = Integer.MAX_VALUE;
        for (int candidate : colors) {
            int d = distance(candidate, rgb);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    /** 母版里实际用到的颜色（去重后的调色板）。 */
    static int[] paletteOf(int[][] pixels) {
        java.util.LinkedHashSet<Integer> set = new java.util.LinkedHashSet<>();
        for (int[] row : pixels) {
            for (int argb : row) {
                if ((argb >>> 24) >= ALPHA_THRESHOLD) {
                    set.add(argb);
                }
            }
        }
        int[] out = new int[set.size()];
        int i = 0;
        for (int value : set) {
            out[i++] = value;
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 动区（剑身内部）
    // ------------------------------------------------------------------

    /**
     * 动区掩码：{@code true} 表示该像素会随帧流动。
     *
     * <p>优先用 {@code art/v3/masks/stageN.png}（亮色像素 = 动区）；
     * 否则用"腐蚀 1px 的不透明轮廓 ∩ 对角轴 u ≥ {@link #BLADE_START}"。
     */
    static int[][] bladeMask(int[][] base, Path maskFile) {
        int[][] mask = new int[OUT][OUT];
        if (Files.isRegularFile(maskFile)) {
            try {
                BufferedImage image = ImageIO.read(maskFile.toFile());
                if (image != null) {
                    for (int y = 0; y < OUT && y < image.getHeight(); y++) {
                        for (int x = 0; x < OUT && x < image.getWidth(); x++) {
                            int argb = image.getRGB(x, y);
                            int alpha = (argb >>> 24) & 0xFF;
                            int luma = (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3;
                            if (alpha >= ALPHA_THRESHOLD && luma >= 128 && (base[y][x] >>> 24) >= ALPHA_THRESHOLD) {
                                mask[y][x] = 1;
                            }
                        }
                    }
                    return mask;
                }
            } catch (IOException failure) {
                System.err.println("[v3] 掩码读取失败，改用内置启发式：" + maskFile + " (" + failure + ")");
            }
        }
        int minX = OUT;
        int minY = OUT;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < OUT; y++) {
            for (int x = 0; x < OUT; x++) {
                if ((base[y][x] >>> 24) >= ALPHA_THRESHOLD) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return mask;
        }
        double diagonal = (maxX - minX) + (maxY - minY);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if ((base[y][x] >>> 24) < ALPHA_THRESHOLD || !isInterior(base, x, y)) {
                    continue;
                }
                // u=0 在左下（握柄端），u=1 在右上（剑尖端）。
                double u = ((x - minX) + (maxY - y)) / diagonal;
                if (u >= BLADE_START) {
                    mask[y][x] = 1;
                }
            }
        }
        return mask;
    }

    /** 四邻域都在轮廓内（即不是描边像素）。 */
    private static boolean isInterior(int[][] pixels, int x, int y) {
        return opaque(pixels, x - 1, y) && opaque(pixels, x + 1, y)
                && opaque(pixels, x, y - 1) && opaque(pixels, x, y + 1);
    }

    private static boolean opaque(int[][] pixels, int x, int y) {
        return x >= 0 && y >= 0 && y < pixels.length && x < pixels[y].length
                && (pixels[y][x] >>> 24) >= ALPHA_THRESHOLD;
    }

    // ------------------------------------------------------------------
    // 动画
    // ------------------------------------------------------------------

    /** 每阶段的帧数：stage0/1 静态，中期 6 帧，后期 8 帧。 */
    static int framesFor(int stage) {
        if (stage <= 1) {
            return FRAMES_STATIC;
        }
        return stage <= 4 ? FRAMES_EARLY : FRAMES_LATE;
    }

    /**
     * 生成第 {@code frame} 帧：<b>只</b>让剑身内部的深色材质在相邻明暗档之间缓慢流动，
     * 外加最多 {@link #MAX_HIGHLIGHTS} 个 <b>1px</b> 稀疏亮点，亮点沿剑身<b>连续移动</b>；
     * 动区之外（含 alpha）逐像素复制基图。
     *
     * <h2>为什么不能"全 palette 相邻 index 移动"</h2>
     * 按调色板索引整体平移会把<b>银白切刃/描边</b>也卷进动画：暗剑身会突然变成大面积亮色，
     * 看起来就是"随机白补丁"。这里把调色板按亮度分成
     * <b>深色材质档（会动的只有这些）</b>与<b>亮色档（永远只属于边缘/护手，不参与动画）</b>。
     *
     * @param darkMaterials 深色材质档（按亮度升序），动画只在这些颜色之间过渡
     * @param starColor     稀疏亮点用的颜色（低饱和青紫），也必须来自母版自身
     */
    static int[][] animate(int[][] base, int[][] mask, int[] palette, int[] darkMaterials, int starColor,
            int frame, int frames, double[][] uMap) {
        int[][] out = new int[OUT][OUT];
        for (int y = 0; y < OUT; y++) {
            System.arraycopy(base[y], 0, out[y], 0, OUT);
        }
        if (frames <= 1 || darkMaterials.length == 0) {
            return out;
        }
        // 沿剑身的缓慢明暗流动（相位随帧推进、随 u 连续变化）→ 看起来是"材质在流动"。
        double phase = 2.0D * Math.PI * frame / frames;
        for (int y = 0; y < OUT; y++) {
            for (int x = 0; x < OUT; x++) {
                if (mask[y][x] == 0) {
                    continue;
                }
                double u = uMap[y][x];
                double wave = 0.5D + 0.5D * Math.sin(phase + u * 3.2D);
                int level = (int) Math.floor(wave * darkMaterials.length);
                level = Math.max(0, Math.min(darkMaterials.length - 1, level));
                out[y][x] = 0xFF000000 | (darkMaterials[level] & 0x00FFFFFF);
            }
        }
        // 稀疏亮点：最多 MAX_HIGHLIGHTS 个，每个 1px，位置沿剑身 u 轴逐帧前进（连续，不炸闪）。
        int highlights = Math.min(MAX_HIGHLIGHTS, Math.max(1, maskPixelCount(mask) / 24));
        for (int k = 0; k < highlights; k++) {
            double target = 0.10D + 0.80D * frac(0.18D + k * 0.37D + frame / (double) frames * 0.55D);
            int[] pixel = pixelNearestU(mask, uMap, target);
            if (pixel != null) {
                out[pixel[1]][pixel[0]] = 0xFF000000 | (starColor & 0x00FFFFFF);
            }
        }
        return out;
    }

    /** 单帧最多几个亮点（"极少青紫颗粒"）。 */
    static final int MAX_HIGHLIGHTS = 3;

    private static int maskPixelCount(int[][] mask) {
        int total = 0;
        for (int[] row : mask) {
            for (int value : row) {
                total += value == 1 ? 1 : 0;
            }
        }
        return total;
    }

    private static double frac(double value) {
        return value - Math.floor(value);
    }

    /** 动区里 u 最接近 {@code target} 的像素（保证亮点落在剑身上并随帧移动）。 */
    private static int[] pixelNearestU(int[][] mask, double[][] uMap, double target) {
        int[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int y = 0; y < OUT; y++) {
            for (int x = 0; x < OUT; x++) {
                if (mask[y][x] == 0) {
                    continue;
                }
                double distance = Math.abs(uMap[y][x] - target);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[] {x, y};
                }
            }
        }
        return best;
    }

    /**
     * 把母版调色板按亮度分成两档：
     * <ul>
     *   <li>返回值的 {@code [0]} = <b>深色材质档</b>（按亮度升序），动画只在这些颜色间过渡；</li>
     *   <li>返回值的 {@code [1]} = 亮点颜色（深色档里最偏蓝/紫的一项，保持低饱和、
     *       绝不使用银白边缘色）。</li>
     * </ul>
     * 判据：亮度低于整体中位数的颜色算"深色材质"；这样银白切刃/描边永远不会被卷进动画。
     */
    static int[][] splitMaterials(int[] palette) {
        int[] sorted = palette.clone();
        // 按亮度升序
        for (int i = 0; i < sorted.length; i++) {
            for (int j = i + 1; j < sorted.length; j++) {
                if (luma(sorted[j]) < luma(sorted[i])) {
                    int tmp = sorted[i];
                    sorted[i] = sorted[j];
                    sorted[j] = tmp;
                }
            }
        }
        int half = Math.max(1, sorted.length / 2);
        int[] dark = new int[half];
        System.arraycopy(sorted, 0, dark, 0, half);
        int star = dark[0];
        int bestScore = Integer.MIN_VALUE;
        for (int color : dark) {
            int r = (color >> 16) & 0xFF;
            int b = color & 0xFF;
            int score = b - r;
            if (score > bestScore) {
                bestScore = score;
                star = color;
            }
        }
        return new int[][] {dark, new int[] {star}};
    }

    private static int luma(int rgb) {
        return (((rgb >> 16) & 0xFF) * 30 + ((rgb >> 8) & 0xFF) * 59 + (rgb & 0xFF) * 11) / 100;
    }

    /** 动区像素的沿对角轴参数 u ∈ [0,1]（0=握柄端，1=剑尖端）；非动区为 0。 */
    static double[][] buildUMap(int[][] base, int[][] mask) {
        double[][] uMap = new double[OUT][OUT];
        int minX = OUT;
        int minY = OUT;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < OUT; y++) {
            for (int x = 0; x < OUT; x++) {
                if ((base[y][x] >>> 24) >= ALPHA_THRESHOLD) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        double diagonal = Math.max(1.0D, (maxX - minX) + (maxY - minY));
        for (int y = 0; y < OUT; y++) {
            for (int x = 0; x < OUT; x++) {
                if (mask[y][x] == 1) {
                    uMap[y][x] = ((x - minX) + (maxY - y)) / diagonal;
                }
            }
        }
        return uMap;
    }

    /** 调色板里最偏蓝/紫的一项（用作"青紫颗粒"，必须来自母版自身颜色）。 */
    private static int accentIndex(int[] palette, int fallback) {
        int best = fallback;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int rgb = palette[i] & 0x00FFFFFF;
            int r = (rgb >> 16) & 0xFF;
            int b = rgb & 0xFF;
            int score = b - r;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // 预览图
    // ------------------------------------------------------------------

    private static int[][] buildContactSheet(List<int[][]> stages) {
        int cell = OUT * SHEET_ZOOM;
        int gap = 6;
        int width = stages.size() * cell + (stages.size() + 1) * gap;
        int height = cell + gap * 2;
        int[][] sheet = new int[height][width];
        fillChecker(sheet, gap, 8);
        for (int i = 0; i < stages.size(); i++) {
            blit(sheet, stages.get(i), gap + i * (cell + gap), gap, SHEET_ZOOM);
        }
        return sheet;
    }

    private static int[][] buildFrameGrid(List<List<int[][]>> all) {
        int cols = FRAMES_LATE;
        int cell = OUT * FRAME_GRID_ZOOM;
        int gap = 4;
        int width = cols * cell + (cols + 1) * gap;
        int height = all.size() * cell + (all.size() + 1) * gap;
        int[][] grid = new int[height][width];
        fillChecker(grid, gap, 6);
        for (int row = 0; row < all.size(); row++) {
            for (int col = 0; col < all.get(row).size(); col++) {
                blit(grid, all.get(row).get(col), gap + col * (cell + gap),
                        gap + row * (cell + gap), FRAME_GRID_ZOOM);
            }
        }
        return grid;
    }

    private static int[][] buildMaskSheet(List<int[][]> masks) {
        int cell = OUT * SHEET_ZOOM;
        int gap = 6;
        int width = masks.size() * cell + (masks.size() + 1) * gap;
        int height = cell + gap * 2;
        int[][] sheet = new int[height][width];
        fillChecker(sheet, gap, 8);
        for (int i = 0; i < masks.size(); i++) {
            int[][] overlay = new int[OUT][OUT];
            for (int y = 0; y < OUT; y++) {
                for (int x = 0; x < OUT; x++) {
                    overlay[y][x] = masks.get(i)[y][x] == 1 ? 0xFF2FD8E8 : 0xFF8A8A8A;
                }
            }
            blit(sheet, overlay, gap + i * (cell + gap), gap, SHEET_ZOOM);
        }
        return sheet;
    }

    private static void fillChecker(int[][] image, int gap, int size) {
        for (int y = 0; y < image.length; y++) {
            for (int x = 0; x < image[y].length; x++) {
                image[y][x] = ((x / size + y / size) % 2 == 0) ? 0xFF6E6E6E : 0xFF565656;
            }
        }
    }

    private static void blit(int[][] target, int[][] art, int ox, int oy, int zoom) {
        for (int y = 0; y < art.length; y++) {
            for (int x = 0; x < art[y].length; x++) {
                int argb = art[y][x];
                if ((argb >>> 24) < ALPHA_THRESHOLD) {
                    continue;
                }
                for (int dy = 0; dy < zoom; dy++) {
                    for (int dx = 0; dx < zoom; dx++) {
                        int ty = oy + y * zoom + dy;
                        int tx = ox + x * zoom + dx;
                        if (ty >= 0 && ty < target.length && tx >= 0 && tx < target[ty].length) {
                            target[ty][tx] = argb;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static int count(int[][] mask, boolean wantSet) {
        int total = 0;
        for (int[] row : mask) {
            for (int value : row) {
                if ((value == 1) == wantSet) {
                    total++;
                }
            }
        }
        return total;
    }

    static int[][] stack(List<int[][]> frames) {
        int height = OUT * frames.size();
        int[][] out = new int[height][OUT];
        for (int i = 0; i < frames.size(); i++) {
            for (int y = 0; y < OUT; y++) {
                System.arraycopy(frames.get(i)[y], 0, out[i * OUT + y], 0, OUT);
            }
        }
        return out;
    }

    static String mcmetaJson() {
        return "{\n  \"animation\": {\n    \"frametime\": " + FRAMETIME
                + ",\n    \"interpolate\": false\n  }\n}\n";
    }

    /** alpha 掩码的稳定指纹输入（只含 0/255 的轮廓）。 */
    private static byte[] maskDigestInput(int[][] mask) {
        byte[] bytes = new byte[OUT * OUT];
        int at = 0;
        for (int y = 0; y < OUT; y++) {
            for (int x = 0; x < OUT; x++) {
                bytes[at++] = (byte) ((mask[y][x] == 1 || (mask[y][x] >>> 24) >= ALPHA_THRESHOLD) ? 1 : 0);
            }
        }
        return bytes;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    // ------------------------------------------------------------------
    // PNG 写（RGBA8、filter 0；与其它导出器同一个编码器）
    // ------------------------------------------------------------------

    private static void writePng(int[][] pixels, Path file) throws IOException {
        int height = pixels.length;
        int width = pixels[0].length;
        byte[] raw = new byte[height * (1 + width * 4)];
        int at = 0;
        for (int y = 0; y < height; y++) {
            raw[at++] = 0;
            for (int x = 0; x < width; x++) {
                int argb = pixels[y][x];
                raw[at++] = (byte) ((argb >>> 16) & 0xFF);
                raw[at++] = (byte) ((argb >>> 8) & 0xFF);
                raw[at++] = (byte) (argb & 0xFF);
                raw[at++] = (byte) ((argb >>> 24) & 0xFF);
            }
        }
        java.io.ByteArrayOutputStream idat = new java.io.ByteArrayOutputStream();
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(9);
        deflater.setInput(raw);
        deflater.finish();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            idat.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        java.io.ByteArrayOutputStream ihdr = new java.io.ByteArrayOutputStream();
        writeInt(ihdr, width);
        writeInt(ihdr, height);
        ihdr.write(8);
        ihdr.write(6);
        ihdr.write(0);
        ihdr.write(0);
        ihdr.write(0);
        chunk(png, "IHDR", ihdr.toByteArray());
        chunk(png, "IDAT", idat.toByteArray());
        chunk(png, "IEND", new byte[0]);
        Files.write(file, png.toByteArray());
    }

    private static void writeInt(java.io.ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void chunk(java.io.ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        byte[] crcInput = new byte[typeBytes.length + data.length];
        System.arraycopy(typeBytes, 0, crcInput, 0, typeBytes.length);
        System.arraycopy(data, 0, crcInput, typeBytes.length, data.length);
        out.write(typeBytes);
        out.write(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(crcInput);
        writeInt(out, (int) crc.getValue());
    }
}
