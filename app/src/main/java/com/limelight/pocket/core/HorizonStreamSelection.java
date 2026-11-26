package com.limelight.pocket.core;

import android.content.Intent;
import android.os.Bundle;

import java.util.UUID;

/** The launcher selection that created a pending stream Intent, scoped to this process. */
public final class HorizonStreamSelection {
    private static final String EXTRA_GENERATION = "pocket.stream.selection.generation";
    private static final String EXTRA_PROCESS = "pocket.stream.selection.process";
    private static final String PROCESS = UUID.randomUUID().toString();

    private HorizonStreamSelection() {}

    public static void stamp(Intent intent) {
        intent.putExtra(EXTRA_GENERATION, HorizonLaunchRequests.shared().generation());
        intent.putExtra(EXTRA_PROCESS, PROCESS);
    }

    /** Legacy direct starts adopt the selection present when the Activity is created. */
    public static long capture(Intent intent) {
        if (!intent.hasExtra(EXTRA_GENERATION) && !intent.hasExtra(EXTRA_PROCESS)) {
            return HorizonLaunchRequests.shared().generation();
        }
        return PROCESS.equals(intent.getStringExtra(EXTRA_PROCESS)) ? intent.getLongExtra(EXTRA_GENERATION, -1) : -1;
    }

    public static boolean current(long generation) {
        return generation >= 0 && generation == HorizonLaunchRequests.shared().generation();
    }

    /** Resume data retains its target and settings, and receives a fresh selection at take. */
    public static void clear(Bundle extras) {
        extras.remove(EXTRA_GENERATION);
        extras.remove(EXTRA_PROCESS);
    }
}
