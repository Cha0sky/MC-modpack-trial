import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * 生成 GameTest 用的空结构 NBT（9x5x9，只有一层基岩地板）。
 *
 * <p>NeoForge/原版 GameTest 必须从 {@code data/<ns>/structure/<name>.nbt} 读取结构模板，
 * 缺文件会直接报 {@code Missing test structure}。这里按 1.21.1
 * {@code StructureTemplate#load} 的字段约定（{@code size} / {@code palette} / {@code blocks} / {@code entities}）
 * 手工写出压缩 NBT，避免依赖任何外部工具。
 *
 * <p>用法：{@code java tools/GenerateGameTestStructure.java <输出 .nbt 路径>}
 */
public final class GenerateGameTestStructure {

    private static final int SIZE_X = 9;
    private static final int SIZE_Y = 5;
    private static final int SIZE_Z = 9;

    private static final byte TAG_END = 0;
    private static final byte TAG_INT = 3;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: java GenerateGameTestStructure.java <output.nbt>");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        Files.createDirectories(out.getParent());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(buffer)) {
            data.writeByte(TAG_COMPOUND);
            data.writeUTF("");

            // size: [x, y, z]
            // StructureTemplate.load uses a LIST of INTs for size.
            data.writeByte(TAG_LIST);
            data.writeUTF("size");
            data.writeByte(TAG_INT);
            data.writeInt(3);
            data.writeInt(SIZE_X);
            data.writeInt(SIZE_Y);
            data.writeInt(SIZE_Z);

            // palette: [{Name: "minecraft:bedrock"}]
            data.writeByte(TAG_LIST);
            data.writeUTF("palette");
            data.writeByte(TAG_COMPOUND);
            data.writeInt(1);
            data.writeByte(TAG_STRING);
            data.writeUTF("Name");
            data.writeUTF("minecraft:bedrock");
            data.writeByte(TAG_END);

            // blocks: 一层基岩地板
            data.writeByte(TAG_LIST);
            data.writeUTF("blocks");
            data.writeByte(TAG_COMPOUND);
            data.writeInt(SIZE_X * SIZE_Z);
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    data.writeByte(TAG_LIST);
                    data.writeUTF("pos");
                    data.writeByte(TAG_INT);
                    data.writeInt(3);
                    data.writeInt(x);
                    data.writeInt(0);
                    data.writeInt(z);
                    data.writeByte(TAG_INT);
                    data.writeUTF("state");
                    data.writeInt(0);
                    data.writeByte(TAG_END);
                }
            }

            // entities: []
            data.writeByte(TAG_LIST);
            data.writeUTF("entities");
            data.writeByte(TAG_COMPOUND);
            data.writeInt(0);

            data.writeByte(TAG_END);
        }

        try (DataOutputStream file = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(out)))) {
            file.write(buffer.toByteArray());
        }
        System.out.println("wrote " + out.toAbsolutePath() + " (" + Files.size(out) + " bytes, "
                + SIZE_X + "x" + SIZE_Y + "x" + SIZE_Z + ")");
    }

    private GenerateGameTestStructure() throws IOException {}
}
