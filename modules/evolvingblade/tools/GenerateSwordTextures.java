import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 程序化生成 8 张 32x32 透明像素剑贴图（stage0..stage7）。
 *
 * <p>纯 JDK（只用 java.base 的 zip/CRC + java.io），不依赖 AWT 或任何图片库，
 * 因此可以在任意 JDK 上复现。刻意不使用任何下载素材：所有像素都是这里的几何原语画出来的。
 *
 * <p>用法：{@code java tools/GenerateSwordTextures.java <输出目录>}
 */
public final class GenerateSwordTextures {

    private static final int SIZE = 32;

    /** 剑身起始点（护手位置），剑刃沿 (1,-1) 方向生长。 */
    private static final int BASE_X = 11;
    private static final int BASE_Y = 21;

    /** 每个阶段的剑刃长度（沿对角线的步数）。 */
    private static final int[] BLADE_LENGTH = {9, 11, 13, 15, 16, 17, 18, 19};
    /** 每个阶段的剑刃半宽（垂直方向的像素偏移数）。 */
    private static final int[] BLADE_HALF_WIDTH = {1, 1, 1, 2, 2, 2, 2, 3};
    /** 剑身核心色（0xAARRGGBB）。 */
    private static final int[] CORE = {
            0xFF9AA0A8, 0xFFA9B4BE, 0xFFAFCBE4, 0xFF9FE8D8,
            0xFF7FE3FF, 0xFFC9A8FF, 0xFFFFD98A, 0xFFFFF3C4};
    /** 剑身边缘色。 */
    private static final int[] EDGE = {
            0xFF4A4E55, 0xFF5C636C, 0xFF5A7590, 0xFF3E8F86,
            0xFF2B7FA8, 0xFF6A45B0, 0xFFB07A20, 0xFFFF7BD5};
    /** 护手 / 宝石颜色。 */
    private static final int[] GUARD = {
            0xFF6B5A3E, 0xFF7A6A48, 0xFF8A7A52, 0xFF6FA89A,
            0xFF4E7FA0, 0xFF7A5AB0, 0xFFD4A544, 0xFFFFE08A};

    private static int[][] pixels;

    private GenerateSwordTextures() {}

    public static void main(String[] args) throws IOException {
        Path output = Path.of(args.length > 0 ? args[0] : ".");
        Files.createDirectories(output);
        for (int stage = 0; stage < 8; stage++) {
            pixels = new int[SIZE][SIZE];
            drawSword(stage);
            Path file = output.resolve("star_eater_stage" + stage + ".png");
            Files.write(file, encodePng(pixels));
            System.out.println("wrote " + file + " (" + Files.size(file) + " bytes)");
        }
    }

    private static void drawSword(int stage) {
        int length = BLADE_LENGTH[stage];
        int halfWidth = BLADE_HALF_WIDTH[stage];
        int core = CORE[stage];
        int edge = EDGE[stage];
        int guard = GUARD[stage];

        // 1) 剑刃：沿 (1,-1) 对角线，末端两格收窄成剑尖。
        for (int i = 0; i <= length; i++) {
            int cx = BASE_X + i;
            int cy = BASE_Y - i;
            int half = halfWidth;
            if (i >= length - 1) {
                half = 0;
            } else if (i == length - 2) {
                half = Math.max(0, halfWidth - 1);
            }
            for (int o = -half; o <= half; o++) {
                int color = (Math.abs(o) == half) ? edge : core;
                if (o == 0 && stage >= 2 && i % 3 == 1) {
                    // 符文：沿剑脊的高光像素，越高阶越密。
                    color = blend(core, 0xFFFFFFFF, 0.55);
                }
                plot(cx + o, cy + o, color);
            }
        }

        // 2) 护手：垂直于剑刃。
        for (int j = -3; j <= 3; j++) {
            plot(BASE_X + j, BASE_Y + j, guard);
        }

        // 3) 剑柄：向 (1,1) 反方向延伸，深棕色，柄尾有配重球。
        for (int i = 1; i <= 5; i++) {
            int x = BASE_X - i;
            int y = BASE_Y + i;
            plot(x, y, 0xFF3B2B1B);
            plot(x + 1, y, 0xFF5A4326);
        }
        plot(BASE_X - 6, BASE_Y + 6, guard);
        plot(BASE_X - 6, BASE_Y + 7, guard);
        plot(BASE_X - 7, BASE_Y + 6, guard);

        // 4) 高阶灵光：剑刃外圈的半透明辉光（仍是 32x32 透明贴图的一部分）。
        if (stage >= 4) {
            int alpha = stage >= 6 ? 0x9A : 0x66;
            for (int i = 0; i <= length; i++) {
                int cx = BASE_X + i;
                int cy = BASE_Y - i;
                glow(cx + halfWidth + 1, cy + halfWidth + 1, withAlpha(edge, alpha));
                glow(cx - halfWidth - 1, cy - halfWidth - 1, withAlpha(core, alpha));
                glow(cx, cy - halfWidth - 1, withAlpha(core, alpha / 2));
            }
        }
        // 5) 终焉：环绕剑身的星屑。
        if (stage == 7) {
            int[][] sparks = {{6, 10}, {10, 6}, {22, 24}, {26, 16}, {18, 2}, {28, 8}, {4, 18}};
            for (int[] spark : sparks) {
                plot(spark[0], spark[1], 0xFFFFF3C4);
                glow(spark[0] + 1, spark[1], 0x88FF7BD5);
                glow(spark[0], spark[1] + 1, 0x88FF7BD5);
            }
        }
    }

