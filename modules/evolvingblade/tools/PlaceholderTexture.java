import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * PHASE 1 占位贴图生成器（不是最终美术）。
 *
 * 由于 PHASE 1 不交付美术资源，这里用程序化像素画生成一张 32x32 的占位图标，
 * 保证物品在游戏里"有图可显示"而不是紫黑格。PHASE 2 会用正式的 8 张 32x32 贴图替换。
 *
 * 用法： java tools/PlaceholderTexture.java <输出 png 路径>
 */
public final class PlaceholderTexture {

    private static final String[] ART = {
            ".......WW.......",
            "......WSSW......",
            "......WSSW......",
            "......WSDW......",
            "......WSSW......",
            "......WSDW......",
            "......WSSW......",
            "......WSDW......",
            "......WSSW......",
            "....GGGGGGGG....",
            "....GgGPPGgG....",
            "......pHHp......",
            ".......HH.......",
            ".......HH.......",
            "......gGGg......",
            "................",
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: java PlaceholderTexture.java <output.png>");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        Files.createDirectories(out.getParent());
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            String row = ART[y];
            for (int x = 0; x < 16; x++) {
                int argb = color(row.charAt(x));
                if (argb == 0) {
                    continue;
                }
                // 2x 放大到 32x32
                image.setRGB(x * 2, y * 2, argb);
                image.setRGB(x * 2 + 1, y * 2, argb);
                image.setRGB(x * 2, y * 2 + 1, argb);
                image.setRGB(x * 2 + 1, y * 2 + 1, argb);
            }
        }
        ImageIO.write(image, "png", out.toFile());
        System.out.println("wrote " + out.toAbsolutePath() + " (" + new File(out.toString()).length() + " bytes)");
    }

    private static int color(char c) {
        return switch (c) {
            case 'W' -> 0xFFE9F1FF; // 刃口高光
            case 'S' -> 0xFFA9B6C9; // 剑身
            case 'D' -> 0xFF5C6B80; // 剑身暗部
            case 'G' -> 0xFFE0B33C; // 护手亮金
            case 'g' -> 0xFF8A6A1E; // 护手暗金
            case 'H' -> 0xFF4A3220; // 握柄
            case 'P' -> 0xFFB07CE8; // 紫水晶
            case 'p' -> 0xFF6E3FA8; // 紫水晶暗部
            default -> 0;
        };
    }
}
