import dev.ftb.mods.ftblibrary.snbt.SNBT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Add collection tasks to the live FTB book, preserving its own SNBT layout and local edits. */
class UpdateEquipmentTasks {
    private static Map<String, CompoundTag> byId(ListTag list) {
        Map<String, CompoundTag> result = new HashMap<>();
        for (Tag tag : list) {
            CompoundTag node = (CompoundTag) tag;
            if (result.put(node.getString("id"), node) != null)
                throw new IllegalStateException("Duplicate FTB ID " + node.getString("id"));
        }
        return result;
    }

    private static boolean update(CompoundTag live, CompoundTag source, boolean starter) {
        var existing = byId(live.getList("quests", Tag.TAG_COMPOUND));
        boolean changed = false;
        for (var pair : byId(source.getList("quests", Tag.TAG_COMPOUND)).entrySet()) {
            if (starter && !pair.getKey().equals("6B3AEF5579D1E440")) continue;
            var quest = existing.get(pair.getKey());
            if (quest == null) throw new IllegalStateException("Missing installed quest " + pair.getKey());
            var desired = pair.getValue();
            var tasks = quest.getList("tasks", Tag.TAG_COMPOUND);
            var installed = byId(tasks);
            for (Tag entry : desired.getList("tasks", Tag.TAG_COMPOUND)) {
                CompoundTag task = (CompoundTag)entry;
                String id = task.getString("id");
                if (starter && !id.equals("0AF1EE5B491B7133")) continue;
                if (installed.containsKey(id)) {
                    var prior = installed.get(id);
                    if (!prior.getString("type").equals(task.getString("type"))
                            || !prior.getCompound("item").getString("id").equals(task.getCompound("item").getString("id")))
                        throw new IllegalStateException("Local task conflicts with collection task " + id);
                    continue;
                }
                tasks.add(task.copy());
                quest.put("tasks", tasks);
                changed = true;
            }
            if (!starter) {
                for (String field : new String[]{"optional", "disable_toast"}) {
                    if (quest.getBoolean(field) != desired.getBoolean(field)) {
                        quest.putBoolean(field, desired.getBoolean(field));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    public static void main(String[] args) throws Exception {
        Path liveDir = Path.of(args[0]), stagedDir = Path.of(args[1]), out = Path.of(args[2]);
        try (var stream = Files.list(stagedDir)) {
            for (Path path : stream.sorted().toList()) {
                String name = path.getFileName().toString();
                boolean starter = name.equals("00_lostworld_chapter1.snbt");
                if (!(starter || name.equals("gear_field_guide.snbt") || name.startsWith("equipment_") && name.endsWith(".snbt"))) continue;
                var live = SNBT.tryRead(liveDir.resolve(name));
                var staged = SNBT.tryRead(path);
                if (!live.getString("id").equals(staged.getString("id"))) throw new IllegalStateException("Wrong chapter " + name);
                if (update(live, staged, starter)) SNBT.tryWrite(out.resolve(name), live);
            }
        }
    }
}
