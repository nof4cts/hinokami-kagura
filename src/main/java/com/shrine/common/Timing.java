package com.shrine.common;

/**
 * Every move's timeline, in ticks from the cast. Shared by the server (when damage and cuts happen) and the
 * client (animation, cutscene shots, sounds and impact frames), so all of them land on the same frame.
 */
public final class Timing {
    private Timing() {}

    // Dismantle: a flick of the hand and the cut is already there
    public static final int DISMANTLE_CUT = 2;
    public static final int DISMANTLE_END = 30;

    // Barrage: a storm of Dismantles
    public static final int BARRAGE_FIRST = 3;
    public static final int BARRAGE_EVERY = 2;
    public static final int BARRAGE_COUNT = 16;
    public static final int BARRAGE_END = BARRAGE_FIRST + BARRAGE_EVERY * BARRAGE_COUNT + 20;

    // Cleave: a touch, and the target comes apart in a net of cuts
    public static final int CLEAVE_HIT = 3;
    public static final int CLEAVE_END = 30;

    // Spiderweb: a palm to the ground; the ground falls apart
    public static final int WEB_HIT = 7;
    public static final int WEB_END = 50;

    // Black Flash: the distortion of cursed energy within a millionth of a second of the blow
    public static final int FLASH_HIT = 4;
    public static final int FLASH_END = 30;

    // Open (the fire arrow)
    public static final int FUGA_IGNITE = 0;     // the hand opens; fire pours out of it
    public static final int FUGA_CARD = 8;       // title card
    public static final int FUGA_BOW = 30;       // the fire takes the shape of a bow
    public static final int FUGA_DRAW = 48;      // an arrow of fire is nocked and drawn
    public static final int FUGA_RELEASE = 80;   // loosed
    public static final int FUGA_FLIGHT = 9;     // ticks to the target
    public static final int FUGA_IMPACT = FUGA_RELEASE + FUGA_FLIGHT;
    public static final int FUGA_END = 175;

    // Domain Expansion
    public static final int DOMAIN_SIGN = 0;     // the hand sign
    public static final int DOMAIN_CARD = 26;    // "Domain Expansion"
    public static final int DOMAIN_NAME = 40;    // its name
    public static final int DOMAIN_OPEN = 52;    // it opens: the world turns
    public static final int DOMAIN_SPREAD = 40;  // ticks for it to reach full radius
    public static final int DOMAIN_CUT_END = 150; // the cutscene ends, the domain goes on

    // World-Cutting Slash
    public static final int WS_SIGN1 = 0;
    public static final int WS_SIGN2 = 18;
    public static final int WS_SIGN3 = 36;
    public static final int WS_AIM = 54;         // the hand comes up to cut
    public static final int WS_SLASH = 78;       // the cut
    public static final int WS_SPLIT = 92;       // the world comes apart along it
    public static final int WS_END = 200;

    public static int end(Move m) {
        return switch (m) {
            case DISMANTLE -> DISMANTLE_END;
            case BARRAGE -> BARRAGE_END;
            case CLEAVE -> CLEAVE_END;
            case SPIDERWEB -> WEB_END;
            case BLACK_FLASH -> FLASH_END;
            case FUGA -> FUGA_END;
            case DOMAIN -> DOMAIN_OPEN + 20 * 120;   // bounded by the config duration in practice
            case WORLD_SLASH -> WS_END;
        };
    }
}
