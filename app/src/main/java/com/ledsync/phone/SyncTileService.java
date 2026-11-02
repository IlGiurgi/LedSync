// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: tap to start (opens the app, which asks for capture) or stop the light sync. */
public class SyncTileService extends TileService {
    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        if (CaptureService.running) {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_STOP));
            Tile t = getQsTile();
            if (t != null) { t.setState(Tile.STATE_INACTIVE); t.updateTile(); }
            return;
        }
        String token = java.util.UUID.randomUUID().toString();
        MainActivity.autostartToken = token;
        Intent open = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_AUTOSTART, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(open);
        }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(CaptureService.running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
