import dev.ftb.mods.ftblibrary.snbt.SNBT;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Edit only the moved reward and its former icon in an FTB-resaved prologue. */
class UpdateStarterReward {
    static CompoundTag find(ListTag entries, String id) {
        for (Tag entry : entries) {
            if (entry instanceof CompoundTag data && id.equals(data.getString("id"))) return data;
        }
        throw new IllegalStateException("Missing FTB object " + id);
    }

    public static void main(String[] args) throws Exception {
        var current = SNBT.tryRead(Path.of(args[0]));
        var staged = SNBT.tryRead(Path.of(args[1]));
        var quests = current.getList("quests", Tag.TAG_COMPOUND);
        var revised = staged.getList("quests", Tag.TAG_COMPOUND);
        var arrival = find(quests, args[3]).getList("rewards", Tag.TAG_COMPOUND);
        var workshop = find(quests, args[4]);
        var workshopRewards = workshop.getList("rewards", Tag.TAG_COMPOUND);
        var reward = find(find(revised, args[3]).getList("rewards", Tag.TAG_COMPOUND), args[5]);
        String item = reward.getCompound("item").getString("id");
        int found = 0;
        for (ListTag list : new ListTag[]{arrival, workshopRewards}) {
            for (int i = list.size() - 1; i >= 0; i--) {
                var existing = list.getCompound(i);
                if (!args[5].equals(existing.getString("id"))) continue;
                if (!item.equals(existing.getCompound("item").getString("id")))
                    throw new IllegalStateException("The local starter reward item has changed");
                list.remove(i);
                found++;
            }
        }
        if (found != 1) throw new IllegalStateException("Expected one copy of the existing starter reward");
        arrival.add(0, reward.copy());
        if (item.equals(workshop.getCompound("icon").getString("id")))
            workshop.put("icon", find(revised, args[4]).getCompound("icon").copy());
        SNBT.tryWrite(Path.of(args[2]), current);
    }
}
