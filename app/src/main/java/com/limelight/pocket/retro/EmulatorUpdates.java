package com.limelight.pocket.retro;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeping the emulators up to date with Obtainium, the app that installs and updates apps
 * straight from their makers' own release pages (GitHub and the like): open it to update them
 * all, or hand it an emulator to follow. The emulators listed are the ones released as APKs on
 * their own projects' pages.
 */
public final class EmulatorUpdates {
    private EmulatorUpdates() {}

    private static final String[] OBTAINIUM = {"dev.imranr.obtainium", "dev.imranr.obtainium.fdroid"};
    private static final String OBTAINIUM_PAGE = "https://github.com/ImranR98/Obtainium/releases/latest";
    /** Every popular emulator in one file Obtainium imports (Import/Export, Obtainium Import). */
    private static final String EMULATION_PACK = "https://github.com/RJNY/Obtainium-Emulation-Pack/releases/latest";

    /** {name, its project's releases page}. */
    private static final String[][] SOURCES = {
            {"XenDroid (Xbox 360)", "https://github.com/rfandango/XenDroid"},
            {"Azahar (3DS)", "https://github.com/azahar-emu/azahar"},
            {"melonDS (DS)", "https://github.com/rafaelvcaetano/melonDS-android"},
            {"Vita3K (PS Vita)", "https://github.com/Vita3K/Vita3K-Android"},
            {"Cemu (Wii U)", "https://github.com/SSimco/Cemu"},
            {"aPS3e (PS3)", "https://github.com/aenu1/aps3e"},
            {"Lemuroid (retro)", "https://github.com/Swordfish90/Lemuroid"},
            {"Winlator (Windows)", "https://github.com/brunodev85/winlator"},
            {"GameNative (Steam)", "https://github.com/utkarshdalal/GameNative"},
    };

    static String installedObtainium(PackageManager pm) {
        for (String p : OBTAINIUM) {
            try {
                pm.getPackageInfo(p, 0);
                return p;
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            }
        }
        return null;
    }

    /** The "Update emulators" page of an options menu. */
    public static List<StreamMenuView.Item> items(final Activity a) {
        List<StreamMenuView.Item> items = new ArrayList<>();
        final String obtainium = installedObtainium(a.getPackageManager());
        if (obtainium != null) {
            items.add(StreamMenuView.Item.action(a.getString(R.string.pocket_emu_update_open), () -> {
                Intent launch = a.getPackageManager().getLaunchIntentForPackage(obtainium);
                if (launch != null) {
                    start(a, launch);
                }
            }));
        }
        else {
            items.add(StreamMenuView.Item.action(a.getString(R.string.pocket_emu_update_get), () -> view(a, OBTAINIUM_PAGE)));
        }
        items.add(StreamMenuView.Item.action(a.getString(R.string.pocket_emu_update_pack), () -> view(a, EMULATION_PACK)));
        items.add(StreamMenuView.Item.header(a.getString(R.string.pocket_emu_update_follow)));
        for (final String[] src : SOURCES) {
            items.add(StreamMenuView.Item.action(src[0], () -> {
                if (installedObtainium(a.getPackageManager()) == null) {
                    // Without Obtainium: the project's own page, to download it there
                    view(a, src[1] + "/releases/latest");
                    return;
                }
                view(a, "obtainium://add/" + src[1]);
            }));
        }
        items.add(StreamMenuView.Item.header(a.getString(R.string.pocket_emu_update_note)));
        return items;
    }

    private static void view(Activity a, String url) {
        start(a, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private static void start(Activity a, Intent intent) {
        try {
            a.startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(a, R.string.pocket_emu_update_cant, Toast.LENGTH_SHORT).show();
        }
    }
}
