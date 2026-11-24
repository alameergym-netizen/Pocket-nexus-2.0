package com.limelight.pocket.pclib;

/** Process-local, bounded, single-use command ownership. No persistence or logging. */
public final class HorizonPcLaunchQueue {
    static final long VALID_MS = 180000L;
    public static final class Ticket {
        private final String uuid, command, name, token;
        private final int appId;
        private final long at;
        public final int profile;
        private Ticket(String uuid, int appId, String command, String name, int profile, long at) {
            this.token = java.util.UUID.randomUUID().toString();
            this.uuid = uuid; this.appId = appId; this.command = command;
            this.name = name; this.profile = profile; this.at = at;
        }
    }
    private Ticket pending;
    public synchronized String arm(String uuid, int appId, String command, String name, int profile, long now) {
        // Invalid newer requests invalidate old wishes instead of accidentally replaying them.
        pending = null;
        if (uuid == null || uuid.trim().isEmpty() || appId == 0 || command == null
                || command.trim().isEmpty() || now < 0) return null;
        pending = new Ticket(uuid, appId, command, name == null ? "" : name, profile, now);
        return pending.token;
    }
    public synchronized void clearHost(String uuid) {
        if (pending != null && pending.uuid.equals(uuid)) pending = null;
    }
    private void expire(long now) {
        if (pending != null && (now < pending.at || now - pending.at > VALID_MS)) pending = null;
    }
    public synchronized Ticket snapshotFor(String token, String uuid, int appId, long now) {
        expire(now);
        if (token == null || pending == null || !pending.token.equals(token)
                || !pending.uuid.equals(uuid) || pending.appId != appId) return null;
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
