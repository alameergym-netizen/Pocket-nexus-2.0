package com.limelight.pocket.device;

import java.util.Locale;

/** Pure capability and safety policy. No privileged writes. */
public final class HorizonBridgePolicy {
    private HorizonBridgePolicy() {}

    public static boolean isRp6(String maker, String model) {
        String m = maker == null ? "" : maker.toLowerCase(Locale.ROOT);
        String n = model == null ? "" : model.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return n.equals("retroidpocket6") || (m.contains("retroid") && (n.equals("rp6") || n.equals("pocket6")));
    }

    /** Android thermal levels: moderate and above warrant caution; missing is never normal. */
    public static int recommendation(int thermalStatus, boolean batterySaver) {
        if (thermalStatus >= 3) { return 3; } // stop heavy workload
        if (thermalStatus == 2 || batterySaver) { return 2; } // reduce workload
        if (thermalStatus == 0 || thermalStatus == 1) { return 1; } // standard limits
        return 0; // unknown
    }

    public static long parseFrequency(String value) {
        if (value == null || !value.matches("[0-9]{1,12}")) { return -1; }
        try {
            long f = Long.parseLong(value);
            return f > 0 ? f : -1;
        } catch (NumberFormatException e) { return -1; }
    }
}
