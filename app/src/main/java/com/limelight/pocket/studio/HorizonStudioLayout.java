package com.limelight.pocket.studio;

/** Theme Studio regions in dp: the look list beside the preview, or stacked under it. */
final class HorizonStudioLayout {
    final boolean split;
    final int padding, listWidth, previewHeight;

    private HorizonStudioLayout(boolean split, int padding, int listWidth, int previewHeight) {
        this.split = split;
        this.padding = padding;
        this.listWidth = listWidth;
        this.previewHeight = previewHeight;
    }

    static HorizonStudioLayout forWindow(float widthDp, float heightDp, float fontScale) {
        int w = Math.max(1, (int) widthDp), h = Math.max(1, (int) heightDp);
        float scale = Math.max(1f, Math.min(2f, fontScale));
        int pad = w >= 600 ? 20 : 12;
        if (w >= 600 && h >= 320) {
            // Larger text needs a wider list so look names keep to one or two lines
            int want = Math.round(w * (scale > 1.15f ? 0.46f : 0.40f));
            int list = Math.max(250, Math.min(440, want));
            return new HorizonStudioLayout(true, pad, Math.min(list, w - 2 * pad - 240), 0);
        }
        int preview = Math.max(140, Math.min(300, Math.round(h * 0.42f)));
        return new HorizonStudioLayout(false, pad, 0, preview);
    }

    /**
     * Text drawn on an accent fill: near-black or white, whichever has more contrast with it
     * (WCAG relative luminance). A pasted custom accent can be dark.
     */
    static int textOn(int accent) {
        double luminance = 0.2126 * channel((accent >> 16) & 255) + 0.7152 * channel((accent >> 8) & 255)
                + 0.0722 * channel(accent & 255);
        return luminance > 0.18 ? 0xFF0B0B0D : 0xFFFFFFFF;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
