package com.midoali.atari.core;

/** Pure, testable eight-way D-pad hit testing with a stable radial dead zone. */
public final class PadMath {
    private PadMath() {}
    public static int direction(float dx, float dy, float radius) {
        if (radius <= 0 || dx * dx + dy * dy < radius * radius * 0.22f * 0.22f) return 0;
        if (dx * dx + dy * dy > radius * radius * 1.35f * 1.35f) return 0;
        double angle = Math.atan2(dy, dx);
        int sector = (int)Math.floor((angle + Math.PI / 8 + 2 * Math.PI) / (Math.PI / 4)) % 8;
        switch (sector) {
            case 0: return bit(SegaButtons.RIGHT);
            case 1: return bit(SegaButtons.RIGHT) | bit(SegaButtons.DOWN);
            case 2: return bit(SegaButtons.DOWN);
            case 3: return bit(SegaButtons.LEFT) | bit(SegaButtons.DOWN);
            case 4: return bit(SegaButtons.LEFT);
            case 5: return bit(SegaButtons.LEFT) | bit(SegaButtons.UP);
            case 6: return bit(SegaButtons.UP);
            default: return bit(SegaButtons.RIGHT) | bit(SegaButtons.UP);
        }
    }
    public static boolean inCircle(float x, float y, float cx, float cy, float radius) {
        float dx = x - cx, dy = y - cy;
        return radius > 0 && dx * dx + dy * dy <= radius * radius;
    }
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    private static int bit(int id) { return 1 << id; }
}
