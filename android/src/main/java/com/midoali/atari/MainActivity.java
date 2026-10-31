package com.midoali.atari;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;

import com.midoali.atari.core.CoreStatus;
import com.midoali.atari.core.RomInventory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Launchable packaging-verification screen. No ROM is executed by this activity. */
public final class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applySystemBarInsets(findViewById(R.id.content));

        ((TextView) findViewById(R.id.build_info)).setText(getString(
                R.string.build_info, BuildConfig.VERSION_NAME, BuildConfig.APPLICATION_ID));
        ((TextView) findViewById(R.id.core_status)).setText(
                CoreStatus.isEmulatorImplemented() ? CoreStatus.message() : getString(R.string.core_not_implemented));
        findViewById(R.id.refresh_roms).setOnClickListener(view -> displayRomInventory());
        displayRomInventory();
    }

    private void displayRomInventory() {
        TextView output = findViewById(R.id.rom_inventory);
        try {
            String[] names = getAssets().list("roms");
            List<String> roms = RomInventory.supportedNames(
                    Arrays.asList(names == null ? new String[0] : names));
            if (roms.isEmpty()) {
                output.setText(R.string.no_bundled_roms);
            } else {
                output.setText(getString(R.string.bundled_roms, String.join("\n", roms)));
            }
        } catch (IOException exception) {
            output.setText(R.string.rom_read_error);
        }
    }

    private static void applySystemBarInsets(View view) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((target, windowInsets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                target.setPadding(left + insets.left, top + insets.top,
                        right + insets.right, bottom + insets.bottom);
            } else {
                target.setPadding(left + windowInsets.getSystemWindowInsetLeft(),
                        top + windowInsets.getSystemWindowInsetTop(),
                        right + windowInsets.getSystemWindowInsetRight(),
                        bottom + windowInsets.getSystemWindowInsetBottom());
            }
            return windowInsets;
        });
        view.requestApplyInsets();
    }
}
