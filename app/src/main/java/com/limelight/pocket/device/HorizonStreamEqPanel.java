package com.limelight.pocket.device;

import android.content.SharedPreferences;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.binding.audio.HorizonStreamDsp;
import com.limelight.binding.audio.HorizonStreamEqSettings;
import com.limelight.pocket.ConsoleActivity;
import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.ui.StreamMenuView;

import java.util.ArrayList;
import java.util.List;

public final class HorizonStreamEqPanel {
    private HorizonStreamEqPanel() {}

    public static void show(ConsoleActivity screen) {
        screen.showConsolePanel(screen.getString(R.string.horizon_eq_title), null, null, rows(screen));
    }

    private static List<StreamMenuView.Item> rows(ConsoleActivity screen) {
        SharedPreferences prefs = PocketPrefs.prefs(screen);
        List<StreamMenuView.Item> rows = new ArrayList<>();
        rows.add(StreamMenuView.Item.note(screen.getString(R.string.horizon_eq_description)));
        for (HorizonStreamEqSettings.Control control : HorizonStreamEqSettings.Control.values()) {
            rows.add(StreamMenuView.Item.value(screen.getString(control.label), display(screen, control, control.read(prefs)),
                    direction -> display(screen, control, control.step(prefs, direction))).withStepButtons());
        }
        rows.add(StreamMenuView.Item.action(screen.getString(R.string.horizon_eq_enable), () -> {
            prefs.edit().putString(HorizonStreamDsp.KEY_PROFILE, HorizonStreamEqSettings.CUSTOM).apply();
            Toast.makeText(screen, R.string.horizon_eq_enabled, Toast.LENGTH_SHORT).show();
        }));
        rows.add(StreamMenuView.Item.repeatable(screen.getString(R.string.horizon_eq_reset), () -> {
            HorizonStreamEqSettings.reset(prefs);
            screen.replaceConsolePage(rows, rows(screen));
        }));
        return rows;
    }

    private static String display(ConsoleActivity screen, HorizonStreamEqSettings.Control control, int value) {
        if (control != HorizonStreamEqSettings.Control.BALANCE) return screen.getString(R.string.horizon_eq_db, value);
        if (value == 0) return screen.getString(R.string.horizon_eq_center);
        return screen.getString(value < 0 ? R.string.horizon_eq_left : R.string.horizon_eq_right, Math.abs(value));
    }
}
