package com.limelight.pocket.global;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.device.PocketNotifications;
import com.limelight.pocket.ui.FocusGlow;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.Spring;

/**
 * A quick reply to a message (Discord, WhatsApp, Telegram...) over the game: the last few
 * messages, a line to type in with the controller's keyboard, and Send, through the message's
 * own reply (as in its notification). "Open" goes to the app for anything more (a call, a
 * voice channel).
 */
public class QuickReplyActivity extends Activity {

    private static PocketNotifications.Note pending;

    public static void open(Context c, PocketNotifications.Note note) {
        pending = note;
        try {
            c.startActivity(new Intent(c, QuickReplyActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (RuntimeException ignored) {
        }
    }

    private PocketNotifications.Note note;
    private EditText input;
    private LinearLayout card;
    private boolean done;
    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        note = pending;
        if (note == null) {
            finish();
            return;
        }
        ConsoleActivity.applyFullscreenWindow(this);
        overridePendingTransition(0, 0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        d = getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= 31) {
            WindowManager wm = getSystemService(WindowManager.class);
            if (wm != null && wm.isCrossWindowBlurEnabled()) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.setBlurBehindRadius(Math.round(22 * d));
                getWindow().setAttributes(lp);
            }
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x8C000000);
        setContentView(root);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF5222229, 0xF516161C});
        bg.setCornerRadius(dp(24));
        bg.setStroke(Math.max(1, Math.round(d)), 0x26FFFFFF);
        card.setBackground(bg);
        card.setElevation(dp(24));
        card.setClipChildren(false);
        card.setClipToPadding(false);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(dp(580), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cl.topMargin = dp(18);
        root.addView(card, cl);

        // Who, and where
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView face = new ImageView(this);
        face.setScaleType(ImageView.ScaleType.CENTER_CROP);
        face.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        face.setClipToOutline(true);
        Drawable pic = null;
        if (note.picture != null && Build.VERSION.SDK_INT >= 23) {
            try {
                pic = note.picture.loadDrawable(this);
            } catch (RuntimeException ignored) {
            }
        }
        face.setImageDrawable(pic != null ? pic : MessageBanner.appIcon(this, note.packageName));
        head.addView(face, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wl.setMarginStart(dp(12));
        head.addView(who, wl);
        TextView app = text(9.5f, 0x99FFFFFF, PocketFonts.bold());
        app.setLetterSpacing(0.12f);
        String where = note.conversation != null && !note.conversation.equals(note.title) ? "  ·  " + note.conversation : "";
        app.setText((note.app + where).toUpperCase(java.util.Locale.ROOT));
        who.addView(app);
        TextView name = text(16f, 0xFFFFFFFF, PocketFonts.semibold());
        name.setText(note.title);
        who.addView(name);
        card.addView(head);

        // The last few messages
        LinearLayout talk = new LinearLayout(this);
        talk.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(12);
        card.addView(talk, tl);
        if (note.history.isEmpty()) {
            talk.addView(line(note.text, true));
        }
        else {
            for (int i = 0; i < note.history.size(); i++) {
                talk.addView(line(note.history.get(i), i == note.history.size() - 1));
            }
        }

        // The line to type in, and its buttons
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        bl.topMargin = dp(14);
        card.addView(bottom, bl);
        input = new EditText(this);
        input.setHint(getString(R.string.pocket_message_reply_to, note.title));
        input.setHintTextColor(0x73FFFFFF);
        input.setTextColor(0xFFFFFFFF);
        input.setTypeface(PocketFonts.medium());
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        input.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable ib = new GradientDrawable();
        ib.setCornerRadius(dp(14));
        ib.setColor(0x1AFFFFFF);
        ib.setStroke(Math.max(1, Math.round(d)), 0x33FFFFFF);
        input.setBackground(ib);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                send();
                return true;
            }
            return false;
        });
        bottom.addView(input, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView send = pill(getString(R.string.pocket_message_send), true);
        send.setOnClickListener(v -> {
            FocusGlow.press(v);
            send();
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        sl.setMarginStart(dp(10));
        bottom.addView(send, sl);
        TextView openApp = pill(getString(R.string.pocket_device_open_in, note.app), false);
        openApp.setOnClickListener(v -> {
            FocusGlow.press(v);
            PocketSounds.play(this, PocketSounds.OPEN);
            done = true;
            PocketNotifications.open(getApplicationContext(), note);
            finishQuiet();
        });
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        ol.setMarginStart(dp(8));
        bottom.addView(openApp, ol);

        LinearLayout hints = com.limelight.pocket.ui.ConsoleScreen.hintBar(this);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.addView(hints, hlp);
        ConsoleActivity.fillHintBar(this, hints,
                ConsoleActivity.hintA(this, R.string.pocket_message_hint_choose),
                ConsoleActivity.hintB(this, R.string.pocket_hint_back));
        root.post(() -> {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) hints.getLayoutParams();
            p.topMargin = card.getBottom() + dp(10);
            hints.setLayoutParams(p);
        });

        if (PocketPrefs.animationsEnabled(this)) {
            card.setTranslationY(-dp(40));
            card.setAlpha(0f);
            card.animate().translationY(0).alpha(1f).setDuration(360).setInterpolator(Spring.POP).start();
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(160).start();
        }
        PocketSounds.play(this, PocketSounds.OPEN);
        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 180);
    }

    private void send() {
        if (done) {
            return;
        }
        CharSequence text = input.getText();
        if (text == null || text.toString().trim().isEmpty()) {
            PocketSounds.play(this, PocketSounds.ERROR);
            return;
        }
        if (PocketNotifications.reply(getApplicationContext(), note, text.toString().trim())) {
            done = true;
            PocketSounds.play(this, PocketSounds.SELECT);
            hideKeyboard();
            Toast.makeText(getApplicationContext(), getString(R.string.pocket_message_sent, note.title),
                    Toast.LENGTH_SHORT).show();
            finishQuiet();
        }
        else {
            PocketSounds.play(this, PocketSounds.ERROR);
            Toast.makeText(this, R.string.pocket_message_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && input != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void finishQuiet() {
        hideKeyboard();
        if (PocketPrefs.animationsEnabled(this) && card != null) {
            card.animate().translationY(-dp(30)).alpha(0f).setDuration(180).withEndAction(this::close).start();
            card.postDelayed(this::close, 400);
        }
        else {
            close();
        }
    }

    private void close() {
        if (isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.setBlurBehindRadius(0);
                getWindow().setAttributes(lp);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            } catch (RuntimeException ignored) {
            }
        }
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing() && done) {
            close();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean first = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
        if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (first) {
                PocketSounds.play(this, PocketSounds.BACK);
                done = true;
                finishQuiet();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BUTTON_A && first) {
            View f = getCurrentFocus();
            if (f == input) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
                return true;
            }
            if (f != null && f.isClickable()) {
                f.performClick();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private TextView line(String s, boolean newest) {
        TextView t = text(13f, newest ? 0xF2FFFFFF : 0x8CFFFFFF, newest ? PocketFonts.medium() : PocketFonts.regular());
        t.setText(s);
        t.setMaxLines(newest ? 3 : 1);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView pill(String s, boolean primary) {
        TextView t = text(13f, primary ? 0xFF0B0B0D : 0xFFFFFFFF, PocketFonts.semibold());
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(21));
        g.setColor(primary ? 0xFFFFFFFF : 0x26FFFFFF);
        t.setBackground(g);
        t.setFocusable(true);
        t.setClickable(true);
        FocusGlow.apply(t, dp(21), false, 1.05f, null);
        return t;
    }

    private TextView text(float sp, int color, android.graphics.Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }
}
