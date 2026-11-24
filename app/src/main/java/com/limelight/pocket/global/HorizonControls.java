package com.limelight.pocket.global;

/** Preferences shared by controller settings and the quick-control panel. */
public final class HorizonControls {
    public static final String KEY_LAYOUT = "horizon_control_layout";
    private static final int[][] ORDERS = {
        {0,1,2,3,4,5}, // Classic
        {5,3,4,1,2,0}, // Gaming
        {1,2,5,4,3,0}  // Connectivity
    };
    private HorizonControls() {}
    /** Light theme tint with a guaranteed pale background for dark focus text. */
    static int focusColor(int accent) {
        int r=(((accent>>>16)&255)+4*255)/5;
        int g=(((accent>>>8)&255)+4*255)/5;
        int b=((accent&255)+4*255)/5;
        return 0xFF000000 | (r<<16) | (g<<8) | b;
    }
    static float clampLevel(float value) {
        return Float.isNaN(value) ? 0f : Math.max(0f, Math.min(1f, value));
    }
    public static int layoutIndex(int index) { return index >= 0 && index < ORDERS.length ? index : 0; }
    public static int[] order(int index) { return ORDERS[layoutIndex(index)].clone(); }
    public static int holdMs(int value) { return Math.max(350, Math.min(1000, value)); }
}
