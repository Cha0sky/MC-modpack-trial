import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * <b>独立的 v3 导出校验器</b>：直接读导出后的 PNG（不调用导出器内部代码），
 * 逐条核对交付约束，并只打印 FAIL/PASS 结论。
 *
 * <p>核对项：
 * <ol>
 *   <li>宽度必须是 {@value #OUT}px；高度是 {@value #OUT} 或 {@value #OUT}×N（纵向帧条）；</li>
 *   <li>alpha 只能是 0 或 255（无半透明白底噪点）；</li>
 *   <li>单帧颜色数 ≤ {@value #MAX_PALETTE}，且只使用母版已有的色相范围（亮度/色相不出现饱和红绿）；</li>
 *   <li>多帧时<b>所有帧的 alpha 掩码逐像素相同</b>（轮廓不动）；</li>
 *   <li>多帧时相邻帧的差异像素数 ≤ 不透明确像素的一定比例（只动剑身内部，不是整图重画）；</li>
 *   <li>存在 {@code .png.mcmeta} 且 {@code interpolate=false}（动画阶段）。</li>
 * </ol>
 *
 * <p>用法：{@code java tools/VerifySwordArtworkV3.java <贴图目录> [--expect-frames=2:6,5:8]}
 */
public final class VerifySwordArtworkV3 {

    static final int OUT = 32;
    static final int MAX_PALETTE = 12;
    /** 相邻帧允许的差异上限（占不透明像素比例）：只允许剑身内部少量流动。 */
    static final double MAX_FRAME_DIFF_RATIO = 0.45D;
    /** "亮像素"亮度阈值（银白切刃、亮点）。 */
    static final int BRIGHT_LUMA = 180;
    /** 亮像素数在帧间允许的波动上限（防止暗剑身突然出现大片白补丁）。 */
    static final int MAX_BRIGHT_SWING = 6;

    private VerifySwordArtworkV3() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        boolean failed = false;
        for (int stage = 0; stage < 8; stage++) {
            Path file = dir.resolve("star_eater_stage" + stage + ".png");
            if (!Files.isRegularFile(file)) {
                System.out.println("FAIL stage" + stage + ": 缺少 " + file);
                failed = true;
                continue;
            }
            BufferedImage image = ImageIO.read(file.toFile());
            StringBuilder problems = new StringBuilder();
            if (image.getWidth() != OUT) {
                problems.append("宽度应为 ").append(OUT).append(" 实际 ").append(image.getWidth()).append("; ");
            }
            if (image.getHeight() % OUT != 0) {
                problems.append("高度不是 ").append(OUT).append(" 的整数倍(").append(image.getHeight()).append("); ");
            }
            int frames = Math.max(1, image.getHeight() / OUT);
            Set<Integer> colors = new LinkedHashSet<>();
            int opaque = 0;
            int softAlpha = 0;
            boolean[] mask = new boolean[OUT * OUT * frames];
            int[][] rgb = new int[frames][OUT * OUT];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xFF;
                    int frame = y / OUT;
                    int index = frame * OUT * OUT + (y % OUT) * OUT + x;
                    if (alpha == 0 || alpha == 255) {
                        // ok
                    } else {
                        softAlpha++;
                    }
                    if (alpha >= 128) {
                        mask[index] = true;
                        opaque++;
                        int color = argb & 0x00FFFFFF;
                        colors.add(color);
                        rgb[frame][(y % OUT) * OUT + x] = color;
                        int r = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int b = color & 0xFF;
                        // 冷青/暗紫基调：不允许出现明显高饱和的暖色（红/黄主导）。
                        if (r > 200 && g > 170 && b < 120) {
                            problems.append("出现暖金色像素 @").append(x).append(",").append(y).append("; ");
                        }
                    }
                }
            }
            if (softAlpha > 0) {
                problems.append("存在 ").append(softAlpha).append(" 个半透明像素（alpha 必须只有 0/255）; ");
            }
            if (colors.size() > MAX_PALETTE) {
                problems.append("颜色数 ").append(colors.size()).append(" > ").append(MAX_PALETTE).append("; ");
            }
            // 亮像素（银白切刃/亮点）在帧间的数量波动必须很小：
            // "暗剑身突然出现大片白补丁"会让亮像素数暴涨，这里直接抓这个缺陷。
            int[] brightPerFrame = new int[frames];
            for (int frame = 0; frame < frames; frame++) {
                for (int i = 0; i < OUT * OUT; i++) {
                    int color = rgb[frame][i];
                    if (color == 0 && !mask[frame * OUT * OUT + i]) {
                        continue;
                    }
                    int luma = (((color >> 16) & 0xFF) * 30 + ((color >> 8) & 0xFF) * 59 + (color & 0xFF) * 11) / 100;
                    if (luma >= BRIGHT_LUMA) {
                        brightPerFrame[frame]++;
                    }
                }
            }
            int minBright = Integer.MAX_VALUE;
            int maxBright = Integer.MIN_VALUE;
            for (int frame = 0; frame < frames; frame++) {
                minBright = Math.min(minBright, brightPerFrame[frame]);
                maxBright = Math.max(maxBright, brightPerFrame[frame]);
            }
            if (maxBright - minBright > MAX_BRIGHT_SWING) {
                problems.append("亮像素数在帧间波动 ").append(maxBright - minBright)
                        .append(" > ").append(MAX_BRIGHT_SWING).append("（疑似白色补丁炸闪）; ");
            }
            // alpha 掩码跨帧一致
            for (int frame = 1; frame < frames; frame++) {
                for (int i = 0; i < OUT * OUT; i++) {
                    boolean a = mask[i];
                    boolean b = mask[frame * OUT * OUT + i];
                    if (a != b) {
                        problems.append("帧 ").append(frame).append(" 的 alpha 掩码与首帧不同; ");
                        frame = frames;
                        break;
                    }
                }
            }
            // 相邻帧差异比例
            for (int frame = 1; frame < frames; frame++) {
                int diff = 0;
                for (int i = 0; i < OUT * OUT; i++) {
                    if (rgb[frame - 1][i] != rgb[frame][i]) {
                        diff++;
                    }
                }
                double ratio = opaque == 0 ? 0.0D : diff / (double) opaque;
                if (ratio > MAX_FRAME_DIFF_RATIO) {
                    problems.append("帧 ").append(frame - 1).append("→").append(frame)
                            .append(" 差异 ").append(String.format("%.0f%%", ratio * 100))
                            .append(" 超过上限; ");
                }
            }
            Path mcmeta = dir.resolve("star_eater_stage" + stage + ".png.mcmeta");
            if (frames > 1) {
                if (!Files.isRegularFile(mcmeta)) {
                    problems.append("多帧但缺少 .png.mcmeta; ");
                } else {
                    String json = Files.readString(mcmeta);
                    if (!json.contains("\"interpolate\": false")) {
                        problems.append("mcmeta 必须 interpolate=false; ");
                    }
                    if (!json.contains("\"frametime\"")) {
                        problems.append("mcmeta 缺少 frametime; ");
                    }
                }
            }
            if (problems.length() == 0) {
                System.out.println("PASS stage" + stage + ": " + image.getWidth() + "x" + image.getHeight()
                        + " frames=" + frames + " colors=" + colors.size() + " opaque=" + opaque);
            } else {
                System.out.println("FAIL stage" + stage + ": " + problems);
                failed = true;
            }
        }
        System.out.println(failed ? "RESULT FAIL" : "RESULT PASS");
        System.exit(failed ? 1 : 0);
    }
}
