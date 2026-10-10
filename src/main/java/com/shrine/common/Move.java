package com.shrine.common;

/** Every move of the technique, with its base cooldown (ticks) and whether it plays a cutscene. */
public enum Move {
    DISMANTLE("dismantle", 10, false),
    BARRAGE("barrage", 70, false),
    CLEAVE("cleave", 30, false),
    SPIDERWEB("spiderweb", 140, false),
    BLACK_FLASH("black_flash", 50, false),
    FUGA("fuga", 500, true),
    DOMAIN("domain", 1600, true),
    WORLD_SLASH("world_slash", 2200, true);

    public final String key;
    public final int cooldown;
    public final boolean cutscene;

    Move(String key, int cooldown, boolean cutscene) {
        this.key = key;
        this.cooldown = cooldown;
        this.cutscene = cutscene;
    }

    public static Move byId(int id) {
        Move[] v = values();
        return id >= 0 && id < v.length ? v[id] : DISMANTLE;
    }
}
