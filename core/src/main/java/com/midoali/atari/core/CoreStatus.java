package com.midoali.atari.core;

/** Honest build-scaffold status; this is not an emulator implementation. */
public final class CoreStatus {
    private CoreStatus() {}

    public static boolean isEmulatorImplemented() {
        return false;
    }

    public static String message() {
        return "No emulator or game is implemented yet. This APK verifies Android packaging only.";
    }
}
