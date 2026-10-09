import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

/**
 * <b>文本通道备用导出器</b>：把 {@code art/v2/masters} 的八张母版导出为
 * {@value #OUT}×{@value #OUT} RGBA PNG，并在 stdout 打印每阶段的 Base64。
 *
 * <h2>什么时候用它</h2>
 * 正常情况下请用 {@code tools/ExportSwordArtwork.java}（直接写出 128×128 与接触表）。
 * 只有在"运行环境不允许写入工程子目录、只能通过文本通道落地二进制"时才用本工具：
 * 它把 PNG 编码成 Base64 打印出来，调用方再把 Base64 写进一个 {@code .b64} 文本文件，
 * 最后在可写环境里解码成 PNG。**不要**把 Base64 当成源码的一部分提交。
 *
 * <p>算法与 {@code ExportSwordArtwork} 完全一致（可见轮廓裁边 → 等比最近邻缩放 →
 * alpha 硬量化），两处常量必须一起改：
 * <ul>
 *   <li>可见轮廓阈值 {@value #BOUNDS_THRESHOLD}（与 {@code READY.md} 的审计阈值一致）；</li>
 *   <li>alpha 硬量化阈值 {@value #ALPHA_HARD_THRESHOLD}（同为 128：母版里 alpha &lt; 128 的
 *       微弱边缘噪点一律清成透明，不留白底/半透明孤岛）。</li>
 * </ul>
 *
 * <p>用法：{@code java tools/DumpArtworkBase64.java <项目根>}
 */
public final class DumpArtworkBase64 {

    private static final int OUT = Integer.getInteger("eb.out", 64);
    private static final double MARGIN_RATIO = 0.06D;
    /** 与 READY.md 的母版审计阈值一致。 */
    private static final int BOUNDS_THRESHOLD = 128;
    /** 与 ExportSwordArtwork.ALPHA_HARD_THRESHOLD 一致：清除 alpha<128 的微弱噪点。 */
    private static final int ALPHA_HARD_THRESHOLD = 128;
    private static final int STAGES = 8;

    private DumpArtworkBase64() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path masters = root.resolve("art").resolve("v2").resolve("masters");
        int margin = (int) Math.round(OUT * MARGIN_RATIO);
        int usable = OUT - margin * 2;
        for (int stage = 0; stage < STAGES; stage++) {
            BufferedImage master = ImageIO.read(masters.resolve("stage" + stage + ".png").toFile());
            int[] b = visibleBounds(master);
            int sw = b[2] - b[0];
            int sh = b[3] - b[1];
            double scale = Math.min((double) usable / sw, (double) usable / sh);
            int dw = Math.max(1, (int) Math.round(sw * scale));
            int dh = Math.max(1, (int) Math.round(sh * scale));
            int ox = (OUT - dw) / 2;
            int oy = (OUT - dh) / 2;
            int[][] out = new int[OUT][OUT];
            int opaque = 0;
            for (int y = 0; y < dh; y++) {
                int sy = b[1] + Math.min(sh - 1, (int) ((y + 0.5D) / scale));
                for (int x = 0; x < dw; x++) {
                    int sx = b[0] + Math.min(sw - 1, (int) ((x + 0.5D) / scale));
                    int argb = master.getRGB(sx, sy);
                    if (((argb >>> 24) & 0xFF) < ALPHA_HARD_THRESHOLD) {
                        continue;
                    }
                    out[oy + y][ox + x] = 0xFF000000 | (argb & 0x00FFFFFF);
                    opaque++;
                }
            }
            byte[] png = PngWriter.encode(out);
            System.out.println("STAGE " + stage + " size=" + OUT + "x" + OUT + " srcVisible=[" + b[0] + ","
                    + b[1] + "," + b[2] + "," + b[3] + "] scaled=" + dw + "x" + dh + " opaque=" + opaque
                    + " bytes=" + png.length);
            System.out.println("B64 " + stage + " " + Base64.getEncoder().encodeToString(png));
        }
    }

    private static int[] visibleBounds(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) >= BOUNDS_THRESHOLD) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {minX, minY, maxX + 1, maxY + 1};
    }

    /** 纯字节 PNG（RGBA8、filter 0），与 {@code ExportSwordArtwork} 使用完全相同的编码器。 */
    static final class PngWriter {
        static byte[] encode(int[][] pixels) throws Exception {
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
            return png.toByteArray();
        }

        private static void writeInt(java.io.ByteArrayOutputStream out, int value) {
            out.write((value >>> 24) & 0xFF);
            out.write((value >>> 16) & 0xFF);
            out.write((value >>> 8) & 0xFF);
            out.write(value & 0xFF);
        }

        private static void chunk(java.io.ByteArrayOutputStream out, String type, byte[] data) throws Exception {
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
    }
}
