package com.midoali.atari;

import android.content.Context;
import android.content.SharedPreferences;
import com.midoali.atari.core.ControlLayout;

final class SettingsStore {
    private final SharedPreferences prefs;
    SettingsStore(Context context) {
        prefs = context.getSharedPreferences("sega-settings", Context.MODE_PRIVATE);
    }
    ControlLayout load() {
        ControlLayout value = new ControlLayout();
        value.padX = prefs.getFloat("padX", value.padX);
        value.padY = prefs.getFloat("padY", value.padY);
        value.faceX = prefs.getFloat("faceX", value.faceX);
        value.faceY = prefs.getFloat("faceY", value.faceY);
        value.startX = prefs.getFloat("startX", value.startX);
        value.startY = prefs.getFloat("startY", value.startY);
        value.setScale(prefs.getFloat("scale", value.scale));
        value.setOpacity(prefs.getFloat("opacity", value.opacity));
        value.bilinear = prefs.getBoolean("bilinear", true);
        value.haptics = prefs.getBoolean("haptics", true);
        value.move(0, value.padX, value.padY);
        value.move(1, value.faceX, value.faceY);
        value.move(2, value.startX, value.startY);
        return value;
    }
    void save(ControlLayout value) {
        prefs.edit().putFloat("padX", value.padX).putFloat("padY", value.padY)
                .putFloat("faceX", value.faceX).putFloat("faceY", value.faceY)
                .putFloat("startX", value.startX).putFloat("startY", value.startY)
                .putFloat("scale", value.scale).putFloat("opacity", value.opacity)
                .putBoolean("bilinear", value.bilinear).putBoolean("haptics", value.haptics).apply();
    }
    SharedPreferences preferences() { return prefs; }
}
