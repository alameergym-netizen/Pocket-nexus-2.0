package com.limelight.pocket;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.pocket.global.GlobalButtons;
import com.limelight.pocket.ui.ConsoleScreen;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.RadialMenuView;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.List;

/**
 * The radial menu, edited as itself: the real ring on screen; point at a slice with the stick
 * (or the D-pad), A picks what it does from a list with icons, and the ring shows the change
 * straight away. Two rings: the one in a stream, and the one over every game.
 */
public class RadialEditorActivity extends ConsoleActivity {

    private static final String EXTRA_GLOBAL = "global";

    public static void open(Activity from, boolean global) {
        from.startActivity(new Intent(from, RadialEditorActivity.class).putExtra(EXTRA_GLOBAL, global));
    }

    private boolean global;
    private RadialMenuView ring;
    private int lastSlot = -1;

    @Override
    protected boolean allowsAutoResume() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        global = getIntent().getBooleanExtra(EXTRA_GLOBAL, false);
        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setId(R.id.pocketRoot);
        root.setBackground(ConsoleScreen.backdrop(this));
        setContentView(root);

        ring = new RadialMenuView(this);
        root.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Math.round(40 * d), Math.round(30 * d), 0, 0);
        TextView eyebrow = new TextView(this);
        eyebrow.setText(R.string.pocket_radial_editor_eyebrow);
        eyebrow.setTypeface(PocketFonts.bold());
        eyebrow.setLetterSpacing(0.16f);
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        eyebrow.setTextColor(0x99FFFFFF);
        head.addView(eyebrow);
        TextView title = new TextView(this);
        title.setText(global ? R.string.pocket_header_everywhere_radial : R.string.pocket_header_radial);
        title.setTypeface(PocketFonts.display());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTextColor(0xFFFFFFFF);
        head.addView(title);
        TextView how = new TextView(this);
        how.setText(R.string.pocket_radial_editor_how);
        how.setTypeface(PocketFonts.medium());
        how.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        how.setTextColor(0x99FFFFFF);
        how.setMaxWidth(Math.round(300 * d));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Math.round(8 * d);
        head.addView(how, hl);
        root.addView(head, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        LinearLayout hints = ConsoleScreen.hintBar(this);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        hlp.rightMargin = Math.round(32 * d);
        hlp.bottomMargin = Math.round(20 * d);
        root.addView(hints, hlp);
        fillHintBar(this, hints, hintA(this, R.string.pocket_radial_editor_change),
                new Hint("L1 R1", 0xFFFFFFFF, getString(R.string.pocket_radial_editor_turn)),
                hintB(this, R.string.pocket_hint_back));
        show();
    }

    private int slots() {
        return global ? GlobalButtons.RADIAL_SLOTS : ButtonActions.RADIAL_SLOTS;
    }

    private String icon(int slot) {
        if (global) {
            return GlobalButtons.icon(GlobalButtons.radial(this, slot));
        }
        return ButtonActions.getRadial(this, slot).name();
    }

    private String label(int slot) {
        if (global) {
            GlobalButtons.Action a = GlobalButtons.radial(this, slot);
            return a == GlobalButtons.Action.NONE || a == GlobalButtons.Action.PASS
                    ? getString(R.string.pocket_radial_empty_slot) : getString(a.label);
        }
        PocketAction a = ButtonActions.getRadial(this, slot);
        return a == PocketAction.NONE ? getString(R.string.pocket_radial_empty_slot) : a.shortLabel(this);
    }

    /** The ring as it is now (on the slice that was just changed). */
    private void show() {
        int n = slots();
        String[] icons = new String[n];
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            icons[i] = icon(i);
            labels[i] = label(i);
        }
        ring.showItems(icons, labels, getString(R.string.pocket_radial_editor_center),
                getString(global ? R.string.pocket_radial_editor_everywhere : R.string.pocket_radial_editor_stream));
        if (lastSlot >= 0) {
            final int s = lastSlot;
            ring.post(() -> ring.onDirection(s * 8 / n));
        }
    }

    /** A: what the chosen slice does, picked from a list (with icons; the current one ticked). */
    private void edit() {
        final int slot = ring.getSelected();
        if (slot < 0) {
            PocketSounds.play(this, PocketSounds.ERROR);
            return;
        }
        lastSlot = slot;
        List<StreamMenuView.Item> items = new ArrayList<>();
        if (global) {
            GlobalButtons.Action now = GlobalButtons.radial(this, slot);
            for (final GlobalButtons.Action a : GlobalButtons.choices(true)) {
                items.add(StreamMenuView.Item.action((a == now ? "✓  " : "") + getString(a.label), () -> {
                    GlobalButtons.setRadial(this, slot, a);
                    picked();
                }).withIcon(GlobalButtons.icon(a)));
            }
        }
        else {
            PocketAction now = ButtonActions.getRadial(this, slot);
            for (final PocketAction a : PocketAction.forRadial()) {
                items.add(StreamMenuView.Item.action((a == now ? "✓  " : "") + getString(a.label), () -> {
                    ButtonActions.setRadial(this, slot, a);
                    picked();
                }).withIcon(a.name()));
            }
        }
        PocketSounds.play(this, PocketSounds.OPEN);
        showConsolePanel(label(slot), getString(R.string.pocket_radial_editor_pick), null, items);
    }

    private void picked() {
        hideConsolePanel();
        PocketSounds.play(this, PocketSounds.TOGGLE_ON);
        ring.postDelayed(this::show, 160);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isConsolePanelShowing()) {
            return super.dispatchKeyEvent(event);
        }
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean first = down && event.getRepeatCount() == 0;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) {
                    ring.onDirection(0);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    ring.onDirection(2);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) {
                    ring.onDirection(4);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) {
                    ring.onDirection(6);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                if (first) {
                    ring.rotate(-1);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (first) {
                    ring.rotate(1);
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (first) {
                    edit();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (first) {
                    PocketSounds.play(this, PocketSounds.BACK);
                    finish();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (!isConsolePanelShowing() && (ev.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && ev.getAction() == MotionEvent.ACTION_MOVE) {
            float lx = ev.getAxisValue(MotionEvent.AXIS_X);
            float ly = ev.getAxisValue(MotionEvent.AXIS_Y);
            float rx = ev.getAxisValue(MotionEvent.AXIS_Z);
            float ry = ev.getAxisValue(MotionEvent.AXIS_RZ);
            if (Math.hypot(rx, ry) > Math.hypot(lx, ly)) {
                ring.onStick(rx, ry);
            }
            else {
                ring.onStick(lx, ly);
            }
            return true;
        }
        return super.dispatchGenericMotionEvent(ev);
    }
}
