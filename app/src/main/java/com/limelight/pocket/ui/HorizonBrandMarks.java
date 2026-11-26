package com.limelight.pocket.ui;

import com.limelight.R;

/** The drawable of each {@link HorizonBrand} variant. */
public final class HorizonBrandMarks {
    private HorizonBrandMarks() {}

    public static int drawable(int variant) {
        switch (variant) {
            case HorizonBrand.STANDARD_BLACK: return R.drawable.ic_pocket_mark_black;
            case HorizonBrand.PIXEL_WHITE: return R.drawable.ic_pocket_mark_pixel;
            case HorizonBrand.PIXEL_BLACK: return R.drawable.ic_pocket_mark_pixel_black;
            default: return R.drawable.ic_pocket_mark;
        }
    }
}
