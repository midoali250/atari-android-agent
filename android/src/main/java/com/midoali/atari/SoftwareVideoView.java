package com.midoali.atari;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import com.midoali.atari.core.ControlLayout;
import com.midoali.atari.core.DisplayMath;
import java.nio.ByteBuffer;

/**
 * Reliable Canvas/Bitmap video path.
 *
 * <p>v0.4 used a GLSurfaceView + custom GLES2 shader. On some production GPUs the
 * surface presented black while audio and input kept running. This view avoids
 * EGL/GLSL entirely: the core's RGB565 frame is copied into a Bitmap and drawn by
 * the standard Android Canvas compositor, which is hardware-accelerated by the
 * window and works across drivers. Filtering is still genuine bilinear; the
 * enhanced mode adds a 2x pre-scale pass for a smoother result than nearest.
 */
public final class SoftwareVideoView extends View {
    public static final int FILTER_NEAREST = 0, FILTER_LINEAR = 1;
    private final EmulatorEngine engine;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private Bitmap bitmap, prescale;
    private Canvas prescaleCanvas;
    private ByteBuffer pixelBuffer;
    private int lastSerial = -1;
    private int textureWidth, textureHeight;
    private volatile int videoMode = ControlLayout.VIDEO_ENHANCED;
    private volatile int displayMode = ControlLayout.DISPLAY_FIT;
    private volatile int appliedFilter = -1, appliedVideoMode = -1, appliedDisplayMode = -1;
    private volatile int[] appliedViewport = new int[] {0, 0, 0, 0};
    private volatile boolean nonBlackFrameSeen;

    public SoftwareVideoView(Context context, EmulatorEngine engine) {
        super(context);
        this.engine = engine;
        setWillNotDraw(false);
        setBackgroundColor(Color.BLACK);
        setContentDescription(context.getString(R.string.video_surface_description));
    }

    public void setVideoMode(int mode) {
        videoMode = Math.max(ControlLayout.VIDEO_ORIGINAL, Math.min(ControlLayout.VIDEO_ENHANCED, mode));
        postInvalidateOnAnimation();
    }
    public void setDisplayMode(int mode) {
        displayMode = mode == ControlLayout.DISPLAY_STRETCH ? ControlLayout.DISPLAY_STRETCH : ControlLayout.DISPLAY_FIT;
        postInvalidateOnAnimation();
    }
    public void setBilinear(boolean enabled) {
        setVideoMode(enabled ? ControlLayout.VIDEO_SMOOTH : ControlLayout.VIDEO_ORIGINAL);
    }
    public int appliedFilter() { return appliedFilter; }
    public int appliedVideoMode() { return appliedVideoMode; }
    public int appliedDisplayMode() { return appliedDisplayMode; }
    public int[] appliedViewport() { return appliedViewport.clone(); }
    public int frameSerial() { return lastSerial; }
    public boolean nonBlackFrameSeen() { return nonBlackFrameSeen; }
    public boolean enhancedAvailable() { return true; }

    /** Thread-safe refresh request from the emulation thread. */
    public void requestFrame() { postInvalidateOnAnimation(); }

    private boolean ensureBitmap(int width, int height) {
        if (width <= 0 || height <= 0 || width > 1024 || height > 576) return false;
        if (bitmap != null && textureWidth == width && textureHeight == height) return true;
        if (bitmap != null) bitmap.recycle();
        if (prescale != null) { prescale.recycle(); prescale = null; prescaleCanvas = null; }
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        bitmap.setHasAlpha(false);
        pixelBuffer = ByteBuffer.allocateDirect(width * height * 2);
        textureWidth = width; textureHeight = height;
        return true;
    }

    private void copyFrame(EmulatorEngine.Frame frame) {
        int bytes = frame.width * frame.height * 2;
        pixelBuffer.clear();
        pixelBuffer.put(frame.pixels, 0, bytes);
        pixelBuffer.flip();
        if (bitmap.getRowBytes() == frame.width * 2) {
            bitmap.copyPixelsFromBuffer(pixelBuffer);
        } else {
            // Rare stride mismatch: expand RGB565 to ARGB_8888 safely.
            Bitmap argb = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888);
            int[] row = new int[frame.width];
            for (int y = 0; y < frame.height; y++) {
                for (int x = 0; x < frame.width; x++) {
                    int lo = frame.pixels[(y * frame.width + x) * 2] & 0xff;
                    int hi = frame.pixels[(y * frame.width + x) * 2 + 1] & 0xff;
                    int p = (hi << 8) | lo;
                    int r = (p >> 11) & 31, g = (p >> 5) & 63, b = p & 31;
                    row[x] = 0xff000000 | ((r * 255 / 31) << 16) | ((g * 255 / 63) << 8) | (b * 255 / 31);
                }
                argb.setPixels(row, 0, frame.width, 0, y, frame.width, 1);
            }
            bitmap.recycle();
            bitmap = argb;
            bitmap.setHasAlpha(false);
        }
        if (!nonBlackFrameSeen) {
            for (int i = 0; i < bytes; i += 2) {
                int p = (frame.pixels[i] & 0xff) | ((frame.pixels[i + 1] & 0xff) << 8);
                if (p != 0) { nonBlackFrameSeen = true; break; }
            }
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        EmulatorEngine.Frame frame = engine.frame();
        float aspect = textureWidth > 0 ? textureWidth / (float) textureHeight : 0f;
        if (frame != null && frame.acquire()) {
            try {
                aspect = frame.aspect;
                if (frame.serial != lastSerial && ensureBitmap(frame.width, frame.height)) {
                    copyFrame(frame);
                    lastSerial = frame.serial;
                }
            } finally {
                frame.release();
            }
        }
        if (bitmap == null || lastSerial < 0) return;

        int mode = videoMode;
        boolean filter = mode != ControlLayout.VIDEO_ORIGINAL;
        paint.setFilterBitmap(filter);
        Bitmap source = bitmap;
        if (mode == ControlLayout.VIDEO_ENHANCED) {
            int pw = textureWidth * 2, ph = textureHeight * 2;
            if (prescale == null || prescale.getWidth() != pw || prescale.getHeight() != ph) {
                if (prescale != null) prescale.recycle();
                prescale = Bitmap.createBitmap(pw, ph, Bitmap.Config.RGB_565);
                prescale.setHasAlpha(false);
                prescaleCanvas = new Canvas(prescale);
            }
            prescaleCanvas.drawBitmap(bitmap, null, new RectF(0, 0, pw, ph), paint);
            source = prescale;
        }
        int[] viewport = DisplayMath.viewport(getWidth(), getHeight(), aspect,
                displayMode == ControlLayout.DISPLAY_STRETCH);
        dst.set(viewport[0], viewport[1], viewport[0] + viewport[2], viewport[1] + viewport[3]);
        canvas.drawBitmap(source, null, dst, paint);
        appliedFilter = filter ? FILTER_LINEAR : FILTER_NEAREST;
        appliedVideoMode = mode;
        appliedDisplayMode = displayMode;
        appliedViewport = viewport;
    }

    /** Test helper: draw the current view into a bitmap for pixel assertions. */
    public Bitmap captureForTest() {
        Bitmap out = Bitmap.createBitmap(Math.max(1, getWidth()), Math.max(1, getHeight()), Bitmap.Config.ARGB_8888);
        draw(new Canvas(out));
        return out;
    }

    public void release() {
        if (bitmap != null) { bitmap.recycle(); bitmap = null; }
        if (prescale != null) { prescale.recycle(); prescale = null; prescaleCanvas = null; }
        pixelBuffer = null;
    }
}
