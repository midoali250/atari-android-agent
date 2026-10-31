package com.midoali.atari.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CoreStatusTest {
    @Test
    public void doesNotClaimToImplementAnEmulator() {
        assertFalse(CoreStatus.isEmulatorImplemented());
        assertTrue(CoreStatus.message().contains("No emulator or game"));
    }
}
