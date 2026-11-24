package com.limelight.pocket.pclib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Accept a complete counted library reply or none of it. */
final class HorizonLibraryReply {
    private static final int MAX_CHARS = 8 * 1024 * 1024;
    interface Reader { String read() throws IOException; }
    interface Validator { boolean valid(String line); }
    final List<String> lines;
    final String error;

    private HorizonLibraryReply(List<String> lines,String error) {
        this.lines=Collections.unmodifiableList(lines);
        this.error=error;
    }

    static HorizonLibraryReply read(Reader reader,String prefix,int limit,Validator validator) throws IOException {
        List<String> pending=new ArrayList<>();
        int chars=0;
        long start=System.nanoTime();
        while (true) {
            // Per-line socket timeouts are separate; this bounds total elapsed time between reads.
            if (System.nanoTime()-start>=60_000_000_000L) {
                throw new IOException("Library reply deadline");
            }
            String line=reader.read();
            if (System.nanoTime()-start>=60_000_000_000L || line==null) {
                throw new IOException("Incomplete library reply");
            }
            if (line.matches("END [0-9]{1,5}")) {
                int count=Integer.parseInt(line.substring(4));
                if(count!=pending.size()) { throw new IOException("Library count mismatch"); }
                return new HorizonLibraryReply(pending,null);
            }
            if(line.equals("ERR") || line.startsWith("ERR ")) {
                return new HorizonLibraryReply(Collections.emptyList(),line);
            }
            if(pending.size()>=limit || !line.startsWith(prefix) || !validator.valid(line)
                    || line.length()>MAX_CHARS-chars) {
                throw new IOException("Invalid or oversized library reply");
            }
            chars+=line.length();
            pending.add(line);
        }
    }
}
