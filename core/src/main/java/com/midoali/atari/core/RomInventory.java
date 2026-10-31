package com.midoali.atari.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;

/** Lists candidate ROM filenames; it does not validate ROM contents or rights. */
public final class RomInventory {
    private RomInventory() {}

    public static List<String> supportedNames(Collection<String> names) {
        Objects.requireNonNull(names, "names");
        TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            if (name == null || name.contains("/") || name.contains("\\")) {
                continue;
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.endsWith(".a26") || normalized.endsWith(".bin")
                    || normalized.endsWith(".a78")) {
                result.add(name);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(result));
    }
}
