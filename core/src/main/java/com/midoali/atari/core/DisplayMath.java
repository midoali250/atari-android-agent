package com.midoali.atari.core;

/** Pure viewport math shared by the renderer and its unit tests. */
public final class DisplayMath {
    private DisplayMath() { }

    /** x, y, width, height in GL pixels. Fit never crops; stretch fills without cropping. */
    public static int[] viewport(int surfaceWidth, int surfaceHeight, float aspect, boolean stretch) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return new int[] {0, 0, 0, 0};
        if (stretch || !(aspect > 0f) || !Float.isFinite(aspect))
            return new int[] {0, 0, surfaceWidth, surfaceHeight};
        int width = surfaceWidth;
        int height = Math.max(1, Math.round(width / aspect));
        if (height > surfaceHeight) {
            height = surfaceHeight;
            width = Math.max(1, Math.round(height * aspect));
        }
        return new int[] {(surfaceWidth - width) / 2, (surfaceHeight - height) / 2, width, height};
    }

    /** Texture coordinates with the vertical flip required by the core's top-left pixel origin. */
    public static float[] sourceCoordinates(int frameWidth, int frameHeight) {
        float right = frameWidth > 0 ? 1f : 0f;
        float top = frameHeight > 0 ? 1f : 0f;
        return new float[] {0f, top, right, top, 0f, 0f, right, 0f};
    }
}
