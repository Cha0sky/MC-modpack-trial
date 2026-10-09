import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * 资产导出工具：把 Codex 在 {@code art/v2/masters/} 绘制的八张 1254×1254 母版，
 * 导出为 Minecraft 物品图可用的 <b>128×128 RGBA</b> 贴图，并生成接触表用于独立验收。
 *
 * <h2>工具做什么、不做什么</h2>
 * <ul>
 *   <li><b>做</b>：按"可见轮廓"（alpha ≥ {@link #BOUNDS_THRESHOLD}）自动裁边 → 等比缩放进
 *       目标画布（留 6% 边距）→ 最近邻采样保持硬边 → alpha 二值/硬量化去掉半透明毛边 →
 *       写出 RGBA PNG；再生成 {@code art/v2/exports/contact-sheet.png} 接触表。</li>
 *   <li><b>不做</b>：不重新绘制、不换色、不做廉价的调色板替换。像素内容 100% 来自母版，
 *       本工具只做<b>几何适配</b>与<b>透明边缘清理</b>。</li>
 * </ul>
 *
 * <h2>为什么用最近邻 + alpha 量化</h2>
 * 双线性/面积平均会在透明边缘混出"白色底"与"阴影噪点"（半透明灰边），
 * 在 16×16 的物品图标上非常明显。这里把 alpha 硬量化到 0/255，
 * 并只用最近邻取样，得到干净的硬边轮廓。
 *
 * <p>纯 JDK（{@code javax.imageio} + {@code java.awt.image}），不依赖任何图片库。
 *
 * <p>用法：
 * <pre>
 *   java tools/ExportSwordArtwork.java            # 使用默认路径
 *   java tools/ExportSwordArtwork.java &lt;项目根&gt;
 * </pre>
 */
public final class ExportSwordArtwork {

    /** 输出尺寸（Minecraft 物品贴图常用 128×128 RGBA）。 */
    private static final int OUT_SIZE = 128;
    /** 画布边距比例：保留 6% 的呼吸空间，避免剑尖/护手贴边被物品栏裁掉。 */
    private static final double MARGIN_RATIO = 0.06D;
    /** "可见像素"的 alpha 阈值：低于它的母版像素一律视为透明背景（不含阴影噪点）。 */
    private static final int BOUNDS_THRESHOLD = 128;
    /** 输出 alpha 硬量化阈值：>= 它写 255，否则写 0（彻底消除半透明毛边）。 */
    private static final int ALPHA_HARD_THRESHOLD = 128;
    /** 接触表里每个阶段的放大倍数。 */
    private static final int CONTACT_SHEET_ZOOM = 3;
    /** 阶段数量（八阶段模型属性，绝不改变）。 */
    private static final int STAGES = 8;

    private ExportSwordArtwork() {}

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".")
                .toAbsolutePath()
                .normalize();
        Path masters = root.resolve("art").resolve("v2").resolve("masters");
        Path exports = root.resolve("art").resolve("v2").resolve("exports");
        Path textures = root.resolve("src").resolve("main").resolve("resources")
                .resolve("assets").resolve("evolvingblade").resolve("textures").resolve("item");
        Files.createDirectories(exports.resolve("128"));
        Files.createDirectories(textures);

        StringBuilder report = new StringBuilder();
        report.append("# 资产导出报告（tools/ExportSwordArtwork.java）\n\n");
        report.append("目标尺寸：").append(OUT_SIZE).append("×").append(OUT_SIZE).append(" RGBA\n");
        report.append("可见阈值 alpha >= ").append(BOUNDS_THRESHOLD)
                .append("；输出 alpha 硬量化阈值 ").append(ALPHA_HARD_THRESHOLD).append("\n\n");
        report.append("| 阶段 | 母版 | 母版可见包围盒 | 输出文件 | 输出可见包围盒 | 不透明像素 | SHA-256(输出) |\n");
        report.append("|---|---|---|---|---|---|---|\n");

        List<int[][]> renderedStages = new ArrayList<>();
        boolean allOk = true;
        for (int stage = 0; stage < STAGES; stage++) {
            Path masterFile = masters.resolve("stage" + stage + ".png");
            if (!Files.isRegularFile(masterFile)) {
                System.err.println("[缺失母版] " + masterFile + " —— 保留旧贴图，跳过该阶段");
                allOk = false;
                continue;
            }
            BufferedImage master = ImageIO.read(masterFile.toFile());
            if (master == null) {
                System.err.println("[无法读取] " + masterFile);
                allOk = false;
                continue;
            }
            int[] bounds = visibleBounds(master);
            if (bounds == null) {
                System.err.println("[母版完全透明] " + masterFile + " —— 跳过");
                allOk = false;
                continue;
            }
            int[][] out = render(master, bounds);
            renderedStages.add(out);

            Path outFile = exports.resolve("128").resolve("star_eater_stage" + stage + ".png");
            writePng(out, outFile);
            // 工程资源路径沿用同名文件：模型 JSON 无需任何改动，八阶段属性保持不变。
            Path gameFile = textures.resolve("star_eater_stage" + stage + ".png");
            writePng(out, gameFile);

            int[] outBounds = visibleBoundsOf(out);
            int opaque = countOpaque(out);
            String sha = sha256(gameFile);
            report.append("| ").append(stage).append(" | ") 
                    .append(master.getWidth()).append("×").append(master.getHeight())
                    .append(" | [").append(bounds[0]).append(", ").append(bounds[1]).append(", ")
                    .append(bounds[2]).append(", ").append(bounds[3]).append("]")
                    .append(" | ").append(OUT_SIZE).append("×").append(OUT_SIZE)
                    .append(" | [").append(outBounds[0]).append(", ").append(outBounds[1]).append(", ")
                    .append(outBounds[2]).append(", ").append(outBounds[3]).append("]")
                    .append(" | ").append(opaque)
                    .append(" | `").append(sha).append("` |\n");
            System.out.println("导出 stage" + stage + " → " + gameFile + " (" + Files.size(gameFile) + " bytes, "
                    + opaque + " 不透明像素)");
        }

        if (renderedStages.size() == STAGES) {
            Path sheet = exports.resolve("contact-sheet.png");
            writePng(buildContactSheet(renderedStages), sheet);
            System.out.println("接触表 → " + sheet);
            report.append("\n接触表：`art/v2/exports/contact-sheet.png`（每阶段 ")
                    .append(CONTACT_SHEET_ZOOM).append("×，中灰棋盘底，便于人工核对轮廓与硬边）\n");
        } else {
            report.append("\n**母版不齐**：只有 ").append(renderedStages.size()).append("/").append(STAGES)
                    .append(" 张，未生成接触表；工程贴图保持原样。\n");
        }
        report.append("\n全部母版齐备：").append(allOk && renderedStages.size() == STAGES ? "是" : "否").append("\n");
        Files.writeString(exports.resolve("EXPORT-REPORT.md"), report.toString());
        System.out.println("报告 → " + exports.resolve("EXPORT-REPORT.md"));
        if (renderedStages.size() != STAGES) {
            System.exit(2);
        }
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    /** 把母版的可见区域等比缩放进 128×128 画布（最近邻 + alpha 硬量化）。 */
    private static int[][] render(BufferedImage master, int[] bounds) {
        int srcW = bounds[2] - bounds[0];
        int srcH = bounds[3] - bounds[1];
        int margin = (int) Math.round(OUT_SIZE * MARGIN_RATIO);
        int usable = OUT_SIZE - margin * 2;
        // 等比：长边贴满 usable，短边居中。
        double scale = Math.min((double) usable / srcW, (double) usable / srcH);
        int dstW = Math.max(1, (int) Math.round(srcW * scale));
        int dstH = Math.max(1, (int) Math.round(srcH * scale));
        int offsetX = (OUT_SIZE - dstW) / 2;
        int offsetY = (OUT_SIZE - dstH) / 2;

        int[][] out = new int[OUT_SIZE][OUT_SIZE];
        for (int y = 0; y < dstH; y++) {
            // 最近邻：目标像素映射回母版整数坐标（取像素中心，避免整体偏移半格）。
            int srcY = bounds[1] + Math.min(srcH - 1, (int) ((y + 0.5D) / scale));
            for (int x = 0; x < dstW; x++) {
                int srcX = bounds[0] + Math.min(srcW - 1, (int) ((x + 0.5D) / scale));
                int argb = master.getRGB(srcX, srcY);
                int alpha = (argb >>> 24) & 0xFF;
                // 硬边：半透明一律量化到 0 或 255，不留"阴影噪点/白底毛边"。
                int hardened = alpha >= ALPHA_HARD_THRESHOLD ? 0xFF : 0x00;
                if (hardened == 0) {
                    continue;
                }
                out[offsetY + y][offsetX + x] = 0xFF000000 | (argb & 0x00FFFFFF);
            }
        }
        return out;
    }

    /** 母版里 alpha >= 阈值的像素包围盒 [minX, minY, maxX, maxY]（max 为开区间）。 */
    private static int[] visibleBounds(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) >= BOUNDS_THRESHOLD) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return null;
        }
        return new int[] {minX, minY, maxX + 1, maxY + 1};
    }

    /** 像素数组的可见包围盒（用于报告，验证输出真的被裁到合理范围而不是贴边）。 */
    private static int[] visibleBoundsOf(int[][] pixels) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < pixels.length; y++) {
            for (int x = 0; x < pixels[y].length; x++) {
                if ((pixels[y][x] >>> 24) >= BOUNDS_THRESHOLD) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {minX, minY, maxX + 1, maxY + 1};
    }

    private static int countOpaque(int[][] pixels) {
        int count = 0;
        for (int[] row : pixels) {
            for (int argb : row) {
                if ((argb >>> 24) >= BOUNDS_THRESHOLD) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 接触表：八阶段横向排列，中灰棋盘背景（便于看透明区与硬边）。 */
    private static int[][] buildContactSheet(List<int[][]> stages) {
        int cell = OUT_SIZE * CONTACT_SHEET_ZOOM;
        int gap = 4;
        int width = stages.size() * cell + (stages.size() + 1) * gap;
        int height = cell + gap * 2;
        int[][] sheet = new int[height][width];
        for (int gy = 0; gy < height; gy++) {
            for (int gx = 0; gx < width; gx++) {
                boolean light = ((gx / 8) + (gy / 8)) % 2 == 0;
                sheet[gy][gx] = light ? 0xFF6E6E6E : 0xFF565656;
            }
        }
        for (int stage = 0; stage < stages.size(); stage++) {
            int[][] art = stages.get(stage);
            int originX = gap + stage * (cell + gap);
            int originY = gap;
            for (int y = 0; y < OUT_SIZE; y++) {
                for (int x = 0; x < OUT_SIZE; x++) {
                    int argb = art[y][x];
                    if ((argb >>> 24) < BOUNDS_THRESHOLD) {
                        continue;
                    }
                    for (int dy = 0; dy < CONTACT_SHEET_ZOOM; dy++) {
                        for (int dx = 0; dx < CONTACT_SHEET_ZOOM; dx++) {
                            sheet[originY + y * CONTACT_SHEET_ZOOM + dy][originX + x * CONTACT_SHEET_ZOOM + dx] = argb;
                        }
                    }
                }
            }
        }
        return sheet;
    }

    // ------------------------------------------------------------------
    // PNG 读写（纯字节，杜绝 ImageIO 写出的多余色彩配置块）
    // ------------------------------------------------------------------

    private static void writePng(int[][] pixels, Path file) throws IOException {
        int height = pixels.length;
        int width = pixels[0].length;
        byte[] raw = new byte[height * (1 + width * 4)];
        int at = 0;
        for (int y = 0; y < height; y++) {
            raw[at++] = 0; // filter: none
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
            int n = deflater.deflate(buffer);
            idat.write(buffer, 0, n);
        }
        deflater.end();

        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        java.io.ByteArrayOutputStream ihdr = new java.io.ByteArrayOutputStream();
        writeInt(ihdr, width);
        writeInt(ihdr, height);
        ihdr.write(8);  // bit depth
        ihdr.write(6);  // color type: RGBA
        ihdr.write(0);  // compression
        ihdr.write(0);  // filter
        ihdr.write(0);  // interlace
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
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] crcInput = new byte[typeBytes.length + data.length];
        System.arraycopy(typeBytes, 0, crcInput, 0, typeBytes.length);
        System.arraycopy(data, 0, crcInput, typeBytes.length, data.length);
        out.write(typeBytes);
        out.write(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(crcInput);
        writeInt(out, (int) crc.getValue());
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
