package com.limelight.pocket.studio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import com.limelight.R;
import com.limelight.pocket.ui.HorizonAppearance;
import com.limelight.pocket.ui.HorizonLook;
import com.limelight.pocket.ui.HorizonThemeStudio;
import com.limelight.pocket.ui.LegacyStyle;
import com.limelight.pocket.ui.PocketTheme;
import com.limelight.pocket.ui.TitaniumStyle;

/**
 * A miniature console screen drawn from a look's own values: backdrop, material, accent, Home
 * layout, card shape and corners, focus style and art colour. Nothing is copied from a game or
 * another product; the "art" is abstract shapes. Pure OLED and Titanium are drawn with their
 * own layouts, as the real screens are.
 */
public final class ThemePreviewView extends View {
    public static final int HOME = 0;
    public static final int LIBRARY = 1;
    public static final int CONTROL = 2;

    private static final int STANDARD = 0;
    private static final int PURE = 1;
    private static final int TITANIUM = 2;
    private static final int LEGACY = 3;
    /** The layout families drawn over the standard scenes (T01-T03). */
    private static final int SIGNATURE = 4;
    private static final int ATMOSPHERE = 5;
    private static final int HORIZON = 6;

    /** Abstract art colours (original, muted). */
    private static final int[] ART = {0xFF3B4A6B, 0xFF6B3B4A, 0xFF3B6B5C, 0xFF6B5A3B, 0xFF4E3B6B,
            0xFF2F5F73, 0xFF5A6B3B, 0xFF733F2F};
    private static final int[] BADGES = {0xFF7FB2FF, 0xFF9BE58A, 0xFFF2C46B, 0xFFB79CFF};

    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();
    private final RectF r = new RectF();
    private final Path path = new Path();

    private HorizonLook look = HorizonLook.defaults();
    private int variant = STANDARD;
    private int scene = HOME;
    private HorizonLook previousLook;
    private int previousVariant;
    private int previousScene;
    private float mix = 1f;
    private ValueAnimator fade;

    // Frame metrics for the scene being drawn
    private float fl, ft, fw, fh, unit;

    public ThemePreviewView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public HorizonLook look() {
        return look;
    }

    public int scene() {
        return scene;
    }

    public void setLook(HorizonLook next, boolean animate) {
        if (next == null || next.equals(look)) {
            return;
        }
        startFade(animate);
        look = next;
        variant = variantOf(next);
        invalidate();
    }

    /** T09: Home's hero title as set (logo size in percent; a logo or the game's name in text). */
    private int heroLogo = 100;
    private boolean heroLogos = true;

    public void setHero(int logoPercent, boolean logos, boolean animate) {
        int p = Math.max(50, Math.min(200, logoPercent));
        if (p == heroLogo && logos == heroLogos) {
            return;
        }
        startFade(animate);
        heroLogo = p;
        heroLogos = logos;
        invalidate();
    }

    public void setScene(int next, boolean animate) {
        int s = Math.max(HOME, Math.min(CONTROL, next));
        if (s == scene) {
            return;
        }
        startFade(animate);
        scene = s;
        invalidate();
    }

    private void startFade(boolean animate) {
        if (fade != null) {
            fade.cancel();
            fade = null;
        }
        if (!animate) {
            previousLook = null;
            mix = 1f;
            return;
        }
        previousLook = look;
        previousVariant = variant;
        previousScene = scene;
        mix = 0f;
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(180);
        fade.addUpdateListener(a -> {
            mix = (float) a.getAnimatedValue();
            if (mix >= 1f) {
                previousLook = null;
            }
            invalidate();
        });
        fade.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (fade != null) {
            fade.cancel();
            fade = null;
        }
        previousLook = null;
        mix = 1f;
        super.onDetachedFromWindow();
    }

    private static int variantOf(HorizonLook l) {
        int i = HorizonThemeStudio.presetIndex(l);
        if (i < 0) {
            return STANDARD;
        }
        int name = PocketTheme.THEMES[i].name;
        return name == R.string.horizon_theme_pure ? PURE : name == R.string.horizon_theme_titanium ? TITANIUM
                : name == R.string.horizon_theme_legacy ? LEGACY : name == R.string.horizon_theme_signature ? SIGNATURE
                : name == R.string.horizon_theme_atmosphere ? ATMOSPHERE : name == R.string.horizon_theme_horizon ? HORIZON
                : STANDARD;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) {
            h = Math.round(w * 9f / 16f);
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float availW = getWidth() - getPaddingLeft() - getPaddingRight();
        float availH = getHeight() - getPaddingTop() - getPaddingBottom();
        if (availW <= 0 || availH <= 0) {
            return;
        }
        float w = Math.min(availW, availH * 16f / 9f);
        float h = w * 9f / 16f;
        float left = getPaddingLeft() + (availW - w) / 2f;
        // Top-aligned: the texts under the picture follow it closely on tall panes
        float top = getPaddingTop();
        frame.set(left, top, left + w, top + h);
        float radius = 14 * density;

        int save = canvas.save();
        path.reset();
        path.addRoundRect(frame, radius, radius, Path.Direction.CW);
        canvas.clipPath(path);
        if (previousLook != null && mix < 1f) {
            drawScene(canvas, previousLook, previousVariant, previousScene);
            int layer = canvas.saveLayerAlpha(frame, Math.round(255 * mix));
            drawScene(canvas, look, variant, scene);
            canvas.restoreToCount(layer);
        }
        else {
            drawScene(canvas, look, variant, scene);
        }
        canvas.restoreToCount(save);

        line.setShader(null);
        line.setStrokeWidth(Math.max(1f, density));
        line.setColor(0x40FFFFFF);
        canvas.drawRoundRect(frame, radius, radius, line);
    }

    // ---- Scenes ---------------------------------------------------------------------------

    private void drawScene(Canvas c, HorizonLook l, int v, int s) {
        fl = frame.left;
        ft = frame.top;
        fw = frame.width();
        fh = frame.height();
        // One preview unit is one dp of a 640 x 360 dp console screen
        unit = fh / 360f;
        if (v == LEGACY) {
            legacyScene(c, l, s);
            return;
        }
        // Signature, Atmosphere and Horizon are the standard scenes with their own layers
        family = v >= SIGNATURE ? v : STANDARD;
        int base = v >= SIGNATURE ? STANDARD : v;
        backdrop(c, l, base, s == HOME);
        if (family == SIGNATURE) letterbox(c, l, s);
        if (family == ATMOSPHERE) atmosphereLayers(c, l, s);
        status(c, l, base);
        if (s == LIBRARY) {
            library(c, l, base);
        }
        else if (s == CONTROL) {
            control(c, l, base);
        }
        else {
            home(c, l, base);
        }
        if (family == SIGNATURE) signatureMarks(c, l, s);
        if (family == HORIZON && s != CONTROL) streamCards(c, l, s);
    }

    /** The family being drawn (SIGNATURE, ATMOSPHERE, HORIZON), else STANDARD. */
    private int family = STANDARD;

