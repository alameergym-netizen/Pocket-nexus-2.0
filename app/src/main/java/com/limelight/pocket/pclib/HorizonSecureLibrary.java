package com.limelight.pocket.pclib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Minimal display-only data returned by a library-scoped Nexus Host key. */
public final class HorizonSecureLibrary {
    static final int MAX_ENTRIES = 5000;

    public interface Reader {
        String readLine() throws IOException;
    }

    public static final class Entry {
        public final String title, source;
        public final boolean installed;

        private Entry(String title, String source, boolean installed) {
            this.title = title;
            this.source = source;
            this.installed = installed;
        }

        static Entry parse(String line) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 4 || !fields[0].equals("S") || fields[1].trim().isEmpty()
                    || fields[1].length() > 256 || fields[2].length() > 32
                    || (!fields[3].equals("0") && !fields[3].equals("1"))) return null;
            return new Entry(fields[1], fields[2], fields[3].equals("1"));
        }
    }

    public static final class Result {
        public final List<Entry> entries;
        public final String error;

        private Result(List<Entry> entries, String error) {
            this.entries = Collections.unmodifiableList(entries);
            this.error = error;
        }
    }

    private HorizonSecureLibrary() { }

    public static Result read(Reader reader) throws IOException {
        HorizonLibraryReply reply = HorizonLibraryReply.read(
                reader::readLine, "S\t", MAX_ENTRIES, line -> Entry.parse(line) != null);
        List<Entry> entries = new ArrayList<>(reply.lines.size());
        for (String line : reply.lines) entries.add(Entry.parse(line));
        return new Result(entries, reply.error);
    }
}
