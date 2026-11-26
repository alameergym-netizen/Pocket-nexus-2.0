package com.limelight.pocket.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.limelight.R;

/** Nexus Signature's letterbox bars, as views behind a screen's content (see HorizonSignature). */
public final class HorizonSignatureFrame {
    private HorizonSignatureFrame() {}

    /**
     * A bar: solid black for {@code solidPx}, then easing into the art over {@code edgePx} (below
     * it for the top bar, above it for the bottom one).
     */
    public static Drawable bar(boolean top, int solidPx, int edgePx) {
        ColorDrawable solid = new ColorDrawable(HorizonSignature.BAR);
        GradientDrawable edge = new GradientDrawable(
                top ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{HorizonSignature.BAR, HorizonSignature.BAR_EDGE});
        LayerDrawable layers = new LayerDrawable(new Drawable[]{solid, edge});
        if (top) {
            layers.setLayerInset(0, 0, 0, 0, edgePx);
            layers.setLayerInset(1, 0, solidPx, 0, 0);
        }
        else {
            layers.setLayerInset(0, 0, edgePx, 0, 0);
            layers.setLayerInset(1, 0, 0, 0, solidPx);
        }
        return layers;
    }

    /**
     * Puts (or updates) a bar in {@code host} at {@code index} (behind what follows it), solid for
     * {@code solidDp} plus the soft edge. Returns the bar.
     */
    public static View place(ViewGroup host, int index, boolean top, int solidDp) {
        Context c = host.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        int id = top ? R.id.horizon_signature_bar_top : R.id.horizon_signature_bar_bottom;
        View bar = host.findViewById(id);
        if (bar == null) {
            bar = new View(c);
            bar.setId(id);
            bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            host.addView(bar, Math.max(0, Math.min(index, host.getChildCount())),
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        int solid = Math.round(solidDp * d);
        int edge = Math.round(HorizonSignature.EDGE * d);
        bar.setBackground(bar(top, solid, edge));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                solid + edge, top ? Gravity.TOP : Gravity.BOTTOM);
        bar.setLayoutParams(lp);
        bar.setVisibility(View.VISIBLE);
        return bar;
    }

    /** Hides both bars in {@code host}, if it has them. */
    public static void remove(ViewGroup host) {
        for (int id : new int[]{R.id.horizon_signature_bar_top, R.id.horizon_signature_bar_bottom}) {
            View bar = host.findViewById(id);
            if (bar != null) bar.setVisibility(View.GONE);
        }
    }
}
