package local.stargarden.compat.equipment;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;

public final class EquipmentPages {
    // Stable IDs shared with campaign_navigation.py and build_gear_guide.py.
    public static final long GUIDE = 0x15833C8EAA8EC0A0L;
    public static final long ARMOR = 0x2A358FE51A17E292L;
    public static final long ACCESSORIES = 0x6D1938960E54F1E0L;
    public static final long STARTER_POSSESSION = 0x0AF1EE5B491B7133L;

    private EquipmentPages() {}

    public static boolean includes(Chapter chapter) {
        long group = chapter.getGroup().id;
        return chapter.id == GUIDE || group == ARMOR || group == ACCESSORIES;
    }

    public static boolean recipeDiagram(Quest quest) {
        return quest.getChapter().getGroup().id == ACCESSORIES;
    }
}
