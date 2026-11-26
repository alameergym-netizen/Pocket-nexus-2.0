package com.limelight.pocket.ui;

/**
 * S01: the one Pocket Nexus mark (the stencil N: two pillars and the light between them, no
 * stars), in its four variants: standard or pixel (Nexus Legacy), white for dark surfaces or
 * black for light ones. Plain Java (unit tested); {@link HorizonBrandMarks} maps the choice to
 * its drawable.
 */
public final class HorizonBrand {
    private HorizonBrand() {}

    public static final int STANDARD_WHITE = 0;
    public static final int STANDARD_BLACK = 1;
    public static final int PIXEL_WHITE = 2;
    public static final int PIXEL_BLACK = 3;

    /** The variant for a surface: black on a light one (WCAG luminance above 0.18), else white. */
    public static int variant(boolean pixel, int surfaceColor) {
        boolean light = luminance(surfaceColor) > 0.18;
        return pixel ? (light ? PIXEL_BLACK : PIXEL_WHITE) : (light ? STANDARD_BLACK : STANDARD_WHITE);
    }

    /**
     * How the brand reads at a width (dp): the mark with the name on wide screens, the mark alone
     * on mid widths (never a typed letter in its place), nothing on narrow ones.
     */
    public static final int HIDDEN = 0;
    public static final int MARK = 1;
    public static final int MARK_AND_NAME = 2;

    public static int presence(float widthDp) {
        return widthDp >= 940 ? MARK_AND_NAME : widthDp >= 680 ? MARK : HIDDEN;
    }

    /** WCAG relative luminance of an opaque colour. */
    static double luminance(int color) {
        return 0.2126 * channel((color >> 16) & 0xFF) + 0.7152 * channel((color >> 8) & 0xFF)
                + 0.0722 * channel(color & 0xFF);
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
