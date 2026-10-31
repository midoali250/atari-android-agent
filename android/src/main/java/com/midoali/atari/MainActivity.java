package com.midoali.atari;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.midoali.atari.core.ControlLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int OPEN_ROM = 41;
    static final String EXTRA_TEST_ROM = "com.midoali.atari.TEST_ROM";
    private final EmulatorEngine engine = new EmulatorEngine();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SettingsStore settingsStore;
    private ControlLayout layout;
    private TouchControlsView controls;
    private GLSurfaceView surface;
    private GameRenderer renderer;
    private TextView status;
    private Button pauseButton, editDone;
    private LinearLayout toolbar;
    private boolean foreground, userPaused, loading, destroyed, toolbarVisible;
    private AlertDialog settingsDialog;
    private final Runnable hideToolbar = () -> { toolbar.setVisibility(View.GONE); toolbarVisible = false; };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        settingsStore = new SettingsStore(this); layout = settingsStore.load();
        String bundledAsset = findBundledRomAsset();
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        surface = new GLSurfaceView(this); surface.setEGLContextClientVersion(2);
        surface.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        renderer = new GameRenderer(engine);
        renderer.setVideoMode(layout.videoMode); renderer.setDisplayMode(layout.displayMode);
        surface.setRenderer(renderer);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        engine.setFrameListener(() -> surface.requestRender());
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        controls = new TouchControlsView(this);
        controls.setLayout(layout); controls.setInputSink(engine::setInput);
        controls.setOnEmptyTapListener(this::toggleToolbar);
        root.addView(controls, new FrameLayout.LayoutParams(-1, -1));
        toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(5), dp(12), dp(5)); toolbar.setBackgroundColor(0xb0101820);
        if (bundledAsset == null) toolbar.addView(toolbarButton(R.string.import_rom, v -> chooseRom()));
        pauseButton = toolbarButton(R.string.pause, v -> { userPaused = !userPaused; syncPause(); }); toolbar.addView(pauseButton);
        toolbar.addView(toolbarButton(R.string.settings, v -> showSettings()));
        toolbar.addView(toolbarButton(R.string.reset_game, v -> confirmReset()));
        toolbar.setVisibility(View.GONE);
        root.addView(toolbar, new FrameLayout.LayoutParams(-1, dp(52), Gravity.TOP));
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(17); status.setGravity(Gravity.CENTER);
        status.setPadding(dp(28), dp(70), dp(28), dp(28)); status.setText(R.string.import_hint);
        root.addView(status, new FrameLayout.LayoutParams(-1, -1));
        editDone = toolbarButton(R.string.finish_layout, v -> finishEditing()); editDone.setVisibility(View.GONE);
        root.addView(editDone, new FrameLayout.LayoutParams(dp(150), dp(48), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            toolbar.setPadding(dp(12) + insets.getSystemWindowInsetLeft(), dp(5) + insets.getSystemWindowInsetTop(),
                    dp(12) + insets.getSystemWindowInsetRight(), dp(5));
            return insets;
        });
        hideSystemBars();
        String testPath = BuildConfig.DEBUG ? getIntent().getStringExtra(EXTRA_TEST_ROM) : null;
        if (testPath != null) loadRom(new File(testPath));
        else if (bundledAsset != null) loadBundledRom(bundledAsset);
        else {
            String saved = settingsStore.preferences().getString("lastRom", null);
            if (saved != null && new File(saved).isFile()) loadRom(new File(saved));
        }
    }
    private void toggleToolbar() {
        handler.removeCallbacks(hideToolbar);
        toolbarVisible = !toolbarVisible;
        toolbar.setVisibility(toolbarVisible ? View.VISIBLE : View.GONE);
        if (toolbarVisible) handler.postDelayed(hideToolbar, 4500);
    }
    private String findBundledRomAsset() {
        try {
            String[] names = getAssets().list("roms");
            if (names == null) return null;
            java.util.Arrays.sort(names);
            for (String name : names) {
                if (name.equalsIgnoreCase("README.md")) continue;
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".md") || lower.endsWith(".gen") || lower.endsWith(".bin") || lower.endsWith(".smd")) return "roms/" + name;
            }
        } catch (Exception ignored) { }
        return null;
    }
    private void loadBundledRom(String asset) {
        loading = true; status.setVisibility(View.VISIBLE); status.setText(R.string.loading_rom); controls.setInteractive(false);
        io.execute(() -> {
            File temporary = new File(getFilesDir(), "bundled.tmp");
            try (InputStream input = getAssets().open(asset); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[65536]; int count; long total = 0;
                while ((count = input.read(buffer)) != -1) { total += count; if (total > 16 * 1024 * 1024) throw new IllegalStateException(getString(R.string.rom_too_large)); output.write(buffer, 0, count); }
                output.getFD().sync();
                if (destroyed) return;
                if (!RomValidator.isGenesis(temporary)) throw new IllegalStateException(getString(R.string.invalid_genesis_rom));
                File destination = new File(getFilesDir(), "current.gen");
                if (temporary.length() != destination.length() || !RomValidator.sameBytes(temporary, destination)) {
                    if (destination.exists() && !destination.delete()) throw new IllegalStateException("Could not replace ROM");
                    if (!temporary.renameTo(destination)) throw new IllegalStateException("Could not save ROM");
                }
                runOnUiThread(() -> { if (!destroyed) { loading = false; loadRom(destination); } });
            } catch (Exception exception) { runOnUiThread(() -> { if (!destroyed) { loading = false; showError(exception.getMessage()); syncPause(); } }); }
            finally { temporary.delete(); }
        });
    }
    private Button toolbarButton(int text, View.OnClickListener listener) {
        Button button = new Button(this); button.setAllCaps(false); button.setText(text); button.setTextSize(12);
        button.setTextColor(Color.WHITE); button.setBackgroundColor(0x00000000); button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1); button.setLayoutParams(params); return button;
    }
    private void chooseRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(intent, OPEN_ROM);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == OPEN_ROM && result == RESULT_OK && data != null && data.getData() != null) importRom(data.getData());
    }
    private void importRom(Uri uri) {
        if (loading) return;
        loading = true; engine.setPaused(true); controls.clearInput(); status.setVisibility(View.VISIBLE); status.setText(R.string.importing);
        io.execute(() -> {
            File temporary = new File(getFilesDir(), "import.tmp");
            try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IllegalStateException(getString(R.string.rom_read_error));
                byte[] buffer = new byte[65536]; long total = 0; int count;
                while ((count = input.read(buffer)) != -1) { total += count; if (total > 16 * 1024 * 1024) throw new IllegalStateException(getString(R.string.rom_too_large)); output.write(buffer, 0, count); }
                output.getFD().sync();
                if (!RomValidator.isGenesis(temporary)) throw new IllegalStateException(getString(R.string.invalid_genesis_rom));
                File destination = new File(getFilesDir(), "current.gen");
                if (temporary.length() != destination.length() || !RomValidator.sameBytes(temporary, destination)) {
                    if (destination.exists() && !destination.delete()) throw new IllegalStateException("Could not replace ROM");
                    if (!temporary.renameTo(destination)) throw new IllegalStateException("Could not save ROM");
                }
                runOnUiThread(() -> { loading = false; loadRom(destination); });
            } catch (Exception exception) { runOnUiThread(() -> { loading = false; showError(exception.getMessage()); syncPause(); }); }
            finally { temporary.delete(); }
        });
    }
    void loadRom(File rom) {
        if (loading || destroyed) return;
        loading = true; status.setVisibility(View.VISIBLE); status.setText(R.string.loading_rom); controls.setInteractive(false);
        io.execute(() -> {
            try {
                if (destroyed) return;
                if (!RomValidator.isGenesis(rom)) throw new IllegalStateException(getString(R.string.invalid_genesis_rom));
                engine.setSixButton(layout.sixButton);
                engine.setPerformanceOptions(layout.showBorders, layout.autoFrameSkip);
                engine.load(rom, saveFile(rom));
                runOnUiThread(() -> {
                    if (destroyed) return;
                    loading = false; userPaused = false; settingsStore.preferences().edit().putString("lastRom", rom.getAbsolutePath()).apply();
                    status.setVisibility(View.GONE); controls.setInteractive(true); syncPause(); hideSystemBars();
                    surface.requestRender();
                    handler.removeCallbacks(hideToolbar); toolbar.setVisibility(View.GONE); toolbarVisible = false;
                    if (!settingsStore.preferences().getBoolean("toolbarHintShown", false)) {
                        Toast.makeText(this, R.string.toolbar_hint, Toast.LENGTH_LONG).show();
                        settingsStore.preferences().edit().putBoolean("toolbarHintShown", true).apply();
                    }
                });
            } catch (Exception exception) { runOnUiThread(() -> { if (!destroyed) { loading = false; showError(exception.getMessage()); } }); }
        });
    }
    private File saveFile(File rom) throws Exception {
        String hash = RomValidator.sha256(rom); File directory = new File(getFilesDir(), "saves"); directory.mkdirs();
        return new File(directory, hash + ".srm");
    }
    private void showError(String message) { status.setVisibility(View.VISIBLE); status.setText(getString(R.string.emulator_error, message == null ? "Unknown error" : message)); }
    private void confirmReset() { new AlertDialog.Builder(this).setTitle(R.string.reset_game).setMessage(R.string.reset_warning).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.reset, (d, w) -> { String path = settingsStore.preferences().getString("lastRom", null); if (path != null) loadRom(new File(path)); }).show(); }
    private void syncPause() {
        boolean paused = userPaused || !foreground || controls.isEditing(); engine.setPaused(paused);
        if (paused) controls.clearInput(); controls.setInteractive(engine.isLoaded() && !paused);
        pauseButton.setText(userPaused ? R.string.resume : R.string.pause);
    }
    private Spinner spinner(LinearLayout content, int label, int array, int selection, android.widget.AdapterView.OnItemSelectedListener listener) {
        TextView view = new TextView(this); view.setText(label); view.setPadding(0, dp(10), 0, 0); content.addView(view);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter); spinner.setSelection(selection); spinner.setOnItemSelectedListener(listener);
        content.addView(spinner); return spinner;
    }
    private void showSettings() {
        boolean wasPaused = userPaused; userPaused = true; syncPause();
        handler.removeCallbacks(hideToolbar); toolbar.setVisibility(View.GONE); toolbarVisible = false;
        ScrollView scroll = new ScrollView(this); LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24), dp(16), dp(24), dp(16)); scroll.addView(content);
        CheckBox six = new CheckBox(this); six.setText(R.string.six_button); six.setChecked(layout.sixButton); content.addView(six);
        six.setOnCheckedChangeListener((v, checked) -> { layout.sixButton = checked; engine.setSixButton(checked); controls.setLayout(layout); settingsStore.save(layout); });
        Spinner quality = spinner(content, R.string.video_quality, R.array.video_quality_modes, layout.videoMode, new SimpleSpinner() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                layout.setVideoMode(position); renderer.setVideoMode(position); surface.requestRender(); settingsStore.save(layout);
            }
        });
        Spinner display = spinner(content, R.string.display_mode, R.array.display_modes, layout.displayMode, new SimpleSpinner() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                layout.setDisplayMode(position); renderer.setDisplayMode(position); surface.requestRender(); settingsStore.save(layout);
            }
        });
        CheckBox borders = new CheckBox(this); borders.setText(R.string.show_borders); borders.setChecked(layout.showBorders); content.addView(borders);
        borders.setOnCheckedChangeListener((v, checked) -> { layout.showBorders = checked; engine.setPerformanceOptions(checked, layout.autoFrameSkip); settingsStore.save(layout); });
        CheckBox skip = new CheckBox(this); skip.setText(R.string.auto_frame_skip); skip.setChecked(layout.autoFrameSkip); content.addView(skip);
        skip.setOnCheckedChangeListener((v, checked) -> { layout.autoFrameSkip = checked; engine.setPerformanceOptions(layout.showBorders, checked); settingsStore.save(layout); });
        CheckBox haptics = new CheckBox(this); haptics.setText(R.string.haptics); haptics.setChecked(layout.haptics); content.addView(haptics);
        haptics.setOnCheckedChangeListener((v, checked) -> { layout.haptics = checked; controls.setLayout(layout); settingsStore.save(layout); });
        CheckBox mute = new CheckBox(this); mute.setText(R.string.mute); content.addView(mute); mute.setOnCheckedChangeListener((v, checked) -> engine.setMuted(checked));
        TextView sizeLabel = new TextView(this); sizeLabel.setText(R.string.control_size); content.addView(sizeLabel);
        SeekBar size = new SeekBar(this); size.setMax(80); size.setProgress(Math.round((layout.scale - .7f) * 100)); content.addView(size);
        size.setOnSeekBarChangeListener(new SimpleSeek() { @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { layout.setScale(.7f + value / 100f); controls.setLayout(layout); settingsStore.save(layout); } });
        TextView opacityLabel = new TextView(this); opacityLabel.setText(R.string.control_opacity); content.addView(opacityLabel);
        SeekBar opacity = new SeekBar(this); opacity.setMax(65); opacity.setProgress(Math.round((layout.opacity - .3f) * 100)); content.addView(opacity);
        opacity.setOnSeekBarChangeListener(new SimpleSeek() { @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { layout.setOpacity(.3f + value / 100f); controls.setLayout(layout); settingsStore.save(layout); } });
        Button edit = new Button(this); edit.setText(R.string.edit_layout); content.addView(edit);
        Button reset = new Button(this); reset.setText(R.string.reset_layout); content.addView(reset);
        TextView perf = new TextView(this); perf.setPadding(0, dp(10), 0, dp(4)); content.addView(perf);
        TextView explanation = new TextView(this); explanation.setText(R.string.settings_explanation); content.addView(explanation);
        Button notices = new Button(this); notices.setText(R.string.third_party_notices); content.addView(notices);
        notices.setOnClickListener(v -> { try { String text; try (InputStream in = getAssets().open("THIRD_PARTY_NOTICES.txt"); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); text = out.toString("UTF-8"); } TextView view = new TextView(this); view.setText(text); view.setPadding(dp(16), dp(16), dp(16), dp(16)); ScrollView box = new ScrollView(this); box.addView(view); new AlertDialog.Builder(this).setTitle(R.string.third_party_notices).setView(box).setPositiveButton(R.string.done, null).show(); } catch (Exception e) { showError(e.getMessage()); } });
        settingsDialog = new AlertDialog.Builder(this).setTitle(R.string.settings).setView(scroll).setPositiveButton(R.string.done, null).create();
        Runnable updatePerf = new Runnable() { @Override public void run() {
            if (settingsDialog == null || !settingsDialog.isShowing()) return;
            EmulatorEngine.Stats s = engine.stats();
            perf.setText(getString(R.string.perf_readout, s.fps, s.coreMillis, s.width, s.height, s.underruns));
            handler.postDelayed(this, 1000);
        } };
        settingsDialog.setOnDismissListener(d -> { handler.removeCallbacks(updatePerf); userPaused = wasPaused; syncPause(); });
        edit.setOnClickListener(v -> { settingsDialog.dismiss(); controls.setEditing(true); editDone.setVisibility(View.VISIBLE); syncPause(); });
        reset.setOnClickListener(v -> {
            layout.reset(); controls.setLayout(layout); size.setProgress(30); opacity.setProgress(42); settingsStore.save(layout);
        });
        settingsDialog.show(); handler.post(updatePerf);
    }
    private void finishEditing() { layout = controls.layout(); settingsStore.save(layout); controls.setEditing(false); editDone.setVisibility(View.GONE); syncPause(); }
    @Override public void onBackPressed() { if (controls.isEditing()) finishEditing(); else showSettings(); }
    @Override protected void onResume() { super.onResume(); foreground = true; surface.onResume(); syncPause(); hideSystemBars(); surface.requestRender(); }
    @Override protected void onPause() { foreground = false; controls.clearInput(); engine.setPaused(true); engine.persistSave(); surface.onPause(); handler.removeCallbacks(hideToolbar); super.onPause(); }
    @Override protected void onDestroy() { destroyed = true; handler.removeCallbacksAndMessages(null); if (settingsDialog != null) settingsDialog.dismiss(); engine.close(); io.shutdown(); super.onDestroy(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused) hideSystemBars(); }
    private void hideSystemBars() { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    EmulatorEngine engineForTest() { return engine; }
    TouchControlsView controlsForTest() { return controls; }
    GameRenderer rendererForTest() { return renderer; }
    ControlLayout layoutForTest() { return layout; }
    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar bar) { }
        @Override public void onStopTrackingTouch(SeekBar bar) { }
    }
    private abstract static class SimpleSpinner implements android.widget.AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
