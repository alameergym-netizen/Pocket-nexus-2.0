package com.limelight.pocket.device.headphones;

/** Compact readback summary. Prefer the weaker known earbud; never use case charge as earbud charge. */
final class HeadphonesHomeStatus {
    private HeadphonesHomeStatus() {}
    static Integer level(HeadphonesReadback.Battery b) {
        if(b==null) return null;
        return level(b.left,b.right,b.overall);
    }
    static Integer level(Integer left,Integer right,Integer overall) {
        if(left!=null && right!=null) return Math.min(left,right);
        if(left!=null) return left;
        if(right!=null) return right;
        return overall;
    }
    static String description(android.content.Context c,HeadphonesReadback.Battery b) {
        return c.getString(com.limelight.R.string.horizon_hp_home_levels,percent(c,b.left),percent(c,b.right),percent(c,b.caseLevel));
    }
    private static String percent(android.content.Context c,Integer v) {
        return v==null?c.getString(com.limelight.R.string.horizon_hp_battery_unknown)
                :c.getString(com.limelight.R.string.horizon_hp_battery,v);
    }
}
