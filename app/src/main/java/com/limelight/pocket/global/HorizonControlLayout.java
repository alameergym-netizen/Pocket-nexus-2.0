package com.limelight.pocket.global;

/** Size the workspace using logical window dimensions; no device model assumptions. */
final class HorizonControlLayout {
    final int width, height, margin, padding, columns;
    final boolean split;
    private HorizonControlLayout(int width, int height, int margin, int padding, int columns, boolean split) {
        this.width = width; this.height = height; this.margin = margin;
        this.padding = padding; this.columns = columns; this.split = split;
    }
    static HorizonControlLayout forWindow(float widthDp, float heightDp) {
        return forWindow(widthDp, heightDp, false);
    }

    static HorizonControlLayout forWindow(float widthDp, float heightDp, boolean pureOled) {
        int w = Math.max(1, (int) widthDp);
        int h = Math.max(1, (int) heightDp);
        int margin = w >= 600 && h >= 300 ? 14 : 6;
        int width = Math.max(1, Math.min(pureOled ? 600 : 880, w - 2 * margin));
        int height = Math.max(1, Math.min(560, h - 2 * margin));
        boolean split = width >= 720;
        int padding = width >= 400 ? 16 : 10;
        int columns = width >= 400 && !pureOled ? 3 : 2;
        return new HorizonControlLayout(width, height, margin, padding, columns, split);
    }
}
