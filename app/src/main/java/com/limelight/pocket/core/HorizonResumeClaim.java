package com.limelight.pocket.core;

/** Process-local ownership of a taken resume ticket. Never extends persisted expiry. */
public final class HorizonResumeClaim {
    public static final class Claim {
        public final long id, armedAt, maxAge, epoch;
        public final int reason;
        private Claim(long id, long armedAt, long maxAge, long epoch, int reason) {
            this.id=id; this.armedAt=armedAt; this.maxAge=maxAge; this.epoch=epoch; this.reason=reason;
        }
    }
    private long sequence;
    private Claim active;
    public synchronized Claim begin(long armedAt, long maxAge, long now, long epoch, int reason) {
        if (active != null || sequence == Long.MAX_VALUE || !fresh(armedAt,maxAge,now)) return null;
        active=new Claim(++sequence,armedAt,maxAge,epoch,reason);
        return active;
    }
    private static boolean fresh(long armedAt,long maxAge,long now) {
        return armedAt>0 && maxAge>0 && now>=armedAt && now-armedAt<maxAge;
    }
    public synchronized boolean current(long id,long now,long epoch) {
        return active!=null && active.id==id && active.epoch==epoch && fresh(active.armedAt,active.maxAge,now);
    }
    public synchronized Claim finish(long id,long now,long epoch) {
        if (!current(id,now,epoch)) { cancel(id); return null; }
        Claim result=active; active=null; return result;
    }
    public synchronized void cancel(long id) {
        if (active!=null && active.id==id) active=null;
    }
    public synchronized void invalidate() { active=null; }
}
