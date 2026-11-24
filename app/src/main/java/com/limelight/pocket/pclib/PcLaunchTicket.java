package com.limelight.pocket.pclib;

import android.os.SystemClock;

/** Pending desktop-mediated PC launch; the stream captures its ticket at creation. */
public final class PcLaunchTicket {
    private PcLaunchTicket() {}
    private static final HorizonPcLaunchQueue QUEUE = new HorizonPcLaunchQueue();
    public static final String EXTRA_TOKEN = "pocket.pc.commandToken";
    public static String arm(String pcUuid, int viaAppId, String launchCommand, String gameName, int profileId) {
        return QUEUE.arm(pcUuid, viaAppId, launchCommand, gameName, profileId, SystemClock.elapsedRealtime());
    }
    public static void clearHost(String uuid) { QUEUE.clearHost(uuid); }
    public static HorizonPcLaunchQueue.Ticket snapshotFor(String token, String pcUuid, int streamAppId) {
        return QUEUE.snapshotFor(token, pcUuid, streamAppId, SystemClock.elapsedRealtime());
    }
    public static String[] take(HorizonPcLaunchQueue.Ticket ticket, String pcUuid, int streamAppId) {
        return QUEUE.take(ticket, pcUuid, streamAppId, SystemClock.elapsedRealtime());
    }
}
