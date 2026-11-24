package com.limelight.pocket.cloud;

import com.limelight.pocket.core.HorizonLaunchRequests;
import com.limelight.pocket.core.HorizonLaunchTarget;

/** Process-local dispatch evidence, never confirmation of remote gameplay or sign-in. */
public final class HorizonCloudDispatch {
    private HorizonCloudDispatch() {}
    public enum Result {
        REJECTED, EXTERNAL_LINK_DISPATCHED, EXTERNAL_APP_DISPATCHED,
        BROWSER_DISPATCHED, INTERNAL_PLAYER_DISPATCHED;
        public boolean dispatched() { return this != REJECTED; }
        public boolean gameplayConfirmed() { return false; }
    }
    public static boolean claim(HorizonLaunchRequests requests, String service, String url, long now) {
        if (requests == null || service == null || service.trim().isEmpty()) return false;
        long ticket = requests.begin(HorizonLaunchTarget.cloud(service, url), now);
        return ticket != 0 && requests.claim(ticket, now);
    }
}
