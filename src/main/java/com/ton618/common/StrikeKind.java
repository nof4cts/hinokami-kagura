package com.ton618.common;

/** The three things the remote can call down. */
public enum StrikeKind {
    /** The cannon's beam pierces a star; the star collapses into TON 618, which is driven down onto the target. */
    HOLE("hole"),
    /** The Sun is torn out of its place and falls onto the target. */
    SUN("sun"),
    /** An Archangel descends from heaven and cleaves the land in two with a sword of fire. */
    ANGEL("angel");

    public final String key;

    StrikeKind(String key) {
        this.key = key;
    }

    public static StrikeKind byId(int id) {
        StrikeKind[] v = values();
        return id >= 0 && id < v.length ? v[id] : HOLE;
    }

    public StrikeKind next() {
        return byId((ordinal() + 1) % values().length);
    }
}
