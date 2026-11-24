package com.limelight.pocket.retro;

import java.io.File;
import java.io.IOException;

/** UI request generation and launch preflight. Not a volume identity or filesystem lock. */
final class HorizonRetroSelection {
    private long generation;
    synchronized long next() { return ++generation; }
    synchronized boolean current(long request) { return request==generation; }

    static boolean canLaunch(File active,File indexed,RomScanner.Rom requested,RomScanner.Rom current) {
        if(active==null || indexed==null || requested==null || current==null
                || requested.file==null || current.file==null) { return false; }
        try {
            if(!active.isDirectory() || !active.canRead()
                    || !active.getCanonicalPath().equals(indexed.getCanonicalPath())
                    || !requested.key().equals(current.key())
                    || requested.directory!=current.directory
                    || !requested.file.getCanonicalPath().equals(current.file.getCanonicalPath())) { return false; }
            return requested.file.canRead() && (requested.directory
                    ? requested.file.isDirectory() : requested.file.isFile());
        } catch(IOException | RuntimeException e) { return false; }
    }
}
