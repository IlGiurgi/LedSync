// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static com.ledsync.phone.MainActivity.ACCENT;
import static com.ledsync.phone.MainActivity.ACCENT_2;
import static com.ledsync.phone.MainActivity.BG;
import static com.ledsync.phone.MainActivity.CARD;
import static com.ledsync.phone.MainActivity.CARD_2;
import static com.ledsync.phone.MainActivity.TEXT;
import static com.ledsync.phone.MainActivity.TEXT_2;

/** First-launch welcome pages and the "How it works" guide, sharing the same content. */
final class Guide {
    private Guide() { }

    /** {icon, title, text} */
    static String[][] pages() {
        return new String[][]{
                {"🎵", L.t("Benvenuto in LedSync", "Welcome to LedSync"),
                        L.t("LedSync fa ballare le tue luci con la musica che suona sul telefono: Spotify, YouTube, qualsiasi app.\n\n"
                                + "Il telefono ascolta l'audio, lo analizza e manda i colori alle luci via Wi-Fi. Niente microfono sulle luci, niente PC.",
                            "LedSync makes your lights dance to the music playing on your phone: Spotify, YouTube, any app.\n\n"
                                + "The phone listens to the audio, analyses it and sends the colours to your lights over Wi-Fi. No microphone on the lights, no PC.")},
                {"💡", L.t("1. Aggiungi le luci", "1. Add your lights"),
                        L.t("Premi \"Cerca in rete\": l'app trova da sola WLED, Yeelight, Govee e il bridge Philips Hue. Se una luce non compare, usa \"Aggiungi IP\" e scegli il tipo.\n\n"
                                + "Telefono e luci devono essere sulla stessa rete Wi-Fi.\n\nAlcune luci vanno preparate una volta sola: guarda la pagina successiva.",
                            "Tap \"Search network\": the app finds WLED, Yeelight, Govee and the Philips Hue bridge by itself. If a light is missing, use \"Add IP\" and pick its type.\n\n"
                                + "Phone and lights must be on the same Wi-Fi network.\n\nSome lights need a one-time setup: see the next page.")},
                {"🔧", L.t("2. Prepara le luci", "2. Prepare your lights"),
                        L.t("• WLED: niente da fare, la ricezione in tempo reale è già attiva (Config → Sync Interfaces → \"Receive UDP realtime\"). "
                                + "Solo se vuoi usare gli effetti di WLED invece di quelli dell'app: serve WLED con Audio Reactive e, in Config → Usermods → AudioReactive, Sync su \"Receive\".\n"
                                + "• Yeelight: nell'app Yeelight apri la lampadina → impostazioni → attiva \"Controllo LAN\".\n"
                                + "• Govee: nell'app Govee Home apri la luce → impostazioni → attiva \"LAN Control\" (non tutti i modelli ce l'hanno).\n"
                                + "• Philips Hue: aggiungi il bridge, premi il pulsante rotondo sul bridge e tocca Continua entro 30 secondi. Arrivano tutte le lampadine colorate.\n"
                                + "• ESPHome: la luce deve avere l'effetto \"wled\" ed essere accesa con quell'effetto.\n"
                                + "• Orologi e matrici: tocca la luce e imposta quanti LED ha e la larghezza della griglia.",
                            "• WLED: nothing to do, realtime receive is on by default (Config → Sync Interfaces → \"Receive UDP realtime\"). "
                                + "Only if you want WLED's own effects instead of the app's: you need WLED with Audio Reactive and, in Config → Usermods → AudioReactive, Sync set to \"Receive\".\n"
                                + "• Yeelight: in the Yeelight app open the bulb → settings → enable \"LAN Control\".\n"
                                + "• Govee: in the Govee Home app open the light → settings → enable \"LAN Control\" (not every model has it).\n"
                                + "• Philips Hue: add the bridge, press the round button on it and tap Continue within 30 seconds. All colour bulbs are added.\n"
                                + "• ESPHome: the light needs the \"wled\" effect and must be on with that effect.\n"
                                + "• Clocks and matrices: tap the light and set its LED count and grid width.")},
                {"▶", L.t("3. Avvia", "3. Start"),
                        L.t("Fai partire la musica e premi Avvia. Android chiede cosa condividere: scegli \"Schermo intero\". Serve solo per l'audio: lo schermo non viene registrato e niente viene salvato.\n\n"
                                + "Alcune app bloccano la cattura dell'audio. Per la musica da TV, PC o casse usa \"Ascolta col microfono\".",
                            "Play some music and tap Start. Android asks what to share: choose \"Entire screen\". It is only for the audio: the screen is not recorded and nothing is saved.\n\n"
                                + "Some apps block audio capture. For music from a TV, PC or speakers use \"Listen with the microphone\".")},
                {"🎨", L.t("4. Personalizza", "4. Make it yours"),
                        L.t("• Scegli un effetto; ce ne sono molti altri in \"Altri effetti\".\n• Colori: gradiente, luminosità, \"Colori a tempo\".\n"
                                + "• Tocca una luce per: rinominarla, cambiarne la luminosità, darle un effetto diverso, invertirla, dirle quanti LED ha o che è una griglia.\n"
                                + "• L'interruttore accanto a ogni luce la spegne senza toglierla.\n• Salva le combinazioni che ti piacciono nei Preferiti (tieni premuto un preferito per cancellarlo).\n"
                                + "• Cambio automatico: l'effetto cambia da solo a tempo di musica.",
                            "• Pick an effect; many more are under \"More effects\".\n• Colours: gradient, brightness, \"Colours on the beat\".\n"
                                + "• Tap a light to rename it, change its brightness, give it its own effect, reverse it, set its LED count or mark it as a grid.\n"
                                + "• The switch next to each light turns it off without removing it.\n• Save the combinations you like as Favourites (long-press one to delete it).\n"
                                + "• Automatic change: the effect changes by itself in time with the music.")},
                {"✅", L.t("5. Consigli", "5. Tips"),
                        L.t("• Non usare insieme un altro programma che comanda le stesse luci (es. un effetto da Home Assistant o LedFx): si darebbero fastidio.\n"
                                + "• Su WLED attiva \"Disable WiFi sleep\" (Config → WiFi Setup) per una risposta più pronta.\n"
                                + "• Imposta la batteria di LedSync su \"Senza restrizioni\", così non si ferma a schermo spento.\n"
                                + "• Con casse Bluetooth le luci arrivano prima del suono: usa \"Ritardo luci\" nelle regolazioni avanzate.\n"
                                + "• Se qualcosa va a scatti, apri \"Diagnostica connessione e ritardi\".\n\nQuesta guida resta sempre nel pulsante ⓘ in alto.",
                            "• Do not run another program on the same lights at the same time (e.g. a Home Assistant effect or LedFx): they would fight.\n"
                                + "• On WLED enable \"Disable WiFi sleep\" (Config → WiFi Setup) for a snappier response.\n"
                                + "• Set LedSync's battery usage to \"Unrestricted\" so it keeps running with the screen off.\n"
                                + "• With Bluetooth speakers the lights come before the sound: use \"Light delay\" in the advanced settings.\n"
                                + "• If something stutters, open \"Connection and latency check\".\n\nThis guide is always available from the ⓘ button at the top.")},
        };
    }

