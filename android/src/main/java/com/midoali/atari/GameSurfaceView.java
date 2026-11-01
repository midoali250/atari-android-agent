package com.midoali.atari;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.midoali.atari.core.ControlLayout;
import com.midoali.atari.core.DisplayMath;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Native-style game surface: a dedicated render thread draws decoded RGB565
 * frames into an ARGB_8888 bitmap and presents them with SurfaceHolder Canvas.
 * This avoids GL shaders and also avoids relying on UI-thread invalidation.
 */
public final class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    public static final int FILTER_NEAREST = 0, FILTER_LINEAR = 1;
    private final EmulatorEngine engine;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final AtomicBoolean surfaceReady = new AtomicBoolean();
    private final Object wake = new Object();
    private Bitmap bitmap;
    private int[] argb;
    private int lastSerial = -1, frameWidth, frameHeight;
    private float frameAspect;
    private volatile boolean running;
    private volatile int videoMode = ControlLayout.VIDEO_ENHANCED;
    private volatile int displayMode = ControlLayout.DISPLAY_FIT;
    private volatile int appliedFilter = -1, appliedVideoMode = -1, appliedDisplayMode = -1;
    private volatile int[] appliedViewport = new int[] {0, 0, 0, 0};
    private volatile boolean nonBlackFrameSeen;
    private volatile long presentedFrames;
    private Thread thread;

    public GameSurfaceView(Context context, EmulatorEngine engine) {
        super(context);
        this.engine = engine;
        getHolder().addCallback(this);
        setFocusable(false);
        setContentDescription(context.getString(R.string.video_surface_description));
    }

    public void setVideoMode(int mode) {
        videoMode = Math.max(ControlLayout.VIDEO_ORIGINAL, Math.min(ControlLayout.VIDEO_ENHANCED, mode));
        requestFrame();
    }
    public void setDisplayMode(int mode) {
        displayMode = mode == ControlLayout.DISPLAY_STRETCH ? ControlLayout.DISPLAY_STRETCH : ControlLayout.DISPLAY_FIT;
        requestFrame();
    }
    public void setBilinear(boolean enabled) { setVideoMode(enabled ? ControlLayout.VIDEO_SMOOTH : ControlLayout.VIDEO_ORIGINAL); }
    public int appliedFilter() { return appliedFilter; }
    public int appliedVideoMode() { return appliedVideoMode; }
    public int appliedDisplayMode() { return appliedDisplayMode; }
    public int[] appliedViewport() { return appliedViewport.clone(); }
    public int frameSerial() { return lastSerial; }
    public boolean nonBlackFrameSeen() { return nonBlackFrameSeen; }
    public long presentedFrames() { return presentedFrames; }
    public boolean enhancedAvailable() { return true; }
    public void requestFrame() { Thread t = thread; if (t != null) LockSupport.unpark(t); }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady.set(true);
        startThread();
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { requestFrame(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady.set(false);
        stopThread();
    }

    private void startThread() {
        if (running) return;
        running = true;
        thread = new Thread(this::renderLoop, "UMK3-Video");
        thread.setDaemon(true);
        thread.start();
    }
    private void stopThread() {
        running = false;
        Thread t = thread;
        if (t != null) {
            LockSupport.unpark(t);
            try { t.join(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        thread = null;
    }

    private void renderLoop() {
        long next = System.nanoTime();
        while (running && surfaceReady.get()) {
            boolean drew = drawOnce();
            if (drew) presentedFrames++;
            next += 16_666_667L;
            long delay = next - System.nanoTime();
            if (delay > 0) LockSupport.parkNanos(this, Math.min(delay, 16_666_667L));
            else next = System.nanoTime();
        }
    }

    private boolean drawOnce() {
        SurfaceHolder holder = getHolder();
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas == null) return false;
            canvas.drawColor(Color.BLACK);
            EmulatorEngine.Frame frame = engine.frame();
            float aspect = frameAspect;
            if (frame != null && frame.acquire()) {
                try {
                    aspect = frame.aspect;
                    if (frame.serial != lastSerial) {
                        ensureBitmap(frame.width, frame.height);
                        if (bitmap != null) {
                            convert(frame);
                            lastSerial = frame.serial;
                            frameAspect = aspect;
                        }
                    }
                } finally { frame.release(); }
            }
            if (bitmap == null || lastSerial < 0) return true;
            int mode = videoMode;
            boolean filter = mode != ControlLayout.VIDEO_ORIGINAL;
            paint.setFilterBitmap(filter);
            int[] vp = DisplayMath.viewport(canvas.getWidth(), canvas.getHeight(), aspect,
                    displayMode == ControlLayout.DISPLAY_STRETCH);
            dst.set(vp[0], vp[1], vp[0] + vp[2], vp[1] + vp[3]);
            canvas.drawBitmap(bitmap, null, dst, paint);
            appliedFilter = filter ? FILTER_LINEAR : FILTER_NEAREST;
            appliedVideoMode = mode; appliedDisplayMode = displayMode; appliedViewport = vp;
            return true;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            if (canvas != null) {
                try { holder.unlockCanvasAndPost(canvas); } catch (RuntimeException ignored) { }
            }
        }
    }

    private void ensureBitmap(int w, int h) {
        if (w <= 0 || h <= 0 || w > 1024 || h > 576) return;
        if (bitmap != null && frameWidth == w && frameHeight == h) return;
        if (bitmap != null) bitmap.recycle();
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(false);
        argb = new int[w * h];
        frameWidth = w; frameHeight = h;
    }

    private void convert(EmulatorEngine.Frame frame) {
        int w = frame.width, h = frame.height;
        byte[] p = frame.pixels;
        boolean any = false;
        for (int i = 0, n = w * h; i < n; i++) {
            int lo = p[i * 2] & 0xff, hi = p[i * 2 + 1] & 0xff;
            int v = (hi << 8) | lo;
            if (v != 0) any = true;
            int r = (v >> 11) & 31, g = (v >> 5) & 63, b = v & 31;
            argb[i] = 0xff000000 | ((r * 255 / 31) << 16) | ((g * 255 / 63) << 8) | (b * 255 / 31);
        }
        bitmap.setPixels(argb, 0, w, 0, 0, w, h);
        if (any) nonBlackFrameSeen = true;
    }

    public Bitmap captureForTest() {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.BLACK);
        if (bitmap != null) {
            int[] vp = DisplayMath.viewport(w, h, frameAspect, displayMode == ControlLayout.DISPLAY_STRETCH);
            c.drawBitmap(bitmap, null, new RectF(vp[0], vp[1], vp[0] + vp[2], vp[1] + vp[3]), paint);
        }
        return out;
    }

    public void release() {
        stopThread();
        if (bitmap != null) { bitmap.recycle(); bitmap = null; }
        argb = null;
    }
}
