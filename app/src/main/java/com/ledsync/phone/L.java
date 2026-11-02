// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import android.content.Context;

/** Interface language: chosen in the app ("lang" = it / en), otherwise Italian when the phone is set to Italian. */
final class L {
    private L() { }

    static boolean IT = "it".equals(java.util.Locale.getDefault().getLanguage());

    /** Called by {@link App} before anything else runs, so every text is built in the chosen language. */
    static void init(Context c) {
        String lang = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "");
        if ("it".equals(lang)) IT = true;
        else if ("en".equals(lang)) IT = false;
    }

    static String t(String it, String en) {
        return IT ? it : en;
    }
}
