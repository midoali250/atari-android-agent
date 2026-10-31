package com.midoali.atari.core;

/** Libretro joypad IDs, not Android key codes. Sega A=Y, B=B, C=A. */
public final class SegaButtons {
    private SegaButtons() {}
    public static final int B = 0;
    public static final int A = 1;
    public static final int START = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;
    public static final int LEFT = 6;
    public static final int RIGHT = 7;
    public static final int C = 8;
    public static final int X = 9;
    public static final int Y = 10;
    public static final int Z = 11;

    public static int sanitize(int mask) { return mask & 0xffff; }
    public static boolean pressed(int mask, int button) {
        return button >= 0 && button < 16 && (mask & (1 << button)) != 0;
    }
}
