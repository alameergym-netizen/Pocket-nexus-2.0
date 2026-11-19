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
            + "nexus:apps,nexus:surprise,widgets,nexus:settings";

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
        LinearLayout band = new LinearLayout(activity);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.CENTER_VERTICAL);
        band.setClipChildren(false);
        band.setClipToPadding(false);
        band.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(BAND)));
        root.addView(band, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(BAND), Gravity.BOTTOM | Gravity.START));
        // The chosen button's name: a small pill just above it (in the gap over the covers)
        label = new TextView(activity);
        label.setTypeface(PocketFonts.semibold());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTextColor(0xFFFFFFFF);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(11), 0, dp(11), 0);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(11));
        pill.setColor(0xE61A1A20);
        pill.setStroke(Math.max(1, dp(1)), 0x2EFFFFFF);
        label.setBackground(pill);
        label.setAlpha(0f);
        root.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(22),
                Gravity.TOP | Gravity.START));
    }

    /** The dock's height on Home: the row, and the name's pill over it. */
    static final int HEIGHT_DP = 74;
    private static final int BAND = 48;
    private static final int BOX = 44;
    private static final int ICON = 38;



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

    /** Its colours (Pocket Nexus's apps have their own; the widgets a cool grey). */
    private static int[] colorsOf(String entry) {
        if (entry.startsWith(NEXUS)) {
            NexusApps.App a = NexusApps.byId(entry.substring(NEXUS.length()));
            if (a != null) {
                return new int[]{a.from, a.to};
            }
        }
        return new int[]{0xFF7C8AA8, 0xFF2F3850};
    }

    private String aboutOf(String entry) {
        if (WIDGETS.equals(entry)) {
            return activity.getString(R.string.pocket_dock_widgets_about);
        }
        if (entry.startsWith(NEXUS)) {
            NexusApps.App a = NexusApps.byId(entry.substring(NEXUS.length()));
            return a != null ? activity.getString(a.blurb) : "";
        }
        return activity.getString(R.string.pocket_dock_app_about);
    }

    /**
     * A round app icon: its own colours, a soft light from the top, a white glyph (an Android
     * app: its own icon). Chosen: it grows a little and a white ring with a glow goes round it,
     * the same as everything else that's chosen.
     */
    private View button(final String entry, final int index) {
        final FrameLayout b = new FrameLayout(activity);
        b.setFocusable(true);
        b.setClipChildren(false);
        b.setClipToPadding(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            b.setDefaultFocusHighlightEnabled(false);
        }
        final int box = dp(BOX);
        final int size = dp(ICON);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(box, box);
        lp.setMarginEnd(dp(8));
        b.setLayoutParams(lp);
        // The ring round it when chosen
        final View ring = new View(activity);
        GradientDrawable rd = new GradientDrawable();
        rd.setShape(GradientDrawable.OVAL);
        rd.setStroke(Math.round(2.2f * d), 0xFFFFFFFF);
        ring.setBackground(rd);
        ring.setAlpha(0f);
        ring.setScaleX(0.86f);
        ring.setScaleY(0.86f);
        b.addView(ring, new FrameLayout.LayoutParams(box, box, Gravity.CENTER));
        final ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        icon.setClipToOutline(true);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            // Its shadow is a soft white glow when it rises
            icon.setOutlineAmbientShadowColor(0xFFFFFFFF);
            icon.setOutlineSpotShadowColor(0xFFFFFFFF);
        }
        // One look for all: frosted glass, a hairline edge, a white glyph (no colours)
        final GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x3DFFFFFF, 0x1FFFFFFF});
        glass.setShape(GradientDrawable.OVAL);
        glass.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        icon.setBackground(glass);
        final boolean appIcon = entry.startsWith(APP);
        if (appIcon) {
            icon.setImageDrawable(appGlyph(entry.substring(APP.length()), dp(22)));
        }
        else {
            icon.setImageDrawable(PocketIcons.drawable(glyphOf(entry), dp(18), 0xF2FFFFFF));
        }
        b.addView(icon, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        b.setOnFocusChangeListener((v, has) -> {
            icon.animate().cancel();
            ring.animate().cancel();
            if (has) {
                lastFocus = index;
                PocketSounds.play(activity, PocketSounds.NAV);
                glass.setColors(new int[]{0x5CFFFFFF, 0x33FFFFFF});
                icon.animate().scaleX(1.06f).scaleY(1.06f).translationZ(dp(8)).setDuration(220)
                        .setInterpolator(Spring.POP).start();
                ring.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(Spring.POP).start();
                showLabel(labelOf(entry), b);
            }
            else {
                glass.setColors(new int[]{0x3DFFFFFF, 0x1FFFFFFF});
                icon.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(160).start();
                ring.animate().alpha(0f).scaleX(0.86f).scaleY(0.86f).setDuration(150).start();
                if (!row.hasFocus()) {
                    label.animate().alpha(0f).setDuration(120).start();
                }
            }
        });
        b.setOnClickListener(v -> open(entry, icon));
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

    /** A soft light over the top half, like a lit glass button. */
    private static android.graphics.drawable.Drawable gloss() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x47FFFFFF, 0x00FFFFFF, 0x00000000});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    private static String glyphOf(String entry) {
        if (WIDGETS.equals(entry)) {
            return "SPARKLE";
        }
        if (entry.startsWith(NEXUS)) {
            NexusApps.App a = NexusApps.byId(entry.substring(NEXUS.length()));
            return a != null ? a.icon : "NONE";
        }
        return "NONE";
    }

    /**
     * An Android app on the dock in the dock's look: its themed (one-colour) icon in white when
     * it has one, otherwise its icon in greys.
     */
    private Drawable appGlyph(String pkg, int size) {
        try {
            Drawable dr = activity.getPackageManager().getApplicationIcon(pkg);
            if (android.os.Build.VERSION.SDK_INT >= 33 && dr instanceof android.graphics.drawable.AdaptiveIconDrawable) {
                Drawable mono = ((android.graphics.drawable.AdaptiveIconDrawable) dr).getMonochrome();
                if (mono != null) {
                    Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bm);
                    // The layer has the adaptive icon's safe-zone margins: draw it larger
                    int extra = Math.round(size * 0.25f);
                    Drawable m = mono.mutate();
                    m.setTint(0xF2FFFFFF);
                    m.setBounds(-extra, -extra, size + extra, size + extra);
                    m.draw(c);
                    return new android.graphics.drawable.BitmapDrawable(activity.getResources(), bm);
                }
            }
            Drawable grey = appIcon(pkg, size);
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(0f);
            grey.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            return grey;
        } catch (Exception e) {
            return PocketIcons.drawable("APPS", dp(18), 0xF2FFFFFF);
        }
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

    /** The name in its pill, centred over the chosen button. */
    private void showLabel(String name, final View over) {
        label.setText(name);
        label.animate().cancel();
        label.post(() -> {
            float cx = row.getLeft() + over.getLeft() + over.getWidth() / 2f;
            float x = Math.max(0, cx - label.getWidth() / 2f);
            boolean first = label.getAlpha() < 0.05f;
            if (first || !PocketPrefs.animationsEnabled(activity)) {
                label.setTranslationX(x);
                label.setTranslationY(dp(4));
                label.animate().alpha(1f).translationY(0).setDuration(180).setInterpolator(Spring.POP).start();
            }
            else {
                label.animate().alpha(1f).translationX(x).translationY(0).setDuration(200)
                        .setInterpolator(Spring.POP).start();
            }
        });
    }

    private void open(String entry, View b) {
        b.animate().cancel();
        b.setScaleX(1.05f);
        b.setScaleY(1.05f);
        b.animate().scaleX(1.08f).scaleY(1.08f).setDuration(220).setInterpolator(Spring.POP).start();
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
