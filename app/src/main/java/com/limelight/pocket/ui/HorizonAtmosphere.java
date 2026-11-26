package com.limelight.pocket.ui;

/**
 * Nexus Atmosphere's layered layout, decided in plain Java (unit tested): the art at the back, a
 * glass tray under the games, the hero floating on its own glass veil above it, and Nexus Control
 * as a lighter pane over the blurred screen. Real blur only where Android can do it cheaply
 * (12+, live glass on); everywhere else the same layers are drawn as plain translucent glass,
 * with denser tints so text keeps its contrast without a blur behind it.
 */
public final class HorizonAtmosphere {
    private HorizonAtmosphere() {}

    /** Android 12 (API 31): RenderNode blur for the glass and blur-behind for windows. */
    public static final int BLUR_SDK = 31;

    /** Below this much room (dp) the hero keeps its plain layout: a veil would crowd its logo. */
    public static final float VEIL_MIN_ROOM_DP = 200f;

    /** The veil's padding around the hero (dp): left/right, top/bottom. */
    public static final int VEIL_PAD_X = 18;
    public static final int VEIL_PAD_Y = 14;

    /** How far the veil lifts the hero off the tray (dp). */
    public static final int VEIL_LIFT = 12;

    /** The tray's inset from the screen sides and above the hints (dp), and its reach above the row. */
    public static final int TRAY_SIDE = 12;
    public static final int TRAY_ABOVE_HINTS = 40;
    public static final int TRAY_OVER_ROW = 24;

    /** Real blur, or the translucent fallback. */
    public static boolean live(int sdk, boolean liveGlassOn) {
        return sdk >= BLUR_SDK && liveGlassOn;
    }

    /** Whether the hero gets its veil with this much room above the row (dp). */
    public static boolean veil(float heroRoomDp) {
        return heroRoomDp >= VEIL_MIN_ROOM_DP;
    }

    /** The hero's room once the veil's padding and lift are taken out of it (dp). */
    public static float roomInsideVeil(float heroRoomDp) {
        return heroRoomDp - 2 * VEIL_PAD_Y - VEIL_LIFT;
    }

    /** The tray's gap above the bottom of the screen (dp): clear of the hints, never under the row. */
    public static int trayBottom(int rowBottomMarginDp) {
        return Math.max(0, Math.min(TRAY_ABOVE_HINTS, rowBottomMarginDp));
    }

    /**
     * The tray's height (dp): from a little above the row (its label) down to {@link #trayBottom},
     * so the row and the dock under it sit on one sheet of glass.
     */
    public static int trayHeight(int rowBottomMarginDp, int rowHeightDp) {
        return Math.max(0, rowBottomMarginDp) + Math.max(0, rowHeightDp) + TRAY_OVER_ROW - trayBottom(rowBottomMarginDp);
    }

    /**
     * Nexus Control's pane (top, bottom colours): see-through over a blurred screen, nearly solid
     * without one so its words never sit on raw, busy art.
     */
    public static int[] controlPane(boolean blurBehind) {
        return blurBehind ? new int[]{0xB8222B3A, 0xC8121822} : new int[]{0xF0222B3A, 0xF6121822};
    }

    /** The pane's hairline rim and its top sheen (a light from above, part of the layering). */
    public static final int RIM = 0x5CFFFFFF;
    public static final int SHEEN_TOP = 0x1FFFFFFF;
}
