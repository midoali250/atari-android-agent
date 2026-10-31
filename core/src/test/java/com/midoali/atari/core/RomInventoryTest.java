package com.midoali.atari.core;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public final class RomInventoryTest {
    @Test
    public void acceptsSupportedExtensionsCaseInsensitively() {
        assertEquals(
                List.of("a.a26", "b.BIN", "c.a78"),
                RomInventory.supportedNames(List.of("c.a78", "a.a26", "b.BIN")));
    }

    @Test
    public void ignoresDocumentationHiddenFilesAndNestedPaths() {
        assertEquals(
                List.of("game.a26"),
                RomInventory.supportedNames(Arrays.asList(
                        "README.md", ".gitkeep", "nested/game.bin", "..\\game.bin", null, "game.a26")));
    }

    @Test
    public void deduplicatesAndSortsNames() {
        assertEquals(
                List.of("alpha.bin", "Zebra.a26"),
                RomInventory.supportedNames(List.of("Zebra.a26", "alpha.bin", "alpha.bin")));
    }

    @Test
    public void emptyInputProducesEmptyInventory() {
        assertEquals(List.of(), RomInventory.supportedNames(List.of()));
    }
}