    /** Full-screen welcome with one page at a time; onDone runs when it is closed. */
    static void showWelcome(Activity a, Runnable onDone) {
        String[][] pages = pages();
        Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(BG));
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(a, 24), dp(a, 40), dp(a, 24), dp(a, 28));

        TextView skip = text(a, L.t("Salta", "Skip"), 15, TEXT_2, true);
        skip.setGravity(Gravity.END);
        root.addView(skip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(0, dp(a, 24), 0, dp(a, 16));
        sv.addView(page);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout dots = new LinearLayout(a);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, dp(a, 8), 0, dp(a, 16));
        root.addView(dots, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout nav = new LinearLayout(a);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = pill(a, L.t("Indietro", "Back"), false);
        TextView next = pill(a, "", true);
        nav.addView(back, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        nav.addView(new View(a), new LinearLayout.LayoutParams(dp(a, 12), 1));
        nav.addView(next, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int[] at = {0};
        Runnable[] show = new Runnable[1];
        show[0] = () -> {
            String[] p = pages[at[0]];
            page.removeAllViews();
            TextView icon = text(a, p[0], 56, TEXT, false);
            icon.setGravity(Gravity.CENTER);
            GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT, ACCENT_2});
            circle.setShape(GradientDrawable.OVAL);
            icon.setBackground(circle);
            page.addView(icon, new LinearLayout.LayoutParams(dp(a, 120), dp(a, 120)));
            TextView t = text(a, p[1], 26, TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(a, 28), 0, dp(a, 16));
            page.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView body = text(a, p[2], 16, TEXT_2, false);
            body.setLineSpacing(0, 1.25f);
            page.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            sv.scrollTo(0, 0);
            dots.removeAllViews();
            for (int i = 0; i < pages.length; i++) {
                View dot = new View(a);
                GradientDrawable g = new GradientDrawable();
                g.setColor(i == at[0] ? ACCENT : CARD_2);
                g.setCornerRadius(dp(a, 4));
                dot.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, i == at[0] ? 22 : 8), dp(a, 8));
                lp.leftMargin = lp.rightMargin = dp(a, 4);
                dots.addView(dot, lp);
            }
            back.setVisibility(at[0] == 0 ? View.INVISIBLE : View.VISIBLE);
            next.setText(at[0] == pages.length - 1 ? L.t("Inizia", "Get started") : L.t("Avanti", "Next"));
        };
        back.setOnClickListener(v -> { if (at[0] > 0) { at[0]--; show[0].run(); } });
        next.setOnClickListener(v -> {
            if (at[0] < pages.length - 1) { at[0]++; show[0].run(); } else d.dismiss();
        });
        skip.setOnClickListener(v -> d.dismiss());
        d.setOnDismissListener(x -> { if (onDone != null) onDone.run(); });
        d.setContentView(root);
        show[0].run();
        d.show();
    }

    /** The same content as one scrolling page, from the ⓘ button. */
    static void showGuide(Activity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        for (String[] p : pages()) {
            LinearLayout sec = new LinearLayout(a);
            sec.setOrientation(LinearLayout.VERTICAL);
            sec.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 14));
            GradientDrawable g = new GradientDrawable();
            g.setColor(CARD_2);
            g.setCornerRadius(dp(a, 14));
            sec.setBackground(g);
            TextView t = text(a, p[0] + "  " + p[1], 16, TEXT, true);
            t.setPadding(0, 0, 0, dp(a, 6));
            sec.addView(t);
            TextView body = text(a, p[2], 14, TEXT_2, false);
            body.setLineSpacing(0, 1.2f);
            sec.addView(body);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(a, 10);
            box.addView(sec, lp);
        }
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(box);
        new AppDialog(a)
                .setTitle(L.t("Come funziona", "How it works"))
                .setView(sv)
                .setNeutralButton(L.t("Benvenuto", "Welcome"), (x, y) -> showWelcome(a, null))
                .setPositiveButton(L.t("Chiudi", "Close"), null)
                .show();
    }

    private static TextView pill(Activity a, String label, boolean primary) {
        TextView b = text(a, label, 16, primary ? Color.WHITE : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(a, 16), dp(a, 15), dp(a, 16), dp(a, 15));
        GradientDrawable d = primary
                ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACCENT, ACCENT_2})
                : new GradientDrawable();
        if (!primary) d.setColor(CARD);
        d.setCornerRadius(dp(a, 16));
        b.setBackground(d);
        return b;
    }

    private static TextView text(Activity a, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
