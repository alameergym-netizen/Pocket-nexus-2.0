package com.limelight.pocket.core;

/** Profile identity is separate from a Desktop stream's transport identity and launch command. */
public final class HorizonProfileOwner {
    public final String host;
    public final int streamAppId;
    public final int profileId;

    private HorizonProfileOwner(String host, int streamAppId, int profileId) {
        this.host = host;
        this.streamAppId = streamAppId;
        this.profileId = profileId;
    }

    public boolean valid() {
        return host != null && !host.trim().isEmpty() && streamAppId != 0 && profileId != 0;
    }

    public boolean isOverrideFor(String targetHost, int targetStream) {
        return valid() && profileId != streamAppId && host.equals(targetHost) && streamAppId == targetStream;
    }

    public static HorizonProfileOwner resolve(String host, int streamAppId, int ticketProfile,
            String resumeHost, int resumeStreamId, int resumeProfile) {
        HorizonProfileOwner direct = new HorizonProfileOwner(host, streamAppId, streamAppId);
        if (!direct.valid()) return direct;
        if (ticketProfile != 0) return new HorizonProfileOwner(host, streamAppId, ticketProfile);
        HorizonProfileOwner saved = new HorizonProfileOwner(resumeHost, resumeStreamId, resumeProfile);
        return saved.isOverrideFor(host, streamAppId) ? saved : direct;
    }
}
