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
    public static int layoutIndex(int index) { return index >= 0 && index < ORDERS.length ? index : 0; }
    public static int[] order(int index) { return ORDERS[layoutIndex(index)].clone(); }
    public static int holdMs(int value) { return Math.max(350, Math.min(1000, value)); }
}
