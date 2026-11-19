package com.limelight.pocket.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;

/**
 * One line to type, over the screen: a small glass card with a title, a line of help, and the
 * field, the keyboard up straight away. Enter (or the keyboard's Done) gives the text; B leaves.
 */
public final class TextPrompt {

    public interface Done {
        void onText(String text);
    }

    private TextPrompt() {
    }

    public static void show(Activity a, String title, String help, String initial, String hint, final Done done) {
        show(a, title, help, initial, hint, false, done);
    }

    public static void show(Activity a, String title, String help, String initial, String hint, boolean secret,
                            final Done done) {
        final float d = a.getResources().getDisplayMetrics().density;
        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(20 * d);
        card.setPadding(p, Math.round(18 * d), p, p);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF7222229, 0xF716161C});
        bg.setCornerRadius(24 * d);
        bg.setStroke(Math.max(1, Math.round(d)), 0x26FFFFFF);
        card.setBackground(bg);

        TextView t = new TextView(a);
        t.setText(title);
        t.setTypeface(PocketFonts.display());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        t.setTextColor(0xFFFFFFFF);
        card.addView(t);
        if (help != null) {
            TextView h = new TextView(a);
            h.setText(help);
            h.setTypeface(PocketFonts.medium());
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            h.setTextColor(0x99FFFFFF);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            hl.topMargin = Math.round(4 * d);
            card.addView(h, hl);
        }
        final EditText field = new EditText(a);
        field.setSingleLine(true);
        field.setText(initial != null ? initial : "");
        field.setSelection(field.getText().length());
        field.setHint(hint);
        field.setHintTextColor(0x66FFFFFF);
        field.setTextColor(0xFFFFFFFF);
        field.setTypeface(PocketFonts.medium());
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        field.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        int fp = Math.round(16 * d);
        field.setPadding(fp, 0, fp, 0);
        GradientDrawable fb = new GradientDrawable();
        fb.setCornerRadius(14 * d);
        fb.setColor(0x1AFFFFFF);
        fb.setStroke(Math.max(1, Math.round(d)), 0x40FFFFFF);
        field.setBackground(fb);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(48 * d));
        fl.topMargin = Math.round(14 * d);
        card.addView(field, fl);
        final boolean[] given = {false};
        field.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_SEARCH
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN)) {
                if (!given[0]) {
                    given[0] = true;
                    PocketSounds.play(a, PocketSounds.SELECT);
                    String s = field.getText().toString().trim();
                    dialog.dismiss();
                    done.onText(s);
                }
                return true;
            }
            return false;
        });
        dialog.setContentView(card);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Math.round(520 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.y = Math.round(28 * d);
            lp.dimAmount = 0.6f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            if (PocketPrefs.animationsEnabled(a)) {
                w.setWindowAnimations(android.R.style.Animation_Dialog);
            }
        }
        dialog.setOnShowListener(x -> {
            field.requestFocus();
            field.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 120);
        });
        PocketSounds.play(a, PocketSounds.OPEN);
        dialog.show();
    }
}
