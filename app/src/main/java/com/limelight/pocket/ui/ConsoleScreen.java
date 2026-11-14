package com.limelight.pocket.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.limelight.pocket.ConsoleActivity;

/**
 * The look Pocket Nexus's own tools share (Files, the browser, search): the dark backdrop with
 * a glow of the accent, titles, list rows that light up white with dark words when chosen (like
 * the menus), the button hints, and a box to type in (with the controller keyboard).
 */
public final class ConsoleScreen {
    private ConsoleScreen() {}

    /** The screen's backdrop: near black, a soft glow of the accent from the top corner. */
    public static Drawable backdrop(Context c) {
        return new Backdrop(PocketTheme.accent(c));
    }

    private static final class Backdrop extends Drawable {
        private final Paint base = new Paint();
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;

        Backdrop(int accent) {
            this.accent = accent;
        }

        @Override
        protected void onBoundsChange(@NonNull Rect b) {
            base.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xFF131318, 0xFF07070A, Shader.TileMode.CLAMP));
            float r = Math.max(b.width(), b.height()) * 0.75f;
            glow.setShader(new RadialGradient(b.left + b.width() * 0.12f, b.top - b.height() * 0.1f, r,
                    (accent & 0x00FFFFFF) | 0x33000000, accent & 0x00FFFFFF, Shader.TileMode.CLAMP));
            glow2.setShader(new RadialGradient(b.right, b.bottom + b.height() * 0.2f, r * 0.8f,
                    0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            canvas.drawRect(b, base);
            canvas.drawRect(b, glow);
            canvas.drawRect(b, glow2);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(medium ? PocketFonts.medium() : PocketFonts.regular());
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, 22, 0xFFFFFFFF, true);
        t.setTypeface(PocketFonts.semibold());
        t.setLetterSpacing(-0.02f);
        t.setText(s);
        return t;
    }

    /** A bar for the button hints (filled with ConsoleActivity.fillHintBar). */
    public static LinearLayout hintBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        return bar;
    }

    /** A row's chosen look: white with dark words when focused, glass otherwise. */
    public static void lightRow(View row, boolean focused, TextView... words) {
        Context c = row.getContext();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 12));
        if (focused) {
            bg.setColor(0xF7FFFFFF);
        }
        else {
            bg.setColor(0x0DFFFFFF);
        }
        row.setBackground(bg);
        for (int i = 0; i < words.length; i++) {
            TextView t = words[i];
            if (t == null) {
                continue;
            }
            boolean first = i == 0;
            t.setTextColor(focused ? (first ? 0xFF111114 : 0xA6111114) : (first ? 0xF2FFFFFF : 0x8CFFFFFF));
        }
        row.animate().cancel();
        if (focused) {
            row.setScaleX(0.98f);
            row.setScaleY(0.98f);
            row.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(Spring.POP).start();
        }
    }

    public interface TextResult {
        void onText(String text);
    }

    /** Asks for a line of text (the keyboard comes up by itself). */
    public static void prompt(Activity a, String title, String initial, String hint, final TextResult result) {
        final EditText field = new EditText(a);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (initial != null) {
            field.setText(initial);
            field.setSelection(initial.length());
        }
        if (hint != null) {
            field.setHint(hint);
        }
        FrameLayout box = new FrameLayout(a);
        int pad = dp(a, 20);
        box.setPadding(pad, dp(a, 8), pad, 0);
        box.addView(field, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (dlg, w) -> result.onText(field.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEARCH) {
                dialog.dismiss();
                result.onText(field.getText().toString().trim());
                return true;
            }
            return false;
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        ConsoleActivity.showImmersive(dialog);
        field.requestFocus();
    }
}
