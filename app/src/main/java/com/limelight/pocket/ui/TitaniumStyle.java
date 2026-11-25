package com.limelight.pocket.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import com.limelight.R;

/** Solid, thin-edged surfaces shared by the Titanium workspaces. */
public final class TitaniumStyle {
    public static final int BACKGROUND = 0xFF11181D;
    public static final int TEXT = 0xFFD7E2EA;
    public static final int SECONDARY = 0xFFACBECA;
    private static final int SURFACE = 0xFF202B33;
    private static final int EDGE = 0xFF50636F;
    private static final int[] THERMAL_LABELS = {R.string.horizon_thermal_none, R.string.horizon_thermal_light,
            R.string.horizon_thermal_moderate, R.string.horizon_thermal_severe, R.string.horizon_thermal_critical,
            R.string.horizon_thermal_emergency, R.string.horizon_thermal_shutdown};

    private TitaniumStyle() {}

    public static GradientDrawable surface(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable result = new GradientDrawable();
        result.setColor(SURFACE);
        result.setCornerRadius(5 * density);
        result.setStroke(Math.max(1, Math.round(density)), EDGE);
        return result;
    }

    public static String thermal(Context context, int status) {
        return context.getString(status >= 0 && status < THERMAL_LABELS.length ? THERMAL_LABELS[status]
                : R.string.horizon_titanium_unavailable);
    }
}
