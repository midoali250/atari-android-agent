package com.midoali.atari;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import com.midoali.atari.core.SegaButtons;
import com.midoali.atari.core.SegaCore;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** One emulation thread owns audio pacing; input and lifecycle are thread-safe. */
final class EmulatorEngine {
    static final class Frame {
        final byte[] pixels;
        final int width, height, serial;
        Frame(byte[] pixels, int width, int height, int serial) {
            this.pixels = pixels; this.width = width; this.height = height; this.serial = serial;
        }
    }
    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Object frameMonitor = new Object();
    private final AtomicInteger input = new AtomicInteger();
    private final short[] audio = new short[32768];
    private final ByteBuffer video = ByteBuffer.allocateDirect(1024 * 576 * 2).order(ByteOrder.nativeOrder());
    private final int[] metadata = new int[5];
    private volatile boolean running, paused, muted, closed, disposed;
    private volatile Frame frame;
    private volatile String error;
    private Thread thread;
    private AudioTrack track;
    private File saveFile;
    private long framesSinceSave;

    void load(File rom, File save) throws IOException {
        lifecycle.lock();
        try {
            if (disposed) throw new IOException("Emulator engine was destroyed");
            closeLocked();
            closed = false; error = null; frame = null; saveFile = save;
            SegaCore.nativeLoad(rom.getAbsolutePath(), false);
            if (save.isFile() && SegaCore.nativeSaveSize() == (int)save.length()) {
                if (!SegaCore.nativePersistSave(save.getAbsolutePath(), true))
                    Log.w("SegaEngine", "Could not restore battery save");
            }
            int rate = SegaCore.nativeSampleRate();
            int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IOException("Audio output is unavailable");
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(Math.max(min * 3, 8192)).setTransferMode(AudioTrack.MODE_STREAM).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IOException("Audio initialization failed");
            track.setVolume(muted ? 0f : 1f);
            track.play();
            running = true; paused = false;
            thread = new Thread(this::runLoop, "SegaEmulation");
            thread.start();
        } catch (IOException | RuntimeException exception) {
            closeLocked();
            throw exception;
        } finally { lifecycle.unlock(); }
    }
    private void runLoop() {
        long nextSave = System.nanoTime() + 5_000_000_000L;
        double frameNanos = 1_000_000_000.0 / SegaCore.nativeFps();
        long nextFrame = System.nanoTime();
        while (running) {
            if (paused || closed) { sleepQuietly(10); nextFrame = System.nanoTime(); continue; }
            try {
                int count = SegaCore.nativeRun(audio, video, metadata);
                if (count < 0) throw new IllegalStateException("Emulator closed");
                int width = metadata[0], height = metadata[1];
                byte[] pixels = new byte[width * height * 2];
                video.position(0); video.get(pixels, 0, pixels.length); video.clear();
                synchronized (frameMonitor) {
                    frame = new Frame(pixels, width, height, metadata[2]);
                }
                int offset = 0;
                while (running && !paused && offset < count * 2) {
                    int written = track.write(audio, offset, Math.min(2048, count * 2 - offset), AudioTrack.WRITE_BLOCKING);
                    if (written <= 0) throw new IllegalStateException("Audio write failed: " + written);
                    offset += written;
                }
                if (++framesSinceSave >= 300 || System.nanoTime() >= nextSave) {
                    persistSave(); framesSinceSave = 0; nextSave = System.nanoTime() + 5_000_000_000L;
                }
                // Audio normally provides the clock. This fallback also handles cores without audio.
                if (count == 0) {
                    nextFrame += (long)frameNanos;
                    long delay = nextFrame - System.nanoTime();
                    if (delay > 0) sleepQuietly(delay / 1_000_000);
                    else nextFrame = System.nanoTime();
                }
            } catch (RuntimeException exception) {
                if (running && !closed) {
                    error = exception.getMessage();
                    Log.e("SegaEngine", "Emulation stopped", exception);
                }
                running = false;
            }
        }
    }
    Frame frame() { synchronized (frameMonitor) { return frame; } }
    String error() { return error; }
    boolean isLoaded() { return running && !closed; }
    boolean isPaused() { return paused; }
    int inputMask() { return input.get(); }
    void setInput(int mask) {
        int sanitized = SegaButtons.sanitize(mask);
        input.set(sanitized); SegaCore.nativeSetInput(sanitized);
    }
    void setPaused(boolean value) {
        lifecycle.lock();
        try {
            if (closed || !running) return;
            paused = value;
            if (value) { setInput(0); track.pause(); track.flush(); }
            else track.play();
        } finally { lifecycle.unlock(); }
    }
    void setMuted(boolean value) {
        muted = value;
        lifecycle.lock();
        try { if (track != null) track.setVolume(value ? 0f : 1f); }
        finally { lifecycle.unlock(); }
    }
    synchronized void persistSave() {
        File destination = saveFile;
        if (destination == null || closed || SegaCore.nativeSaveSize() <= 0) return;
        File temp = new File(destination.getAbsolutePath() + ".tmp");
        if (SegaCore.nativePersistSave(temp.getAbsolutePath(), false) && !temp.renameTo(destination))
            Log.w("SegaEngine", "Could not commit battery save");
    }
    void close() { lifecycle.lock(); try { disposed = true; closeLocked(); } finally { lifecycle.unlock(); } }
    private void closeLocked() {
        running = false; closed = true; input.set(0); SegaCore.nativeSetInput(0);
        // Unblock a pending audio write before joining the emulation thread.
        if (track != null) {
            try { track.pause(); track.flush(); } catch (IllegalStateException ignored) { }
        }
        if (thread != null && Thread.currentThread() != thread) {
            try { thread.join(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        }
        thread = null;
        if (track != null) { track.release(); track = null; }
        if (saveFile != null && SegaCore.nativeSaveSize() > 0) {
            File temp = new File(saveFile.getAbsolutePath() + ".tmp");
            if (SegaCore.nativePersistSave(temp.getAbsolutePath(), false)) temp.renameTo(saveFile);
        }
        SegaCore.nativeClose(); saveFile = null;
    }
    private static void sleepQuietly(long millis) {
        try { Thread.sleep(Math.max(1, millis)); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }
}
