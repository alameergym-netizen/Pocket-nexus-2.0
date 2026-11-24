package com.limelight.pocket.device;

import java.util.Locale;
import java.util.TreeSet;

/** Names are hints only; no Bluetooth identity or vendor command is inferred. */
public final class HorizonBudsPolicy {
    private HorizonBudsPolicy() {}
    public static boolean matches7i(String name) {
        if(name==null) { return false; }
        String normalized=name.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+","");
        return normalized.equals("huaweifreebuds7i") || normalized.equals("freebuds7i");
    }
    public static String positiveValues(int[] values) {
        if(values==null) { return ""; }
        TreeSet<Integer> sorted=new TreeSet<>();
        for(int value:values) { if(value>0) { sorted.add(value); } }
        StringBuilder out=new StringBuilder();
        int shown=0;
        for(int value:sorted) {
            if(shown==8) { out.append(", …"); break; }
            if(shown>0) { out.append(", "); }
            out.append(value); shown++;
        }
        return out.toString();
    }
}
