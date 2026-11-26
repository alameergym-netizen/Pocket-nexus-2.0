package com.limelight.pocket.home;

import android.graphics.drawable.GradientDrawable;

/** Home-only visual tokens. Other screens keep their existing themes. */
final class HorizonHomeStyle {
    private HorizonHomeStyle() {}
    static int lightAccent(int accent,int whitePercent) {
        int w=Math.max(0,Math.min(100,whitePercent));
        int r=(((accent>>>16)&255)*(100-w)+255*w)/100;
        int g=(((accent>>>8)&255)*(100-w)+255*w)/100;
        int b=((accent&255)*(100-w)+255*w)/100;
        return 0xFF000000 | r<<16 | g<<8 | b;
    }
    /** S01: one rule for the brand (see HorizonBrand.presence). */
    static int brandMode(float widthDp) { return com.limelight.pocket.ui.HorizonBrand.presence(widthDp); }
    static boolean showSummary(float widthDp,float heightDp,boolean shelf,boolean portal) {
        return shelf && !portal && widthDp>=720 && heightDp>=400;
    }
    static GradientDrawable play(float d,int accent) {
        GradientDrawable face=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{lightAccent(accent,86),lightAccent(accent,62)});
        face.setCornerRadius(24*d);
        face.setStroke(Math.max(1,Math.round(d)),lightAccent(accent,91));
        return face;
    }
    static GradientDrawable glass(float d,int accent,boolean focused) {
        GradientDrawable face=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{focused?(accent&0xFFFFFF)|0x99000000:0xCF343044,0xE51D1928});
        face.setCornerRadius(14*d);
        face.setStroke(Math.max(1,Math.round((focused?2:1)*d)),focused?lightAccent(accent,45):0x59FFFFFF);
        return face;
    }
}
