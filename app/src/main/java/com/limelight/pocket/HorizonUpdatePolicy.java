package com.limelight.pocket;

import java.net.URI;
import java.util.Locale;

/** Conservative update transport rules. Android's package installer remains the final verifier. */
public final class HorizonUpdatePolicy {
    static final String REPO = "alameergym-netizen/Pocket-nexus-2.0";
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

    static boolean releaseAsset(String url, String sha) {
        if (!validSha(sha) || !allowedUrl(url)) return false;
        URI uri = URI.create(url);
        return "github.com".equalsIgnoreCase(uri.getHost()) && uri.getRawQuery() == null
                && uri.getRawFragment() == null && ("/" + REPO + "/releases/download/latest/PocketNexus2-"
                + sha + ".apk").equals(uri.getRawPath());
    }

    static boolean validDigest(String digest) {
        return digest != null && digest.matches("sha256:[0-9a-f]{64}");
    }

    static boolean matchesDigest(String expected, byte[] actual) {
        if (!validDigest(expected) || actual == null || actual.length != 32) return false;
        StringBuilder hex = new StringBuilder("sha256:");
        for (byte b : actual) {
            hex.append(Character.forDigit((b >>> 4) & 0xf, 16));
            hex.append(Character.forDigit(b & 0xf, 16));
        }
        return expected.equals(hex.toString());
    }
}
