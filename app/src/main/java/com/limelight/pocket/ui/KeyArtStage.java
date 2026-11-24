package com.limelight.pocket.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.view.View;

import com.limelight.R;
import com.limelight.pocket.PocketPrefs;

/**
 * A game's key art, sharp across the top of the screen (console style), cross-faded between two
 * layers with a slow settle in size. Used by the home screen and the library alike.
 *
 * Layout: pocketKeyArtUnderlay (dims the blurred art while key art shows, so its colours still
 * glow through where the key art fades out), pocketKeyArtFrame holding pocketKeyArtA / B
 * (full screen; each fades itself out, see {@link KeyArtView}).
 */
public final class KeyArtStage {

    /** How much the underlay dims the blurred art while key art shows. */
    private static final float UNDER = 0.78f;

    private final Activity activity;
    private final View frame;
    private final View under;
    private final KeyArtView a;
    private final KeyArtView b;
    private boolean onA;
    private Bitmap shown;
    private android.animation.ObjectAnimator drift;

    private KeyArtStage(Activity activity, View frame, View under, KeyArtView a, KeyArtView b) {
        this.activity = activity;
        this.frame = frame;
        this.under = under;
        this.a = a;
        this.b = b;
    }

    /** The stage of this screen, or null when its layout has none. */
    public static KeyArtStage find(Activity activity) {
        View frame = activity.findViewById(R.id.pocketKeyArtFrame);
        View under = activity.findViewById(R.id.pocketKeyArtUnderlay);
        KeyArtView a = activity.findViewById(R.id.pocketKeyArtA);
        KeyArtView b = activity.findViewById(R.id.pocketKeyArtB);
        if (frame == null || under == null || a == null || b == null) {
            return null;
        }
        return new KeyArtStage(activity, frame, under, a, b);
    }

