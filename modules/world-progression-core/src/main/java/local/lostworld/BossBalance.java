package local.lostworld;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

final class BossBalance {
    static final Map<ResourceLocation, Profile> PROFILES = load();

    record Profile(int tier, double nativeHp, double soloHp, double damageScale, float hitCap,
                   double l2FloorBonus, boolean nativeHealthConfig) {
        Profile {
            if (tier < 1 || tier > 6 || !(nativeHp > 0 && soloHp > 0 && damageScale > 0 && hitCap > 0)
                    || l2FloorBonus < 0 || tier == 1 && hitCap > 10) {
                throw new IllegalArgumentException("Invalid encounter budget for tier " + tier);
            }
        }

        double healthMultiplier(boolean terraDifficulty, double hostilityBonus) {
            // Confluence uses 0.66 in solo Classic. Keep the native difficulty/player
            // modifier intact: it still scales higher difficulties and larger parties.
            double reference = nativeHp * (terraDifficulty ? 0.66F : 1.0);
            // Only offset the fixed L2 minimum already represented by our reference.
            // Higher regional levels, trait bonuses and difficulty scaling still apply.
            reference *= 1 + Math.clamp(hostilityBonus, 0, l2FloorBonus);
            return soloHp / reference;
        }

        float incoming(float damage) { return (float)Math.min(damage * damageScale, hitCap); }
        float finalLimit(float damage) { return Math.min(damage, hitCap); }
    }

    private static Map<ResourceLocation, Profile> load() {
        String path = "/data/world_progression_core/balance/encounters.json";
        try (var reader = new InputStreamReader(Objects.requireNonNull(BossBalance.class.getResourceAsStream(path)),
                StandardCharsets.UTF_8)) {
            var tiers = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("tiers");
            Map<ResourceLocation, Profile> profiles = new HashMap<>();
            for (int i = 0; i < tiers.size(); i++) {
                for (var value : tiers.get(i).getAsJsonArray()) {
                    JsonObject row = value.getAsJsonObject();
                    var key = ResourceLocation.parse(row.get("id").getAsString());
                    var profile = new Profile(i + 1, row.get("nativeHp").getAsDouble(), row.get("soloHp").getAsDouble(),
                            row.get("damageScale").getAsDouble(), row.get("hitCap").getAsFloat(),
                            row.has("l2FloorBonus") ? row.get("l2FloorBonus").getAsDouble() : 0,
                            row.has("nativeHealthConfig") && row.get("nativeHealthConfig").getAsBoolean());
                    if (profiles.put(key, profile) != null) throw new IllegalArgumentException("Duplicate boss: " + key);
                }
            }
            return Map.copyOf(profiles);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read encounter budgets", e);
        }
    }

    private BossBalance() {}
}
