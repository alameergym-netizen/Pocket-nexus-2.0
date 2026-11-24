package com.limelight.pocket.core;

import com.limelight.pocket.home.HomeItem;
import java.util.Arrays;

/** Immutable process-local launch identity. Never persisted or logged (URLs/commands may be private). */
public final class HorizonLaunchTarget {
    public final HorizonProviderPolicy.Provider provider;
    private final String[] fields;

    private HorizonLaunchTarget(HorizonProviderPolicy.Provider provider, String... fields) {
        this.provider = provider;
        this.fields = fields.clone();
    }
    public static HorizonLaunchTarget pc(String uuid, int appId) {
        return new HorizonLaunchTarget(HorizonProviderPolicy.Provider.PC_STREAM,
                uuid == null ? "" : uuid, Integer.toString(appId));
    }
    /** Shortcut host/UUID identity without conflating UUID-only games with numeric app IDs. */
    public static HorizonLaunchTarget pcShortcut(String host, String appUuid, int appId, String appName) {
        if (appUuid == null || appUuid.isEmpty()) {
            if (appId > 0) return pc(host, appId);
        }
        String h = host == null ? "" : host;
        if (appUuid != null && !appUuid.isEmpty()) {
            return new HorizonLaunchTarget(HorizonProviderPolicy.Provider.PC_STREAM, h, "uuid", appUuid);
        }
        return new HorizonLaunchTarget(HorizonProviderPolicy.Provider.PC_STREAM, h,
                "shortcut", appName == null ? "" : appName);
    }
    /** A missing UUID is not an identity. Positive numeric IDs remain a legacy fallback. */
    public static boolean sameRunningPcApp(int runningId, String runningUuid, int requestedId, String requestedUuid) {
        boolean runningHasUuid = runningUuid != null && !runningUuid.trim().isEmpty();
        boolean requestedHasUuid = requestedUuid != null && !requestedUuid.trim().isEmpty();
        if (runningHasUuid && requestedHasUuid) return runningUuid.equalsIgnoreCase(requestedUuid);
        return runningId > 0 && requestedId > 0 && runningId == requestedId;
    }
    public static HorizonLaunchTarget cloud(String service, String url) {
        return new HorizonLaunchTarget(HorizonProviderPolicy.Provider.CLOUD,
                service == null ? "" : service, url == null ? "" : url);
    }
    public static HorizonLaunchTarget from(HomeItem i) {
        HorizonProviderPolicy.Provider p = HorizonProviderPolicy.provider(i);
        if (i == null) return new HorizonLaunchTarget(p);
        switch (p) {
            case PC_STREAM: return pc(i.a, i.n);
            case PC_LIBRARY: return new HorizonLaunchTarget(p, i.a, Integer.toString(i.n), i.b);
            case RETRO: return new HorizonLaunchTarget(p, i.a, i.b, i.c);
            case CLOUD: return cloud(i.a, i.b);
            case NATIVE: return new HorizonLaunchTarget(p, i.a,
                    i.b.isEmpty() ? "STEAM" : i.b.toUpperCase(java.util.Locale.ROOT), Integer.toString(i.n));
            case SHORTCUT: return i.b.isEmpty() ? new HorizonLaunchTarget(p, i.a, "", i.c)
                    : new HorizonLaunchTarget(p, i.a, i.b);
            default: return new HorizonLaunchTarget(p, i.a);
        }
    }
    public boolean isGameRequest() {
        switch (HorizonProviderPolicy.observation(provider)) {
            case INTERNAL_STREAM:
            case EXTERNAL_UNCONFIRMED:
            case PROVIDER_DEPENDENT: return true;
            default: return false;
        }
    }
    @Override public boolean equals(Object obj) {
        if (!(obj instanceof HorizonLaunchTarget)) return false;
        HorizonLaunchTarget other = (HorizonLaunchTarget)obj;
        return provider == other.provider && Arrays.equals(fields, other.fields);
    }
    @Override public int hashCode() { return 31 * provider.hashCode() + Arrays.hashCode(fields); }
}
