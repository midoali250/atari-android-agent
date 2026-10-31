package com.midoali.atari.core;

import java.io.IOException;
import java.nio.ByteBuffer;

/** JNI bridge to the pinned, unmodified Genesis Plus GX libretro core. */
public final class SegaCore {
    static { System.loadLibrary("sega_jni"); }
    private SegaCore() {}

    public static native boolean nativeLoad(String path, boolean sixButton) throws IOException;
    /** Returns stereo frame count; metadata: width, height, serial, samples, sampled input. */
    public static native int nativeRun(short[] audio, ByteBuffer video, int[] metadata);
    public static native void nativeSetInput(int mask);
    public static native void nativeClose();
    public static native double nativeFps();
    public static native int nativeSampleRate();
    public static native int nativeSaveSize();
    public static native boolean nativePersistSave(String path, boolean restore);
    public static native int nativeTestInput(int id);
    public static native byte[] nativeTestRam(int offset, int length);
}
