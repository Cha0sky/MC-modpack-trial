package local.lostworld;

/** Server-owned first chapter state; notifications are emitted only on a real transition. */
public final class ChapterState {
    public static final String KING_SLIME = "terra_entity:king_slime";
    private boolean active;
    private boolean defeated;
    public ChapterState(boolean active, boolean defeated) { this.active = active; this.defeated = defeated; }
    public boolean active() { return active; }
    public boolean defeated() { return defeated; }
    public void activate() { active = true; }
    public boolean defeat(String entityId) {
        if (!active || defeated || !KING_SLIME.equals(entityId)) return false;
        defeated = true;
        return true;
    }
    public boolean blocksEvil(boolean evilBiome, boolean spectator) {
        return active && !defeated && evilBiome && !spectator;
    }
    public void setDefeated(boolean value) { defeated = value; }
}
