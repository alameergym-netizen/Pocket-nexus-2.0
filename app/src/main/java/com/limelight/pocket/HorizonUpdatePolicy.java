package com.limelight.pocket;

import java.net.URI;
import java.util.Locale;

/** Conservative update transport rules. Android's package installer remains the final verifier. */
public final class HorizonUpdatePolicy {
    public static final long MAX_APK_BYTES = 512L * 1024 * 1024;
    private HorizonUpdatePolicy() {}

    public static boolean validSha(String sha) {
        return sha != null && sha.matches("[0-9a-f]{7}");
    }

    public static boolean allowedUrl(String url) {
        if (url == null) { return false; }
        try {
            URI u = new URI(url);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null
                    || u.getUserInfo() != null || (u.getPort() != -1 && u.getPort() != 443)) {
                return false;
            }
            switch (u.getHost().toLowerCase(Locale.ROOT)) {
                case "github.com":
                case "api.github.com":
                case "release-assets.githubusercontent.com":
                case "objects.githubusercontent.com":
                case "github-releases.githubusercontent.com":
                    return true;
                default: return false;
            }
        } catch (Exception e) { return false; }
    }
}
