// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

/** Interface language: Italian when the phone is set to Italian, English otherwise. */
final class L {
    private L() { }

    static final boolean IT = "it".equals(java.util.Locale.getDefault().getLanguage());

    static String t(String it, String en) {
        return IT ? it : en;
    }
}
