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
    static String description(HeadphonesReadback.Battery b) {
        return "left "+percent(b.left)+", right "+percent(b.right)+", case "+percent(b.caseLevel);
    }
    private static String percent(Integer v) { return v==null?"not reported":v+" percent"; }
}
