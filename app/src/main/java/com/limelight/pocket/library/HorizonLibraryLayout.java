package com.limelight.pocket.library;

/** Independent detail/grid regions, sized in dp. */
final class HorizonLibraryLayout {
    final boolean split;
    final int padding, detailWidth, artHeight, columns;
    private HorizonLibraryLayout(boolean split, int padding, int detailWidth, int artHeight, int columns) {
        this.split = split; this.padding = padding; this.detailWidth = detailWidth;
        this.artHeight = artHeight; this.columns = columns;
    }
    static HorizonLibraryLayout forWindow(float widthDp, float heightDp) {
        return forWindow(widthDp, heightDp, false);
    }

    static HorizonLibraryLayout forWindow(float widthDp, float heightDp, boolean pureOled) {
        int w = Math.max(1, (int) widthDp), h = Math.max(1, (int) heightDp);
        boolean split = w >= 720 && h >= 320;
        int pad = w >= 600 ? 20 : 12;
        int detail = split ? Math.min(300, Math.max(220, w / 3)) : 0;
        int remaining = Math.max(1, w - 2 * pad - (split ? detail + 12 : 0));
        if (pureOled) {
            return new HorizonLibraryLayout(split, pad, detail, 0, remaining >= 420 ? 2 : 1);
        }
        int columns = Math.max(2, Math.min(9, remaining / 124));
        int art = split ? Math.min(162, Math.max(86, h / 3 - 15)) : 0;
        return new HorizonLibraryLayout(split, pad, detail, art, columns);
    }
}
