package local.lostworld;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

final class StructureCompassCheck {
    private static final String DATA = "/data/world_progression_core/worldgen/";
    private static final String LANG = "/assets/world_progression_core/lang/";

    private static JsonObject resource(String path) {
        try (var reader = new InputStreamReader(
                Objects.requireNonNull(StructureCompassCheck.class.getResourceAsStream(path), path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException error) {
            throw new AssertionError(path, error);
        }
    }

    static void run() {
        for (String name : List.of("abandoned_workshop", "starter_town")) {
            JsonObject structure = resource(DATA + "structure/" + name + ".json");
            JsonObject set = resource(DATA + "structure_set/" + name + ".json");
            if (!set.getAsJsonArray("structures").get(0).getAsJsonObject().get("structure").getAsString()
                    .equals("world_progression_core:" + name)) throw new AssertionError("Unlisted " + name);
            JsonObject placement = set.getAsJsonObject("placement");
            if (!placement.get("type").getAsString().equals("minecraft:random_spread")) throw new AssertionError(name);
            if (name.equals("starter_town") && (placement.get("frequency").getAsFloat() != 0
                    || !structure.get("start_pool").getAsString().equals("minecraft:empty")))
                throw new AssertionError("The compass index must never generate a second town");
            if (name.equals("abandoned_workshop") && placement.has("frequency") && placement.get("frequency").getAsFloat() == 0)
                throw new AssertionError("Natural workshops need actual starts");
            String languageKey = "structure.world_progression_core." + name;
            String searchTerm = name.equals("starter_town") ? "集镇" : "工坊";
            if (!resource(LANG + "zh_cn.json").get(languageKey).getAsString().contains(searchTerm)
                    || resource(LANG + "en_us.json").get(languageKey).getAsString().isBlank())
                throw new AssertionError("Missing compass search name for " + name);
        }

        CompoundTag oldSave = new CompoundTag();
        BlockPos home = new BlockPos(-250, 77, 36);
        oldSave.putLong("settlement", home.offset(-4, -1, 6).asLong());
        oldSave.putLong("home", home.asLong());
        ChapterData town = ChapterData.load(oldSave, null);
        if (!home.equals(StarterTownLocator.nextHome(town, List.of()))
                || StarterTownLocator.nextHome(town, List.of(home)) != null
                || StarterTownLocator.nextHome(new ChapterData(), List.of()) != null
                || StarterTownLocator.nextHome(null, List.of()) != null)
            throw new AssertionError("The compass must resolve the saved home exactly once");
        System.out.println("PASS: compass names and placements, existing town coordinates, missing and next town");
    }
}
