package com.limelight.pocket.files;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;
import com.limelight.pocket.retro.RetroLibrary;
import com.limelight.pocket.ui.ConsoleScreen;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.StreamMenuView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Files, with the controller: the handheld's storage, its SD card, Downloads, screenshots and
 * the ROM folder on the left; the folder's files on the right. A opens (a folder, a picture, an
 * APK to install), B goes up, X marks files, Y has what can be done with them (copy, move,
 * rename, delete, share...), Start the folder's own (paste, a new folder, use it as the ROM
 * folder, sorting).
 */
public class FilesActivity extends Activity {

    public static void open(Context c) {
        try {
            c.startActivity(new Intent(c, FilesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
        }
    }

    private static final String KEY_SORT = "pocket_files_sort";
    private static final String KEY_HIDDEN = "pocket_files_hidden";
    private static final int SORT_NAME = 0;
    private static final int SORT_DATE = 1;
    private static final int SORT_SIZE = 2;

    /** Copied or cut files (kept while the app runs, so they can be pasted anywhere). */
    private static final List<File> CLIP = new ArrayList<>();
    private static boolean clipCut;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Preview preview;
    /** Where the last folder change went (1: into a folder, -1: up, 0: another place). */
    private int moved;
    private final Runnable showPreview = this::updatePreview;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Entry> entries = new ArrayList<>();
    private final Set<File> marked = new HashSet<>();
    private final List<Place> places = new ArrayList<>();
    private LinearLayout placeList;
    private RecyclerView list;
    private Rows rows;
    private TextView titleView;
    private TextView pathView;
    private TextView freeView;
    private TextView emptyView;
    private LinearLayout hints;
    private StreamMenuView menu;
    private File dir;
    private Place place;
    private int accent;
    private String focusName;

    private static final class Place {
        final String name;
        final File root;
        final String icon;

        Place(String name, File root, String icon) {
            this.name = name;
            this.root = root;
            this.icon = icon;
        }
    }

    private static final class Entry {
        final File file;
        final boolean folder;
        final long size;
        final long modified;
        final int count;

        Entry(File f, boolean folder, long size, long modified, int count) {
            this.file = f;
            this.folder = folder;
            this.size = size;
            this.modified = modified;
            this.count = count;
        }
    }

    // ---- Screen -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConsoleActivity.applyFullscreenWindow(this);
        accent = PocketTheme.accent(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(ConsoleScreen.backdrop(this));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(26), dp(18), dp(26), dp(12));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        titleView = ConsoleScreen.title(this, getString(R.string.pocket_files_title));
        titleView.setTypeface(com.limelight.pocket.ui.PocketFonts.display());
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26);
        head.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        freeView = ConsoleScreen.text(this, 11.5f, 0x99FFFFFF, true);
        head.addView(freeView);
        column.addView(head);
        pathView = ConsoleScreen.text(this, 11.5f, 0x8CFFFFFF, false);
        pathView.setSingleLine(true);
        pathView.setEllipsize(TextUtils.TruncateAt.START);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(4);
        plp.bottomMargin = dp(12);
        column.addView(pathView, plp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView side = new ScrollView(this);
        side.setVerticalScrollBarEnabled(false);
        placeList = new LinearLayout(this);
        placeList.setOrientation(LinearLayout.VERTICAL);
        side.addView(placeList);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(196), ViewGroup.LayoutParams.MATCH_PARENT);
        slp.setMarginEnd(dp(14));
        body.addView(side, slp);

        FrameLayout listBox = new FrameLayout(this);
        body.addView(listBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        // What's chosen, big: its picture (a photo, a video's frame, an app's icon, a song's
        // cover) and its details
        preview = new Preview(this);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(dp(290), ViewGroup.LayoutParams.MATCH_PARENT);
        vlp.setMarginStart(dp(16));
        body.addView(preview, vlp);
        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setPadding(0, dp(2), 0, dp(40));
        rows = new Rows();
        list.setAdapter(rows);
        listBox.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emptyView = ConsoleScreen.text(this, 13, 0x8CFFFFFF, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        listBox.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hints = ConsoleScreen.hintBar(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        hlp.topMargin = dp(6);
        column.addView(hints, hlp);
        ConsoleActivity.fillHintBar(this, hints,
                ConsoleActivity.hintA(this, R.string.pocket_hint_open),
                ConsoleActivity.hintX(this, R.string.pocket_files_mark),
                ConsoleActivity.hintY(this, R.string.pocket_hint_options),
                ConsoleActivity.hintStart(this, R.string.pocket_files_folder),
                ConsoleActivity.hintB(this, R.string.pocket_hint_back));

        menu = new StreamMenuView(this);
        menu.setConfirmLabel(getString(R.string.pocket_sm_confirm));
        menu.setPlacement(true, 336);
        menu.setBaseColor(0xF514141A);
        root.addView(menu, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        buildPlaces();
        if (!RetroLibrary.hasAccess(this)) {
            showAccessNeeded();
        }
        else if (!places.isEmpty()) {
            openPlace(places.get(0), false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConsoleActivity.applyFullscreenWindow(this);
        if (dir == null && RetroLibrary.hasAccess(this) && !places.isEmpty()) {
            openPlace(places.get(0), false);
        }
        else if (dir != null) {
            load(dir, null);
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(float v) {
        return ConsoleScreen.dp(this, v);
    }

    private void showAccessNeeded() {
        emptyView.setText(R.string.pocket_files_need_access);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setFocusable(true);
        emptyView.setOnClickListener(v -> RetroLibrary.requestAccess(this));
        emptyView.requestFocus();
    }

    // ---- Places -------------------------------------------------------------------------

    private void buildPlaces() {
        places.clear();
        File internal = Environment.getExternalStorageDirectory();
        places.add(new Place(getString(R.string.pocket_files_internal), internal, "phone"));
        // SD cards and drives: the roots of the app folders Android gives on each
        File[] dirs = getExternalFilesDirs(null);
        for (int i = 1; dirs != null && i < dirs.length; i++) {
            if (dirs[i] == null) {
                continue;
            }
            String p = dirs[i].getAbsolutePath();
            int cut = p.indexOf("/Android/data/");
            if (cut > 0) {
                File card = new File(p.substring(0, cut));
                places.add(new Place(getString(R.string.pocket_files_sd) + (i > 1 ? " " + i : ""), card, "sd"));
            }
        }
        places.add(new Place(getString(R.string.pocket_files_downloads),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "down"));
        File shots = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots");
        if (!shots.isDirectory()) {
            shots = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots");
        }
        places.add(new Place(getString(R.string.pocket_files_screenshots), shots.isDirectory() ? shots
                : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "pic"));
        File roms = RetroLibrary.romRoot(this);
        if (roms != null && roms.isDirectory()) {
            places.add(new Place(getString(R.string.pocket_files_roms), roms, "roms"));
        }
        File music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (music.isDirectory()) {
            places.add(new Place(getString(R.string.pocket_files_music), music, "music"));
        }
        placeList.removeAllViews();
        for (final Place p : places) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(9), dp(10), dp(10));
            row.setFocusable(true);
            row.setClickable(true);
            final TextView name = ConsoleScreen.text(this, 13.5f, 0xF2FFFFFF, true);
            name.setText(p.name);
            android.graphics.drawable.Drawable glyph = com.limelight.pocket.ui.PocketIcons.drawable(placeIcon(p.icon), dp(16), 0xFFFFFFFF);
            glyph.setBounds(0, 0, dp(16), dp(16));
            name.setCompoundDrawablesRelative(glyph, null, null, null);
            name.setCompoundDrawablePadding(dp(8));
            row.addView(name);
            final TextView sub = ConsoleScreen.text(this, 10.5f, 0x8CFFFFFF, false);
            sub.setText(freeText(p.root));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.topMargin = dp(3);
            row.addView(sub, sl);
            // How full it is
            float used = usedFraction(p.root);
            if (used >= 0) {
                FrameLayout track = new FrameLayout(this);
                android.graphics.drawable.GradientDrawable tb = new android.graphics.drawable.GradientDrawable();
                tb.setCornerRadius(dp(2));
                tb.setColor(0x26FFFFFF);
                track.setBackground(tb);
                View fill = new View(this);
                android.graphics.drawable.GradientDrawable fb = new android.graphics.drawable.GradientDrawable();
                fb.setCornerRadius(dp(2));
                fb.setColor(used > 0.9f ? 0xFFFF6B6B : accent);
                fill.setBackground(fb);
                fill.setPivotX(0);
                fill.setScaleX(used);
                track.addView(fill, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
                tl.topMargin = dp(7);
                row.addView(track, tl);
            }
            ConsoleScreen.lightRow(row, false, name, sub);
            row.setOnFocusChangeListener((v, has) -> {
                ConsoleScreen.lightRow(row, has || p == place, name, sub);
                if (has) {
                    PocketSounds.play(this, PocketSounds.NAV);
                }
            });
            row.setOnClickListener(v -> openPlace(p, true));
            row.setTag(p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            placeList.addView(row, lp);
        }
    }

    private static String placeIcon(String kind) {
        switch (kind) {
            case "sd":
                return "SLIDERS";
            case "down":
                return "NEXT";
            case "pic":
                return "PHOTO";
            case "roms":
                return "GAMEPAD";
            case "music":
                return "MUSIC";
            default:
                return "FOLDER";
        }
    }

    /** How full the storage is (0..1), or -1 when it can't be read. */
    private static float usedFraction(File f) {
        try {
            StatFs s = new StatFs(f.getAbsolutePath());
            long total = s.getTotalBytes();
            return total > 0 ? 1f - s.getAvailableBytes() / (float) total : -1f;
        } catch (RuntimeException e) {
            return -1f;
        }
    }

    private String freeText(File f) {
        try {
            StatFs s = new StatFs(f.getAbsolutePath());
            return getString(R.string.pocket_files_free, Formatter.formatShortFileSize(this, s.getAvailableBytes()),
                    Formatter.formatShortFileSize(this, s.getTotalBytes()));
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void openPlace(Place p, boolean focusList) {
        place = p;
        marked.clear();
        for (int i = 0; i < placeList.getChildCount(); i++) {
            View row = placeList.getChildAt(i);
            if (row instanceof LinearLayout && ((LinearLayout) row).getChildCount() >= 2) {
                ConsoleScreen.lightRow(row, row.hasFocus() || row.getTag() == p,
                        (TextView) ((LinearLayout) row).getChildAt(0), (TextView) ((LinearLayout) row).getChildAt(1));
            }
        }
        if (focusList) {
            PocketSounds.play(this, PocketSounds.SELECT);
        }
        load(p.root, null);
        if (focusList) {
            main.postDelayed(this::focusList, 120);
        }
    }

    // ---- The folder ---------------------------------------------------------------------

    private int sort() {
        return PocketPrefs.prefs(this).getInt(KEY_SORT, SORT_NAME);
    }

    private boolean showHidden() {
        return PocketPrefs.prefs(this).getBoolean(KEY_HIDDEN, false);
    }

    private void load(final File folder, final String focus) {
        if (dir != null && place != null) {
            String now = dir.getAbsolutePath();
            String next = folder.getAbsolutePath();
            moved = next.startsWith(now + "/") ? 1 : now.startsWith(next + "/") ? -1 : 0;
        }
        else {
            moved = 0;
        }
        dir = folder;
        focusName = focus;
        pathView.setText(breadcrumb(folder));
        freeView.setText(freeText(folder));
        final int sort = sort();
        final boolean hidden = showHidden();
        io.execute(() -> {
            File[] files = folder.listFiles();
            final List<Entry> out = new ArrayList<>();
            if (files != null) {
                for (File f : files) {
                    if (!hidden && f.getName().startsWith(".")) {
                        continue;
                    }
                    boolean isDir = f.isDirectory();
                    int count = 0;
                    if (isDir) {
                        String[] inside = f.list();
                        count = inside != null ? inside.length : 0;
                    }
                    out.add(new Entry(f, isDir, isDir ? 0 : f.length(), f.lastModified(), count));
                }
            }
            Comparator<Entry> byName = (a, b) -> a.file.getName().compareToIgnoreCase(b.file.getName());
            Comparator<Entry> cmp = sort == SORT_DATE ? (a, b) -> Long.compare(b.modified, a.modified)
                    : sort == SORT_SIZE ? (a, b) -> Long.compare(b.size, a.size) : byName;
            Collections.sort(out, (a, b) -> a.folder != b.folder ? (a.folder ? -1 : 1) : cmp.compare(a, b));
            main.post(() -> {
                if (isFinishing() || dir != folder) {
                    return;
                }
                entries.clear();
                entries.addAll(out);
                rows.notifyDataSetChanged();
                if (com.limelight.pocket.PocketPrefs.animationsEnabled(FilesActivity.this)) {
                    // Into a folder: it comes from the right; up: from the left
                    list.animate().cancel();
                    list.setAlpha(0f);
                    list.setTranslationX(moved * dp(40));
                    list.setTranslationY(moved == 0 ? dp(10) : 0);
                    list.animate().alpha(1f).translationX(0).translationY(0).setDuration(260)
                            .setInterpolator(com.limelight.pocket.ui.PocketMotion.DECELERATE).start();
                }
                if (out.isEmpty()) {
                    preview.show(null);
                }
                emptyView.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
                emptyView.setText(files == null ? R.string.pocket_files_cant_read : R.string.pocket_files_empty);
                emptyView.setFocusable(false);
                int at = 0;
                for (int i = 0; focusName != null && i < out.size(); i++) {
                    if (out.get(i).file.getName().equals(focusName)) {
                        at = i;
                    }
                }
                final int target = at;
                list.scrollToPosition(target);
                if (list.hasFocus() || focusName != null || getCurrentFocus() == null) {
                    list.post(() -> focusAt(target));
                }
            });
        });
    }

    /** "Internal storage  ›  Download  ›  Games": the folder from its place down. */
    private String breadcrumb(File folder) {
        String path = folder.getAbsolutePath();
        if (place == null) {
            return path;
        }
        String root = place.root.getAbsolutePath();
        StringBuilder b = new StringBuilder(place.name);
        if (path.startsWith(root)) {
            for (String part : path.substring(root.length()).split("/")) {
                if (!part.isEmpty()) {
                    b.append("  \u203A  ").append(part);
                }
            }
            return b.toString();
        }
        return path;
    }

    /** The chosen file in the pane on the right (after a moment's rest: moving fast stays smooth). */
    private void updatePreview() {
        Entry e = focused();
        if (e != null) {
            preview.show(e);
        }
    }

    private void focusList() {
        int at = 0;
        focusAt(at);
    }

    private void focusAt(int position) {
        if (entries.isEmpty()) {
            if (placeList.getChildCount() > 0 && !placeList.hasFocus()) {
                placeList.getChildAt(Math.max(0, places.indexOf(place))).requestFocus();
            }
            return;
        }
        RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(position);
        if (h != null) {
            h.itemView.requestFocus();
        }
        else {
            list.scrollToPosition(position);
            list.post(() -> {
                RecyclerView.ViewHolder again = list.findViewHolderForAdapterPosition(position);
                if (again != null) {
                    again.itemView.requestFocus();
                }
            });
        }
    }

    private Entry focused() {
        View f = getCurrentFocus();
        if (f == null || f.getParent() != list) {
            return null;
        }
        int pos = list.getChildAdapterPosition(f);
        return pos >= 0 && pos < entries.size() ? entries.get(pos) : null;
    }

    private boolean atPlaceRoot() {
        return place == null || dir == null || dir.getAbsolutePath().equals(place.root.getAbsolutePath())
                || dir.getParentFile() == null;
    }

    private void goUp() {
        if (atPlaceRoot()) {
            View row = placeList.getChildAt(Math.max(0, places.indexOf(place)));
            if (row != null) {
                row.requestFocus();
            }
            return;
        }
        PocketSounds.play(this, PocketSounds.BACK);
        File from = dir;
        load(dir.getParentFile(), from.getName());
    }

    // ---- Opening ------------------------------------------------------------------------

    private void open(Entry e) {
        if (e.folder) {
            PocketSounds.play(this, PocketSounds.SELECT);
            load(e.file, null);
            return;
        }
        String ext = ext(e.file.getName());
        if ("apk".equals(ext)) {
            install(e.file);
            return;
        }
        view(e.file, false);
    }

    private static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private Uri uriOf(File f) {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
    }

    private String mimeOf(File f) {
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(f.getName()));
        return m != null ? m : "*/*";
    }

    private void view(File f, boolean chooser) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(f), mimeOf(f))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            PocketSounds.play(this, PocketSounds.SELECT);
            startActivity(chooser ? Intent.createChooser(i, f.getName()) : i);
        } catch (ActivityNotFoundException ex) {
            if (!chooser) {
                view(f, true);
            }
        } catch (RuntimeException ex) {
            Toast.makeText(this, R.string.pocket_files_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void install(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.pocket_files_allow_install, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException ignored) {
            }
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(apk), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.pocket_files_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Menus --------------------------------------------------------------------------

    /** What's chosen: the marked files, else the focused one. */
    private List<File> chosen(Entry e) {
        List<File> out = new ArrayList<>(marked);
        if (out.isEmpty() && e != null) {
            out.add(e.file);
        }
        return out;
    }

    private void options(final Entry e) {
        final List<File> files = chosen(e);
        if (files.isEmpty()) {
            folderMenu();
            return;
        }
        List<StreamMenuView.Item> items = new ArrayList<>();
        final boolean one = files.size() == 1;
        if (one && e != null) {
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_hint_open), () -> open(e)).withIcon("PLAY"));
            if (!e.folder) {
                items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_open_with), () -> view(e.file, true)));
            }
        }
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_copy), () -> clip(files, false)).withIcon("CLIPBOARD"));
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_cut), () -> clip(files, true)));
        if (!CLIP.isEmpty() && one && e != null && e.folder) {
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_paste_into, e.file.getName()), () -> paste(e.file)));
        }
        if (one) {
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_rename), () -> rename(files.get(0))));
        }
        if (!files.isEmpty() && !hasFolder(files)) {
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_share), () -> share(files)));
        }
        if (one && e != null && e.folder) {
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_use_roms), () -> useAsRoms(e.file)));
        }
        items.add(StreamMenuView.Item.confirm(getString(R.string.pocket_files_delete, files.size()), () -> delete(files)));
        if (one) {
            File f = files.get(0);
            items.add(StreamMenuView.Item.header(f.getAbsolutePath()));
            items.add(StreamMenuView.Item.header(DateUtils.formatDateTime(this, f.lastModified(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR)
                    + (f.isFile() ? "  ·  " + Formatter.formatFileSize(this, f.length()) : "")));
        }
        String title = one ? files.get(0).getName() : getString(R.string.pocket_files_marked, files.size());
        PocketSounds.play(this, PocketSounds.OPEN);
        menu.show(title, null, null, items);
    }

    private static boolean hasFolder(List<File> files) {
        for (File f : files) {
            if (f.isDirectory()) {
                return true;
            }
        }
        return false;
    }

    private void folderMenu() {
        if (dir == null) {
            return;
        }
        List<StreamMenuView.Item> items = new ArrayList<>();
        if (!CLIP.isEmpty()) {
            items.add(StreamMenuView.Item.action(getString(clipCut ? R.string.pocket_files_move_here : R.string.pocket_files_paste_here, CLIP.size()), () -> paste(dir)).withIcon("CLIPBOARD"));
        }
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_new_folder), this::newFolder));
        items.add(StreamMenuView.Item.value(getString(R.string.pocket_files_sort), sortLabel(), direction -> {
            int next = (sort() + (direction < 0 ? 2 : 1)) % 3;
            PocketPrefs.prefs(this).edit().putInt(KEY_SORT, next).apply();
            load(dir, null);
            return sortLabel();
        }));
        items.add(StreamMenuView.Item.value(getString(R.string.pocket_files_hidden),
                getString(showHidden() ? R.string.pocket_on : R.string.pocket_off), direction -> {
                    boolean now = !showHidden();
                    PocketPrefs.prefs(this).edit().putBoolean(KEY_HIDDEN, now).apply();
                    load(dir, null);
                    return getString(now ? R.string.pocket_on : R.string.pocket_off);
                }));
        if (!marked.isEmpty()) {
            items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_unmark), () -> {
                marked.clear();
                rows.notifyDataSetChanged();
            }));
        }
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_mark_all), () -> {
            for (Entry en : entries) {
                marked.add(en.file);
            }
            rows.notifyDataSetChanged();
        }));
        items.add(StreamMenuView.Item.action(getString(R.string.pocket_files_use_roms), () -> useAsRoms(dir)));
        items.add(StreamMenuView.Item.header(dir.getAbsolutePath()));
        PocketSounds.play(this, PocketSounds.OPEN);
        menu.show(dir.getName().isEmpty() ? dir.getAbsolutePath() : dir.getName(), freeText(dir), null, items);
    }

    private String sortLabel() {
        switch (sort()) {
            case SORT_DATE:
                return getString(R.string.pocket_files_sort_date);
            case SORT_SIZE:
                return getString(R.string.pocket_files_sort_size);
            default:
                return getString(R.string.pocket_files_sort_name);
        }
    }

    // ---- Doing things -------------------------------------------------------------------

    private void clip(List<File> files, boolean cut) {
        CLIP.clear();
        CLIP.addAll(files);
        clipCut = cut;
        marked.clear();
        rows.notifyDataSetChanged();
        Toast.makeText(this, getString(cut ? R.string.pocket_files_cut_done : R.string.pocket_files_copied, files.size()), Toast.LENGTH_SHORT).show();
    }

    private void paste(final File into) {
        final List<File> what = new ArrayList<>(CLIP);
        final boolean cut = clipCut;
        if (what.isEmpty()) {
            return;
        }
        Toast.makeText(this, R.string.pocket_files_working, Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            int done = 0;
            for (File f : what) {
                if (into.getAbsolutePath().startsWith(f.getAbsolutePath() + File.separator)
                        || into.getAbsolutePath().equals(f.getAbsolutePath())) {
                    continue; // never into itself
                }
                File target = freeName(new File(into, f.getName()));
                try {
                    if (cut && f.renameTo(target)) {
                        done++;
                        continue;
                    }
                    copy(f, target);
                    if (cut) {
                        deleteAll(f);
                    }
                    done++;
                } catch (IOException e) {
                    // the rest still go
                }
            }
            final int n = done;
            main.post(() -> {
                if (cut) {
                    CLIP.clear();
                }
                Toast.makeText(this, getString(R.string.pocket_files_pasted, n), Toast.LENGTH_SHORT).show();
                if (dir != null) {
                    load(dir, null);
                }
            });
        });
    }

    private static File freeName(File f) {
        if (!f.exists()) {
            return f;
        }
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 && f.getName().indexOf('.') != 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            File t = new File(f.getParentFile(), base + " (" + i + ")" + ext);
            if (!t.exists()) {
                return t;
            }
        }
        return f;
    }

    private static void copy(File from, File to) throws IOException {
        if (from.isDirectory()) {
            if (!to.mkdirs() && !to.isDirectory()) {
                throw new IOException("mkdir " + to);
            }
            File[] kids = from.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    copy(k, new File(to, k.getName()));
                }
            }
            return;
        }
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        to.setLastModified(from.lastModified());
    }

    private static boolean deleteAll(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    deleteAll(k);
                }
            }
        }
        return f.delete();
    }

    private void delete(final List<File> files) {
        io.execute(() -> {
            int n = 0;
            for (File f : files) {
                if (deleteAll(f)) {
                    n++;
                }
            }
            final int done = n;
            main.post(() -> {
                marked.clear();
                PocketSounds.play(this, PocketSounds.TOGGLE_OFF);
                Toast.makeText(this, getString(R.string.pocket_files_deleted, done),
                        Toast.LENGTH_SHORT).show();
                if (dir != null) {
                    load(dir, null);
                }
            });
        });
    }

    private void rename(final File f) {
        ConsoleScreen.prompt(this, getString(R.string.pocket_rename), f.getName(), null, name -> {
            if (name.isEmpty() || name.contains("/") || name.equals(f.getName())) {
                return;
            }
            File to = new File(f.getParentFile(), name);
            if (to.exists() || !f.renameTo(to)) {
                Toast.makeText(this, R.string.pocket_files_cant_rename, Toast.LENGTH_SHORT).show();
                return;
            }
            load(dir, name);
        });
    }

    private void newFolder() {
        ConsoleScreen.prompt(this, getString(R.string.pocket_files_new_folder), null,
                getString(R.string.pocket_files_new_folder_hint), name -> {
                    if (name.isEmpty() || name.contains("/")) {
                        return;
                    }
                    File f = new File(dir, name);
                    if (!f.mkdirs()) {
                        Toast.makeText(this, R.string.pocket_files_cant_rename, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    load(dir, name);
                });
    }

    private void share(List<File> files) {
        try {
            Intent i;
            if (files.size() == 1) {
                i = new Intent(Intent.ACTION_SEND).setType(mimeOf(files.get(0))).putExtra(Intent.EXTRA_STREAM, uriOf(files.get(0)));
            }
            else {
                ArrayList<Uri> uris = new ArrayList<>();
                for (File f : files) {
                    uris.add(uriOf(f));
                }
                i = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, getString(R.string.pocket_files_share)));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.pocket_files_cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void useAsRoms(File folder) {
        RetroLibrary.setRoot(this, folder);
        Toast.makeText(this, getString(R.string.pocket_retro_folder_set, folder.getAbsolutePath()), Toast.LENGTH_LONG).show();
        buildPlaces();
    }

    // ---- Buttons ------------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (menu.isShowing()) {
            menu.handleKey(event);
            return true;
        }
        int code = event.getKeyCode();
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (up) {
                    if (list.hasFocus()) {
                        // Up a folder (from a place's top: to the places)
                        goUp();
                    }
                    else {
                        PocketSounds.play(this, PocketSounds.CLOSE);
                        finish();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
                if (up) {
                    View f = getCurrentFocus();
                    if (f != null) {
                        f.performClick();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                if (up) {
                    Entry e = focused();
                    if (e != null) {
                        if (!marked.remove(e.file)) {
                            marked.add(e.file);
                        }
                        PocketSounds.play(this, marked.contains(e.file) ? PocketSounds.TOGGLE_ON : PocketSounds.TOGGLE_OFF);
                        rows.notifyItemChanged(entries.indexOf(e));
                    }
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (up) {
                    if (list.hasFocus()) {
                        options(focused());
                    }
                    else {
                        folderMenu();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                if (up) {
                    folderMenu();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
                if (!up && list.hasFocus()) {
                    Entry e = focused();
                    int at = e != null ? entries.indexOf(e) : 0;
                    int to = Math.max(0, Math.min(entries.size() - 1, at + (code == KeyEvent.KEYCODE_BUTTON_R1 ? 8 : -8)));
                    focusAt(to);
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    // ---- The rows -----------------------------------------------------------------------

    private final class Rows extends RecyclerView.Adapter<Rows.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView sub;
            final TextView mark;

            Holder(LinearLayout row) {
                super(row);
                icon = (ImageView) row.getChildAt(0);
                LinearLayout texts = (LinearLayout) row.getChildAt(1);
                name = (TextView) texts.getChildAt(0);
                sub = (TextView) texts.getChildAt(1);
                mark = (TextView) row.getChildAt(2);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(7), dp(14), dp(7));
            row.setFocusable(true);
            row.setClickable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            row.setLayoutParams(lp);
            ImageView icon = new ImageView(c);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(32), dp(32));
            ilp.setMarginEnd(dp(12));
            row.addView(icon, ilp);
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = ConsoleScreen.text(c, 14, 0xF2FFFFFF, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            texts.addView(name);
            TextView sub = ConsoleScreen.text(c, 11, 0x8CFFFFFF, false);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.topMargin = dp(3);
            texts.addView(sub, sl);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView mark = ConsoleScreen.text(c, 15, accent, true);
            mark.setText("✓");
            row.addView(mark);
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder h, int position) {
            final Entry e = entries.get(position);
            h.icon.setImageDrawable(new FileIcon(e.folder, e.file.getName(), accent));
            h.name.setText(e.file.getName());
            String sub;
            if (e.folder) {
                sub = getString(R.string.pocket_files_items, e.count);
            }
            else {
                sub = Formatter.formatShortFileSize(FilesActivity.this, e.size);
            }
            sub += "  ·  " + DateUtils.getRelativeTimeSpanString(e.modified, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
            h.sub.setText(sub);
            h.mark.setVisibility(marked.contains(e.file) ? View.VISIBLE : View.INVISIBLE);
            ConsoleScreen.lightRow(h.itemView, h.itemView.hasFocus(), h.name, h.sub);
            h.itemView.setOnFocusChangeListener((v, has) -> {
                ConsoleScreen.lightRow(v, has, h.name, h.sub);
                if (has) {
                    PocketSounds.play(FilesActivity.this, PocketSounds.NAV);
                    main.removeCallbacks(showPreview);
                    main.postDelayed(showPreview, 110);
                }
            });
            h.itemView.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos >= 0 && pos < entries.size()) {
                    open(entries.get(pos));
                }
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    // ---- The pane on the right ----------------------------------------------------------

    /** The chosen file, big: a picture of it when there is one, then its name and details. */
    private final class Preview extends LinearLayout {
        final ImageView art;
        final TextView name;
        final TextView details;
        File showing;

        Preview(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setPadding(dp(18), dp(18), dp(18), dp(18));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(0x14FFFFFF);
            bg.setStroke(Math.max(1, dp(1)), 0x14FFFFFF);
            setBackground(bg);
            art = new ImageView(c);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
                }
            });
            art.setClipToOutline(true);
            addView(art, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
            name = ConsoleScreen.text(c, 15, 0xFFFFFFFF, true);
            name.setTypeface(com.limelight.pocket.ui.PocketFonts.semibold());
            name.setMaxLines(3);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams nl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = dp(14);
            addView(name, nl);
            details = ConsoleScreen.text(c, 11.5f, 0x99FFFFFF, false);
            details.setLineSpacing(0, 1.35f);
            LayoutParams dl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dl.topMargin = dp(8);
            addView(details, dl);
            setAlpha(0f);
        }

        void show(final Entry e) {
            if (e == null) {
                showing = null;
                animate().alpha(0f).setDuration(160).start();
                return;
            }
            if (e.file.equals(showing)) {
                return;
            }
            showing = e.file;
            name.setText(e.file.getName());
            StringBuilder b = new StringBuilder();
            String ext = ext(e.file.getName());
            if (e.folder) {
                b.append(getString(R.string.pocket_files_items, e.count));
            }
            else {
                b.append(ext.isEmpty() ? getString(R.string.pocket_files_kind_file) : ext.toUpperCase(Locale.ROOT))
                        .append("  \u00B7  ").append(Formatter.formatShortFileSize(FilesActivity.this, e.size));
            }
            b.append("\n").append(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                    java.text.DateFormat.SHORT).format(new java.util.Date(e.modified)));
            details.setText(b.toString());
            art.setImageDrawable(new FileIcon(e.folder, e.file.getName(), accent));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setPadding(dp(46), dp(36), dp(46), dp(36));
            art.setBackgroundColor(0x0FFFFFFF);
            animate().cancel();
            setAlpha(0.35f);
            setTranslationX(dp(8));
            animate().alpha(1f).translationX(0).setDuration(200).start();
            if (e.folder) {
                return;
            }
            final File f = e.file;
            final int size = dp(290);
            io.execute(() -> {
                final android.graphics.Bitmap pic = picture(f, ext, size);
                main.post(() -> {
                    if (pic != null && f.equals(showing)) {
                        art.setPadding(0, 0, 0, 0);
                        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        art.setImageBitmap(pic);
                        art.setAlpha(0f);
                        art.animate().alpha(1f).setDuration(220).start();
                    }
                });
            });
        }
    }

    /** A picture of the file: a photo itself, a video's frame, an app's icon, a song's cover. */
    private android.graphics.Bitmap picture(File f, String ext, int size) {
        try {
            switch (ext) {
                case "jpg":
                case "jpeg":
                case "png":
                case "webp":
                case "gif":
                case "bmp":
                case "heic": {
                    android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
                    int sample = 1;
                    while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) {
                        sample *= 2;
                    }
                    android.graphics.BitmapFactory.Options d = new android.graphics.BitmapFactory.Options();
                    d.inSampleSize = sample;
                    return android.graphics.BitmapFactory.decodeFile(f.getPath(), d);
                }
                case "mp4":
                case "mkv":
                case "webm":
                case "mov":
                case "avi":
                    if (Build.VERSION.SDK_INT >= 29) {
                        return android.media.ThumbnailUtils.createVideoThumbnail(f, new android.util.Size(size, size), null);
                    }
                    return null;
                case "mp3":
                case "flac":
                case "m4a":
                case "ogg":
                case "opus":
                case "wav": {
                    android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                    try {
                        r.setDataSource(f.getPath());
                        byte[] pic = r.getEmbeddedPicture();
                        return pic != null ? android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.length) : null;
                    } finally {
                        try {
                            r.release();
                        } catch (Exception ignored) {
                        }
                    }
                }
                case "apk": {
                    android.content.pm.PackageInfo info = getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
                    if (info == null || info.applicationInfo == null) {
                        return null;
                    }
                    info.applicationInfo.sourceDir = f.getPath();
                    info.applicationInfo.publicSourceDir = f.getPath();
                    android.graphics.drawable.Drawable icon = info.applicationInfo.loadIcon(getPackageManager());
                    android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c = new android.graphics.Canvas(b);
                    c.drawColor(0xFF1C1C24);
                    int inset = size / 4;
                    icon.setBounds(inset, inset, size - inset, size - inset);
                    icon.draw(c);
                    return b;
                }
                default:
                    return null;
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