    /** Where the shelf's cards start (their top edge), as shelfHome lays them out. */
    private float shelfTop(HorizonLook l) {
        float aspect = aspect(l.layout);
        float scale = HorizonAppearance.cardScale(l.density, true, 540f);
        float th = fh * (aspect < 1f ? .30f : aspect > 1.5f ? .19f : .24f) * scale;
        return y(.94f) - th;
    }

    // ---- Nexus Signature: letterbox bars, an accent rule, a widescreen still, a black slate ----

    private void letterbox(Canvas c, HorizonLook l, int s) {
        float topBar = ft + fh * .12f;
        float bottomBar = s == HOME ? shelfTop(l) - 16 * unit : y(.93f);
        float edge = 18 * unit;
        fill.setShader(null);
        fill.setColor(0xFF000000);
        c.drawRect(fl, ft, fl + fw, topBar, fill);
        c.drawRect(fl, bottomBar, fl + fw, ft + fh, fill);
        fill.setShader(new LinearGradient(0, topBar, 0, topBar + edge, 0xFF000000, 0, Shader.TileMode.CLAMP));
        c.drawRect(fl, topBar, fl + fw, topBar + edge, fill);
        fill.setShader(new LinearGradient(0, bottomBar, 0, bottomBar - edge, 0xFF000000, 0, Shader.TileMode.CLAMP));
        c.drawRect(fl, bottomBar - edge, fl + fw, bottomBar, fill);
        fill.setShader(null);
    }

    private void signatureMarks(Canvas c, HorizonLook l, int s) {
        if (s == HOME) {
            // The accent rule over the title
            bar(c, x(.06f), y(.28f) - 10 * unit, 28 * unit, 2 * unit, l.accent);
        }
        else if (s == LIBRARY) {
            // The detail art as a 2.39:1 still: black bands above and below it
            RectF pane = new RectF(x(.67f), y(.15f) + 30 * unit, x(.965f), y(.93f));
            float pad = 10 * unit;
            RectF a = new RectF(pane.left + pad, pane.top + pad, pane.right - pad, pane.top + pad + pane.height() * .36f);
            float still = a.width() / 2.39f;
            float band = Math.max(0, (a.height() - still) / 2f);
            fill.setShader(null);
            fill.setColor(0xFF000000);
            c.drawRect(a.left, a.top, a.right, a.top + band, fill);
            c.drawRect(a.left, a.bottom - band, a.right, a.bottom, fill);
        }
    }

    // ---- Nexus Atmosphere: glass layers over the art (a tray, a veil, panes) -----------------

