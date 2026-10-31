package com.midoali.atari.core;

/** Libretro joypad IDs used by the Genesis Plus GX frontend. */
public final class SegaButtons {
    public static final int B = 0, A = 1, C = 8, MODE = 2, START = 3;
    public static final int UP = 4, DOWN = 5, LEFT = 6, RIGHT = 7;
    // Genesis Plus GX maps the six-button pad's X/Y/Z to RetroPad L/X/R.
    public static final int Y = 9, X = 10, Z = 11;
    public static final int ALL = (1 << B) | (1 << A) | (1 << C) | (1 << MODE) | (1 << START)
            | (1 << UP) | (1 << DOWN) | (1 << LEFT) | (1 << RIGHT)
            | (1 << X) | (1 << Y) | (1 << Z);
    private SegaButtons() { }
    public static int sanitize(int mask) { return mask & ALL; }
    public static boolean pressed(int mask, int id) { return id >= 0 && id < 16 && (mask & (1 << id)) != 0; }
    public static int cancelOpposites(int mask) {
        mask = sanitize(mask);
        if ((mask & (1 << UP)) != 0 && (mask & (1 << DOWN)) != 0) mask &= ~((1 << UP) | (1 << DOWN));
        if ((mask & (1 << LEFT)) != 0 && (mask & (1 << RIGHT)) != 0) mask &= ~((1 << LEFT) | (1 << RIGHT));
        return mask;
    }
}
