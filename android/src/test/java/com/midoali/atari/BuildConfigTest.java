package com.midoali.atari;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BuildConfigTest {
    @Test
    public void variantsHaveDistinctIdentities() {
        if (BuildConfig.DEBUG) {
            assertEquals("com.midoali.atari.debug", BuildConfig.APPLICATION_ID);
            assertEquals("0.2.0-debug", BuildConfig.VERSION_NAME);
        } else {
            assertEquals("com.midoali.atari", BuildConfig.APPLICATION_ID);
            assertEquals("0.2.0", BuildConfig.VERSION_NAME);
        }
        assertEquals(2, BuildConfig.VERSION_CODE);
    }

    @Test
    public void buildTypeFlagMatchesPackageName() {
        assertTrue(BuildConfig.APPLICATION_ID.startsWith("com.midoali.atari"));
        assertEquals(BuildConfig.DEBUG, BuildConfig.APPLICATION_ID.endsWith(".debug"));
        assertFalse(BuildConfig.VERSION_NAME.isEmpty());
    }
}
