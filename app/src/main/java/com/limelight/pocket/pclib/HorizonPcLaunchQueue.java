package com.limelight.pocket.pclib;

/** Process-local, bounded, single-use command ownership. No persistence or logging. */
public final class HorizonPcLaunchQueue {
    static final long VALID_MS = 180000L;
    public static final class Ticket {
        private final String uuid, command, name;
        private final int appId;
        private final long at;
        public final int profile;
        private Ticket(String uuid, int appId, String command, String name, int profile, long at) {
            this.uuid = uuid; this.appId = appId; this.command = command;
            this.name = name; this.profile = profile; this.at = at;
        }
    }
    private Ticket pending;
    public synchronized void arm(String uuid, int appId, String command, String name, int profile, long now) {
        // Invalid newer requests invalidate old wishes instead of accidentally replaying them.
        pending = null;
        if (uuid == null || uuid.trim().isEmpty() || appId == 0 || command == null
                || command.trim().isEmpty() || now < 0) return;
        pending = new Ticket(uuid, appId, command, name == null ? "" : name, profile, now);
    }
    public synchronized void clearHost(String uuid) {
        if (pending != null && pending.uuid.equals(uuid)) pending = null;
    }
    private void expire(long now) {
        if (pending != null && (now < pending.at || now - pending.at > VALID_MS)) pending = null;
    }
    public synchronized Ticket snapshotFor(String uuid, int appId, long now) {
        expire(now);
        if (pending == null || !pending.uuid.equals(uuid)) return null;
        if (pending.appId != appId) {
            // Same host, different explicitly selected stream.
            pending = null;
            return null;
        }
        return pending;
    }
    public synchronized String[] take(Ticket expected, String uuid, int appId, long now) {
        expire(now);
        if (expected == null || expected != pending || !expected.uuid.equals(uuid)
                || expected.appId != appId) return null;
        pending = null;
        return new String[]{expected.command, expected.name};
    }
}
