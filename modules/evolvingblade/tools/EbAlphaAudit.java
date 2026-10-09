import java.awt.image.BufferedImage;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/** 检查已导出贴图的 alpha 分布是否满足"硬阈值 128"（是否存在 1..127 的半透明残留）。 */
public final class EbAlphaAudit {
    private EbAlphaAudit() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        for (int stage = 0; stage < 8; stage++) {
            Path file = dir.resolve("star_eater_stage" + stage + ".png");
            BufferedImage image = ImageIO.read(file.toFile());
            int soft = 0;
            int zero = 0;
            int full = 0;
            int minSoft = 255;
            int maxSoft = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int a = (image.getRGB(x, y) >>> 24) & 0xFF;
                    if (a == 0) {
                        zero++;
                    } else if (a == 255) {
                        full++;
                    } else {
                        soft++;
                        minSoft = Math.min(minSoft, a);
                        maxSoft = Math.max(maxSoft, a);
                    }
                }
            }
            System.out.println("stage" + stage + " " + image.getWidth() + "x" + image.getHeight()
                    + " alpha0=" + zero + " alpha255=" + full + " soft(1..254)=" + soft
                    + (soft > 0 ? " range=[" + minSoft + "," + maxSoft + "]" : ""));
        }
    }
}
