package com.limelight.pocket.home;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.RecentGames;
import com.limelight.pocket.device.DevicePanel;
import com.limelight.pocket.device.PocketNotifications;
import com.limelight.pocket.ui.PocketFonts;
import com.limelight.pocket.ui.PocketMotion;
import com.limelight.pocket.ui.SectionBar;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Home's widgets, on a frosted shelf that rises from the bottom (DOWN from the games): a clock,
 * what's playing (with play / pause), the notifications, the device, the PCs, and any Android
 * widget. A opens or acts, Y moves or removes one, B / UP puts the shelf away.
 */
public final class WidgetShelf {

    private static final String KEY = "pocket_home_widgets";
    private static final String DEFAULT = "clock,nowplaying,weather,week,notifications,device";
    private static final String CLOCK = "clock";
    private static final String NOW_PLAYING = "nowplaying";
    private static final String NOTIFICATIONS = "notifications";
    private static final String DEVICE = "device";
    private static final String PCS = "pcs";
    private static final String WEATHER = "weather";
    private static final String WEEK = "week";
    private static final String APP_WIDGET = "aw:";
    private static final String[] BUILT_IN = {CLOCK, NOW_PLAYING, WEATHER, WEEK, NOTIFICATIONS, DEVICE, PCS};
    private static final int HOST_ID = 0x504E;
    static final int REQUEST_BIND = 7411;
    static final int REQUEST_CONFIGURE = 7412;