    private void glass(Canvas c, RectF b, float radius) {
        fill.setShader(null);
        fill.setColor(0x29FFFFFF);
        c.drawRoundRect(b, radius, radius, fill);
        fill.setColor(0xFF000000);
        fill.setShader(new LinearGradient(b.left, b.top, b.left, b.top + b.height() * .4f, 0x1FFFFFFF, 0,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(b, radius, radius, fill);
        fill.setShader(null);
        stroke(c, b, radius, 0x4DFFFFFF, unit);
    }

    private void atmosphereLayers(Canvas c, HorizonLook l, int s) {
        if (s == HOME) {
            float tray = shelfTop(l) - 16 * unit;
            glass(c, new RectF(x(.02f), tray, x(.98f), y(.985f)), 14 * unit);
            glass(c, new RectF(x(.045f), y(.28f) - 12 * unit, x(.06f) + fw * .34f + 14 * unit, tray - 10 * unit),
                    14 * unit);
        }
        else if (s == LIBRARY) {
            glass(c, new RectF(x(.025f), y(.15f) + 22 * unit, x(.67f) - 6 * unit, y(.965f)), 14 * unit);
        }
    }

    // ---- Nexus Horizon: the host, session and network cards --------------------------------

    private void streamCards(Canvas c, HorizonLook l, int s) {
        float left, top, right, h;
        boolean slim = s != HOME;
        if (s == HOME) {
            left = x(.52f);
            right = x(.965f);
            h = fh * .15f;
            top = y(.92f) - h;
        }
        else {
            // The Library: one-line cards beside the source chips
            left = x(.47f);
            right = x(.965f);
            h = 18 * unit;
            top = y(.15f);
        }
        drawStreamCards(c, l, left, top, right, h, slim);
    }

    private void drawStreamCards(Canvas c, HorizonLook l, float left, float top, float right, float h, boolean slim) {
        float gap = 7 * unit;
        float w = (right - left - 2 * gap) / 3f;
        int[] lamps = {0xFF9CA3AF, 0xFF9CA3AF, 0xFF4ADE80};
        for (int i = 0; i < 3; i++) {
            float x0 = left + i * (w + gap);
            r.set(x0, top, x0 + w, top + h);
            fill.setShader(null);
            fill.setColor(0xD81A2432);
            float rad = Math.min(slim ? h / 2f : 12 * unit, h / 2f);
            c.drawRoundRect(r, rad, rad, fill);
            stroke(c, r, rad, i == 0 ? l.accent : 0x3DFFFFFF, (i == 0 ? 1.6f : 1f) * unit);
            fill.setColor(lamps[i]);
            if (slim) {
                c.drawCircle(x0 + 9 * unit, top + h / 2f, 2.5f * unit, fill);
                bar(c, x0 + 15 * unit, top + h / 2f - 2.5f * unit, w * .25f, 5 * unit, alpha(l.accent, 0xE6));
                bar(c, x0 + 15 * unit + w * .3f, top + h / 2f - 2.5f * unit, w * .38f, 5 * unit, 0xE6FFFFFF);
            }
            else {
                c.drawCircle(x0 + 11 * unit, top + 11 * unit, 3 * unit, fill);
                bar(c, x0 + 18 * unit, top + 8 * unit, w * .35f, 5 * unit, alpha(l.accent, 0xE6));
                bar(c, x0 + 9 * unit, top + h * .45f, w * .62f, 7 * unit, 0xF2FFFFFF);
                bar(c, x0 + 9 * unit, top + h * .72f, w * .45f, 5 * unit, 0x8CFFFFFF);
            }
        }
    }

    private float x(float f) {
        return fl + fw * f;
    }

    private float y(float f) {
        return ft + fh * f;
    }

    private void backdrop(Canvas c, HorizonLook l, int v, boolean home) {
        fill.setShader(null);
        if (v == TITANIUM) {
            fill.setColor(TitaniumStyle.BACKGROUND);
            c.drawRect(frame, fill);
            return;
        }
        if (v == PURE || "black".equals(l.backdrop)) {
            fill.setColor(0xFF000000);
            c.drawRect(frame, fill);
            return;
        }
        if ("glow".equals(l.backdrop)) {
            fill.setColor(0xFF07080C);
            c.drawRect(frame, fill);
            fill.setColor(0xFF000000);
            fill.setShader(new RadialGradient(x(.22f), y(.18f), fw * .75f,
                    alpha(l.accent, 0x66), alpha(l.accent, 0), Shader.TileMode.CLAMP));
            c.drawRect(frame, fill);
            fill.setShader(null);
            return;
        }
        if ("ambient".equals(l.backdrop)) {
            fill.setColor(0xFF000000);
            fill.setShader(new LinearGradient(fl, ft, fl + fw, ft + fh,
                    blend(art(l, 5), 0xFF000000, .35f), 0xFF0A0A0E, Shader.TileMode.CLAMP));
            c.drawRect(frame, fill);
            fill.setColor(0xFF000000);
            fill.setShader(new RadialGradient(x(.7f), y(.25f), fw * .6f,
                    alpha(blend(art(l, 4), l.accent, .3f), 0x55), 0, Shader.TileMode.CLAMP));
            c.drawRect(frame, fill);
            fill.setShader(null);
            return;
        }
        // Game art: an abstract landscape under a scrim, so text stays readable
        fill.setColor(0xFF000000);
        fill.setShader(new LinearGradient(fl, ft, fl, ft + fh * .7f,
                blend(art(l, 5), 0xFF000000, .1f), blend(art(l, 1), 0xFF000000, .35f), Shader.TileMode.CLAMP));
        c.drawRect(frame, fill);
        fill.setShader(null);
        fill.setColor(alpha(blend(art(l, 3), 0xFFFFFFFF, .3f), 0xCC));
        c.drawCircle(x(.72f), y(.33f), fh * .15f, fill);
        path.reset();
        path.moveTo(fl, y(.66f));
        path.lineTo(x(.18f), y(.46f));
        path.lineTo(x(.34f), y(.6f));
        path.lineTo(x(.55f), y(.4f));
        path.lineTo(x(.78f), y(.58f));
        path.lineTo(fl + fw, y(.5f));
        path.lineTo(fl + fw, ft + fh);
        path.lineTo(fl, ft + fh);
        path.close();
        fill.setColor(blend(art(l, 4), 0xFF000000, .4f));
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(fl, y(.8f));
        path.lineTo(x(.25f), y(.66f));
        path.lineTo(x(.48f), y(.78f));
        path.lineTo(x(.7f), y(.68f));
        path.lineTo(fl + fw, y(.8f));
        path.lineTo(fl + fw, ft + fh);
        path.lineTo(fl, ft + fh);
        path.close();
        fill.setColor(blend(art(l, 0), 0xFF000000, .6f));
        c.drawPath(path, fill);
        fill.setColor(0xFF000000);
        fill.setShader(new LinearGradient(fl, y(.35f), fl, ft + fh, 0, 0xE6000000, Shader.TileMode.CLAMP));
        c.drawRect(frame, fill);
        if (home) {
            fill.setColor(0xFF000000);
            fill.setShader(new LinearGradient(fl, ft, x(.6f), ft, 0xB3000000, 0, Shader.TileMode.CLAMP));
            c.drawRect(frame, fill);
        }
        fill.setShader(null);
    }

    private void status(Canvas c, HorizonLook l, int v) {
        // The Nexus mark: an N drawn in a rounded square
        float size = 18 * unit;
        float mx = x(.035f), my = y(.04f);
        line.setShader(null);
        line.setStrokeWidth(Math.max(1f, 1.6f * unit));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(v == TITANIUM ? TitaniumStyle.TEXT : 0xFFFFFFFF);
        r.set(mx, my, mx + size, my + size);
        c.drawRoundRect(r, 5 * unit, 5 * unit, line);
        float inset = size * .28f;
        path.reset();
        path.moveTo(mx + inset, my + size - inset);
        path.lineTo(mx + inset, my + inset);
        path.lineTo(mx + size - inset, my + size - inset);
        path.lineTo(mx + size - inset, my + inset);
        c.drawPath(path, line);
        // Clock and battery
        bar(c, x(.965f) - 64 * unit, my + 5 * unit, 30 * unit, 8 * unit, 0xB3FFFFFF);
        r.set(x(.965f) - 26 * unit, my + 4 * unit, x(.965f) - 4 * unit, my + 14 * unit);
        line.setStrokeWidth(Math.max(1f, unit));
        line.setColor(0x99FFFFFF);
        c.drawRoundRect(r, 2 * unit, 2 * unit, line);
        fill.setShader(null);
        fill.setColor(0xCCFFFFFF);
        r.set(r.left + 2 * unit, r.top + 2 * unit, r.left + 15 * unit, r.bottom - 2 * unit);
        c.drawRoundRect(r, unit, unit, fill);
        if (v == TITANIUM) {
            telemetry(c);
        }
    }

    private void telemetry(Canvas c) {
        float top = y(.115f), h = 26 * unit, gap = 8 * unit;
        float w = (fw * .6f - 2 * gap) / 3f;
        for (int i = 0; i < 3; i++) {
            float left = x(.035f) + i * (w + gap);
            r.set(left, top, left + w, top + h);
            surface(c, r, 5 * unit);
            bar(c, left + 7 * unit, top + 6 * unit, w * .38f, 5 * unit, 0x99ACBECA);
            bar(c, left + 7 * unit, top + 15 * unit, w * (.5f + .12f * i), 6 * unit, TitaniumStyle.TEXT);
        }
    }

    private void home(Canvas c, HorizonLook l, int v) {
        if (v == PURE) {
            pureHome(c, l);
            return;
        }
        if (v == TITANIUM) {
            titaniumHome(c, l);
            return;
        }
        switch (l.layout) {
            case "list":
                listHome(c, l);
                break;
            case "portal":
                portalHome(c, l);
                break;
            case "grid_covers":
            case "grid_tiles":
            case "grid_wide":
            case "horizon_workspace":
                gridHome(c, l);
                break;
            default:
                shelfHome(c, l);
                break;
        }
    }

    private void shelfHome(Canvas c, HorizonLook l) {
        heroText(c, l, x(.06f), y(.28f));
        float aspect = aspect(l.layout);
        float scale = HorizonAppearance.cardScale(l.density, true, 540f);
        float th = fh * (aspect < 1f ? .30f : aspect > 1.5f ? .19f : .24f) * scale;
        float tw = th * aspect;
        float gap = 9 * unit;
        float bottom = y(.94f);
        float radius = radius(l, Math.min(tw, th));
        int count = 0;
        for (float left = x(.06f); left + tw <= x(.97f) && count < 9; left += tw + gap) {
            count++;
        }
        for (int i = count - 1; i >= 0; i--) {
            float left = x(.06f) + i * (tw + gap);
            r.set(left, bottom - th, left + tw, bottom);
            card(c, l, r, radius, i, i == 0);
        }
    }

    private void gridHome(Canvas c, HorizonLook l) {
        boolean workspace = "horizon_workspace".equals(l.layout);
        float aspect = aspect(l.layout);
        RectF pane = new RectF(x(workspace ? .6f : .66f), y(.16f), x(.965f), y(.93f));
        float gridLeft = x(.035f), gridRight = pane.left - 12 * unit, top = y(.16f), bottom = y(.95f);
        int cols = aspect < 1f ? 5 : aspect > 1.5f ? 2 : 4;
        if (l.density == 1) {
            cols++;
        }
        else if (l.density == 2) {
            cols = Math.max(2, cols - 1);
        }
        float gap = 8 * unit;
        float tw = (gridRight - gridLeft - gap * (cols - 1)) / cols;
        float th = tw / aspect;
        float label = workspace ? 14 * unit : 0;
        float radius = radius(l, Math.min(tw, th));
        int rows = Math.max(1, (int) ((bottom - top + gap) / (th + label + gap)));
        for (int i = rows * cols - 1; i >= 0; i--) {
            int row = i / cols, col = i % cols;
            float left = gridLeft + col * (tw + gap);
            float t = top + row * (th + label + gap);
            r.set(left, t, left + tw, t + th);
            card(c, l, r, radius, i, i == 0);
            if (workspace) {
                bar(c, left + 2 * unit, t + th + 5 * unit, tw * (i == 0 ? .8f : .6f), 5 * unit,
                        i == 0 ? 0xE6FFFFFF : 0x66FFFFFF);
            }
        }
        detailPane(c, l, pane);
    }

    private void listHome(Canvas c, HorizonLook l) {
        float rowH = fh * .115f, top = y(.16f), left = x(.04f), right = x(.5f);
        for (int i = 0; i < 6 && top + rowH <= y(.97f); i++) {
            float icon = rowH * .78f;
            float t = top + i * rowH;
            boolean focused = i == 1;
            if (focused) {
                r.set(left - 6 * unit, t - 2 * unit, right, t + rowH - 2 * unit);
                float rad = radius(l, rowH);
                fill.setShader(null);
                fill.setColor(alpha(l.accent, 0x2E));
                c.drawRoundRect(r, rad, rad, fill);
                if (ring(l)) {
                    stroke(c, r, rad, l.accent, 2 * unit);
                }
            }
            r.set(left, t + (rowH - icon) / 2f - 2 * unit, left + icon, t + (rowH + icon) / 2f - 2 * unit);
            art(c, l, r, radius(l, icon) * .6f, i);
            bar(c, r.right + 10 * unit, t + rowH * .32f, (right - r.right) * (focused ? .7f : .5f), 7 * unit,
                    focused ? 0xF2FFFFFF : 0x8CFFFFFF);
            bar(c, r.right + 10 * unit, t + rowH * .58f, (right - r.right) * .3f, 5 * unit, 0x4DFFFFFF);
        }
        heroText(c, l, x(.58f), y(.52f));
    }

    private void portalHome(Canvas c, HorizonLook l) {
        float big = fh * .17f, small = fh * .12f, gap = 8 * unit, top = y(.15f);
        float left = x(.05f);
        float radius = radius(l, small);
        float label = fw * .16f;
        float sx = left + big + gap + label;
        int index = 1;
        for (float x0 = sx; x0 + small <= x(.97f); x0 += small + gap) {
            r.set(x0, top + (big - small) / 2f, x0 + small, top + (big + small) / 2f);
            card(c, l, r, radius, index++, false);
        }
        bar(c, left + big + gap + 4 * unit, top + big / 2f - 4 * unit, label * .8f, 8 * unit, 0xE6FFFFFF);
        r.set(left, top, left + big, top + big);
        card(c, l, r, radius(l, big), 0, true);
        bar(c, x(.06f), y(.62f), fw * .34f, fh * .065f, 0xF2FFFFFF);
        bar(c, x(.06f), y(.72f), fw * .2f, fh * .03f, 0x8CFFFFFF);
        play(c, l, x(.06f), y(.8f), fw * .13f, fh * .08f);
    }

    private void pureHome(Canvas c, HorizonLook l) {
        // Text-led: a list of names, no art, thin white lines, Quick Access along the bottom
        float rowH = fh * .1f, top = y(.16f), left = x(.05f), right = x(.52f);
        for (int i = 0; i < 6; i++) {
            float t = top + i * rowH;
            if (i == 0) {
                r.set(left - 6 * unit, t, right, t + rowH - 4 * unit);
                stroke(c, r, radius(l, rowH), 0xFFFFFFFF, 1.6f * unit);
            }
            bar(c, left + 4 * unit, t + rowH * .32f, (right - left) * (i == 0 ? .62f : .45f - .03f * i),
                    7 * unit, i == 0 ? 0xFFFFFFFF : 0x99FFFFFF);
        }
        bar(c, x(.6f), y(.24f), fw * .3f, fh * .06f, 0xFFFFFFFF);
        bar(c, x(.6f), y(.34f), fw * .2f, fh * .028f, 0x8CFFFFFF);
        r.set(x(.6f), y(.42f), x(.6f) + fw * .13f, y(.42f) + fh * .075f);
        stroke(c, r, r.height() / 2f, 0xFFFFFFFF, 1.4f * unit);
        triangle(c, r.left + r.height() * .55f, r.centerY(), r.height() * .32f, 0xFFFFFFFF);
        float size = 30 * unit, gap = 12 * unit;
        for (int i = 0; i < 5; i++) {
            float x0 = x(.05f) + i * (size + gap);
            r.set(x0, y(.82f), x0 + size, y(.82f) + size);
            stroke(c, r, size / 2f, 0x80FFFFFF, unit);
            fill.setShader(null);
            fill.setColor(0xB3FFFFFF);
            c.drawCircle(r.centerX(), r.centerY(), 3 * unit, fill);
        }
    }

    private void titaniumHome(Canvas c, HorizonLook l) {
        float top = y(.25f), left = x(.035f), right = x(.5f), gap = 7 * unit;
        float cw = (right - left - gap) / 2f, ch = 40 * unit;
        for (int i = 0; i < 8; i++) {
            int row = i / 2, col = i % 2;
            float x0 = left + col * (cw + gap), t = top + row * (ch + gap);
            if (t + ch > y(.97f)) {
                break;
            }
            r.set(x0, t, x0 + cw, t + ch);
            surface(c, r, 5 * unit);
            if (i == 0) {
                stroke(c, r, 5 * unit, l.accent, 1.6f * unit);
            }
            RectF a = new RectF(x0 + 6 * unit, t + 6 * unit, x0 + 6 * unit + ch - 12 * unit, t + ch - 6 * unit);
            art(c, l, a, 3 * unit, i);
            bar(c, a.right + 7 * unit, t + 11 * unit, cw * .45f, 6 * unit, TitaniumStyle.TEXT);
            bar(c, a.right + 7 * unit, t + 22 * unit, cw * .3f, 5 * unit, 0x99ACBECA);
        }
        RectF pane = new RectF(x(.53f), top, x(.965f), y(.93f));
        surface(c, pane, 5 * unit);
        bar(c, pane.left + 12 * unit, pane.top + 14 * unit, pane.width() * .6f, 10 * unit, TitaniumStyle.TEXT);
        for (int i = 0; i < 3; i++) {
            float t = pane.top + 36 * unit + i * 16 * unit;
            bar(c, pane.left + 12 * unit, t, pane.width() * .3f, 5 * unit, 0x99ACBECA);
            bar(c, pane.left + pane.width() * .5f, t, pane.width() * .35f, 5 * unit, TitaniumStyle.TEXT);
        }
        play(c, l, pane.left + 12 * unit, pane.bottom - 36 * unit, pane.width() * .32f, 24 * unit);
    }

    private void library(Canvas c, HorizonLook l, int v) {
        // Source chips along the top
        float chipTop = v == TITANIUM ? y(.25f) : y(.15f);
        float chipX = x(.035f);
        for (int i = 0; i < 4; i++) {
            float w = (i == 0 ? 46 : 54 + 6 * i) * unit, h = 18 * unit;
            r.set(chipX, chipTop, chipX + w, chipTop + h);
            if (i == 0) {
                if (v == PURE) {
                    stroke(c, r, h / 2f, 0xFFFFFFFF, 1.4f * unit);
                }
                else {
                    fill.setShader(null);
                    fill.setColor(v == TITANIUM ? TitaniumStyle.TEXT : l.accent);
                    c.drawRoundRect(r, h / 2f, h / 2f, fill);
                }
                bar(c, r.left + 10 * unit, r.centerY() - 2.5f * unit, w - 20 * unit, 5 * unit,
                        v == PURE ? 0xFFFFFFFF : 0xE6101014);
            }
            else {
                stroke(c, r, h / 2f, 0x4DFFFFFF, unit);
                bar(c, r.left + 10 * unit, r.centerY() - 2.5f * unit, w - 20 * unit, 5 * unit, 0x80FFFFFF);
            }
            chipX += w + 7 * unit;
        }
        float top = chipTop + 30 * unit;
        RectF pane = new RectF(x(.67f), top, x(.965f), y(.93f));
        if (v == PURE || v == TITANIUM) {
            // Text-led rows in two columns
            float left = x(.035f), right = pane.left - 12 * unit, gap = 7 * unit;
            float cw = (right - left - gap) / 2f, rh = 30 * unit;
            for (int i = 0; i < 12; i++) {
                int row = i / 2, col = i % 2;
                float x0 = left + col * (cw + gap), t = top + row * (rh + gap);
                if (t + rh > y(.97f)) {
                    break;
                }
                r.set(x0, t, x0 + cw, t + rh);
                if (v == TITANIUM) {
                    surface(c, r, 5 * unit);
                }
                if (i == 0) {
                    stroke(c, r, v == TITANIUM ? 5 * unit : rh / 2f, v == TITANIUM ? l.accent : 0xFFFFFFFF,
                            1.5f * unit);
                }
                bar(c, x0 + 10 * unit, t + rh / 2f - 3 * unit, cw * (i == 0 ? .6f : .45f), 6 * unit,
                        i == 0 ? 0xFFFFFFFF : 0x99FFFFFF);
            }
            if (v == TITANIUM) {
                surface(c, pane, 5 * unit);
            }
            else {
                stroke(c, pane, 8 * unit, 0x59FFFFFF, unit);
            }
            bar(c, pane.left + 12 * unit, pane.top + 14 * unit, pane.width() * .6f, 9 * unit, 0xFFFFFFFF);
            bar(c, pane.left + 12 * unit, pane.top + 32 * unit, pane.width() * .4f, 5 * unit, 0x8CFFFFFF);
            play(c, l, pane.left + 12 * unit, pane.bottom - 34 * unit, pane.width() * .36f, 22 * unit);
            return;
        }
        int cols = 5 + (l.density == 1 ? 1 : l.density == 2 ? -1 : 0);
        float left = x(.035f), right = pane.left - 12 * unit, gap = 8 * unit;
        float tw = (right - left - gap * (cols - 1)) / cols, th = tw * 1.5f;
        float radius = radius(l, tw);
        int rows = Math.max(1, (int) ((y(.97f) - top + gap) / (th + gap)));
        for (int i = rows * cols - 1; i >= 0; i--) {
            int row = i / cols, col = i % cols;
            float x0 = left + col * (tw + gap), t = top + row * (th + gap);
            r.set(x0, t, x0 + tw, t + th);
            card(c, l, r, radius, i + 2, i == 0);
            fill.setShader(null);
            fill.setColor(BADGES[i % BADGES.length]);
            c.drawCircle(x0 + 7 * unit, t + 7 * unit, 3 * unit, fill);
        }
        detailPane(c, l, pane);
    }

    private void control(Canvas c, HorizonLook l, int v) {
        fill.setShader(null);
        fill.setColor(v == STANDARD ? 0x99000000 : 0x00000000);
        c.drawRect(frame, fill);
        RectF panel = v == STANDARD ? new RectF(x(.1f), y(.13f), x(.9f), y(.92f))
                : new RectF(x(.035f), y(v == TITANIUM ? .25f : .14f), x(.965f), y(.93f));
        if (v == TITANIUM) {
            surface(c, panel, 6 * unit);
        }
        else if (v == PURE) {
            stroke(c, panel, 10 * unit, 0x59FFFFFF, unit);
        }
        else if (family == SIGNATURE) {
            // A near-black slate with an accent hairline
            fill.setShader(null);
            fill.setColor(0xF5050505);
            c.drawRoundRect(panel, 6 * unit, 6 * unit, fill);
            stroke(c, panel, 6 * unit, alpha(l.accent, 0x80), unit);
        }
        else if (family == ATMOSPHERE) {
            // A lighter, layered pane: see-through, a light from above and a bright rim
            fill.setShader(null);
            fill.setColor(0xB8222B3A);
            c.drawRoundRect(panel, 22 * unit, 22 * unit, fill);
            fill.setColor(0xFF000000);
            fill.setShader(new LinearGradient(panel.left, panel.top, panel.left, panel.top + panel.height() * .4f,
                    0x1FFFFFFF, 0, Shader.TileMode.CLAMP));
            c.drawRoundRect(panel, 22 * unit, 22 * unit, fill);
            fill.setShader(null);
            stroke(c, panel, 22 * unit, 0x5CFFFFFF, unit);
        }
        else {
            panel(c, l, panel, radius(l, 80 * unit));
        }
        float pad = 14 * unit;
        bar(c, panel.left + pad, panel.top + pad, panel.width() * .22f, 9 * unit, 0xF2FFFFFF);
        bar(c, panel.left + pad, panel.top + pad + 15 * unit, panel.width() * .12f, 5 * unit, 0x66FFFFFF);
        boolean twoColumns = v != STANDARD;
        float areaRight = twoColumns ? panel.centerX() - 6 * unit : panel.right - pad;
        int cols = 4;
        float gap = 8 * unit;
        float tileW = (areaRight - panel.left - pad - gap * (cols - 1)) / cols;
        float tileH = Math.min(tileW * .8f, 46 * unit);
        float top = panel.top + pad + 30 * unit;
        if (family == HORIZON) {
            // Nexus Horizon: the stream cards under the title, the tiles below them
            drawStreamCards(c, l, panel.left + pad, top - 4 * unit, panel.right - pad, 18 * unit, true);
            top += 24 * unit;
        }
        float rad = v == STANDARD ? radius(l, tileH) : v == TITANIUM ? 5 * unit : tileH / 2f;
        for (int i = 0; i < 8; i++) {
            int row = i / cols, col = i % cols;
            float x0 = panel.left + pad + col * (tileW + gap), t = top + row * (tileH + gap);
            r.set(x0, t, x0 + tileW, t + tileH);
            boolean focused = i == 0;
            fill.setShader(null);
            if (v == PURE) {
                stroke(c, r, rad, focused ? 0xFFFFFFFF : 0x59FFFFFF, (focused ? 1.6f : 1f) * unit);
            }
            else {
                fill.setColor(focused ? (v == TITANIUM ? 0xFF2C3A44 : l.accent)
                        : v == TITANIUM ? 0xFF1A242B : 0x1FFFFFFF);
                c.drawRoundRect(r, rad, rad, fill);
                if (focused && (v == TITANIUM || ring(l))) {
                    stroke(c, r, rad, v == TITANIUM ? l.accent : blend(l.accent, 0xFFFFFFFF, .5f), 1.4f * unit);
                }
            }
            fill.setColor(focused && v == STANDARD ? 0xE6101014 : 0xB3FFFFFF);
            c.drawCircle(r.centerX(), r.centerY() - 3 * unit, 4 * unit, fill);
            bar(c, r.centerX() - tileW * .25f, r.bottom - 10 * unit, tileW * .5f, 4 * unit,
                    focused && v == STANDARD ? 0xB3101014 : 0x66FFFFFF);
        }
        float sliderTop = top + 2 * (tileH + gap) + 6 * unit;
        float sliderLeft = twoColumns ? panel.centerX() + 6 * unit : panel.left + pad;
        if (twoColumns) {
            sliderTop = top;
        }
        float sliderRight = panel.right - pad;
        for (int i = 0; i < 2; i++) {
            float t = sliderTop + i * 26 * unit;
            if (t + 12 * unit > panel.bottom - pad) {
                break;
            }
            fill.setShader(null);
            fill.setColor(0xB3FFFFFF);
            c.drawCircle(sliderLeft + 6 * unit, t + 6 * unit, 4 * unit, fill);
            float trackLeft = sliderLeft + 20 * unit;
            bar(c, trackLeft, t + 4 * unit, sliderRight - trackLeft, 4 * unit, 0x33FFFFFF);
            float value = i == 0 ? .62f : .4f;
            int tint = v == PURE ? 0xFFFFFFFF : v == TITANIUM ? TitaniumStyle.TEXT : l.accent;
            bar(c, trackLeft, t + 4 * unit, (sliderRight - trackLeft) * value, 4 * unit, tint);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(trackLeft + (sliderRight - trackLeft) * value, t + 6 * unit, 5 * unit, fill);
        }
        if (v == TITANIUM) {
            for (int i = 0; i < 3; i++) {
                float t = sliderTop + 64 * unit + i * 16 * unit;
                if (t > panel.bottom - pad) {
                    break;
                }
                bar(c, sliderLeft, t, (sliderRight - sliderLeft) * .35f, 5 * unit, 0x99ACBECA);
                bar(c, sliderLeft + (sliderRight - sliderLeft) * .55f, t, (sliderRight - sliderLeft) * .3f,
                        5 * unit, TitaniumStyle.TEXT);
            }
        }
    }

    // ---- Nexus Legacy: drawn with the real Legacy frames, pixel mark and scanlines ----------

    private android.graphics.drawable.Drawable legacyDialog, legacyCard, legacyCardFocused, legacyScan;

    private void legacyScene(Canvas c, HorizonLook l, int s) {
        if (legacyDialog == null) {
            legacyDialog = LegacyStyle.dialog(getContext());
            legacyCard = LegacyStyle.card(getContext(), false);
            legacyCardFocused = LegacyStyle.card(getContext(), true);
            legacyScan = LegacyStyle.scanlines(getContext());
        }
        fill.setShader(null);
        fill.setColor(LegacyStyle.BACKGROUND);
        c.drawRect(frame, fill);
        pixelMark(c, x(.035f), y(.04f), 18 * unit);
        block(c, x(.965f) - 64 * unit, y(.04f) + 5 * unit, 30 * unit, 8 * unit, LegacyStyle.DIM);
        block(c, x(.965f) - 26 * unit, y(.04f) + 4 * unit, 22 * unit, 10 * unit, LegacyStyle.DIM);
        if (s == LIBRARY) {
            legacyLibrary(c, l);
        }
        else if (s == CONTROL) {
            legacyControl(c);
        }
        else {
            legacyHome(c, l);
        }
        legacyScan.setBounds(Math.round(frame.left), Math.round(frame.top), Math.round(frame.right),
                Math.round(frame.bottom));
        legacyScan.draw(c);
    }

    private void legacyHome(Canvas c, HorizonLook l) {
        float top = y(.16f), bottom = y(.80f), left = x(.035f), right = x(.56f), gap = 8 * unit;
        int cols = 4;
        float tw = (right - left - gap * (cols - 1)) / cols, th = tw * 1.5f;
        for (int i = 0; i < cols * 2; i++) {
            float x0 = left + (i % cols) * (tw + gap), t = top + (i / cols) * (th + gap);
            if (t + th > bottom) {
                break;
            }
            legacyCover(c, l, new RectF(x0, t, x0 + tw, t + th), i, i == 0);
        }
        RectF panel = new RectF(x(.59f), top, x(.965f), bottom);
        legacyPanel(c, panel);
        float px = panel.left + 16 * unit, pw = panel.width() - 32 * unit;
        block(c, px, panel.top + 18 * unit, pw * .45f, 6 * unit, LegacyStyle.GOLD);
        block(c, px, panel.top + 32 * unit, pw * .8f, 10 * unit, LegacyStyle.INK);
        block(c, px, panel.top + 50 * unit, pw * .5f, 6 * unit, LegacyStyle.DIM);
        block(c, px, panel.top + 62 * unit, pw * .35f, 6 * unit, LegacyStyle.DIM);
        legacyButton(c, px, panel.bottom - 40 * unit, pw * .45f, 22 * unit);
        float size = 26 * unit;
        for (int i = 0; i < 6; i++) {
            float x0 = x(.035f) + i * (size + 10 * unit);
            RectF r0 = new RectF(x0, y(.84f), x0 + size, y(.84f) + size);
            drawFrame(c, legacyCard, r0);
            block(c, r0.centerX() - 3 * unit, r0.centerY() - 3 * unit, 6 * unit, 6 * unit, LegacyStyle.DIM);
        }
    }

    private void legacyLibrary(Canvas c, HorizonLook l) {
        block(c, x(.08f), y(.05f), fw * .22f, 9 * unit, LegacyStyle.GOLD);
        float chipX = x(.035f), chipTop = y(.15f);
        for (int i = 0; i < 4; i++) {
            float w = (i == 0 ? 40 : 52) * unit, h = 16 * unit;
            if (i == 0) {
                block(c, chipX, chipTop, w, h, LegacyStyle.GOLD);
            }
            else {
                drawFrame(c, legacyCard, new RectF(chipX, chipTop, chipX + w, chipTop + h));
            }
            chipX += w + 6 * unit;
        }
        float top = chipTop + 28 * unit;
        RectF detail = new RectF(x(.035f), top, x(.33f), y(.95f));
        legacyPanel(c, detail);
        float px = detail.left + 14 * unit, pw = detail.width() - 28 * unit;
        block(c, px, detail.top + 16 * unit, pw * .6f, 6 * unit, LegacyStyle.GOLD);
        RectF cover = new RectF(px, detail.top + 28 * unit, px + pw, detail.top + 28 * unit + detail.height() * .3f);
        art(c, l, cover, 0, 0);
        block(c, px, cover.bottom + 10 * unit, pw * .8f, 9 * unit, LegacyStyle.INK);
        block(c, px, cover.bottom + 26 * unit, pw * .5f, 6 * unit, LegacyStyle.DIM);
        float left = x(.36f), right = x(.965f), gap = 8 * unit;
        int cols = 5;
        float tw = (right - left - gap * (cols - 1)) / cols, th = tw * 1.5f;
        for (int i = 0; i < cols * 2; i++) {
            float x0 = left + (i % cols) * (tw + gap), t = top + (i / cols) * (th + gap);
            if (t + th > y(.97f)) {
                break;
            }
            legacyCover(c, l, new RectF(x0, t, x0 + tw, t + th), i + 2, i == 0);
        }
    }

    private void legacyControl(Canvas c) {
        RectF panel = new RectF(x(.1f), y(.13f), x(.9f), y(.92f));
        legacyPanel(c, panel);
        float pad = 20 * unit;
        block(c, panel.left + pad, panel.top + pad, panel.width() * .22f, 8 * unit, LegacyStyle.GOLD);
        int cols = 4;
        float gap = 8 * unit;
        float tileW = (panel.width() - 2 * pad - gap * (cols - 1)) / cols;
        float tileH = Math.min(tileW * .8f, 46 * unit);
        float top = panel.top + pad + 22 * unit;
        for (int i = 0; i < 8; i++) {
            float x0 = panel.left + pad + (i % cols) * (tileW + gap), t = top + (i / cols) * (tileH + gap);
            RectF tile = new RectF(x0, t, x0 + tileW, t + tileH);
            if (i == 0) {
                block(c, tile.left, tile.top, tile.width(), tile.height(), LegacyStyle.GOLD);
            }
            drawFrame(c, i == 0 ? legacyCardFocused : legacyCard, tile);
            block(c, tile.centerX() - 4 * unit, tile.centerY() - 6 * unit, 8 * unit, 8 * unit,
                    i == 0 ? LegacyStyle.BACKGROUND : LegacyStyle.DIM);
        }
        float sliderTop = top + 2 * (tileH + gap) + 8 * unit;
        for (int i = 0; i < 2; i++) {
            float t = sliderTop + i * 24 * unit;
            if (t + 10 * unit > panel.bottom - pad) {
                break;
            }
            float trackLeft = panel.left + pad + 18 * unit, trackRight = panel.right - pad;
            block(c, panel.left + pad, t, 8 * unit, 8 * unit, LegacyStyle.DIM);
            block(c, trackLeft, t + 2 * unit, trackRight - trackLeft, 4 * unit, 0x40F3E7C4);
            block(c, trackLeft, t + 2 * unit, (trackRight - trackLeft) * (i == 0 ? .62f : .4f), 4 * unit,
                    LegacyStyle.GOLD);
        }
    }

    private void legacyCover(Canvas c, HorizonLook l, RectF box, int index, boolean focused) {
        art(c, l, box, 0, index);
        if (!focused) {
            fill.setShader(null);
            fill.setColor(0x40000000);
            c.drawRect(box, fill);
        }
        drawFrame(c, focused ? legacyCardFocused : legacyCard, box);
    }

    private void legacyPanel(Canvas c, RectF r0) {
        drawFrame(c, legacyDialog, r0);
    }

    private void legacyButton(Canvas c, float left, float top, float w, float h) {
        block(c, left, top, w, h, LegacyStyle.GOLD);
        path.reset();
        float cx = left + h * .55f, cy = top + h / 2f, size = h * .3f;
        path.moveTo(cx - size * .45f, cy - size * .6f);
        path.lineTo(cx + size * .6f, cy);
        path.lineTo(cx - size * .45f, cy + size * .6f);
        path.close();
        fill.setColor(LegacyStyle.BACKGROUND);
        c.drawPath(path, fill);
        block(c, left + h * 1.05f, cy - h * .1f, w - h * 1.5f, h * .2f, LegacyStyle.BACKGROUND);
    }

    /** The N mark's pixel variant: two pillars and a stepped diagonal on a 12 x 12 grid. */
    private void pixelMark(Canvas c, float left, float top, float size) {
        float cell = size / 12f;
        block(c, left + cell, top + cell, 2 * cell, 10 * cell, LegacyStyle.GOLD);
        block(c, left + 9 * cell, top + cell, 2 * cell, 10 * cell, LegacyStyle.GOLD);
        int[][] diagonal = {{1, 3}, {2, 3}, {2, 4}, {3, 4}, {4, 4}, {4, 5}, {5, 5}, {6, 5}, {6, 6}, {7, 6},
                {8, 6}, {8, 7}, {9, 7}, {9, 8}, {10, 8}};
        for (int[] cellAt : diagonal) {
            block(c, left + cellAt[1] * cell, top + cellAt[0] * cell, cell, cell, 0xB3E0C98A);
        }
    }

    private void drawFrame(Canvas c, android.graphics.drawable.Drawable d, RectF r0) {
        d.setBounds(Math.round(r0.left), Math.round(r0.top), Math.round(r0.right), Math.round(r0.bottom));
        d.draw(c);
    }

    private void block(Canvas c, float left, float top, float w, float h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        fill.setShader(null);
        fill.setColor(color);
        c.drawRect(left, top, left + w, top + h, fill);
    }

    // ---- Pieces ---------------------------------------------------------------------------

    private void heroText(Canvas c, HorizonLook l, float left, float top) {
        if (heroLogos) {
            // The logo, at its set size (kept inside the screen), growing upward from its baseline
            float k = heroLogo / 100f;
            float w = Math.min(fw * .34f * k, fw * .96f - left);
            float h = fh * .065f * k;
            bar(c, left, top + fh * .065f - h, w, h, 0xF2FFFFFF);
        } else {
            // The name in plain type: a thinner, longer line
            bar(c, left, top + fh * .02f, fw * .3f, fh * .04f, 0xF2FFFFFF);
        }
        bar(c, left, top + fh * .1f, fw * .22f, fh * .03f, 0x8CFFFFFF);
        bar(c, left, top + fh * .145f, fw * .16f, fh * .03f, 0x59FFFFFF);
        play(c, l, left, top + fh * .21f, fw * .13f, fh * .08f);
    }

    private void detailPane(Canvas c, HorizonLook l, RectF pane) {
        panel(c, l, pane, radius(l, 80 * unit));
        float pad = 10 * unit;
        RectF a = new RectF(pane.left + pad, pane.top + pad, pane.right - pad, pane.top + pad + pane.height() * .36f);
        art(c, l, a, radius(l, a.height()) * .7f, 0);
        bar(c, a.left, a.bottom + 10 * unit, pane.width() * .62f, 9 * unit, 0xF2FFFFFF);
        bar(c, a.left, a.bottom + 26 * unit, pane.width() * .4f, 5 * unit, 0x8CFFFFFF);
        float badge = a.left;
        for (int i = 0; i < 3; i++) {
            r.set(badge, a.bottom + 38 * unit, badge + 26 * unit, a.bottom + 48 * unit);
            stroke(c, r, 5 * unit, 0x59FFFFFF, unit);
            badge += 32 * unit;
        }
        play(c, l, a.left, pane.bottom - pad - 22 * unit, pane.width() * .4f, 22 * unit);
    }

    private void card(Canvas c, HorizonLook l, RectF box, float radius, int index, boolean focused) {
        RectF b = new RectF(box);
        if (focused && zoom(l)) {
            float dx = b.width() * .06f, dy = b.height() * .06f;
            b.inset(-dx, -dy);
            // A soft lift under the card
            fill.setShader(null);
            fill.setColor(0x66000000);
            RectF shadow = new RectF(b.left + 2 * unit, b.top + 6 * unit, b.right - 2 * unit, b.bottom + 6 * unit);
            c.drawRoundRect(shadow, radius, radius, fill);
        }
        art(c, l, b, radius, index);
        if (focused && ring(l)) {
            float out = 3 * unit;
            RectF ringRect = new RectF(b.left - out, b.top - out, b.right + out, b.bottom + out);
            stroke(c, ringRect, radius + out, l.accent, 2.2f * unit);
        }
        else if (!focused) {
            fill.setShader(null);
            fill.setColor(0x33000000);
            c.drawRoundRect(b, radius, radius, fill);
        }
    }

    private void art(Canvas c, HorizonLook l, RectF b, float radius, int index) {
        int base = art(l, index);
        fill.setColor(0xFF000000);
        fill.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                blend(base, 0xFFFFFFFF, .18f), blend(base, 0xFF000000, .45f), Shader.TileMode.CLAMP));
        c.drawRoundRect(b, radius, radius, fill);
        fill.setShader(null);
        int save = c.save();
        path.reset();
        path.addRoundRect(b, radius, radius, Path.Direction.CW);
        c.clipPath(path);
        fill.setColor(alpha(blend(art(l, index + 3), 0xFFFFFFFF, .35f), 0x99));
        c.drawCircle(b.left + b.width() * .7f, b.top + b.height() * .35f, Math.min(b.width(), b.height()) * .22f, fill);
        fill.setColor(0x40000000);
        path.reset();
        path.moveTo(b.left, b.bottom);
        path.lineTo(b.left, b.top + b.height() * .7f);
        path.lineTo(b.left + b.width() * .45f, b.top + b.height() * .55f);
        path.lineTo(b.right, b.top + b.height() * .78f);
        path.lineTo(b.right, b.bottom);
        path.close();
        c.drawPath(path, fill);
        c.restoreToCount(save);
    }

