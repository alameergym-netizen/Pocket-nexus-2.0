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
    public static HorizonLaunchTarget from(HomeItem i) {
        HorizonProviderPolicy.Provider p = HorizonProviderPolicy.provider(i);
        if (i == null) return new HorizonLaunchTarget(p);
        switch (p) {
            case PC_STREAM: return pc(i.a, i.n);
            case PC_LIBRARY: return new HorizonLaunchTarget(p, i.a, Integer.toString(i.n), i.b);
            case RETRO: return new HorizonLaunchTarget(p, i.a, i.b, i.c);
            case CLOUD: return new HorizonLaunchTarget(p, i.a, i.b);
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
