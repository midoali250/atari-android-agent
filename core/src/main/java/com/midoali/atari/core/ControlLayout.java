package com.midoali.atari.core;

/** Normalized centers remain valid across screen sizes and orientations. */
public final class ControlLayout {
    public float padX = 0.16f, padY = 0.69f;
    public float faceX = 0.83f, faceY = 0.68f;
    public float startX = 0.5f, startY = 0.88f;
    public float scale = 1f;
    public float opacity = 0.72f;
    public boolean bilinear = true;
    public boolean haptics = true;

    public void move(int group, float x, float y) {
        x = PadMath.clamp(x, 0.12f, 0.88f);
        y = PadMath.clamp(y, 0.22f, 0.88f);
        if (group == 0) { padX = x; padY = y; }
        else if (group == 1) { faceX = x; faceY = y; }
        else { startX = x; startY = y; }
    }
    public void setScale(float value) { scale = PadMath.clamp(value, 0.7f, 1.5f); }
    public void setOpacity(float value) { opacity = PadMath.clamp(value, 0.3f, 0.95f); }
    public void reset() {
        padX = 0.16f; padY = 0.69f; faceX = 0.83f; faceY = 0.68f;
        startX = 0.5f; startY = 0.88f; scale = 1f; opacity = 0.72f;
    }
    public ControlLayout copy() {
        ControlLayout result = new ControlLayout();
        result.padX = padX; result.padY = padY; result.faceX = faceX; result.faceY = faceY;
        result.startX = startX; result.startY = startY; result.scale = scale; result.opacity = opacity;
        result.bilinear = bilinear; result.haptics = haptics;
        return result;
    }
}