    private void panel(Canvas c, HorizonLook l, RectF b, float radius) {
        fill.setShader(null);
        fill.setColor(l.frost() ? 0xD9161920 : 0x2EFFFFFF);
        c.drawRoundRect(b, radius, radius, fill);
        if (l.liveGlass) {
            fill.setColor(0xFF000000);
            fill.setShader(new LinearGradient(b.left, b.top, b.left, b.top + b.height() * .4f,
                    0x26FFFFFF, 0, Shader.TileMode.CLAMP));
            c.drawRoundRect(b, radius, radius, fill);
            fill.setShader(null);
        }
        stroke(c, b, radius, l.frost() ? 0x1FFFFFFF : 0x4DFFFFFF, unit);
    }

    private void surface(Canvas c, RectF b, float radius) {
        fill.setShader(null);
        fill.setColor(0xFF202B33);
        c.drawRoundRect(b, radius, radius, fill);
        stroke(c, b, radius, 0xFF50636F, Math.max(1f, unit * .8f));
    }

    private void play(Canvas c, HorizonLook l, float left, float top, float w, float h) {
        r.set(left, top, left + w, top + h);
        fill.setShader(null);
        fill.setColor(l.accent);
        c.drawRoundRect(r, h / 2f, h / 2f, fill);
        triangle(c, left + h * .55f, top + h / 2f, h * .3f, 0xFF101014);
        bar(c, left + h * 1.05f, top + h / 2f - h * .1f, w - h * 1.5f, h * .2f, 0xCC101014);
    }