    /** Once settled, the art drifts very slowly (a living backdrop, like a console's). */
    private void startDrift(KeyArtView view) {
        stopDrift();
        if (shown == null || !PocketMotion.on(view) || !HorizonAppearance.artMotion(activity)
                || !activity.hasWindowFocus() || view.getAlpha() < 0.99f) {
            return;
        }
        view.setPivotX(view.getWidth() * 0.75f);
        view.setPivotY(0f);
        android.animation.PropertyValuesHolder sx = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f);
        android.animation.PropertyValuesHolder sy = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f);
        android.animation.PropertyValuesHolder tx = android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f,
                -view.getWidth() * 0.012f);
        drift = android.animation.ObjectAnimator.ofPropertyValuesHolder(view, sx, sy, tx);
        drift.setDuration(22000);
        drift.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        drift.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        drift.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        drift.start();
    }

    private void stopDrift() {
        if (drift != null) {
            drift.cancel();
            drift = null;
        }
        a.setTranslationX(0f);
        b.setTranslationX(0f);
    }

    /** Release background animation resources when Home is no longer visible. */
    public void pauseMotion() {
        a.animate().withEndAction(null).cancel();
        b.animate().withEndAction(null).cancel();
        stopMotion();
        stopDrift();
        if (shown != null) {
            KeyArtView active = onA ? a : b;
            KeyArtView other = onA ? b : a;
            active.setAlpha(1f);
            active.setScaleX(1f);
            active.setScaleY(1f);
            other.setAlpha(0f);
        }
    }

    /** Forget what is shown, so the next request puts it up again (new art arrived). */
    public void invalidate() {
        shown = null;
    }

    /** True while key art is on screen. */
    public boolean showing() {
        return shown != null;
    }

    // ---- Moving art (a video or an animated picture over the still) ----------------------------

    private android.view.TextureView motionVideo;
    private android.widget.ImageView motionImage;
    private TrailerPlayer motionPlayer;
    private java.io.File motionFile;

    /**
     * The game's moving background over its still one (null: none). A video plays muted and
     * looping, cropped to fill; an animated picture plays as it is. It fades in over the still
     * once its first frame is ready, so nothing ever flashes.
     */
    public void showMotion(java.io.File f) {
        if (f != null && f.equals(motionFile)) {
            return;
        }
        stopMotion();
        if (f == null || shown == null || !PocketMotion.on(frame) || !HorizonAppearance.artMotion(activity)
                || !(frame instanceof android.view.ViewGroup)) {
            return;
        }
        motionFile = f;
        final android.view.ViewGroup holder = (android.view.ViewGroup) frame;
        if (MotionArt.isVideo(f)) {
            final android.view.TextureView tv = new android.view.TextureView(activity);
            tv.setAlpha(0f);
            holder.addView(tv, new android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            motionVideo = tv;
            motionPlayer = new TrailerPlayer(activity, tv);
            motionPlayer.play(android.net.Uri.fromFile(f).toString(), true, true, new TrailerPlayer.Listener() {
                @Override
                public void onFirstFrame() {
                    if (motionVideo == tv) {
                        tv.animate().alpha(1f).setDuration(700).start();
                    }
                }

                @Override
                public void onError() {
                    if (motionVideo == tv) {
                        stopMotion();
                    }
                }

                @Override
                public void onVideoSize(int width, int height, float pixelRatio) {
                    cropToFill(tv, width * pixelRatio, height);
                }
            });
        }
        else {
            android.graphics.drawable.Drawable d = MotionArt.animated(f);
            if (d == null) {
                motionFile = null;
                return;
            }
            android.widget.ImageView iv = new android.widget.ImageView(activity);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(d);
            iv.setAlpha(0f);
            holder.addView(iv, new android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            motionImage = iv;
            iv.animate().alpha(1f).setDuration(700).start();
        }
    }

    /** A video fills the screen (cropped, centred), whatever its shape. */
    private static void cropToFill(android.view.TextureView tv, float vw, float vh) {
        float w = tv.getWidth();
        float h = tv.getHeight();
        if (w <= 0 || h <= 0 || vw <= 0 || vh <= 0) {
            return;
        }
        float scale = Math.max(w / vw, h / vh);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(vw * scale / w, vh * scale / h, w / 2f, h / 2f);
        tv.setTransform(m);
    }

    private void stopMotion() {
        motionFile = null;
        if (motionPlayer != null) {
            motionPlayer.release();
            motionPlayer = null;
        }
        for (final View v : new View[]{motionVideo, motionImage}) {
            if (v == null) {
                continue;
            }
            v.animate().cancel();
            v.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                if (v.getParent() instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) v.getParent()).removeView(v);
                }
            }).start();
        }
        motionVideo = null;
        motionImage = null;
    }

    private static final java.util.WeakHashMap<Bitmap, Float> DIM = new java.util.WeakHashMap<>();

    /**
     * How much to dim this art (1: not at all) so the left part, where the hero's words are,
     * isn't brighter than about mid-grey on average.
     */
    private static float textAreaDim(Bitmap b) {
        synchronized (DIM) {
            Float hit = DIM.get(b);
            if (hit != null) {
                return hit;
            }
        }
        float k = 1f;
        try {
            int w = b.getWidth();
            int h = b.getHeight();
            float sum = 0;
            int n = 0;
            for (int y = Math.round(h * 0.18f); y < h * 0.78f; y += Math.max(1, h / 24)) {
                for (int x = 0; x < w * 0.55f; x += Math.max(1, w / 32)) {
                    int p = b.getPixel(x, y);
                    sum += (0.2126f * android.graphics.Color.red(p) + 0.7152f * android.graphics.Color.green(p)
                            + 0.0722f * android.graphics.Color.blue(p)) / 255f;
                    n++;
                }
            }
            float l = n > 0 ? sum / n : 0f;
            if (l > 0.46f) {
                k = Math.max(0.6f, 0.46f / l);
            }
        } catch (RuntimeException e) {
            k = 1f;
        }
        synchronized (DIM) {
            DIM.put(b, k);
        }
        return k;
    }

    /** Shows this key art (null: none, the blurred art and lights show again). */
    public void show(Bitmap bitmap) {
        // Key art is made to be seen: every style shows it, except pure black
        if (bitmap != null && PocketPrefs.BACKDROP_BLACK.equals(PocketPrefs.backdrop(activity))) {
            bitmap = null;
        }
        if (bitmap != null && bitmap.isRecycled()) {
            bitmap = null;
        }
        if (bitmap == shown && (bitmap != null || frame.getAlpha() == 0f)) {
            return;
        }
        // Another game's art: its moving art (if any) comes after, by itself
        stopMotion();
        shown = bitmap;
        boolean animate = PocketMotion.on(frame);
        if (bitmap == null) {
            stopDrift();
            if (animate) {
                frame.animate().alpha(0f).setDuration(300).start();
                under.animate().alpha(0f).setDuration(300).start();
            }
            else {
                frame.setAlpha(0f);
                under.setAlpha(0f);
            }
            return;
        }
        KeyArtView incoming = onA ? b : a;
        KeyArtView outgoing = onA ? a : b;
        onA = !onA;
        incoming.setImageBitmap(bitmap);
        // An adaptive scrim: art that's bright where the name and the buttons sit (snow, a white
        // sky) is taken down until white words read on it; dark art is left as it is
        float k = textAreaDim(bitmap);
        boolean color = PocketPrefs.colorBackgroundArt(activity);
        if (color && k >= 0.999f) {
            incoming.clearColorFilter();
        }
        else {
            ColorMatrix m = new ColorMatrix();
            m.setScale(k, k, k, 1f);
            if (!color) {
                ColorMatrix mono = new ColorMatrix();
                mono.setSaturation(0f);
                m.postConcat(mono);
            }
            incoming.setColorFilter(new ColorMatrixColorFilter(m));
        }
        // Background layers: no entrance runs on them, so taking them over completely is fine
        incoming.animate().cancel();
        outgoing.animate().cancel();
        stopDrift();
        if (animate) {
            // A soft cross-fade with a slow settle in size (like a console's home screen)
            incoming.setPivotY(0f);
            incoming.setScaleX(1.05f);
            incoming.setScaleY(1.05f);
            incoming.setAlpha(0f);
            final KeyArtView settled = incoming;
            incoming.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900)
                    .setInterpolator(PocketMotion.DECELERATE)
                    .withEndAction(() -> startDrift(settled)).start();
            outgoing.animate().alpha(0f).setDuration(420).start();
            frame.animate().alpha(1f).setDuration(420).start();
            under.animate().alpha(UNDER).setDuration(420).start();
        }
        else {
            incoming.setAlpha(1f);
            incoming.setScaleX(1f);
            incoming.setScaleY(1f);
            outgoing.setAlpha(0f);
            frame.setAlpha(1f);
            under.setAlpha(UNDER);
        }
    }
}
