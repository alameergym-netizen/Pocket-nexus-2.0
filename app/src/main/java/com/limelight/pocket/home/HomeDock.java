package com.limelight.pocket.home;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.apps.AndroidLibrary;
import com.limelight.pocket.apps.NexusApps;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketIcons;
import com.limelight.pocket.ui.Spring;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Home's dock: a row of round buttons under the games, like a console's (Music, Files, the
 * browser, Search, your play, every app, the widgets, the settings), and any Android app put
 * there. DOWN from the games comes here; UP goes back; DOWN again brings up the widgets. The
 * chosen one grows, turns white and says its name above it. Y changes the dock (move, remove,
 * add Pocket Nexus's apps or any app).
 */
final class HomeDock {

    interface Host {
        /** UP from the dock: back to the games. */
        void dockUp();

        /** DOWN from the dock (or its Widgets button): the widget shelf. */
        void dockWidgets();
    }

    private static final String KEY = "pocket_home_dock";
    static final String WIDGETS = "widgets";
    private static final String NEXUS = "nexus:";
    private static final String APP = "app:";
    private static final String DEFAULT = "nexus:music,nexus:files,nexus:browser,nexus:search,nexus:stats,"
            + "nexus:apps,widgets,nexus:settings";

    private final HomeActivity activity;
    private final Host host;
    private final float d;
    private final FrameLayout root;
    private final LinearLayout row;
    private final TextView label;
    private int lastFocus;

    HomeDock(HomeActivity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.d = activity.getResources().getDisplayMetrics().density;
        root = new FrameLayout(activity);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(56), Gravity.BOTTOM | Gravity.START);
        root.addView(row, rlp);
        // The chosen button's name, in a small pill over it
        label = new TextView(activity);
        label.setTypeface(PocketFonts.semibold());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        label.setTextColor(0xFF0B0B0D);
        label.setSingleLine(true);
        label.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(11));
        pill.setColor(0xF2FFFFFF);
        label.setBackground(pill);
        label.setAlpha(0f);
        label.setElevation(dp(6));
        root.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
    }

    View view() {
        return root;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    // ---- What's on it -------------------------------------------------------------------------

    private List<String> entries() {
        String s = PocketPrefs.prefs(activity).getString(KEY, DEFAULT);
        List<String> out = new ArrayList<>();
        for (String e : s.split(",")) {
            if (e.isEmpty()) {
                continue;
            }
            if (e.startsWith(NEXUS) && NexusApps.byId(e.substring(NEXUS.length())) == null) {
                continue;
            }
            if (e.startsWith(APP) && !installed(e.substring(APP.length()))) {
                continue; // uninstalled since
            }
            out.add(e);
        }
        return out;
    }

    private void save(List<String> list) {
        PocketPrefs.prefs(activity).edit().putString(KEY, TextUtils.join(",", list)).apply();
    }

    private boolean installed(String pkg) {
        return activity.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    /** Builds the buttons again (after a change, or an app was installed or removed). */
    void refresh() {
        int keep = focusedIndex();
        row.removeAllViews();
        List<String> list = entries();
        for (int i = 0; i < list.size(); i++) {
            row.addView(button(list.get(i), i));
        }
        if (keep >= 0) {
            focus(Math.min(keep, row.getChildCount() - 1));
        }
    }

    boolean hasFocus() {
        return row.hasFocus();
    }

    int focusedIndex() {
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i).hasFocus()) {
                return i;
            }
        }
        return -1;
    }

    /** The button chosen last time (DOWN from the games). */
    void focusLast() {
        focus(Math.max(0, Math.min(lastFocus, row.getChildCount() - 1)));
    }

    void focus(int i) {
        if (i >= 0 && i < row.getChildCount()) {
            row.getChildAt(i).requestFocus();
        }
    }

    // ---- A button -------------------------------------------------------------------------------

    private String labelOf(String entry) {
        if (WIDGETS.equals(entry)) {
            return activity.getString(R.string.pocket_home_widgets);
        }
        if (entry.startsWith(NEXUS)) {
            NexusApps.App a = NexusApps.byId(entry.substring(NEXUS.length()));
            return a != null ? activity.getString(a.label) : "";
        }
        String pkg = entry.substring(APP.length());
        try {
            PackageManager pm = activity.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private View button(final String entry, final int index) {
        final FrameLayout b = new FrameLayout(activity);
        b.setFocusable(true);
        b.setClipChildren(false);
        int size = dp(44);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(dp(12));
        b.setLayoutParams(lp);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x33FFFFFF);
        bg.setStroke(Math.max(1, dp(1)), 0x1FFFFFFF);
        b.setBackground(bg);
        b.setElevation(dp(3));
        final ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        final boolean appIcon = entry.startsWith(APP);
        final String glyph = glyphOf(entry);
        if (appIcon) {
            icon.setImageDrawable(appIcon(entry.substring(APP.length()), size));
            icon.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            icon.setClipToOutline(true);
            b.addView(icon, new FrameLayout.LayoutParams(size, size));
        }
        else {
            icon.setImageDrawable(PocketIcons.drawable(glyph, dp(20), 0xF2FFFFFF));
            b.addView(icon, new FrameLayout.LayoutParams(size, size));
        }
        b.setOnFocusChangeListener((v, has) -> {
            b.animate().cancel();
            if (has) {
                lastFocus = index;
                PocketSounds.play(activity, PocketSounds.NAV);
                b.animate().scaleX(1.18f).scaleY(1.18f).translationY(-dp(3)).setDuration(260)
                        .setInterpolator(Spring.POP).start();
                bg.setColor(0xFFFFFFFF);
                bg.setStroke(0, 0);
                if (!appIcon) {
                    icon.setImageDrawable(PocketIcons.drawable(glyph, dp(20), 0xFF0B0B0D));
                }
                b.setElevation(dp(10));
                showLabel(b, labelOf(entry));
            }
            else {
                b.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(200).start();
                bg.setColor(0x33FFFFFF);
                bg.setStroke(Math.max(1, dp(1)), 0x1FFFFFFF);
                if (!appIcon) {
                    icon.setImageDrawable(PocketIcons.drawable(glyph, dp(20), 0xF2FFFFFF));
                }
                b.setElevation(dp(3));
                if (!row.hasFocus()) {
                    label.animate().alpha(0f).setDuration(140).start();
                }
            }
        });
        b.setOnClickListener(v -> open(entry, b));
        b.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_MENU;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    host.dockUp();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    host.dockWidgets();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return index == 0; // the dock's end
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return index == row.getChildCount() - 1;
                case KeyEvent.KEYCODE_BUTTON_Y:
                case KeyEvent.KEYCODE_MENU:
                    if (event.getRepeatCount() == 0) {
                        options(entry, index);
                    }
                    return true;
                default:
                    return false;
            }
        });
        return b;
    }

    private static String glyphOf(String entry) {
        if (WIDGETS.equals(entry)) {
            return "GAME_BAR";
        }
        if (entry.startsWith(NEXUS)) {
            NexusApps.App a = NexusApps.byId(entry.substring(NEXUS.length()));
            return a != null ? a.icon : "NONE";
        }
        return "NONE";
    }

    private Drawable appIcon(String pkg, int size) {
        try {
            Drawable dr = activity.getPackageManager().getApplicationIcon(pkg);
            Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            Path clip = new Path();
            clip.addOval(new RectF(0, 0, size, size), Path.Direction.CW);
            c.clipPath(clip);
            Paint p = new Paint();
            p.setColor(0xFF1B1B22);
            c.drawRect(0, 0, size, size, p);
            // A little inset: adaptive icons' edges are part of their shape
            int inset = Math.round(size * 0.06f);
            dr.setBounds(-inset, -inset, size + inset, size + inset);
            dr.draw(c);
            return new android.graphics.drawable.BitmapDrawable(activity.getResources(), bm);
        } catch (Exception e) {
            return PocketIcons.drawable("NONE", size, 0xFFFFFFFF);
        }
    }

    private void showLabel(View over, String text) {
        label.setText(text);
        label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        float cx = over.getLeft() + over.getWidth() / 2f + row.getLeft();
        label.setTranslationX(Math.max(0, cx - label.getMeasuredWidth() / 2f));
        label.animate().cancel();
        // Just over the button (the dock's frame is 100dp tall, its buttons in the bottom 56dp)
        float top = dp(39) - label.getMeasuredHeight();
        label.setTranslationY(top + dp(5));
        label.animate().alpha(1f).translationY(top).setDuration(220).setInterpolator(Spring.POP).start();
    }

    private void open(String entry, View b) {
        b.animate().cancel();
        b.setScaleX(1.05f);
        b.setScaleY(1.05f);
        b.animate().scaleX(1.18f).scaleY(1.18f).setDuration(220).setInterpolator(Spring.POP).start();
        if (WIDGETS.equals(entry)) {
            host.dockWidgets();
            return;
        }
        PocketSounds.play(activity, PocketSounds.OPEN);
        if (entry.startsWith(NEXUS)) {
            NexusApps.open(activity, entry.substring(NEXUS.length()));
            return;
        }
        String pkg = entry.substring(APP.length());
        HomeItem item = new HomeItem(HomeItem.ANDROID, labelOf(entry), pkg, "", "", 0);
        HomeLauncher.launch(activity, item);
    }

    // ---- Changing it ---------------------------------------------------------------------------

    private void options(final String entry, final int index) {
        PocketSounds.play(activity, PocketSounds.OPEN);
        List<StreamMenuView.Item> items = new ArrayList<>();
        final List<String> list = entries();
        if (index > 0) {
            items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_dock_move_left), () -> {
                move(list, index, -1);
            }).withIcon("SWAP"));
        }
        if (index < list.size() - 1) {
            items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_dock_move_right), () -> {
                move(list, index, 1);
            }).withIcon("SWAP"));
        }
        items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_dock_remove), () -> {
            list.remove(index);
            save(list);
            activity.hideConsolePanel();
            refresh();
            focus(Math.max(0, index - 1));
        }).withIcon("TRASH"));
        items.add(StreamMenuView.Item.submenu(activity.getString(R.string.pocket_dock_add_nexus), this::addNexusPage).withIcon("APPS"));
        items.add(StreamMenuView.Item.submenu(activity.getString(R.string.pocket_dock_add_app), this::addAppPage).withIcon("ANDROID_HOME"));
        items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_dock_reset), () -> {
            PocketPrefs.prefs(activity).edit().remove(KEY).apply();
            activity.hideConsolePanel();
            refresh();
            focus(0);
        }).withIcon("RESTART"));
        activity.showConsolePanel(labelOf(entry), activity.getString(R.string.pocket_dock_title), null, items);
    }

    private void move(List<String> list, int index, int dir) {
        String e = list.remove(index);
        list.add(index + dir, e);
        save(list);
        activity.hideConsolePanel();
        refresh();
        focus(index + dir);
    }

    private void addNexusPage() {
        List<StreamMenuView.Item> items = new ArrayList<>();
        final List<String> list = entries();
        for (final NexusApps.App a : NexusApps.ALL) {
            final String e = NEXUS + a.id;
            if (list.contains(e)) {
                continue;
            }
            items.add(StreamMenuView.Item.action(activity.getString(a.label), () -> add(e)).withIcon(a.icon));
        }
        if (!list.contains(WIDGETS)) {
            items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_home_widgets), () -> add(WIDGETS))
                    .withIcon("GAME_BAR"));
        }
        if (items.isEmpty()) {
            items.add(StreamMenuView.Item.note(activity.getString(R.string.pocket_dock_all_there)));
        }
        activity.pushConsolePage(activity.getString(R.string.pocket_dock_add_nexus), items);
    }

    private void addAppPage() {
        final List<String> list = entries();
        List<StreamMenuView.Item> items = new ArrayList<>();
        List<AndroidLibrary.InstalledApp> apps = AndroidLibrary.installed(activity);
        java.util.Collections.sort(apps, (x, y) -> x.label.compareToIgnoreCase(y.label));
        for (final AndroidLibrary.InstalledApp a : apps) {
            final String e = APP + a.packageName;
            if (list.contains(e)) {
                continue;
            }
            items.add(StreamMenuView.Item.action(a.label, () -> add(e)));
        }
        activity.pushConsolePage(activity.getString(R.string.pocket_dock_add_app), items);
    }

    private void add(String e) {
        List<String> list = entries();
        int at = Math.min(list.size(), Math.max(0, focusedIndexOr(lastFocus) + 1));
        list.add(at, e);
        save(list);
        activity.hideConsolePanel();
        PocketSounds.play(activity, PocketSounds.TOGGLE_ON);
        refresh();
        focus(at);
    }

    private int focusedIndexOr(int fallback) {
        int f = focusedIndex();
        return f >= 0 ? f : fallback;
    }

    /** Every entry the dock can have (for tests and the settings). */
    static List<String> defaults() {
        return new ArrayList<>(Arrays.asList(DEFAULT.split(",")));
    }
}
