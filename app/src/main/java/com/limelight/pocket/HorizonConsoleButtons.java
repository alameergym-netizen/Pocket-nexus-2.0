package com.limelight.pocket;

import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit, narrowly scoped preset; does not touch calibrated key codes or M1/M2. */
public final class HorizonConsoleButtons {
    private HorizonConsoleButtons() {}
    static Map<String,String> actions() {
        Map<String,String> out=new LinkedHashMap<>();
        out.put("pocket_btn_back_tap","quickmenu");
        out.put("pocket_btn_back_double","none");
        out.put("pocket_btn_back_hold","none");
        out.put("pocket_btn_home_tap","guide");
        out.put("pocket_btn_home_double","none");
        out.put("pocket_btn_home_hold","guide");
        out.put("pocket_gb_back_tap","control");
        out.put("pocket_gb_back_double","none");
        out.put("pocket_gb_back_hold","none");
        out.put("pocket_gb_guide_tap","quick");
        out.put("pocket_gb_guide_double","none");
        out.put("pocket_gb_guide_hold","radial");
        return out;
    }
    public static void apply(SharedPreferences prefs) {
        SharedPreferences.Editor edit=prefs.edit();
        for(Map.Entry<String,String> e:actions().entrySet()) { edit.putString(e.getKey(),e.getValue()); }
        edit.putBoolean("pocket_gb_enabled",true).apply();
    }
    public static boolean matches(SharedPreferences prefs) {
        if(!prefs.getBoolean("pocket_gb_enabled",true)) { return false; }
        for(Map.Entry<String,String> e:actions().entrySet()) {
            if(!e.getValue().equals(prefs.getString(e.getKey(),e.getValue()))) { return false; }
        }
        return true;
    }
}
