// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import android.app.Application;

/** Sets the interface language before any screen, service or tile is created. */
public final class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        L.init(this);
    }
}
