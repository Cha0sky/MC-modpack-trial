package local.lostworld;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Persistent attribution for hostile summons whose native damage has no reference to their original boss. */
public final class BossSummonOrigin {
    public static final String KEY = "lostworld_boss_origin";
    public static final String KING = "terra_entity:king_slime";
    public static final String EYE = "terra_entity:eye_of_cthulhu";
    public static final String BROOD = "goety:brood_mother";
    public static final String WALL = "terra_entity:wall_of_flesh";
    public static final String HILL = "terra_entity:hill_of_flesh";

    private BossSummonOrigin() {}

    public static boolean is(Entity entity, String id) {
        return entity != null && id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    public static Entity mark(Entity child, String boss) {
        child.getPersistentData().putString(KEY, boss);
        return child;
    }

    public static Entity inherit(Entity parent, Entity child) {
        String boss = parent.getPersistentData().getString(KEY);
        if (KING.equals(boss) || EYE.equals(boss) || BROOD.equals(boss) || WALL.equals(boss) || HILL.equals(boss))
            mark(child, boss);
        return child;
    }
}
