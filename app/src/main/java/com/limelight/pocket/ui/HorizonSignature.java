package com.limelight.pocket.ui;

/**
 * Nexus Signature's cinematic layout, decided in plain Java (unit tested): the screen framed like
 * a film. A black bar across the top holds the status, a black bar across the bottom holds the
 * games like a credits roll, the game's art shows in the band between, and a short accent rule
 * marks the title. The Library's art becomes a widescreen still and Nexus Control a black slate.
 */
public final class HorizonSignature {
    private HorizonSignature() {}

    /** Widescreen ("scope") picture aspect for the Library still. */
    public static final float SCOPE = 2.39f;

    /** The top bar's height (dp): under the status line and the section tabs. */
    public static final int TOP_BAR = 44;

    /** How far the bottom bar reaches above the games row's top (dp), covering its label. */
    public static final int BOTTOM_OVER_ROW = 30;

    /** The band of art between the bars needs at least this much height (dp), or the bars give way. */
    public static final int MIN_BAND = 150;

    /** The soft edge where each bar meets the art (dp). */
    public static final int EDGE = 18;

    /** The accent rule over the title (dp). */
    public static final int RULE_W = 28;
    public static final int RULE_H = 2;

    /** The bottom bar's height (dp): from the screen's bottom to a little above the row. */
    public static int bottomBar(int rowBottomMarginDp, int rowHeightDp) {
        return Math.max(0, rowBottomMarginDp) + Math.max(0, rowHeightDp) + BOTTOM_OVER_ROW;
    }

    /** The art left between the bars (dp). */
    public static int band(int screenHeightDp, int bottomBarDp) {
        return screenHeightDp - TOP_BAR - bottomBarDp;
    }

    /** Whether the screen is tall enough to frame: otherwise the bars would leave no picture. */
    public static boolean framed(int screenHeightDp, int bottomBarDp) {
        return band(screenHeightDp, bottomBarDp) >= MIN_BAND;
    }

    /** The Library still's height (dp) for a pane this wide, kept between 72 and 162 dp. */
    public static int stillHeight(int paneWidthDp) {
        return Math.max(72, Math.min(162, Math.round(paneWidthDp / SCOPE)));
    }

    /** The bars: solid black, easing into the art over {@link #EDGE} (top, edge colours). */
    public static final int BAR = 0xFF000000;
    public static final int BAR_EDGE = 0x00000000;

    /** Nexus Control's slate: near-black, square-cornered, with a hairline in the accent. */
    public static final int SLATE = 0xF5050505;
    public static final int SLATE_CORNER = 6;
}
