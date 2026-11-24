package com.limelight.pocket.device;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.format.Formatter;
import com.limelight.R;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.retro.RetroLibrary;
import com.limelight.pocket.ui.StreamMenuView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Read-only storage inventory. Never modifies a volume or changes the selected ROM root. */
public final class HorizonStorage {
    private HorizonStorage() {}

    static boolean mounted(String state) {
        return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    static boolean containsPath(String root, String path) {
        if (root == null || path == null || root.isEmpty() || path.isEmpty()) { return false; }
        while (root.length() > 1 && root.endsWith("/")) { root = root.substring(0, root.length() - 1); }
        return path.equals(root) || path.startsWith(root.endsWith("/") ? root : root + "/");
    }

    private static String space(Context c, File directory) {
        if (directory == null) { return c.getString(R.string.horizon_storage_unknown); }
        try {
            StatFs fs = new StatFs(directory.getAbsolutePath());
            return c.getString(R.string.horizon_storage_space,
                    Formatter.formatShortFileSize(c, fs.getAvailableBytes()),
                    Formatter.formatShortFileSize(c, fs.getTotalBytes()));
        } catch (RuntimeException e) { return c.getString(R.string.horizon_storage_unknown); }
    }

    public static void show(ConsoleActivity a) {
        List<StreamMenuView.Item> rows = new ArrayList<>();
        File romRoot = null;
        try { romRoot = RetroLibrary.romRoot(a); } catch (RuntimeException ignored) {}
        rows.add(StreamMenuView.Item.header(a.getString(R.string.horizon_storage_rom)));
        if (romRoot != null) {
            boolean readable = false;
            try { readable = romRoot.isDirectory() && romRoot.canRead(); } catch (RuntimeException ignored) {}
            rows.add(StreamMenuView.Item.info(a.getString(readable
                    ? R.string.horizon_storage_accessible : R.string.horizon_storage_missing), romRoot.getName()));
            rows.add(StreamMenuView.Item.note(romRoot.getAbsolutePath()));
            if (!readable) {
                rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_storage_keep)));
            }
        } else {
            rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_storage_no_root)));
        }
        rows.add(StreamMenuView.Item.header(a.getString(R.string.horizon_storage_volumes)));
        boolean listed = false;
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                StorageManager sm = (StorageManager) a.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    for (StorageVolume v : sm.getStorageVolumes()) {
                        String label = v.isPrimary() ? a.getString(R.string.horizon_storage_internal) : v.getDescription(a);
                        File dir = Build.VERSION.SDK_INT >= 30 ? v.getDirectory() : null;
                        if (dir == null && v.isPrimary()) { dir = Environment.getExternalStorageDirectory(); }
                        String state = v.getState();
                        String value = mounted(state) ? space(a, dir) : a.getString(R.string.horizon_storage_disconnected);
                        rows.add(StreamMenuView.Item.info(label, value));
                        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                            rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_storage_readonly)));
                        }
                        if (dir != null && romRoot != null && containsPath(dir.getAbsolutePath(), romRoot.getAbsolutePath())) {
                            rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_storage_selected)));
                        }
                        listed = true;
                    }
                }
            } catch (RuntimeException ignored) {}
        }
        if (!listed) {
            rows.add(StreamMenuView.Item.info(a.getString(R.string.horizon_storage_internal),
                    space(a, Environment.getExternalStorageDirectory())));
        }
        rows.add(StreamMenuView.Item.note(a.getString(R.string.horizon_storage_limit)));
        a.showConsolePanel(a.getString(R.string.horizon_storage_title), null, null, rows);
    }
}
