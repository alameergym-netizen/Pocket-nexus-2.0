package com.limelight.pocket.device;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory simulation only. No kernel or privileged transport implementation. */
public final class HorizonProfileSimulation {
    private HorizonProfileSimulation() {}
    public enum Outcome { APPLIED, REJECTED, ROLLED_BACK, INDETERMINATE }

    public static final class Result {
        public final Outcome outcome;
        public final Map<String,Long> observed;
        public final List<String> journal;
        Result(Outcome outcome, Map<String,Long> observed, List<String> journal) {
            this.outcome=outcome;
            this.observed=Collections.unmodifiableMap(new LinkedHashMap<>(observed));
            this.journal=Collections.unmodifiableList(new ArrayList<>(journal));
        }
    }

    /**
     * Simulate a multi-domain transaction. Baseline is the exact observed caps.
     * Target must cover identical domains and never raise a cap.
     * Failure and rollback-failure positions are zero-based; -1 disables injection.
     * An injected write failure models a write that took effect before an error.
     */
    public static Result run(Map<String,Long> baseline, Map<String,Long> target,
                             int failAt, int rollbackFailAt) {
        Map<String,Long> state=new LinkedHashMap<>();
        List<String> journal=new ArrayList<>();
        if (baseline!=null) { state.putAll(baseline); }
        if (baseline==null || target==null || baseline.isEmpty() || baseline.size()>33
                || !baseline.keySet().equals(target.keySet())) {
            return new Result(Outcome.REJECTED,state,journal);
        }
        for (String id:baseline.keySet()) {
            Long old=baseline.get(id), next=target.get(id);
            if (id==null || !id.matches("(policy[0-9]{1,3}|gpu)") || old==null || next==null
                    || old<=0 || next<=0 || next>old) {
                return new Result(Outcome.REJECTED,state,journal);
            }
        }
        List<String> ids=new ArrayList<>(baseline.keySet());
        Collections.sort(ids);
        List<String> touched=new ArrayList<>();
        for (String id:ids) {
            if (baseline.get(id).equals(target.get(id))) { continue; }
            int index=touched.size();
            touched.add(id);
            state.put(id,target.get(id));
            journal.add("apply:"+id);
            if (index==failAt) {
                boolean restored=true;
                int undo=0;
                for (int i=touched.size()-1;i>=0;i--,undo++) {
                    String key=touched.get(i);
                    if (undo==rollbackFailAt) {
                        restored=false;
                        journal.add("rollback-failed:"+key);
                    } else {
                        state.put(key,baseline.get(key));
                        journal.add("rollback:"+key);
                    }
                }
                return new Result(restored && state.equals(baseline)
                        ? Outcome.ROLLED_BACK : Outcome.INDETERMINATE,state,journal);
            }
        }
        return new Result(Outcome.APPLIED,state,journal);
    }
}