    private void triangle(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx - size * .45f, cy - size * .6f);
        path.lineTo(cx + size * .6f, cy);
        path.lineTo(cx - size * .45f, cy + size * .6f);
        path.close();
        fill.setShader(null);
        fill.setColor(color);
        c.drawPath(path, fill);
    }

    private void bar(Canvas c, float left, float top, float w, float h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        RectF b = new RectF(left, top, left + w, top + h);
        fill.setShader(null);
        fill.setColor(color);
        c.drawRoundRect(b, h / 2f, h / 2f, fill);
    }

    private void stroke(Canvas c, RectF b, float radius, int color, float width) {
        line.setShader(null);
        line.setColor(color);
        line.setStrokeWidth(Math.max(1f, width));
        c.drawRoundRect(b, radius, radius, line);
    }

    // ---- Values ---------------------------------------------------------------------------

    private float radius(HorizonLook l, float shortest) {
        float dp = HorizonAppearance.radius(l.corners, 10f, shortest / unit);
        return dp * unit;
    }

    private static boolean ring(HorizonLook l) {
        return !"zoom".equals(l.focus);
    }

    private static boolean zoom(HorizonLook l) {
        return !"ring".equals(l.focus);
    }

    static float aspect(String layout) {
        if (layout.contains("covers")) {
            return 0.67f;
        }
        if (layout.contains("wide")) {
            return 1.78f;
        }
        return 1f;
    }

    private static int art(HorizonLook l, int index) {
        int color = ART[Math.abs(index) % ART.length];
        return l.colorArt ? color : gray(color);
    }

    static int gray(int color) {
        int y = (((color >> 16) & 255) * 30 + ((color >> 8) & 255) * 59 + (color & 255) * 11) / 100;
        return 0xFF000000 | y << 16 | y << 8 | y;
    }

    static int blend(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 255) * (1 - k) + ((to >>> 24) & 255) * k);
        int rr = Math.round(((from >> 16) & 255) * (1 - k) + ((to >> 16) & 255) * k);
        int g = Math.round(((from >> 8) & 255) * (1 - k) + ((to >> 8) & 255) * k);
        int b = Math.round((from & 255) * (1 - k) + (to & 255) * k);
        return a << 24 | rr << 16 | g << 8 | b;
    }

    static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
