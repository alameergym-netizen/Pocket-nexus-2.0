package com.limelight.pocket.home;

import android.graphics.drawable.GradientDrawable;

/** Home-only layout; existing saved layouts are not migrated. */
final class HorizonHomeWorkspace {
    final int margin, gridWidth, detailWidth, bodyTop, bodyHeight, columns, tile, logoHeight, descriptionLines;
    private HorizonHomeWorkspace(int margin, int gridWidth, int detailWidth, int bodyTop, int bodyHeight,
            int columns, int tile, int logoHeight, int descriptionLines) {
        this.margin = margin; this.gridWidth = gridWidth; this.detailWidth = detailWidth;
        this.bodyTop = bodyTop; this.bodyHeight = bodyHeight; this.columns = columns;
        this.tile = tile; this.logoHeight = logoHeight; this.descriptionLines = descriptionLines;
    }
    static boolean supported(float w, float h) { return w >= 640 && h >= 360; }
    static HorizonHomeWorkspace forWindow(float width, float height) {
        int w = Math.max(640, (int) width), h = Math.max(360, (int) height);
        int margin = 16, gap = 12, top = 52, body = h - top - 100;
        int detail = Math.min(400, Math.max(254, (int)(w * .38f)));
        int grid = w - 2 * margin - gap - detail;
        int cols = Math.max(2, Math.min(7, (grid - 12) / 100));
        int tile = Math.min(102, (grid - 12) / cols - 20);
        int logo = Math.max(42, Math.min(106, body - 138));
        int lines = body >= 320 ? 2 : body >= 275 ? 1 : 0;
        return new HorizonHomeWorkspace(margin, grid, detail, top, body, cols, tile, logo, lines);
    }
    static GradientDrawable surface(float d, boolean detail) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{detail ? 0xED202B39 : 0xC81B2330, 0xE910151E});
        bg.setCornerRadius(14 * d);
        bg.setStroke(Math.max(1, Math.round(d)), 0x44FFFFFF);
        return bg;
    }
}