    private static int blend(int base, int over, double ratio) {
        int a = (base >>> 24) & 0xFF;
        int r = (base >>> 16) & 0xFF;
        int g = (base >>> 8) & 0xFF;
        int b = base & 0xFF;
        int or = (over >>> 16) & 0xFF;
        int og = (over >>> 8) & 0xFF;
        int ob = over & 0xFF;
        r = (int) Math.round(r * (1 - ratio) + or * ratio);
        g = (int) Math.round(g * (1 - ratio) + og * ratio);
        b = (int) Math.round(b * (1 - ratio) + ob * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static void plot(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
            return;
        }
        int src = color;
        int dst = pixels[y][x];
        int sa = (src >>> 24) & 0xFF;
        if (sa == 0) {
            return;
        }
        if (sa == 0xFF || dst == 0) {
            pixels[y][x] = src;
            return;
        }
        int da = (dst >>> 24) & 0xFF;
        double s = sa / 255.0;
        double d = (da / 255.0) * (1 - s);
        double outA = s + d;
        int r = (int) Math.round((((src >>> 16) & 0xFF) * s + ((dst >>> 16) & 0xFF) * d) / outA);
        int g = (int) Math.round((((src >>> 8) & 0xFF) * s + ((dst >>> 8) & 0xFF) * d) / outA);
        int b = (int) Math.round(((src & 0xFF) * s + (dst & 0xFF) * d) / outA);
        pixels[y][x] = ((int) Math.round(outA * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    /** 只在不覆盖已有像素的位置画半透明辉光。 */
    private static void glow(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
            return;
        }
        if (((pixels[y][x] >>> 24) & 0xFF) > 0x20) {
            return;
        }
        plot(x, y, color);
    }

    private static byte[] encodePng(int[][] image) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < SIZE; y++) {
            raw.write(0); // filter type 0 (None)
            for (int x = 0; x < SIZE; x++) {
                int c = image[y][x];
                raw.write((c >>> 16) & 0xFF);
                raw.write((c >>> 8) & 0xFF);
                raw.write(c & 0xFF);
                raw.write((c >>> 24) & 0xFF);
            }
        }
        byte[] header = new byte[13];
        header[0] = (byte) (SIZE >>> 24);
        header[1] = (byte) (SIZE >>> 16);
        header[2] = (byte) (SIZE >>> 8);
        header[3] = (byte) SIZE;
        header[4] = (byte) (SIZE >>> 24);
        header[5] = (byte) (SIZE >>> 16);
        header[6] = (byte) (SIZE >>> 8);
        header[7] = (byte) SIZE;
        header[8] = 8; // bit depth
        header[9] = 6; // color type RGBA
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(png, "IHDR", header);
        chunk(png, "IDAT", deflate(raw.toByteArray()));
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] length = new byte[4];
        length[0] = (byte) (data.length >>> 24);
        length[1] = (byte) (data.length >>> 16);
        length[2] = (byte) (data.length >>> 8);
        length[3] = (byte) data.length;
        out.write(length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long value = crc.getValue();
        out.write(new byte[] {
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
    }
}
