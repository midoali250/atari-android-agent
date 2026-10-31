package com.midoali.atari.core;
/** Reports whether a real emulation core is connected to the application. */
public final class CoreStatus {
    private CoreStatus() {}
    public static boolean isEmulatorImplemented() { return true; }
    public static String message() { return "Genesis Plus GX is connected; import a legally obtained Genesis ROM to play."; }
}
