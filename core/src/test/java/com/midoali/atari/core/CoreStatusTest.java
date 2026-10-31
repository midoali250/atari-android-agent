package com.midoali.atari.core;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
public final class CoreStatusTest {
    @Test public void reportsConnectedEmulator() {
        assertTrue(CoreStatus.isEmulatorImplemented());
        assertTrue(CoreStatus.message().contains("Genesis Plus GX"));
    }
}