    private final ConsoleActivity activity;
    private final Runnable onClosed;
    private final float d;
    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout panel;
    private LinearLayout row;
    private boolean open;
    private AppWidgetHost host;
    private int pendingId = -1;
    private AppWidgetProviderInfo pendingInfo;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (open) {
                refresh();
                main.postDelayed(this, 2000);
            }
        }
    };

    WidgetShelf(ConsoleActivity activity, Runnable onClosed) {
        this.activity = activity;
        this.onClosed = onClosed;
        this.d = activity.getResources().getDisplayMetrics().density;
        try {
            host = new AppWidgetHost(activity.getApplicationContext(), HOST_ID);
        } catch (RuntimeException e) {
            host = null;
        }
    }

    void onStart() {
        if (host != null) {
            try {
                host.startListening();
            } catch (RuntimeException ignored) {
            }
        }
    }

    void onStop() {
        if (host != null) {
            try {
                host.stopListening();
            } catch (RuntimeException ignored) {
            }
        }
    }

    void onDestroy() {
        main.removeCallbacksAndMessages(null);
    }

    boolean isOpen() {
        return open;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    // ---- What's on it -------------------------------------------------------------------------

    private List<String> widgets() {
        android.content.SharedPreferences p = PocketPrefs.prefs(activity);
        if (!p.getBoolean("pocket_widgets_v2", false)) {
            // Once: the new weather and week widgets join a shelf set up before they existed
            String had = p.getString(KEY, null);
            if (had != null) {
                StringBuilder b = new StringBuilder(had);
                for (String w : new String[]{WEATHER, WEEK}) {
                    if (!("," + had + ",").contains("," + w + ",")) {
                        b.append(b.length() > 0 ? "," : "").append(w);
                    }
                }
                p.edit().putString(KEY, b.toString()).apply();
            }
            p.edit().putBoolean("pocket_widgets_v2", true).apply();
        }
        String s = p.getString(KEY, DEFAULT);
        List<String> out = new ArrayList<>();
        for (String w : s.split(",")) {
            if (!w.trim().isEmpty()) {
                out.add(w.trim());
            }
        }
        return out;
    }

    private void save(List<String> list) {
        PocketPrefs.prefs(activity).edit().putString(KEY, android.text.TextUtils.join(",", list)).apply();
    }

    // ---- Showing it ---------------------------------------------------------------------------

    private void build() {
        if (root != null) {
            return;
        }
        ViewGroup content = activity.findViewById(R.id.pocketRoot);
        if (content == null) {
            content = activity.findViewById(android.R.id.content);
        }
        root = new FrameLayout(activity);
        root.setVisibility(View.GONE);
        View scrim = new View(activity);
        scrim.setBackgroundColor(0x8C000000);
        scrim.setOnClickListener(v -> close());
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setTag("glass_panel");
        panel.setPadding(dp(18), dp(14), dp(18), dp(16));
        GradientDrawable plain = new GradientDrawable();
        plain.setCornerRadius(dp(22));
        plain.setColor(0xE614141A);
        panel.setBackground(plain);
        activity.makeGlass(panel);

        TextView title = new TextView(activity);
        title.setText(R.string.pocket_home_widgets);
        title.setTypeface(PocketFonts.semibold());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        title.setTextColor(0xFFFFFFFF);
        TextView hint = new TextView(activity);
        hint.setText(R.string.pocket_home_widgets_hint);
        hint.setTypeface(PocketFonts.medium());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        hint.setTextColor(0x99FFFFFF);
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(title);
        View space = new View(activity);
        head.addView(space, new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(hint);
        panel.addView(head);

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(10), 0, dp(4));
        row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(2), dp(4), dp(2), dp(4));
        scroll.addView(row);
        panel.addView(scroll);

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        plp.setMargins(dp(16), 0, dp(16), dp(14));
        root.addView(panel, plp);
        content.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setElevation(dp(30));
    }

    void open() {
        build();
        if (open) {
            return;
        }
        open = true;
        fill();
        root.setVisibility(View.VISIBLE);
        PocketSounds.play(activity, PocketSounds.OPEN);
        if (PocketPrefs.animationsEnabled(activity)) {
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(200).start();
            panel.setTranslationY(dp(160));
            panel.animate().translationY(0f).setDuration(380).setInterpolator(PocketMotion.DECELERATE).start();
        }
        main.post(() -> {
            if (row.getChildCount() > 0) {
                row.getChildAt(0).requestFocus();
            }
        });
        main.removeCallbacks(tick);
        main.postDelayed(tick, 2000);
    }

    void close() {
        if (!open) {
            return;
        }
        open = false;
        main.removeCallbacks(tick);
        PocketSounds.play(activity, PocketSounds.BACK);
        if (PocketPrefs.animationsEnabled(activity)) {
            root.animate().alpha(0f).setDuration(180).withEndAction(() -> root.setVisibility(View.GONE)).start();
            panel.animate().translationY(dp(140)).setDuration(220).start();
        }
        else {
            root.setVisibility(View.GONE);
        }
        onClosed.run();
    }

    /** Fresh contents (time, music, notifications ...). */
    void refresh() {
        if (!open || row == null) {
            return;
        }
        for (int i = 0; i < row.getChildCount(); i++) {
            View card = row.getChildAt(i);
            Object kind = card.getTag();
            if (kind instanceof String && !((String) kind).startsWith(APP_WIDGET) && !"+".equals(kind)) {
                update(card, (String) kind);
            }
        }
    }

    private void fill() {
        row.removeAllViews();
        for (String w : widgets()) {
            View card = w.startsWith(APP_WIDGET) ? appWidgetCard(w) : builtInCard(w);
            if (card != null) {
                row.addView(card);
            }
        }
        row.addView(addCard());
    }

    // ---- Cards ------------------------------------------------------------------------------

    private FrameLayout frame(String kind, int widthDp) {
        final FrameLayout card = new FrameLayout(activity);
        card.setTag(kind);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        final GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x24FFFFFF, 0x0FFFFFFF});
        bg.setCornerRadius(dp(22));
        bg.setStroke(Math.max(1, dp(1)), 0x1AFFFFFF);
        card.setBackground(bg);
        // Chosen like everything else: a white ring and a glow
        com.limelight.pocket.ui.FocusGlow.apply(card, dp(22), false, 1.045f, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(widthDp), dp(WidgetCards.HEIGHT_DP));
        lp.setMarginEnd(dp(14));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView text(float sp, int color, android.graphics.Typeface face) {
        TextView t = new TextView(activity);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private View builtInCard(final String kind) {
        if (!Arrays.asList(BUILT_IN).contains(kind)) {
            return null;
        }
        WidgetCards.Card card;
        switch (kind) {
            case CLOCK:
                card = new WidgetCards.ClockCard(activity, kind);
                break;
            case NOW_PLAYING:
                card = new WidgetCards.MusicCard(activity, kind, this::controller);
                break;
            case NOTIFICATIONS:
                card = new WidgetCards.NotesCard(activity, kind);
                break;
            case DEVICE:
                card = new WidgetCards.DeviceCard(activity, kind);
                break;
            case WEATHER:
                card = new WidgetCards.WeatherCard(activity, kind);
                break;
            case WEEK:
                card = new WidgetCards.WeekCard(activity, kind);
                break;
            default:
                card = new WidgetCards.PcsCard(activity, kind);
                break;
        }
        card.setOnClickListener(v -> {
            com.limelight.pocket.ui.FocusGlow.press(v);
            act(kind);
        });
        return card;
    }

    private void update(View card, String kind) {
        if (card instanceof WidgetCards.Card) {
            ((WidgetCards.Card) card).update();
        }
    }

    private String appName(String pkg) {
        try {
            return activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private MediaController controller() {
        if (!PocketNotifications.allowed(activity)) {
            return null;
        }
        try {
            MediaSessionManager msm = (MediaSessionManager) activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> list = msm.getActiveSessions(new ComponentName(activity, PocketNotifications.class));
            if (list == null || list.isEmpty()) {
                return null;
            }
            for (MediaController c : list) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    return c;
                }
            }
            return list.get(0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void act(String kind) {
        PocketSounds.play(activity, PocketSounds.SELECT);
        switch (kind) {
            case NOW_PLAYING: {
                if (!PocketNotifications.allowed(activity)) {
                    PocketNotifications.askForAccess(activity);
                    return;
                }
                MediaController mc = controller();
                if (mc == null || mc.getMetadata() == null) {
                    close();
                    com.limelight.pocket.music.MusicActivity.open(activity);
                    return;
                }
                if (mc != null) {
                    PlaybackState st = mc.getPlaybackState();
                    if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                        mc.getTransportControls().pause();
                    }
                    else {
                        mc.getTransportControls().play();
                    }
                    main.postDelayed(this::refresh, 300);
                }
                return;
            }
            case NOTIFICATIONS:
                if (!PocketNotifications.allowed(activity)) {
                    PocketNotifications.askForAccess(activity);
                    return;
                }
                DevicePanel.show(activity);
                return;
            case DEVICE:
            case CLOCK:
                DevicePanel.show(activity);
                return;
            case PCS:
                close();
                SectionBar.go(activity, SectionBar.HOME, SectionBar.STREAMING);
                return;
            case WEATHER:
                if (Weather.cached(activity) == null) {
                    askCity();
                }
                else {
                    Weather.refresh(activity, true, now -> refresh());
                    Toast.makeText(activity, R.string.pocket_weather_updating, Toast.LENGTH_SHORT).show();
                }
                return;
            case WEEK:
                close();
                activity.startActivity(new Intent(activity, StatsActivity.class));
                return;
            default:
                break;
        }
    }

    private View addCard() {
        FrameLayout card = frame("+", 150);
        GradientDrawable dash = new GradientDrawable();
        dash.setCornerRadius(dp(22));
        dash.setColor(0x0DFFFFFF);
        dash.setStroke(Math.max(1, dp(1.5f)), 0x4DFFFFFF, dp(7), dp(5));
        card.setBackground(dash);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ImageView plus = new ImageView(activity);
        plus.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable pc = new GradientDrawable();
        pc.setShape(GradientDrawable.OVAL);
        pc.setColor(0x26FFFFFF);
        plus.setBackground(pc);
        android.graphics.drawable.Drawable pd = activity.getDrawable(R.drawable.ic_pocket_plus);
        if (pd != null) {
            pd = pd.mutate();
            pd.setTint(0xFFFFFFFF);
            plus.setImageDrawable(pd);
        }
        box.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = text(12.5f, 0xD9FFFFFF, PocketFonts.semibold());
        t.setText(activity.getString(R.string.pocket_home_add_widget));
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(10);
        box.addView(t, tl);
        card.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.setOnClickListener(v -> showAddPanel());
        return card;
    }

    // ---- Android widgets ----------------------------------------------------------------------

    private View appWidgetCard(String key) {
        if (host == null) {
            return null;
        }
        int id;
        try {
            id = Integer.parseInt(key.substring(APP_WIDGET.length()));
        } catch (NumberFormatException e) {
            return null;
        }
        AppWidgetManager awm = AppWidgetManager.getInstance(activity);
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info == null) {
            return null;
        }
        FrameLayout card = frame(key, 300);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        try {
            AppWidgetHostView view = host.createView(activity.getApplicationContext(), id, info);
            view.setAppWidget(id, info);
            card.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            int w = 300 - 12;
            int h = WidgetCards.HEIGHT_DP - 12;
            view.updateAppWidgetSize(null, w, h, w, h);
        } catch (RuntimeException e) {
            return null;
        }
        return card;
    }

    void showAddPanel() {
        List<StreamMenuView.Item> items = new ArrayList<>();
        List<String> have = widgets();
        int[] names = {R.string.pocket_widget_clock, R.string.pocket_widget_now_playing, R.string.pocket_widget_weather,
                R.string.pocket_widget_week, R.string.pocket_device_notifications, R.string.pocket_device_device,
                R.string.pocket_widget_pcs};
        for (int i = 0; i < BUILT_IN.length; i++) {
            final String kind = BUILT_IN[i];
            if (have.contains(kind)) {
                continue;
            }
            items.add(StreamMenuView.Item.action(activity.getString(names[i]), () -> {
                List<String> now = widgets();
                now.add(kind);
                save(now);
                activity.hideConsolePanel();
                fill();
                focusLast();
            }));
        }
        if (host != null) {
            items.add(StreamMenuView.Item.submenu(activity.getString(R.string.pocket_widget_android), this::pushProviders));
        }
        PocketSounds.play(activity, PocketSounds.OPEN);
        activity.showConsolePanel(activity.getString(R.string.pocket_home_add_widget), null, null, items);
    }

    private void focusLast() {
        main.post(() -> {
            int n = row.getChildCount();
            if (n >= 2) {
                row.getChildAt(n - 2).requestFocus();
            }
        });
    }

    private void pushProviders() {
        List<StreamMenuView.Item> page = new ArrayList<>();
        AppWidgetManager awm = AppWidgetManager.getInstance(activity);
        List<AppWidgetProviderInfo> all;
        try {
            all = awm.getInstalledProviders();
        } catch (RuntimeException e) {
            all = new ArrayList<>();
        }
        for (final AppWidgetProviderInfo info : all) {
            String label;
            try {
                label = info.loadLabel(activity.getPackageManager());
            } catch (RuntimeException e) {
                label = info.provider.getShortClassName();
            }
            page.add(StreamMenuView.Item.action(label + "  ·  " + appName(info.provider.getPackageName()),
                    () -> bind(info)));
        }
        if (page.isEmpty()) {
            page.add(StreamMenuView.Item.header(activity.getString(R.string.pocket_widget_none)));
        }
        activity.pushConsolePage(activity.getString(R.string.pocket_widget_android), page);
    }

    private void bind(AppWidgetProviderInfo info) {
        activity.hideConsolePanel();
        int id = host.allocateAppWidgetId();
        boolean ok;
        try {
            ok = AppWidgetManager.getInstance(activity).bindAppWidgetIdIfAllowed(id, info.provider);
        } catch (RuntimeException e) {
            ok = false;
        }
        if (ok) {
            configure(id, info);
            return;
        }
        pendingId = id;
        pendingInfo = info;
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
        try {
            activity.startActivityForResult(i, REQUEST_BIND);
        } catch (RuntimeException e) {
            host.deleteAppWidgetId(id);
            Toast.makeText(activity, R.string.pocket_widget_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void configure(int id, AppWidgetProviderInfo info) {
        if (info.configure != null) {
            pendingId = id;
            pendingInfo = info;
            try {
                host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null);
                return;
            } catch (RuntimeException e) {
                // Some can't be configured from another app: add it as it is
            }
        }
        added(id);
    }

    private void added(int id) {
        List<String> now = widgets();
        now.add(APP_WIDGET + id);
        save(now);
        pendingId = -1;
        pendingInfo = null;
        if (open) {
            fill();
            focusLast();
        }
    }

    /** True when it was one of the widget's requests. */
    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_BIND && requestCode != REQUEST_CONFIGURE) {
            return false;
        }
        int id = pendingId;
        AppWidgetProviderInfo info = pendingInfo;
        if (id < 0 || host == null) {
            return true;
        }
        if (resultCode != android.app.Activity.RESULT_OK) {
            host.deleteAppWidgetId(id);
            pendingId = -1;
            pendingInfo = null;
            return true;
        }
        if (requestCode == REQUEST_BIND && info != null) {
            configure(id, info);
        }
        else {
            added(id);
        }
        return true;
    }

    // ---- Keys -------------------------------------------------------------------------------------

    private View focused() {
        View f = activity.getCurrentFocus();
        while (f != null && f.getParent() != row) {
            f = f.getParent() instanceof View ? (View) f.getParent() : null;
        }
        return f;
    }

    /** The shelf's keys (true: used). */
    boolean handleKey(KeyEvent event) {
        int k = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        switch (k) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_DPAD_UP:
                if (up) {
                    close();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: {
                View f = focused();
                if (up && f != null) {
                    Object tag = f.getTag();
                    if (tag instanceof String && ((String) tag).startsWith(APP_WIDGET)) {
                        // An Android widget: A taps its middle
                        if (f instanceof ViewGroup && ((ViewGroup) f).getChildCount() > 0) {
                            ((ViewGroup) f).getChildAt(0).performClick();
                        }
                    }
                    else {
                        f.performClick();
                    }
                }
                return true;
            }
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_X: {
                // On the music: the song before / after
                View f = focused();
                if (f == null || !NOW_PLAYING.equals(f.getTag())) {
                    return k != KeyEvent.KEYCODE_BUTTON_X;
                }
                if (up) {
                    MediaController mc = controller();
                    if (mc != null) {
                        PocketSounds.play(activity, PocketSounds.NAV);
                        if (k == KeyEvent.KEYCODE_BUTTON_L2) {
                            mc.getTransportControls().skipToPrevious();
                        }
                        else {
                            mc.getTransportControls().skipToNext();
                        }
                        main.postDelayed(this::refresh, 450);
                    }
                }
                return true;
            }
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (up) {
                    View f = focused();
                    if (f != null && f.getTag() instanceof String && !"+".equals(f.getTag())) {
                        options((String) f.getTag());
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private void options(final String kind) {
        List<StreamMenuView.Item> items = new ArrayList<>();
        if (NOW_PLAYING.equals(kind)) {
            final MediaController mc = controller();
            if (mc != null) {
                items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_widget_prev), () -> {
                    mc.getTransportControls().skipToPrevious();
                    main.postDelayed(this::refresh, 400);
                }));
                items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_widget_next), () -> {
                    mc.getTransportControls().skipToNext();
                    main.postDelayed(this::refresh, 400);
                }));
            }
        }
        if (WEATHER.equals(kind)) {
            items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_weather_change_city), () -> {
                activity.hideConsolePanel();
                askCity();
            }).withIcon("GLOBE"));
            final boolean f = Weather.fahrenheit(activity);
            items.add(StreamMenuView.Item.action(activity.getString(f ? R.string.pocket_weather_use_c : R.string.pocket_weather_use_f), () -> {
                Weather.setFahrenheit(activity, !f);
                activity.hideConsolePanel();
                Weather.refresh(activity, true, now -> refresh());
            }).withIcon("SUN"));
        }
        items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_widget_move_left), () -> move(kind, -1)));
        items.add(StreamMenuView.Item.action(activity.getString(R.string.pocket_widget_move_right), () -> move(kind, 1)));
        items.add(StreamMenuView.Item.confirm(activity.getString(R.string.pocket_widget_remove), () -> {
            List<String> now = widgets();
            now.remove(kind);
            save(now);
            if (kind.startsWith(APP_WIDGET) && host != null) {
                try {
                    host.deleteAppWidgetId(Integer.parseInt(kind.substring(APP_WIDGET.length())));
                } catch (RuntimeException ignored) {
                }
            }
            activity.hideConsolePanel();
            fill();
            main.post(() -> {
                if (row.getChildCount() > 0) {
                    row.getChildAt(0).requestFocus();
                }
            });
        }));
        PocketSounds.play(activity, PocketSounds.OPEN);
        activity.showConsolePanel(activity.getString(R.string.pocket_home_widgets), null, null, items);
    }

    /** The weather's city, typed with the controller's keyboard. */
    private void askCity() {
        com.limelight.pocket.ui.TextPrompt.show(activity, activity.getString(R.string.pocket_weather_city_title),
                activity.getString(R.string.pocket_weather_city_help), Weather.city(activity),
                activity.getString(R.string.pocket_weather_city_hint), text -> {
                    if (text.isEmpty()) {
                        return;
                    }
                    Weather.setCity(activity, text, now -> {
                        if (now == null) {
                            PocketSounds.play(activity, PocketSounds.ERROR);
                            Toast.makeText(activity, R.string.pocket_weather_not_found, Toast.LENGTH_SHORT).show();
                        }
                        else {
                            PocketSounds.play(activity, PocketSounds.TOGGLE_ON);
                        }
                        refresh();
                    });
                });
    }

    private void move(String kind, int delta) {
        List<String> now = widgets();
        int i = now.indexOf(kind);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= now.size()) {
            return;
        }
        now.remove(i);
        now.add(j, kind);
        save(now);
        activity.hideConsolePanel();
        fill();
        final int at = j;
        main.post(() -> {
            if (at < row.getChildCount()) {
                row.getChildAt(at).requestFocus();
            }
        });
    }

    /** The device widget's lines. */
    static final class DevicePanelInfo {
        static String battery(Context c) {
            Intent b = c.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) {
                return "";
            }
            int level = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
            int status = b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            int pct = Math.round(level * 100f / Math.max(1, scale));
            return c.getString(charging ? R.string.pocket_device_battery_charging : R.string.pocket_device_battery, pct);
        }

        static String details(Context c) {
            StringBuilder s = new StringBuilder();
            try {
                android.os.StatFs fs = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
                s.append(c.getString(R.string.pocket_device_free, android.text.format.Formatter.formatShortFileSize(c, fs.getAvailableBytes())));
            } catch (RuntimeException ignored) {
            }
            s.append('\n').append(ConsoleActivity.isWifiConnected(c) ? "Wi-Fi" : c.getString(R.string.pocket_link_offline));
            return s.toString();
        }
    }
}
