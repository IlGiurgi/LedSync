// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 1;
    private static final int REQ_CAPTURE = 2;

    // ---- palette
    static final int BG = 0xFF0E1016;
    static final int CARD = 0xFF181B24;
    static final int CARD_2 = 0xFF222633;
    static final int TEXT = 0xFFEDEEF3;
    static final int TEXT_2 = 0xFF9AA0B4;
    static final int ACCENT = 0xFF8B5CF6;
    static final int ACCENT_2 = 0xFFEC4899;

    private SharedPreferences prefs;
    private final EffectPreview preview = new EffectPreview();
    private ScrollView mainScroll;
    private static int restoreScrollY;
    static final String EXTRA_AUTOSTART = "autostart";
    /** Set by our Quick Settings tile just before it opens the app: other apps cannot fake an autostart. */
    static volatile String autostartToken;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();

    private TextView powerButton, statusView;
    private BarsView bars;
    private LinearLayout deviceChips;
    private final List<View> effectTiles = new ArrayList<>();
    private final List<View> gradientChips = new ArrayList<>();
    private View hueRow;
    private Switch wledSwitch;

    /** ip -> "name|leds" for the devices the user added, in order. */
    private final LinkedHashMap<String, String> devices = new LinkedHashMap<>();
    /** WLEDs found on the network but not added yet: ip -> name. */
    private final LinkedHashMap<String, String> discovered = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        loadTuning();
        loadDevices();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(32));
        root.setBackgroundColor(BG);

        // ---- header
        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("LedSync", 28, TEXT, true);
        head.addView(title, weight());
        TextView info = text("ⓘ", 26, TEXT_2, false);
        info.setPadding(dp(12), dp(4), dp(4), dp(4));
        info.setContentDescription(L.t("Come funziona", "How it works"));
        info.setOnClickListener(v -> Guide.showGuide(this));
        head.addView(info);
        root.addView(head, matchWrap());
        TextView sub = text(L.t("Le tue luci a tempo con la musica del telefono", "Your lights in time with your phone's music"), 14, TEXT_2, false);
        sub.setPadding(0, dp(2), 0, dp(20));
        root.addView(sub);

        // ---- power + visualiser
        LinearLayout hero = card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        powerButton = text("", 18, Color.WHITE, true);
        powerButton.setGravity(Gravity.CENTER);
        powerButton.setOnClickListener(v -> onToggle());
        hero.addView(powerButton, new LinearLayout.LayoutParams(dp(132), dp(132)));
        statusView = text("", 13, TEXT_2, false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(12), 0, dp(12));
        hero.addView(statusView, matchWrap());
        bars = new BarsView(this);
        hero.addView(bars, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90)));
        root.addView(hero, cardParams());

        // ---- devices
        LinearLayout devCard = card();
        devCard.addView(sectionTitle(L.t("Dispositivi", "Devices")));
        TextView devHelp = text(L.t("Tocca un dispositivo per le sue opzioni.", "Tap a device for its options."), 12, TEXT_2, false);
        devHelp.setPadding(0, 0, 0, dp(10));
        devCard.addView(devHelp);
        deviceChips = new LinearLayout(this);
        deviceChips.setOrientation(LinearLayout.VERTICAL);
        devCard.addView(deviceChips, matchWrap());
        LinearLayout devButtons = row();
        devButtons.setPadding(0, dp(8), 0, 0);
        devButtons.addView(pill(L.t("Cerca in rete", "Search network"), false, v -> discover()), weight());
        devButtons.addView(space(dp(8)));
        devButtons.addView(pill(L.t("Aggiungi IP", "Add IP"), false, v -> askIp()), weight());
        devCard.addView(devButtons, matchWrap());
        TextView diag = pill(L.t("Diagnostica connessione e ritardi", "Connection and latency check"), false, v -> showDiagnostics());
        LinearLayout.LayoutParams dlp = matchWrap();
        dlp.topMargin = dp(8);
        devCard.addView(diag, dlp);
        root.addView(devCard, cardParams());
        renderDevices();
        refreshDeviceInfo();

        // ---- effects
        LinearLayout favCard = card();
        favCard.addView(sectionTitle(L.t("Preferiti", "Favourites")));
        TextView favHelp = text(L.t("Salva l'effetto e i colori che ti piacciono. Tieni premuto per cancellare.", "Save the effect and colours you like. Long-press to delete."), 12, TEXT_2, false);
        favHelp.setPadding(0, 0, 0, dp(10));
        favCard.addView(favHelp);
        HorizontalScrollView favScroll = new HorizontalScrollView(this);
        favScroll.setHorizontalScrollBarEnabled(false);
        favRow = row();
        favScroll.addView(favRow);
        favCard.addView(favScroll, matchWrap());
        root.addView(favCard, cardParams());
        renderFavourites();

        LinearLayout fxCard = card();
        fxCard.addView(sectionTitle(L.t("Effetto", "Effect")));
        int[] main = DirectEffects.MAIN_EFFECTS;
        for (int k = 0; k < main.length + 1; k += 2) {
            LinearLayout r = row();
            r.addView(k < main.length ? effectTile(main[k]) : moreTile(), weight());
            r.addView(space(dp(10)));
            if (k + 1 < main.length) r.addView(effectTile(main[k + 1]), weight());
            else if (k + 1 == main.length) r.addView(moreTile(), weight());
            else r.addView(new View(this), weight());
            LinearLayout.LayoutParams lp = matchWrap();
            lp.bottomMargin = dp(10);
            fxCard.addView(r, lp);
        }
        TextView cycle = pill(cycleLabel(), false, null);
        cycle.setOnClickListener(v -> {
            int[] opts = {0, 15, 30, 60};
            int at = 0;
            for (int i = 0; i < opts.length; i++) if (opts[i] == Tuning.autoCycleSec) at = i;
            Tuning.autoCycleSec = opts[(at + 1) % opts.length];
            save("auto_cycle", Tuning.autoCycleSec);
            cycle.setText(cycleLabel());
        });
        fxCard.addView(cycle, matchWrap());
        TextView autoOff = pill(autoOffLabel(), false, null);
        autoOff.setOnClickListener(v -> {
            int[] opts = {0, 5, 15, 30};
            int at = 0;
            for (int i = 0; i < opts.length; i++) if (opts[i] == Tuning.autoOffMin) at = i;
            Tuning.autoOffMin = opts[(at + 1) % opts.length];
            save("auto_off", Tuning.autoOffMin);
            autoOff.setText(autoOffLabel());
        });
        LinearLayout.LayoutParams aop = matchWrap();
        aop.topMargin = dp(8);
        fxCard.addView(autoOff, aop);
        toggleRow(fxCard, L.t("Ascolta col microfono (musica da TV, PC, casse)", "Listen with the microphone (TV, PC, speakers)"), Tuning.micMode, on -> {
            Tuning.micMode = on;
            saveBool("mic_mode", on);
            if (CaptureService.running) toast(L.t("Ferma e riavvia per cambiare sorgente", "Stop and start again to change the source"));
        });
        root.addView(fxCard, cardParams());
        updateEffectTiles();

        // ---- colours
        LinearLayout colCard = card();
        colCard.addView(sectionTitle(L.t("Colori", "Colours")));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout gr = row();
        for (int g = 0; g < DirectEffects.GRADIENT_NAMES.length; g++) gr.addView(gradientChip(g));
        hs.addView(gr);
        colCard.addView(hs, matchWrap());
        updateGradientChips();
        LinearLayout hueBox = new LinearLayout(this);
        hueBox.setOrientation(LinearLayout.VERTICAL);
        slider(hueBox, L.t("Colore della pulsazione", "Pulse colour"), 0, 359, Tuning.hue, v -> v + "°",
                v -> { Tuning.hue = v; save("d_hue", v); });
        hueRow = hueBox;
        colCard.addView(hueBox, matchWrap());
        slider(colCard, L.t("Luminosità", "Brightness"), 1, 100, (int) Math.round(Tuning.brightness * 100), v -> v + "%", v -> {
            Tuning.brightness = v / 100.0;
            save("app_bri", v);
            // the app scales its own effects; in L.t("Effetti di WLED", "WLED effects") mode WLED's brightness is set too
            if (!Tuning.directMode) sendWledDebounced("{\"on\":true,\"bri\":" + Math.round(v * 2.55) + "}");
        });
        toggleRow(colCard, L.t("Colori a tempo (cambiano a ogni battito)", "Colours on the beat (change every beat)"), Tuning.beatColors,
                on -> { Tuning.beatColors = on; saveBool("beat_colors", on); });
        root.addView(colCard, cardParams());
        updateHueVisibility();

        // ---- advanced (collapsed)
        LinearLayout adv = card();
        TextView advTitle = sectionTitle(L.t("Regolazioni avanzate  ▾", "Advanced settings  ▾"));
        adv.addView(advTitle);
        LinearLayout advBody = new LinearLayout(this);
        advBody.setOrientation(LinearLayout.VERTICAL);
        advBody.setVisibility(View.GONE);
        advTitle.setOnClickListener(v -> {
            boolean show = advBody.getVisibility() != View.VISIBLE;
            advBody.setVisibility(show ? View.VISIBLE : View.GONE);
            advTitle.setText(show ? L.t("Regolazioni avanzate  ▴", "Advanced settings  ▴") : L.t("Regolazioni avanzate  ▾", "Advanced settings  ▾"));
        });
        buildAdvanced(advBody);
        adv.addView(advBody, matchWrap());
        root.addView(adv, cardParams());

        TextView note = text(L.t("Quando Android chiede cosa condividere, scegli lo schermo intero. ", "When Android asks what to share, choose the entire screen. ")
                + L.t("Alcune app non permettono di catturare il loro audio.", "Some apps do not allow their audio to be captured."), 12, TEXT_2, false);
        note.setPadding(dp(4), dp(8), dp(4), 0);
        root.addView(note);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.addView(root);
        setContentView(sv);
        mainScroll = sv;
        // after loading a favourite the screen is rebuilt: come back to the same place instead of the top
        if (restoreScrollY > 0) {
            int y = restoreScrollY;
            restoreScrollY = 0;
            sv.post(() -> sv.scrollTo(0, y));
        }
        // first launch: a short welcome that explains how the app works
        if (savedInstanceState == null && !prefs.getBoolean("welcome_seen", false)) {
            Guide.showWelcome(this, () -> prefs.edit().putBoolean("welcome_seen", true).apply());
        }
    }

    // ================================================================ sections

    private void buildAdvanced(LinearLayout box) {
        box.addView(subTitle(L.t("Forma dell'effetto", "Effect shape")));
        toggleRow(box, L.t("Specchia dal centro", "Mirror from the centre"), Tuning.mirror, on -> { Tuning.mirror = on; saveBool("d_mirror", on); });
        toggleRow(box, L.t("Energy: mescola i colori", "Energy: blend colours"), Tuning.additive, on -> { Tuning.additive = on; saveBool("d_additive", on); });
        slider(box, L.t("Sfocatura", "Blur"), 0, 10, Tuning.blur, String::valueOf, v -> { Tuning.blur = v; save("d_blur", v); });
        slider(box, L.t("Rotazione colori", "Colour roll"), 0, 10, Tuning.roll, v -> v == 0 ? "ferma" : String.valueOf(v),
                v -> { Tuning.roll = v; save("d_roll", v); });
        slider(box, L.t("Velocità di scorrimento", "Scroll speed"), 1, 10, Tuning.scrollSpeed, String::valueOf,
                v -> { Tuning.scrollSpeed = v; save("d_speed", v); });

        box.addView(subTitle("Audio"));
        LinearLayout tuning = new LinearLayout(this);
        tuning.setOrientation(LinearLayout.VERTICAL);
        box.addView(tuning, matchWrap());
        buildTuningSliders(tuning);
        box.addView(pill(L.t("Ripristina valori audio", "Reset audio settings"), false, v -> {
            SharedPreferences.Editor e = prefs.edit();
            for (String k : TUNING_KEYS) e.remove(k);
            e.apply();
            loadTuning();
            tuning.removeAllViews();
            buildTuningSliders(tuning);
        }), matchWrap());

        box.addView(subTitle(L.t("Effetti di WLED", "WLED effects")));
        TextView help = text(L.t("In alternativa agli effetti dell'app, il telefono può mandare solo l'audio ", "Instead of the app's effects, the phone can send just the audio ")
                + L.t("e lasciare l'effetto a WLED (Sound Reactive, Sync \"Receive\" porta 11988).", "and let WLED run the effect (Sound Reactive, Sync \"Receive\" port 11988)."), 12, TEXT_2, false);
        box.addView(help);
        LinearLayout wledBox = new LinearLayout(this);
        wledBox.setOrientation(LinearLayout.VERTICAL);
        wledBox.setVisibility(Tuning.directMode ? View.GONE : View.VISIBLE);
        wledSwitch = toggleRow(box, L.t("Usa gli effetti di WLED", "Use WLED's effects"), !Tuning.directMode, on -> {
            Tuning.directMode = !on;
            saveBool("mode_direct", !on);
            wledBox.setVisibility(on ? View.VISIBLE : View.GONE);
        });
        box.addView(wledBox, matchWrap());
        wledBox.addView(pill(L.t("Carica effetti audio di WLED", "Load WLED audio effects"), false, v -> loadEffects()), matchWrap());
        fxSpinner = new Spinner(this);
        fxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (fxIds.isEmpty() || pos >= fxIds.size()) return;
                int fx = fxIds.get(pos);
                if (fx == shownFx) return; // selection set by code, not by the user
                shownFx = fx;
                sendWled("{\"seg\":{\"fx\":" + fx + "}}");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        wledBox.addView(fxSpinner, matchWrap());
        slider(wledBox, L.t("Velocità effetto", "Effect speed"), 0, 255, prefs.getInt("wled_sx", 128), String::valueOf, v -> {
            prefs.edit().putInt("wled_sx", v).apply();
            sendWledDebounced("{\"seg\":{\"sx\":" + v + "}}");
        });
        slider(wledBox, L.t("Intensità effetto", "Effect intensity"), 0, 255, prefs.getInt("wled_ix", 180), String::valueOf, v -> {
            prefs.edit().putInt("wled_ix", v).apply();
            sendWledDebounced("{\"seg\":{\"ix\":" + v + "}}");
        });
    }

    private static final String[] TUNING_KEYS = {"t_range", "t_bass", "t_mid", "t_treble", "t_gate", "t_decay", "t_beat", "t_delay"};

    private void loadTuning() {
        Tuning.rangeDb = prefs.getInt("t_range", 24);
        Tuning.bassDb = prefs.getInt("t_bass", 0);
        Tuning.midDb = prefs.getInt("t_mid", 0);
        Tuning.trebleDb = prefs.getInt("t_treble", 0);
        Tuning.gate = prefs.getInt("t_gate", 0);
        Tuning.decay = prefs.getInt("t_decay", 75) / 100.0;
        Tuning.beatRatio = prefs.getInt("t_beat", 130) / 100.0;
        Tuning.delayMs = prefs.getInt("t_delay", 0);
        Tuning.directMode = prefs.getBoolean("mode_direct", true);
        Tuning.directEffect = Math.min(prefs.getInt("d_effect", 0), DirectEffects.NAMES.length - 1);
        Tuning.hue = prefs.getInt("d_hue", 0);
        Tuning.gradient = Math.min(prefs.getInt("d_gradient", 0), DirectEffects.GRADIENT_NAMES.length - 1);
        Tuning.mirror = prefs.getBoolean("d_mirror", true);
        Tuning.additive = prefs.getBoolean("d_additive", true);
        Tuning.blur = prefs.getInt("d_blur", 2);
        Tuning.roll = prefs.getInt("d_roll", 0);
        Tuning.scrollSpeed = prefs.getInt("d_speed", 3);
        Tuning.brightness = prefs.getInt("app_bri", 80) / 100.0;
        Tuning.micMode = prefs.getBoolean("mic_mode", false);
        Tuning.autoCycleSec = prefs.getInt("auto_cycle", 0);
        Tuning.beatColors = prefs.getBoolean("beat_colors", false);
        Tuning.autoOffMin = prefs.getInt("auto_off", 0);
        Tuning.disabled.clear();
        Tuning.disabled.addAll(prefs.getStringSet("dev_disabled", new java.util.HashSet<>()));
        Tuning.flipped.clear();
        Tuning.flipped.addAll(prefs.getStringSet("dev_flip", new java.util.HashSet<>()));
        try { customNames = new JSONObject(prefs.getString("dev_names", "{}")); } catch (Exception ignored) { }
        try {
            JSONObject hu = new JSONObject(prefs.getString("hue_users", "{}"));
            for (java.util.Iterator<String> it = hu.keys(); it.hasNext(); ) {
                String k = it.next();
                HueLink.users.put(k, hu.getString(k));
            }
        } catch (Exception ignored) { }
        Tuning.ledCount.clear();
        try {
            JSONObject lc = new JSONObject(prefs.getString("dev_leds", "{}"));
            for (java.util.Iterator<String> it = lc.keys(); it.hasNext(); ) {
                String k = it.next();
                Tuning.ledCount.put(k, lc.getInt(k));
            }
        } catch (Exception ignored) { }
        Tuning.matrixWidth.clear();
        try {
            JSONObject mw = new JSONObject(prefs.getString("dev_matrix", "{}"));
            for (java.util.Iterator<String> it = mw.keys(); it.hasNext(); ) {
                String k = it.next();
                Tuning.matrixWidth.put(k, mw.getInt(k));
            }
        } catch (Exception ignored) { }
        Tuning.deviceBrightness.clear();
        try {
            JSONObject db = new JSONObject(prefs.getString("dev_bri", "{}"));
            for (java.util.Iterator<String> it = db.keys(); it.hasNext(); ) {
                String k = it.next();
                Tuning.deviceBrightness.put(k, Math.max(5, Math.min(100, db.getInt(k))));
            }
        } catch (Exception ignored) { }
        Tuning.deviceEffect.clear();
        try {
            JSONObject de = new JSONObject(prefs.getString("dev_effect", "{}"));
            for (java.util.Iterator<String> it = de.keys(); it.hasNext(); ) {
                String k = it.next();
                int e = de.getInt(k);
                if (e >= 0 && e < DirectEffects.NAMES.length) Tuning.deviceEffect.put(k, e);
            }
        } catch (Exception ignored) { }
    }

    private void saveDeviceSettings() {
        try {
            JSONObject de = new JSONObject();
            for (Map.Entry<String, Integer> e : Tuning.deviceEffect.entrySet()) de.put(e.getKey(), e.getValue());
            prefs.edit()
                    .putStringSet("dev_disabled", new java.util.HashSet<>(Tuning.disabled))
                    .putStringSet("dev_flip", new java.util.HashSet<>(Tuning.flipped))
                    .putString("dev_effect", de.toString())
                    .putString("dev_bri", new JSONObject(new java.util.HashMap<String, Object>(Tuning.deviceBrightness)).toString())
                    .putString("dev_leds", new JSONObject(new java.util.HashMap<String, Object>(Tuning.ledCount)).toString())
                    .putString("dev_matrix", new JSONObject(new java.util.HashMap<String, Object>(Tuning.matrixWidth)).toString())
                    .apply();
        } catch (Exception ignored) { }
    }

    private void buildTuningSliders(LinearLayout box) {
        slider(box, L.t("Reattività", "Responsiveness"), 0, 40, 50 - (int) Tuning.rangeDb, v -> Math.round(v * 2.5) + "%",
                v -> { Tuning.rangeDb = 50 - v; save("t_range", 50 - v); });
        slider(box, L.t("Bassi", "Bass"), 0, 24, (int) Tuning.bassDb + 12, v -> signedDb(v - 12),
                v -> { Tuning.bassDb = v - 12; save("t_bass", v - 12); });
        slider(box, L.t("Medi", "Mids"), 0, 24, (int) Tuning.midDb + 12, v -> signedDb(v - 12),
                v -> { Tuning.midDb = v - 12; save("t_mid", v - 12); });
        slider(box, L.t("Alti", "Highs"), 0, 24, (int) Tuning.trebleDb + 12, v -> signedDb(v - 12),
                v -> { Tuning.trebleDb = v - 12; save("t_treble", v - 12); });
        slider(box, L.t("Soglia rumore", "Noise gate"), 0, 150, (int) Tuning.gate, v -> Math.round(v / 2.55) + "%",
                v -> { Tuning.gate = v; save("t_gate", v); });
        slider(box, L.t("Scia", "Trail"), 50, 95, (int) Math.round(Tuning.decay * 100),
                v -> v <= 60 ? "corta" : (v >= 88 ? "lunga" : "media"), v -> { Tuning.decay = v / 100.0; save("t_decay", v); });
        slider(box, L.t("Sensibilità battito", "Beat sensitivity"), 0, 90, 200 - (int) Math.round(Tuning.beatRatio * 100),
                v -> Math.round(v / 0.9) + "%", v -> { Tuning.beatRatio = (200 - v) / 100.0; save("t_beat", 200 - v); });
        slider(box, L.t("Ritardo luci (casse Bluetooth)", "Light delay (Bluetooth speakers)"), 0, 50, Tuning.delayMs / 10, v -> (v * 10) + " ms",
                v -> { Tuning.delayMs = v * 10; save("t_delay", v * 10); });
    }

    // ================================================================ effect tiles & gradients

    private View effectTile(int index) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView name = text(DirectEffects.NAMES[index], 15, TEXT, true);
        TextView desc = text(DirectEffects.DESCRIPTIONS[index], 12, TEXT_2, false);
        desc.setPadding(0, dp(2), 0, 0);
        t.addView(name);
        t.addView(desc);
        // a small animated preview of the effect, driven by a built-in demo song
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        plp.topMargin = dp(10);
        t.addView(preview.strip(this, index), plp);
        t.setMinimumHeight(dp(72));
        t.setOnClickListener(v -> selectEffect(index));
        t.setTag(index);
        effectTiles.add(t);
        return t;
    }

    private LinearLayout moreTileView;
    private LinearLayout favRow;

    // ---- favourites: effect + colours + shape, saved by name
    private static final String[] SCENE_INT_KEYS = {"d_effect", "d_gradient", "d_hue", "d_blur", "d_roll", "d_speed"};

    private JSONArray loadFavourites() {
        try { return new JSONArray(prefs.getString("favourites", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private void renderFavourites() {
        favRow.removeAllViews();
        JSONArray favs = loadFavourites();
        for (int i = 0; i < favs.length(); i++) {
            JSONObject f = favs.optJSONObject(i);
            if (f == null) continue;
            int index = i;
            TextView chip = pill(f.optString("name", L.t("Preferito", "Favourite")), false, v -> applyFavourite(f));
            chip.setOnLongClickListener(v -> {
                new AppDialog(this).setTitle(L.t("Cancellare \"", "Delete \"") + f.optString("name") + "\"?")
                        .setPositiveButton(L.t("Cancella", "Delete"), (d, w) -> {
                            JSONArray all = loadFavourites();
                            all.remove(index);
                            prefs.edit().putString("favourites", all.toString()).apply();
                            renderFavourites();
                        })
                        .setNegativeButton(L.t("Annulla", "Cancel"), null).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            favRow.addView(chip, lp);
        }
        favRow.addView(pill(L.t("+ Salva attuale", "+ Save current"), true, v -> saveFavourite()));
    }

    private void saveFavourite() {
        EditText in = new EditText(this);
        in.setHint(L.t("es. Festa, Relax, Film", "e.g. Party, Relax, Movie"));
        in.setText(DirectEffects.NAMES[Tuning.directEffect]);
        new AppDialog(this).setTitle(L.t("Nome del preferito", "Favourite name")).setView(in)
                .setPositiveButton(L.t("Salva", "Save"), (d, w) -> {
                    try {
                        JSONObject f = new JSONObject();
                        f.put("name", in.getText().toString().trim().isEmpty() ? L.t("Preferito", "Favourite") : in.getText().toString().trim());
                        f.put("d_effect", Tuning.directEffect);
                        f.put("d_gradient", Tuning.gradient);
                        f.put("d_hue", Tuning.hue);
                        f.put("d_blur", Tuning.blur);
                        f.put("d_roll", Tuning.roll);
                        f.put("d_speed", Tuning.scrollSpeed);
                        f.put("d_mirror", Tuning.mirror);
                        f.put("d_additive", Tuning.additive);
                        JSONArray all = loadFavourites();
                        all.put(f);
                        prefs.edit().putString("favourites", all.toString()).apply();
                        renderFavourites();
                    } catch (Exception ignored) { }
                })
                .setNegativeButton(L.t("Annulla", "Cancel"), null).show();
    }

    private void applyFavourite(JSONObject f) {
        SharedPreferences.Editor e = prefs.edit();
        for (String k : SCENE_INT_KEYS) if (f.has(k)) e.putInt(k, f.optInt(k));
        if (f.has("d_mirror")) e.putBoolean("d_mirror", f.optBoolean("d_mirror"));
        if (f.has("d_additive")) e.putBoolean("d_additive", f.optBoolean("d_additive"));
        e.putBoolean("mode_direct", true);
        e.apply();
        // rebuild the screen so every control shows the loaded values, keeping the scroll position
        restoreScrollY = mainScroll != null ? mainScroll.getScrollY() : 0;
        recreate();
    }

    /** The "Altri effetti" tile; shows the current effect when it is not one of the main ones. */
    private View moreTile() {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.addView(text(L.t("Altri effetti  →", "More effects  →"), 15, TEXT, true));
        TextView sub = text("", 12, TEXT_2, false);
        sub.setPadding(0, dp(2), 0, 0);
        t.addView(sub);
        t.setMinimumHeight(dp(72));
        t.setOnClickListener(v -> showAllEffects());
        moreTileView = t;
        return t;
    }

    private void showAllEffects() {
        AppDialog[] dlg = new AppDialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(4));
        box.setBackgroundColor(CARD);
        List<View> dialogTiles = new ArrayList<>();
        for (int c = 0; c < DirectEffects.CATEGORIES.length; c++) {
            box.addView(subTitle(DirectEffects.CATEGORY_NAMES[c]));
            int[] list = DirectEffects.CATEGORIES[c];
            for (int k = 0; k < list.length; k += 2) {
                LinearLayout r = row();
                View a = effectTile(list[k]);
                dialogTiles.add(a);
                r.addView(a, weight());
                r.addView(space(dp(10)));
                if (k + 1 < list.length) {
                    View b = effectTile(list[k + 1]);
                    dialogTiles.add(b);
                    r.addView(b, weight());
                } else {
                    r.addView(new View(this), weight());
                }
                LinearLayout.LayoutParams lp = matchWrap();
                lp.topMargin = dp(8);
                box.addView(r, lp);
            }
        }
        for (View t : dialogTiles) {
            int idx = (int) t.getTag();
            // stay open so several effects can be tried in a row; the tapped one is highlighted
            t.setOnClickListener(v -> selectEffect(idx));
        }
        updateEffectTiles();
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(CARD);
        sv.addView(box);
        dlg[0] = new AppDialog(this).setTitle(L.t("Tutti gli effetti", "All effects")).setView(sv)
                .setNegativeButton(L.t("Chiudi", "Close"), null).create();
        dlg[0].setOnDismissListener(d -> {
            effectTiles.removeAll(dialogTiles);
            List<View> strips = new ArrayList<>();
            for (View t : dialogTiles) strips.add(((LinearLayout) t).getChildAt(2));
            preview.remove(strips);
        });
        dlg[0].show();
    }

    private String autoOffLabel() {
        return Tuning.autoOffMin == 0 ? L.t("Spegni col silenzio: mai", "Stop on silence: never") : L.t("Spegni dopo ", "Stop after ") + Tuning.autoOffMin + L.t(" min di silenzio", " min of silence");
    }

    private String cycleLabel() {
        int sec = Tuning.autoCycleSec;
        return L.t("Cambio automatico effetto: ", "Automatic effect change: ") + (sec == 0 ? L.t("spento", "off") : sec < 60 ? L.t("ogni ", "every ") + sec + " s" : L.t("ogni minuto", "every minute"));
    }

    private void selectEffect(int index) {
        Tuning.directEffect = index;
        save("d_effect", index);
        if (!Tuning.directMode) {
            if (wledSwitch != null) wledSwitch.setChecked(false); // listener switches back to app effects
            toast(L.t("Uso gli effetti dell'app", "Using the app's effects"));
        }
        updateEffectTiles();
        updateHueVisibility();
    }

    private void updateEffectTiles() {
        if (moreTileView != null) {
            boolean other = true;
            for (int m : DirectEffects.MAIN_EFFECTS) if (m == Tuning.directEffect) other = false;
            ((TextView) moreTileView.getChildAt(1)).setText(other
                    ? L.t("Attivo: ", "Active: ") + DirectEffects.NAMES[Tuning.directEffect]
                    : DirectEffects.NAMES.length + L.t(" effetti in tutto", " effects in total"));
            GradientDrawable d = other
                    ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x558B5CF6, 0x55EC4899})
                    : new GradientDrawable();
            if (!other) d.setColor(CARD_2);
            d.setCornerRadius(dp(16));
            d.setStroke(dp(other ? 2 : 1), other ? ACCENT : 0x22FFFFFF);
            moreTileView.setBackground(d);
        }
        for (View t : effectTiles) {
            boolean sel = (int) t.getTag() == Tuning.directEffect;
            GradientDrawable d = sel
                    ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x558B5CF6, 0x55EC4899})
                    : new GradientDrawable();
            if (!sel) d.setColor(CARD_2);
            d.setCornerRadius(dp(16));
            d.setStroke(dp(sel ? 2 : 1), sel ? ACCENT : 0x22FFFFFF);
            t.setBackground(d);
        }
    }

    private View gradientChip(int index) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER_HORIZONTAL);
        chip.setPadding(dp(4), dp(4), dp(4), dp(4));
        View swatch = new View(this);
        int[] stops = DirectEffects.GRADIENTS[index];
        int[] colors = new int[stops.length];
        for (int i = 0; i < stops.length; i++) colors[i] = 0xFF000000 | stops[i];
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(dp(12));
        swatch.setBackground(g);
        chip.addView(swatch, new LinearLayout.LayoutParams(dp(76), dp(40)));
        TextView label = text(DirectEffects.GRADIENT_NAMES[index], 12, TEXT_2, false);
        label.setPadding(0, dp(4), 0, 0);
        chip.addView(label);
        chip.setTag(index);
        chip.setOnClickListener(v -> {
            Tuning.gradient = index;
            save("d_gradient", index);
            updateGradientChips();
        });
        gradientChips.add(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void updateGradientChips() {
        for (View c : gradientChips) {
            boolean sel = (int) c.getTag() == Tuning.gradient;
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(16));
            d.setColor(Color.TRANSPARENT);
            d.setStroke(dp(2), sel ? Color.WHITE : Color.TRANSPARENT);
            c.setBackground(d);
            ((TextView) ((LinearLayout) c).getChildAt(1)).setTextColor(sel ? TEXT : TEXT_2);
        }
    }

    private void updateHueVisibility() {
        if (hueRow != null) hueRow.setVisibility(Tuning.directEffect == DirectEffects.PULSE ? View.VISIBLE : View.GONE);
    }

    // ================================================================ devices

    private void loadDevices() {
        devices.clear();
        try {
            JSONObject o = new JSONObject(prefs.getString("devices", "{}"));
            JSONArray order = o.optJSONArray("order");
            if (order != null) {
                for (int i = 0; i < order.length(); i++) {
                    String ip = order.getString(i);
                    devices.put(ip, o.optString(ip, ip + "|"));
                }
            }
        } catch (Exception ignored) { }
        if (devices.isEmpty()) {
            // migrate the IP list of older versions
            for (String t : prefs.getString("targets", "").split("[,;\\s]+")) {
                if (!t.trim().isEmpty()) devices.put(t.trim(), t.trim() + "|");
            }
            saveDevices();
        }
    }

    private void saveDevices() {
        try {
            JSONObject o = new JSONObject();
            JSONArray order = new JSONArray();
            for (Map.Entry<String, String> e : devices.entrySet()) {
                order.put(e.getKey());
                o.put(e.getKey(), e.getValue());
            }
            o.put("order", order);
            prefs.edit().putString("devices", o.toString()).putString("targets", String.join(",", devices.keySet())).apply();
        } catch (Exception ignored) { }
    }

    private void renderDevices() {
        deviceChips.removeAllViews();
        if (devices.isEmpty() && discovered.isEmpty()) {
            TextView empty = text(L.t("Nessun dispositivo. Premi \"Cerca in rete\".", "No devices. Tap \"Search network\"."), 13, TEXT_2, false);
            empty.setPadding(0, dp(4), 0, dp(4));
            deviceChips.addView(empty);
        }
        for (Map.Entry<String, String> e : devices.entrySet()) {
            String key = e.getKey();
            String ip = ColorLight.hostOf(key);
            String[] info = e.getValue().split("\\|", -1);
            String name = customNames.has(key) ? customNames.optString(key)
                    : info[0].isEmpty() || info[0].equals(key) ? ip : info[0];
            String kind = key.startsWith("y:") ? "Yeelight · " : key.startsWith("g:") ? "Govee · "
                    : key.startsWith("h:") ? "Philips Hue · " : (info.length > 1 && !info[1].isEmpty() && !info[1].equals("0") ? info[1] + " LED · " : "");
            Integer fx = Tuning.deviceEffect.get(key);
            String extra = fx != null ? " · " + DirectEffects.NAMES[fx] : "";
            if (Tuning.flipped.contains(key)) extra += L.t(" · invertito", " · reversed");
            Integer bri = Tuning.deviceBrightness.get(key);
            if (bri != null) extra += " · " + bri + "%";
            View r = deviceRow(Tuning.disabled.contains(key) ? "○" : "●", name, kind + ip + extra, true, v -> deviceOptions(key, name));
            Switch sw = new Switch(this);
            sw.setChecked(!Tuning.disabled.contains(key));
            sw.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
            sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ACCENT, 0x55FFFFFF}));
            sw.setOnCheckedChangeListener((b, on) -> setDeviceOn(key, on));
            ((LinearLayout) r).addView(sw);
            deviceChips.addView(r);
        }
        for (Map.Entry<String, String> e : discovered.entrySet()) {
            if (devices.containsKey(e.getKey())) continue;
            String key = e.getKey();
            String sub = key.startsWith("y:") ? L.t("Yeelight trovata · tocca per aggiungere", "Yeelight found · tap to add")
                    : key.startsWith("g:") ? L.t("Govee trovata · tocca per aggiungere", "Govee found · tap to add")
                    : key.startsWith("hb:") ? L.t("Bridge Philips Hue · tocca per associare", "Philips Hue bridge · tap to pair")
                    : L.t("WLED trovato · tocca per aggiungere", "WLED found · tap to add");
            deviceChips.addView(deviceRow("+", e.getValue(), sub, false,
                    v -> { if (key.startsWith("hb:")) pairHue(key.substring(3)); else addDevice(key); }));
        }
    }

    private View deviceRow(String icon, String title, String subtitle, boolean added, View.OnClickListener click) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable d = new GradientDrawable();
        d.setColor(added ? CARD_2 : Color.TRANSPARENT);
        d.setCornerRadius(dp(14));
        if (!added) d.setStroke(dp(1), 0x44FFFFFF, dp(6), dp(4));
        r.setBackground(d);
        TextView ic = text(icon, 16, added ? 0xFF34D399 : ACCENT, true);
        ic.setPadding(0, 0, dp(12), 0);
        r.addView(ic);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 15, TEXT, true));
        col.addView(text(subtitle, 12, TEXT_2, false));
        r.addView(col, weight());
        r.setOnClickListener(click);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(8);
        r.setLayoutParams(lp);
        return r;
    }

    private void askIp() {
        String[] types = {L.t("WLED (o ESPHome, orologio…)", "WLED (or ESPHome, clock…)"), "Yeelight", "Govee", "Philips Hue (bridge)"};
        new AppDialog(this)
                .setTitle(L.t("Che dispositivo è?", "What kind of device?"))
                .setItems(types, (d, which) -> askIpFor(which))
                .show();
    }

    private void askIpFor(int type) {
        EditText in = new EditText(this);
        in.setHint(L.t("es. 192.168.1.50", "e.g. 192.168.1.50"));
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String[] hints = {null, L.t("Serve \"Controllo LAN\" attivo nell'app Yeelight.", "Needs \"LAN Control\" switched on in the Yeelight app."),
                L.t("Serve \"LAN Control\" attivo per la luce nell'app Govee Home.", "Needs \"LAN Control\" switched on for the light in the Govee Home app."),
                L.t("Indirizzo del bridge Hue: poi ti chiederò di premere il suo pulsante.", "Address of the Hue bridge: you will then be asked to press its button.")};
        AppDialog b = new AppDialog(this)
                .setTitle(L.t("Indirizzo IP", "IP address"))
                .setView(in)
                .setPositiveButton(L.t("Aggiungi", "Add"), (d, w) -> {
                    String ip = in.getText().toString().trim();
                    if (ip.isEmpty()) return;
                    if (type == 0) addDevice(ip);
                    else if (type == 1) addDevice("y:" + ip);
                    else if (type == 2) addDevice("g:" + ip);
                    else pairHue(ip);
                })
                .setNegativeButton(L.t("Annulla", "Cancel"), null);
        if (hints[type] != null) b.setMessage(hints[type]);
        b.show();
    }

    /** Hue: press the bridge's link button, then the app creates a user and adds every colour light. */
    private void pairHue(String bridge) {
        new AppDialog(this)
                .setTitle(L.t("Associa il bridge Hue", "Pair the Hue bridge"))
                .setMessage(L.t("Premi il pulsante rotondo sul bridge Philips Hue, poi tocca Continua entro 30 secondi.", "Press the round button on the Philips Hue bridge, then tap Continue within 30 seconds."))
                .setPositiveButton(L.t("Continua", "Continue"), (d, w) -> net.execute(() -> {
                    try {
                        String user = HueLink.users.containsKey(bridge) ? HueLink.users.get(bridge) : HueLink.pair(bridge);
                        Map<String, String> lights = HueLink.lights(bridge, user);
                        ui.post(() -> {
                            HueLink.users.put(bridge, user);
                            saveHueUsers();
                            discovered.remove("hb:" + bridge);
                            for (Map.Entry<String, String> l : lights.entrySet()) {
                                String key = "h:" + bridge + "#" + l.getKey();
                                if (!devices.containsKey(key)) devices.put(key, l.getValue() + "|");
                            }
                            saveDevices();
                            renderDevices();
                            toast(lights.isEmpty() ? L.t("Nessuna luce colorata sul bridge", "No colour lights on the bridge") : lights.size() + L.t(" luci Hue aggiunte (spegni quelle che non vuoi)", " Hue lights added (switch off the ones you don't want)"));
                        });
                    } catch (Exception e) {
                        toast("Hue: " + e.getMessage());
                    }
                }))
                .setNegativeButton(L.t("Annulla", "Cancel"), null)
                .show();
    }

    private void saveHueUsers() {
        prefs.edit().putString("hue_users", new JSONObject(new HashMap<String, Object>(HueLink.users)).toString()).apply();
    }

    private void addDevice(String ip) {
        devices.put(ip, discovered.containsKey(ip) ? discovered.get(ip) + "|" : ip + "|");
        saveDevices();
        renderDevices();
        refreshDeviceInfo();
        if (CaptureService.running) toast(L.t("Ferma e riavvia per includere il nuovo dispositivo", "Stop and start again to include the new device"));
    }

    /** Switches a device on/off without removing it: the app stops sending to it and turns its light off. */
    private void setDeviceOn(String key, boolean on) {
        if (on) Tuning.disabled.remove(key);
        else Tuning.disabled.add(key);
        saveDeviceSettings();
        net.execute(() -> {
            if (key.startsWith("y:")) {
                if (!CaptureService.running) YeelightLink.power(key.substring(2), on);
            } else if (key.startsWith("g:")) {
                if (!CaptureService.running) GoveeLink.power(key.substring(2), on);
            } else if (key.startsWith("h:")) {
                String[] p = key.substring(2).split("#", 2);
                if (!CaptureService.running && p.length == 2) HueLink.power(p[0], p[1], on);
            } else {
                try { httpPost("http://" + key + "/json/state", "{\"on\":" + on + "}"); } catch (Exception ignored) { }
            }
        });
        renderDevices();
    }

    private void deviceOptions(String key, String name) {
        Integer cur = Tuning.deviceEffect.get(key);
        String[] items = {
                L.t("Effetto: ", "Effect: ") + (cur == null ? L.t("come gli altri", "same as the others") : DirectEffects.NAMES[cur]),
                Tuning.flipped.contains(key) ? L.t("Direzione: invertita (tocca per normale)", "Direction: reversed (tap for normal)") : L.t("Direzione: normale (tocca per invertire)", "Direction: normal (tap to reverse)"),
                L.t("Togli dispositivo", "Remove device"),
                key.startsWith("y:") ? L.t("Prova luce (controlla il collegamento)", "Test light (check the connection)")
                        : L.t("Numero di LED: ", "Number of LEDs: ") + (Tuning.ledCount.containsKey(key) ? Tuning.ledCount.get(key) : L.t("automatico", "automatic")),
                L.t("Forma: ", "Shape: ") + (Tuning.matrixWidth.containsKey(key) ? L.t("griglia da ", "grid of ") + Tuning.matrixWidth.get(key) + L.t(" colonne", " columns") : L.t("striscia", "strip")),
        };
        if (key.startsWith("y:")) items = java.util.Arrays.copyOf(items, 4);
        else if (ColorLight.isColorKey(key)) items = java.util.Arrays.copyOf(items, 3);
        String[] withRename = new String[items.length + 2];
        withRename[0] = L.t("Rinomina", "Rename");
        Integer own = Tuning.deviceBrightness.get(key);
        withRename[1] = L.t("Luminosità: ", "Brightness: ") + (own == null ? 100 : own) + "%";
        System.arraycopy(items, 0, withRename, 2, items.length);
        new AppDialog(this)
                .setTitle(name)
                .setItems(withRename, (d, pick) -> {
                    int which = pick - 2;
                    if (pick == 0) askRename(key, name);
                    else if (pick == 1) askDeviceBrightness(key, name);
                    else if (which == 3 && key.startsWith("y:")) testYeelight(key.substring(2), name);
                    else if (which == 3) askLedCount(key, name);
                    else if (which == 4) askMatrix(key, name);
                    else if (which == 0) chooseDeviceEffect(key, name);
                    else if (which == 1) {
                        if (!Tuning.flipped.remove(key)) Tuning.flipped.add(key);
                        saveDeviceSettings();
                        renderDevices();
                    } else confirmRemove(key, name);
                })
                .show();
    }

    private String displayName(String key) {
        if (customNames.has(key)) return customNames.optString(key);
        String v = devices.get(key);
        String n = v != null ? v.split("\\|", -1)[0] : "";
        return n.isEmpty() || n.equals(key) ? ColorLight.hostOf(key) : n;
    }

    /** One ping run: {avg, max, loss%} or null when ping is not available. */
    private static double[] ping(String ip) {
        try {
            Process pr = new ProcessBuilder("ping", "-c", "20", "-i", "0.2", "-W", "1", ip).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(pr.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            pr.waitFor();
            String o = out.toString();
            double loss = 100;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([\\d.]+)% packet loss").matcher(o);
            if (m.find()) loss = Double.parseDouble(m.group(1));
            m = java.util.regex.Pattern.compile("= ([\\d.]+)/([\\d.]+)/([\\d.]+)").matcher(o);
            if (m.find()) return new double[]{Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)), loss};
            return new double[]{-1, -1, loss};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Live report: the app's own timing (while running) and a ping of every device, refreshed until closed.
     * Ends with plain-language advice.
     */
    private void showDiagnostics() {
        TextView tv = text(L.t("Misuro…", "Measuring…"), 13, TEXT, false);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setPadding(dp(20), dp(12), dp(20), dp(12));
        tv.setTextIsSelectable(true);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);
        Map<String, double[]> pings = new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, Integer> rounds = new java.util.concurrent.ConcurrentHashMap<>();
        final boolean[] open = {true};
        List<String> keys = new ArrayList<>(devices.keySet());
        for (String key : keys) {
            new Thread(() -> {
                String ip = ColorLight.hostOf(key);
                while (open[0]) {
                    double[] r = ping(ip);
                    if (r == null) { pings.put(key, new double[]{-2, 0, 0}); return; }
                    pings.put(key, r);
                    rounds.merge(key, 1, Integer::sum);
                }
            }, "ping-" + key).start();
        }
        AppDialog dlg = new AppDialog(this)
                .setTitle(L.t("Diagnostica", "Diagnostics"))
                .setView(sv)
                .setPositiveButton(L.t("Chiudi", "Close"), null)
                .setOnDismissListener(d -> open[0] = false)
                .show();
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (!open[0]) return;
                tv.setText(diagnosticsReport(keys, pings, rounds));
                ui.postDelayed(this, 1000);
            }
        };
        ui.post(tick);
    }

    private String diagnosticsReport(List<String> keys, Map<String, double[]> pings, Map<String, Integer> rounds) {
        StringBuilder b = new StringBuilder();
        List<String> advice = new ArrayList<>();
        b.append(L.t("TELEFONO\n", "PHONE\n"));
        if (CaptureService.running && CaptureService.statFps > 0) {
            b.append(String.format(java.util.Locale.ITALY, L.t("Aggiornamenti/s  %.0f  (ideale 50)\n", "Updates/s        %.0f  (ideal 50)\n"), CaptureService.statFps));
            b.append(String.format(java.util.Locale.ITALY, L.t("Pausa massima    %.0f ms (ideale ~20)\n", "Longest pause    %.0f ms (ideal ~20)\n"), CaptureService.statMaxGapMs));
            b.append(String.format(java.util.Locale.ITALY, L.t("Calcolo          %.1f ms per aggiornamento\n", "Processing       %.1f ms per update\n"), CaptureService.statProcMs));
            b.append(String.format(java.util.Locale.ITALY, L.t("Buffer audio     %.0f ms\n", "Audio buffer     %.0f ms\n"), CaptureService.statBufferMs));
            b.append(String.format(java.util.Locale.ITALY, L.t("Ritardo impostato %d ms\n", "Delay setting    %d ms\n"), Tuning.delayMs));
            b.append(L.t("Errori di invio  ", "Send errors      ")).append(CaptureService.statSendErrors).append('\n');
            if (CaptureService.statFps < 45) advice.add(L.t("Il telefono non riesce a stare dietro (meno di 45 aggiornamenti/s): chiudi altre app o disattiva il risparmio energetico per LedSync.", "The phone cannot keep up (under 45 updates/s): close other apps or turn off battery saving for LedSync."));
            if (CaptureService.statMaxGapMs > 80) advice.add(L.t("Ci sono pause lunghe nel telefono: spesso è il risparmio batteria. Imposta LedSync come \"Senza restrizioni\" nelle impostazioni batteria.", "The phone has long pauses, often battery saving: set LedSync to \"Unrestricted\" in the battery settings."));
            if (Tuning.delayMs > 0) advice.add(L.t("Lo slider Ritardo è a ", "The Delay slider is at ") + Tuning.delayMs + L.t(" ms: le luci vengono ritardate apposta. Mettilo a 0 se non usi casse Bluetooth.", " ms: the lights are delayed on purpose. Set it to 0 if you don't use Bluetooth speakers."));
            if (CaptureService.statSendErrors > 0) advice.add(L.t("Ci sono errori di invio: il Wi-Fi del telefono ha avuto problemi.", "There are send errors: the phone's Wi-Fi had problems."));
        } else {
            b.append(L.t("Premi Avvia e fai partire la musica per misurare anche il telefono.\n", "Tap Start and play some music to measure the phone too.\n"));
        }
        b.append(L.t("\nRETE (ping, 20 prove ogni 4 s)\n", "\nNETWORK (ping, 20 tries every 4 s)\n"));
        for (String key : keys) {
            String name = displayName(key);
            double[] p = pings.get(key);
            b.append(name).append('\n');
            if (p == null) { b.append(L.t("   misuro…\n", "   measuring…\n")); continue; }
            if (p[0] == -2) { b.append(L.t("   ping non disponibile su questo telefono\n", "   ping not available on this phone\n")); continue; }
            if (p[0] < 0) {
                b.append(L.t("   NON RISPONDE (persi 100%)\n", "   NOT RESPONDING (100% lost)\n"));
                advice.add(name + L.t(" non risponde: è spenta, ha cambiato IP o è fuori portata del Wi-Fi.", " is not responding: it is off, has a new IP or is out of Wi-Fi range."));
                continue;
            }
            b.append(String.format(java.util.Locale.ITALY, L.t("   medio %.0f ms · max %.0f ms · persi %.0f%%  [giro %d]\n", "   avg %.0f ms · max %.0f ms · lost %.0f%%  [round %d]\n"),
                    p[0], p[1], p[2], rounds.getOrDefault(key, 0)));
            if (p[2] >= 10) advice.add(name + L.t(" perde il ", " loses ") + Math.round(p[2]) + L.t("% dei pacchetti: segnale Wi-Fi debole. Avvicinala al router o aggiungi un ripetitore; su WLED attiva anche \"Disable WiFi sleep\".", "% of packets: weak Wi-Fi. Move it closer to the router or add a repeater; on WLED also enable \"Disable WiFi sleep\"."));
            else if (p[1] > 150 || p[0] > 40) advice.add(name + L.t(" ha una connessione lenta o a strappi (fino a ", " has a slow or jerky connection (up to ") + Math.round(p[1]) + L.t(" ms): per questo può andare in ritardo o a scatti. Su WLED attiva \"Disable WiFi sleep\" (Config → WiFi Setup).", " ms): that is why it can lag or stutter. On WLED enable \"Disable WiFi sleep\" (Config → WiFi Setup)."));
        }
        b.append(L.t("\nCOSA FARE\n", "\nWHAT TO DO\n"));
        if (advice.isEmpty()) b.append(CaptureService.running
                ? L.t("Tutto nella norma. Se senti ancora ritardo, è il tempo dell'audio di Android (~50-100 ms, non eliminabile).\n", "All normal. If you still notice a delay, it is Android's audio latency (~50-100 ms, cannot be removed).\n")
                : L.t("Per ora nessun problema di rete.\n", "No network problems so far.\n"));
        for (String a : advice) b.append("• ").append(a).append('\n');
        return b.toString();
    }

    /** Names chosen by the user, key -> name; they win over the name the device reports. */
    private JSONObject customNames = new JSONObject();

    private void askRename(String key, String name) {
        EditText in = new EditText(this);
        in.setText(name);
        in.setSelectAllOnFocus(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AppDialog(this)
                .setTitle(L.t("Rinomina", "Rename"))
                .setView(in)
                .setPositiveButton(L.t("Salva", "Save"), (d, w) -> {
                    String v = in.getText().toString().trim();
                    try {
                        if (v.isEmpty()) customNames.remove(key);
                        else customNames.put(key, v);
                    } catch (Exception ignored) { }
                    prefs.edit().putString("dev_names", customNames.toString()).apply();
                    renderDevices();
                })
                .setNegativeButton(L.t("Annulla", "Cancel"), null)
                .show();
    }

    /** LED grids (e.g. the word clock, 11 columns): equalizer as columns, other effects as rings from the centre. */
    private void askMatrix(String key, String name) {
        EditText in = new EditText(this);
        in.setHint(L.t("es. 11 per l'orologio a parole", "e.g. 11 for a word clock"));
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (Tuning.matrixWidth.containsKey(key)) in.setText(String.valueOf(Tuning.matrixWidth.get(key)));
        new AppDialog(this)
                .setTitle(L.t("Griglia di LED", "LED grid"))
                .setMessage(L.t("Se ", "If ") + name + L.t(" è una griglia (LED riga per riga dall'alto a sinistra), scrivi quante colonne ha. ", " is a grid (LEDs row by row from the top left), enter how many columns it has. ")
                        + L.t("Equalizer e Bands diventano colonne, gli altri effetti cerchi dal centro. Vuoto = striscia.", "Equalizer and Bands become columns, other effects rings from the centre. Empty = strip."))
                .setView(in)
                .setPositiveButton(L.t("Salva", "Save"), (d, w) -> {
                    int n = 0;
                    try { n = Integer.parseInt(in.getText().toString().trim()); } catch (Exception ignored) { }
                    if (n > 1 && n <= 200) Tuning.matrixWidth.put(key, n);
                    else Tuning.matrixWidth.remove(key);
                    saveDeviceSettings();
                    renderDevices();
                })
                .setNegativeButton(L.t("Annulla", "Cancel"), null)
                .show();
    }

    /** For lights that are not WLED (e.g. ESPHome with the "wled" effect) the LED count cannot be read, so it is typed here. */
    /** Brightness of one light, applied live while the slider moves. */
    private void askDeviceBrightness(String key, String name) {
        Integer cur = Tuning.deviceBrightness.get(key);
        int start = cur == null ? 100 : cur;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView val = text(start + "%", 22, TEXT, true);
        val.setGravity(Gravity.CENTER);
        box.addView(val, matchWrap());
        SeekBar bar = new SeekBar(this);
        bar.setMax(95);
        bar.setProgress(start - 5);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(ACCENT_2));
        bar.setPadding(dp(8), dp(16), dp(8), dp(8));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + 5;
                val.setText(v + "%");
                if (v >= 100) Tuning.deviceBrightness.remove(key); else Tuning.deviceBrightness.put(key, v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { saveDeviceSettings(); renderDevices(); }
        });
        box.addView(bar, matchWrap());
        new AppDialog(this)
                .setTitle(L.t("Luminosità di ", "Brightness of ") + name)
                .setMessage(L.t("In proporzione alla luminosità generale (100% = uguale alle altre). Vale per gli effetti dell'app.", "Relative to the overall brightness (100% = same as the others). Works with the app's effects."))
                .setView(box)
                .setPositiveButton(L.t("Fatto", "Done"), null)
                .show();
    }

    private void askLedCount(String key, String name) {
        EditText in = new EditText(this);
        in.setHint(L.t("vuoto = leggilo dal WLED", "empty = read it from WLED"));
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (Tuning.ledCount.containsKey(key)) in.setText(String.valueOf(Tuning.ledCount.get(key)));
        new AppDialog(this)
                .setTitle(L.t("Quanti LED ha " + name + "?", "How many LEDs does " + name + " have?"))
                .setMessage(L.t("Serve solo per luci che non sono WLED, ad esempio ESPHome.", "Only needed for lights that are not WLED, e.g. ESPHome."))
                .setView(in)
                .setPositiveButton(L.t("Salva", "Save"), (d, w) -> {
                    String v = in.getText().toString().trim();
                    int n = 0;
                    try { n = Integer.parseInt(v); } catch (Exception ignored) { }
                    if (n > 0 && n <= 2000) Tuning.ledCount.put(key, n);
                    else Tuning.ledCount.remove(key);
                    saveDeviceSettings();
                    renderDevices();
                    if (CaptureService.running) toast(L.t("Ferma e riavvia per applicare", "Stop and start again to apply"));
                })
                .setNegativeButton(L.t("Annulla", "Cancel"), null)
                .show();
    }

    /** Runs YeelightLink.test and shows the report; the strip should flash red, green, then blue/red. */
    private void testYeelight(String ip, String name) {
        if (CaptureService.running) {
            toast(L.t("Premi Ferma prima della prova", "Tap Stop before the test"));
            return;
        }
        AppDialog dlg = new AppDialog(this)
                .setTitle(L.t("Prova ", "Test ") + name)
                .setMessage(L.t("Controllo in corso… la striscia dovrebbe diventare rossa, poi verde, poi lampeggiare blu/rossa.", "Checking… the light should turn red, then green, then flash blue/red."))
                .setPositiveButton("OK", null)
                .show();
        new Thread(() -> {
            String rep = YeelightLink.test(ip);
            ui.post(() -> dlg.setMessage(rep));
        }).start();
    }

    private void chooseDeviceEffect(String key, String name) {
        String[] items = new String[DirectEffects.NAMES.length + 1];
        items[0] = L.t("Come gli altri", "Same as the others");
        System.arraycopy(DirectEffects.NAMES, 0, items, 1, DirectEffects.NAMES.length);
        new AppDialog(this)
                .setTitle(L.t("Effetto per ", "Effect for ") + name)
                .setItems(items, (d, which) -> {
                    if (which == 0) Tuning.deviceEffect.remove(key);
                    else Tuning.deviceEffect.put(key, which - 1);
                    saveDeviceSettings();
                    renderDevices();
                })
                .show();
    }

    private void confirmRemove(String ip, String name) {
        new AppDialog(this)
                .setTitle(L.t("Togliere ", "Remove ") + name + "?")
                .setPositiveButton(L.t("Togli", "Remove"), (d, w) -> {
                    devices.remove(ip);
                    Tuning.disabled.remove(ip);
                    Tuning.flipped.remove(ip);
                    Tuning.ledCount.remove(ip);
                    Tuning.matrixWidth.remove(ip);
                    customNames.remove(ip);
                    prefs.edit().putString("dev_names", customNames.toString()).apply();
                    Tuning.deviceEffect.remove(ip);
                    Tuning.deviceBrightness.remove(ip);
                    saveDeviceSettings();
                    saveDevices();
                    renderDevices();
                })
                .setNegativeButton(L.t("Annulla", "Cancel"), null)
                .show();
    }

    /** Reads name and LED count of every added WLED. */
    private void refreshDeviceInfo() {
        List<String> ips = new ArrayList<>(devices.keySet());
        // one thread per device: a light that is off must not hold up the others (or the WLED commands)
        for (String ip : ips) {
            if (ColorLight.isColorKey(ip)) continue;
            new Thread(() -> {
                try {
                    JSONObject info = new JSONObject(httpGet("http://" + ip + "/json/info"));
                    wledOk.add(ip);
                    String name = info.optString("name", ip);
                    int leds = info.optJSONObject("leds") != null ? info.getJSONObject("leds").optInt("count", 0) : 0;
                    ui.post(() -> {
                        if (devices.containsKey(ip)) {
                            devices.put(ip, name + "|" + leds);
                            saveDevices();
                            renderDevices();
                        }
                    });
                } catch (Exception ignored) { }
            }, "info-" + ip).start();
        }
    }

    // ---- network discovery (WLED announces itself as _wled._tcp)
    private NsdManager nsd;
    private NsdManager.DiscoveryListener discovery, hueDiscovery;
    private final ArrayDeque<NsdServiceInfo> toResolve = new ArrayDeque<>();
    private boolean resolving;

    private void discover() {
        if (nsd == null) nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
        stopDiscovery();
        toast(L.t("Cerco le luci in rete…", "Searching for lights…"));
        discovery = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String t, int e) { toast(L.t("Ricerca non riuscita (", "Search failed (") + e + ")"); }
            @Override public void onStopDiscoveryFailed(String t, int e) { }
            @Override public void onDiscoveryStarted(String t) { }
            @Override public void onDiscoveryStopped(String t) { }
            @Override public void onServiceLost(NsdServiceInfo s) { }
            @Override public void onServiceFound(NsdServiceInfo s) {
                ui.post(() -> { toResolve.add(s); resolveNext(); });
            }
        };
        try {
            nsd.discoverServices("_wled._tcp", NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Exception e) {
            toast(L.t("Ricerca non riuscita: ", "Search failed: ") + e.getMessage());
        }
        try {
            hueDiscovery = new NsdManager.DiscoveryListener() {
                @Override public void onStartDiscoveryFailed(String t, int e) { }
                @Override public void onStopDiscoveryFailed(String t, int e) { }
                @Override public void onDiscoveryStarted(String t) { }
                @Override public void onDiscoveryStopped(String t) { }
                @Override public void onServiceLost(NsdServiceInfo s) { }
                @Override public void onServiceFound(NsdServiceInfo s) {
                    ui.post(() -> { toResolve.add(s); resolveNext(); });
                }
            };
            nsd.discoverServices("_hue._tcp", NsdManager.PROTOCOL_DNS_SD, hueDiscovery);
        } catch (Exception ignored) { }
        new Thread(() -> {
            Map<String, String> yee = YeelightLink.discover(4000);
            ui.post(() -> {
                for (Map.Entry<String, String> e : yee.entrySet()) discovered.put("y:" + e.getKey(), e.getValue());
                renderDevices();
            });
        }).start();
        new Thread(() -> {
            Map<String, String> gov = GoveeLink.discover(4000);
            ui.post(() -> {
                for (Map.Entry<String, String> e : gov.entrySet()) discovered.put("g:" + e.getKey(), e.getValue());
                renderDevices();
            });
        }).start();
        ui.postDelayed(() -> {
            stopDiscovery();
            if (discovered.isEmpty()) toast(L.t("Nessuna luce trovata. Puoi aggiungerla con l'IP.", "No lights found. You can add one by IP."));
        }, 10000);
    }

    private void stopDiscovery() {
        if (nsd != null && discovery != null) {
            try { nsd.stopServiceDiscovery(discovery); } catch (Exception ignored) { }
        }
        discovery = null;
        if (nsd != null && hueDiscovery != null) {
            try { nsd.stopServiceDiscovery(hueDiscovery); } catch (Exception ignored) { }
        }
        hueDiscovery = null;
    }

    @SuppressWarnings("deprecation")
    private void resolveNext() {
        if (resolving || toResolve.isEmpty()) return;
        resolving = true;
        NsdServiceInfo s = toResolve.poll();
        nsd.resolveService(s, new NsdManager.ResolveListener() {
            @Override public void onResolveFailed(NsdServiceInfo i, int e) {
                ui.post(() -> { resolving = false; resolveNext(); });
            }
            @Override public void onServiceResolved(NsdServiceInfo i) {
                String ip = i.getHost() != null ? i.getHost().getHostAddress() : null;
                String name = i.getServiceName();
                boolean hue = i.getServiceType() != null && i.getServiceType().contains("_hue");
                ui.post(() -> {
                    resolving = false;
                    if (ip != null && !ip.contains(":")) {
                        if (hue) {
                            if (HueLink.users.containsKey(ip)) { resolveNext(); return; }
                            discovered.put("hb:" + ip, name);
                        } else discovered.put(ip, name);
                        renderDevices();
                    }
                    resolveNext();
                });
            }
        });
    }

    // ================================================================ run state

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(refresh);
        preview.start();
        // opened from the Quick Settings tile: start right away
        Intent it = getIntent();
        String token = it != null ? it.getStringExtra(EXTRA_AUTOSTART) : null;
        if (token != null) {
            it.removeExtra(EXTRA_AUTOSTART);
            boolean ours = token.equals(autostartToken);
            autostartToken = null;
            if (ours && !CaptureService.running) onToggle();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(refresh);
        preview.stop();
    }

    @Override
    protected void onDestroy() {
        stopDiscovery();
        net.shutdown();
        super.onDestroy();
    }

    private boolean lastShownRunning = false;
    private int shownEffect = -1;
    private boolean firstRefresh = true;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            boolean on = CaptureService.running;
            if (on != lastShownRunning || firstRefresh) {
                firstRefresh = false;
                lastShownRunning = on;
                GradientDrawable d = on
                        ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT, ACCENT_2})
                        : new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                if (!on) {
                    d.setColor(CARD_2);
                    d.setStroke(dp(2), ACCENT);
                }
                powerButton.setBackground(d);
                powerButton.setText(on ? L.t("■\nFerma", "■\nStop") : L.t("▶\nAvvia", "▶\nStart"));
            }
            if (Tuning.directEffect != shownEffect) {
                shownEffect = Tuning.directEffect;
                updateEffectTiles();
                updateHueVisibility();
            }
            String s = CaptureService.status;
            statusView.setText(on || s.startsWith(L.t("Errore", "Error")) || s.startsWith(L.t("Permesso", "Permission")) || s.startsWith(L.t("Fermato", "Stopped"))
                    ? s : L.t("Pronto. Fai partire la musica e premi Avvia.", "Ready. Play some music and tap Start."));
            AudioAnalyzer a = CaptureService.analyzer;
            bars.update(on && a != null ? a : null);
            ui.postDelayed(this, 50);
        }
    };

    private void onToggle() {
        if (CaptureService.running) {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_STOP));
            return;
        }
        if (devices.isEmpty() && Tuning.directMode) {
            toast(L.t("Aggiungi prima un dispositivo", "Add a device first"));
            return;
        }
        List<String> need = new ArrayList<>();
        need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS);
        List<String> missing = new ArrayList<>();
        for (String p : need) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (missing.isEmpty()) {
            beginCapture();
        } else {
            requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQ_PERMS) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginCapture();
        } else {
            CaptureService.status = L.t("Permesso audio negato: serve per catturare l'audio delle app", "Audio permission denied: it is needed to capture the apps' audio");
        }
    }

    /** Microphone needs no screen-capture consent; the phone's own audio does. */
    private void beginCapture() {
        if (Tuning.micMode) startService(RESULT_OK, null, true);
        else requestCapture();
    }

    private void startService(int resultCode, Intent data, boolean mic) {
        CaptureService.status = "";
        Intent svc = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_MIC, mic)
                .putExtra(CaptureService.EXTRA_TARGETS, String.join(",", devices.keySet()))
                .putExtra(CaptureService.EXTRA_MULTICAST, devices.isEmpty());
        if (data != null) svc.putExtra(CaptureService.EXTRA_DATA, data);
        startForegroundService(svc);
    }

    private void requestCapture() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent i = Build.VERSION.SDK_INT >= 34
                ? mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                : mpm.createScreenCaptureIntent();
        startActivityForResult(i, REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            CaptureService.status = L.t("Permesso di cattura negato", "Permission to capture denied");
            return;
        }
        startService(resultCode, data, false);
    }

    // ================================================================ WLED JSON API

    private Spinner fxSpinner;
    private final List<Integer> fxIds = new ArrayList<>();
    private int shownFx = -1;
    private final Map<String, Runnable> pendingSends = new HashMap<>();

    private void loadEffects() {
        List<String> hosts = wledHosts();
        if (hosts.isEmpty()) { toast(L.t("Nessun WLED raggiungibile", "No WLED reachable")); return; }
        String host = hosts.get(0);
        net.execute(() -> {
            try {
                JSONArray names = new JSONArray(httpGet("http://" + host + "/json/eff"));
                JSONArray data = new JSONArray(httpGet("http://" + host + "/json/fxdata"));
                JSONObject state = new JSONObject(httpGet("http://" + host + "/json/state"));
                JSONObject info = new JSONObject(httpGet("http://" + host + "/json/info"));
                JSONObject leds = info.optJSONObject("leds");
                boolean isMatrix = leds != null && leds.has("matrix");
                int currentFx = -1;
                JSONArray segs = state.optJSONArray("seg");
                if (segs != null && segs.length() > 0) currentFx = segs.getJSONObject(0).optInt("fx", -1);
                List<Integer> ids = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                for (int i = 0; i < names.length() && i < data.length(); i++) {
                    // fxdata entry: "sliders;colors;palette;flags;defaults", flags e.g. "1v", "01f", "2f"
                    String[] parts = data.optString(i, "").split(";", -1);
                    String flags = parts.length > 3 ? parts[3] : "";
                    boolean only2D = flags.indexOf('2') >= 0 && flags.indexOf('1') < 0;
                    if (only2D && !isMatrix) continue;
                    String name = names.optString(i, "");
                    if (name.startsWith("RSVD") || name.equals("-")) continue;
                    if (flags.indexOf('v') >= 0 || flags.indexOf('f') >= 0) {
                        ids.add(i);
                        labels.add(name + (flags.indexOf('f') >= 0 ? "  ♫" : "  ♪"));
                    }
                }
                final int cur = currentFx;
                ui.post(() -> {
                    fxIds.clear();
                    fxIds.addAll(ids);
                    int idx = ids.indexOf(cur);
                    shownFx = idx >= 0 ? cur : (ids.isEmpty() ? -1 : ids.get(0));
                    fxSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
                    if (idx >= 0) fxSpinner.setSelection(idx);
                    toast(ids.size() + L.t(" effetti audio trovati", " audio effects found"));
                });
            } catch (Exception e) {
                toast(L.t("WLED non raggiungibile: ", "WLED not reachable: ") + e.getMessage());
            }
        });
    }

    /** Devices that answered WLED's JSON API (Yeelight, word clock and ESPHome are skipped). */
    private final java.util.Set<String> wledOk = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private List<String> wledHosts() {
        List<String> out = new ArrayList<>();
        for (String k : devices.keySet()) if (wledOk.contains(k)) out.add(k);
        return out;
    }

    /** Sends a state change to every WLED. "seg" as an object applies to all selected segments. */
    private void sendWled(String json) {
        List<String> hosts = wledHosts();
        if (hosts.isEmpty()) { toast(L.t("Nessun WLED raggiungibile", "No WLED reachable")); return; }
        net.execute(() -> {
            for (String h : hosts) {
                try {
                    httpPost("http://" + h + "/json/state", json);
                } catch (Exception e) {
                    toast(L.t("Errore con ", "Error with ") + h + ": " + e.getMessage());
                }
            }
        });
    }

    /** Slider moves fire many events: only send the last value after 150 ms of quiet. */
    private void sendWledDebounced(String json) {
        String key = json.substring(0, Math.min(json.length(), 12));
        Runnable old = pendingSends.remove(key);
        if (old != null) ui.removeCallbacks(old);
        Runnable r = () -> { pendingSends.remove(key); sendWled(json); };
        pendingSends.put(key, r);
        ui.postDelayed(r, 150);
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(5000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static void httpPost(String url, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(5000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = c.getOutputStream()) {
            o.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        c.disconnect();
        if (code != 200) throw new IllegalStateException("HTTP " + code);
    }

    // ================================================================ UI helpers

    interface IntFormat { String format(int v); }
    interface IntChange { void changed(int v); }
    interface BoolChange { void changed(boolean on); }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 18, TEXT, true);
        t.setPadding(0, 0, 0, dp(12));
        return t;
    }

    private TextView subTitle(String s) {
        TextView t = text(s.toUpperCase(), 12, ACCENT, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(18), 0, dp(4));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(dp(24));
        c.setBackground(d);
        return c;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(14);
        return lp;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private View space(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, 1));
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private TextView pill(String label, boolean primary, View.OnClickListener click) {
        TextView b = text(label, 14, primary ? Color.WHITE : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable d = primary
                ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACCENT, ACCENT_2})
                : new GradientDrawable();
        if (!primary) d.setColor(CARD_2);
        d.setCornerRadius(dp(14));
        b.setBackground(d);
        b.setOnClickListener(click);
        return b;
    }

    private void slider(LinearLayout parent, String name, int min, int max, int value, IntFormat fmt, IntChange onChange) {
        LinearLayout head = row();
        head.setPadding(0, dp(12), 0, 0);
        TextView label = text(name, 14, TEXT, false);
        TextView val = text("", 14, TEXT_2, false);
        head.addView(label, weight());
        head.addView(val);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        int start = Math.max(min, Math.min(max, value));
        bar.setProgress(start - min);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
        val.setText(fmt.format(start));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                val.setText(fmt.format(p + min));
                if (fromUser) onChange.changed(p + min);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        parent.addView(head, matchWrap());
        parent.addView(bar, matchWrap());
    }

    @SuppressWarnings("UseSwitchCompatOrMaterialCode")
    private Switch toggleRow(LinearLayout parent, String name, boolean value, BoolChange onChange) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, dp(2));
        r.addView(text(name, 14, TEXT, false), weight());
        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        sw.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ACCENT, 0x55FFFFFF}));
        sw.setOnCheckedChangeListener((b, on) -> onChange.changed(on));
        r.addView(sw);
        parent.addView(r, matchWrap());
        return sw;
    }

    private void save(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    private void saveBool(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    private static String signedDb(int db) {
        return (db > 0 ? "+" : "") + db + " dB";
    }

    private void toast(String msg) {
        ui.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 16-band visualiser mirroring what the app hears. */
    static class BarsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] values = new int[16];
        private final RectF rect = new RectF();

        BarsView(Context c) {
            super(c);
        }

        void update(AudioAnalyzer a) {
            if (a == null) java.util.Arrays.fill(values, 0);
            else System.arraycopy(a.lastBands, 0, values, 0, 16);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float gap = w * 0.012f;
            float bw = (w - gap * 15) / 16f;
            float r = bw / 2;
            for (int i = 0; i < 16; i++) {
                float x = i * (bw + gap);
                paint.setColor(0x1AFFFFFF);
                rect.set(x, 0, x + bw, h);
                c.drawRoundRect(rect, r, r, paint);
                float bh = Math.max(bw, h * values[i] / 255f);
                // the colours of the effect that is playing (gradient, or the hue for Bass pulse)
                int col = 0xFF000000 | DirectEffects.previewColor(Tuning.directEffect, i / 15.0);
                paint.setColor(values[i] == 0 ? (col & 0x00FFFFFF) | 0x33000000 : col);
                rect.set(x, h - bh, x + bw, h);
                c.drawRoundRect(rect, r, r, paint);
            }
        }
    }
}
