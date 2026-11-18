package com.limelight.pocket.ui;

import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.limelight.pocket.PocketPrefs;
import com.limelight.pocket.PocketSounds;

/**
 * The one look of being chosen, everywhere: a white ring hugging the shape, a soft white glow
 * under it (its shadow, lit), and a small rise. Nothing square, nothing grey.
 */
public final class FocusGlow {

    private FocusGlow() {
    }

    /** A rounded rectangle (radius in px), or a circle when oval. */
    public static void apply(final View v, final float radius, final boolean oval, final float grow,
                             final View.OnFocusChangeListener also) {
        final float d = v.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= 26) {
            v.setDefaultFocusHighlightEnabled(false);
        }
        final GradientDrawable ring = new GradientDrawable();
        if (oval) {
            ring.setShape(GradientDrawable.OVAL);
        }
        else {
            ring.setCornerRadius(radius);
        }
        ring.setColor(0x0FFFFFFF);
        ring.setStroke(Math.round(2.5f * d), 0xFFFFFFFF);
        ring.setAlpha(0);
        if (Build.VERSION.SDK_INT >= 23) {
            v.setForeground(ring);
        }
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                if (oval) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
                else {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            }
        });
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineAmbientShadowColor(0xFFFFFFFF);
            v.setOutlineSpotShadowColor(0xFFFFFFFF);
        }
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            private ValueAnimator fade;

            @Override
            public void onFocusChange(View view, boolean has) {
                boolean motion = PocketPrefs.animationsEnabled(view.getContext());
                if (fade != null) {
                    fade.cancel();
                }
                fade = ValueAnimator.ofInt(ring.getAlpha(), has ? 255 : 0);
                fade.setDuration(motion ? (has ? 200 : 150) : 0);
                fade.addUpdateListener(a -> ring.setAlpha((int) a.getAnimatedValue()));
                fade.start();
                view.animate().cancel();
                if (has) {
                    PocketSounds.play(view.getContext(), PocketSounds.NAV);
                    view.animate().scaleX(grow).scaleY(grow).translationZ(9 * d)
                            .setDuration(motion ? 240 : 0).setInterpolator(Spring.POP).start();
                }
                else {
                    view.animate().scaleX(1f).scaleY(1f).translationZ(0f)
                            .setDuration(motion ? 170 : 0).setInterpolator(PocketMotion.DECELERATE).start();
                }
                if (also != null) {
                    also.onFocusChange(view, has);
                }
            }
        });
    }

    /** A short press: a quick squeeze and back. */
    public static void press(View v) {
        if (!PocketPrefs.animationsEnabled(v.getContext())) {
            return;
        }
        final float sx = v.getScaleX();
        v.animate().cancel();
        v.animate().scaleX(sx * 0.93f).scaleY(sx * 0.93f).setDuration(70).withEndAction(() ->
                v.animate().scaleX(sx).scaleY(sx).setDuration(220).setInterpolator(Spring.POP).start()).start();
    }
}
