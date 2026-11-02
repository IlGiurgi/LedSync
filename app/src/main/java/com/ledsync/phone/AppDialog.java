// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static com.ledsync.phone.MainActivity.ACCENT;
import static com.ledsync.phone.MainActivity.ACCENT_2;
import static com.ledsync.phone.MainActivity.CARD;
import static com.ledsync.phone.MainActivity.CARD_2;
import static com.ledsync.phone.MainActivity.TEXT;
import static com.ledsync.phone.MainActivity.TEXT_2;

/**
 * Pop-up in the app's own dark style (rounded card, violet-pink buttons), used instead of the
 * system AlertDialog, which follows the phone maker's theme. Same calls as AlertDialog.Builder.
 */
final class AppDialog {
    private final Context ctx;
    private final Dialog dialog;
    private CharSequence title, message;
    private View view;
    private CharSequence[] items;
    private DialogInterface.OnClickListener itemClick;
    private CharSequence posText, negText, neuText;
    private DialogInterface.OnClickListener posClick, negClick, neuClick;
    private TextView messageView;
    private boolean built;

    AppDialog(Context ctx) {
        this.ctx = ctx;
        this.dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    AppDialog setTitle(CharSequence t) { title = t; return this; }

    AppDialog setMessage(CharSequence m) {
        message = m;
        if (messageView != null) {
            messageView.setText(m);
            messageView.setVisibility(TextUtils.isEmpty(m) ? View.GONE : View.VISIBLE);
        }
        return this;
    }

    AppDialog setView(View v) { view = v; return this; }

    AppDialog setItems(CharSequence[] list, DialogInterface.OnClickListener click) {
        items = list;
        itemClick = click;
        return this;
    }

    AppDialog setPositiveButton(CharSequence t, DialogInterface.OnClickListener c) { posText = t; posClick = c; return this; }

    AppDialog setNegativeButton(CharSequence t, DialogInterface.OnClickListener c) { negText = t; negClick = c; return this; }

    AppDialog setNeutralButton(CharSequence t, DialogInterface.OnClickListener c) { neuText = t; neuClick = c; return this; }

    AppDialog setOnDismissListener(DialogInterface.OnDismissListener l) { dialog.setOnDismissListener(l); return this; }

    AppDialog create() {
        if (!built) build();
        return this;
    }

    AppDialog show() {
        create();
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            int screen = ctx.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(screen - dp(32), dp(440)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return this;
    }

    void dismiss() {
        dialog.dismiss();
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        built = true;
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.6f);
        }
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), 0x22FFFFFF);
        card.setBackground(bg);

        if (!TextUtils.isEmpty(title)) {
            TextView t = text(title, 19, TEXT, true);
            t.setPadding(0, 0, 0, dp(6));
            card.addView(t);
        }
        messageView = text(message == null ? "" : message, 14, TEXT_2, false);
        messageView.setLineSpacing(0, 1.15f);
        messageView.setPadding(0, dp(2), 0, dp(8));
        messageView.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
        card.addView(messageView);

        if (view != null) {
            if (view instanceof EditText) styleInput((EditText) view);
            if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
            MaxHeight box = new MaxHeight(ctx, 0.62f);
            box.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(4);
            lp.bottomMargin = dp(6);
            card.addView(box, lp);
        }

        if (items != null) {
            LinearLayout list = new LinearLayout(ctx);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                TextView row = text(items[i], 15, TEXT, false);
                row.setPadding(dp(16), dp(14), dp(16), dp(14));
                GradientDrawable rb = new GradientDrawable();
                rb.setColor(CARD_2);
                rb.setCornerRadius(dp(14));
                row.setBackground(rb);
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (itemClick != null) itemClick.onClick(dialog, idx);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(8);
                list.addView(row, lp);
            }
            ScrollView sv = new ScrollView(ctx);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(list);
            MaxHeight box = new MaxHeight(ctx, 0.6f);
            box.addView(sv);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            card.addView(box, lp);
        }

        if (posText != null || negText != null || neuText != null) {
            LinearLayout buttons = new LinearLayout(ctx);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            buttons.setPadding(0, dp(10), 0, 0);
            if (neuText != null) buttons.addView(button(neuText, false, neuClick, DialogInterface.BUTTON_NEUTRAL));
            View spacer = new View(ctx);
            buttons.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
            if (negText != null) buttons.addView(button(negText, false, negClick, DialogInterface.BUTTON_NEGATIVE));
            if (posText != null) {
                View gap = new View(ctx);
                buttons.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
                buttons.addView(button(posText, true, posClick, DialogInterface.BUTTON_POSITIVE));
            }
            card.addView(buttons);
        }
        dialog.setContentView(card);
    }

    private TextView button(CharSequence label, boolean primary, DialogInterface.OnClickListener click, int which) {
        TextView b = text(label, 15, primary ? Color.WHITE : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(11), dp(18), dp(11));
        GradientDrawable d = primary
                ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACCENT, ACCENT_2})
                : new GradientDrawable();
        if (!primary) d.setColor(CARD_2);
        d.setCornerRadius(dp(14));
        b.setBackground(d);
        b.setOnClickListener(v -> {
            dialog.dismiss();
            if (click != null) click.onClick(dialog, which);
        });
        return b;
    }

    private void styleInput(EditText e) {
        e.setTextColor(TEXT);
        e.setHintTextColor(TEXT_2);
        e.setTextSize(16);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD_2);
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1), 0x33FFFFFF);
        e.setBackground(d);
    }

    private TextView text(CharSequence s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /** A frame that never grows taller than a share of the screen, so long content scrolls inside. */
    private static final class MaxHeight extends FrameLayout {
        private final float share;

        MaxHeight(Context c, float share) {
            super(c);
            this.share = share;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int max = (int) (getResources().getDisplayMetrics().heightPixels * share);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }
}
