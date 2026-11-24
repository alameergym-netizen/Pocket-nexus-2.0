package com.limelight.pocket.streaming;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;

/** Temporary link observations. A sample is meaningful only for the same host and active route. */
public final class HorizonAdaptiveMemory {
    static final long LIFETIME_MS = 2 * 60 * 60 * 1000L;
    static final int MAX_SCOPES = 32;

    public enum Route { WIFI, ETHERNET, VPN, CELLULAR }

    public static final class Scope {
        public final Route route;
        private final String host;
        private final long networkHandle;

        private Scope(String host, long networkHandle, Route route) {
            this.host = host;
            this.networkHandle = networkHandle;
            this.route = route;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Scope)) return false;
            Scope scope = (Scope) other;
            return networkHandle == scope.networkHandle && route == scope.route && host.equals(scope.host);
        }

        @Override public int hashCode() {
            return Objects.hash(host, networkHandle, route);
        }
    }

    private static final class Sample {
        final int bitrate;
        final long recordedAt;

        Sample(int bitrate, long recordedAt) {
            this.bitrate = bitrate;
            this.recordedAt = recordedAt;
        }
    }

    private final LinkedHashMap<Scope, Sample> observations = new LinkedHashMap<>(8, 0.75f, true);

    public static Scope scope(String host, long networkHandle, Route route) {
        if (host == null || networkHandle == 0 || route == null) return null;
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : new Scope(normalized, networkHandle, route);
    }

    public synchronized int cap(Scope scope, long now) {
        if (scope == null) return 0;
        Sample sample = observations.get(scope);
        if (sample == null) return 0;
        if (now < sample.recordedAt || now - sample.recordedAt >= LIFETIME_MS) {
            observations.remove(scope);
            return 0;
        }
        return sample.bitrate;
    }

    public synchronized void learn(Scope scope, int kbps, long now) {
        if (scope == null || kbps <= 0 || now < 0) return;
        observations.put(scope, new Sample(kbps, now));
        if (observations.size() > MAX_SCOPES) {
            observations.remove(observations.keySet().iterator().next());
        }
    }

    public synchronized void clear(Scope scope) {
        observations.remove(scope);
    }

    public synchronized void clearAll() {
        observations.clear();
    }
}
