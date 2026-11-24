package com.limelight.pocket.pclib;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Versioned bounded format for a completed PC library response. No credentials or account rows. */
final class HorizonLibrarySnapshot {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final int MAX_ROWS = 20000;
    private static final int MAGIC = 0x484C5331; // HLS1
    private static final long MAX_AGE = 30L * 24 * 60 * 60 * 1000;
    private static final long FUTURE_TOLERANCE = 5L * 60 * 1000;

    private HorizonLibrarySnapshot() {}

    static byte[] encode(List<String> rows,long now) throws IOException {
        if (rows == null || rows.size()>MAX_ROWS || now<=0) { throw new IOException("Invalid snapshot"); }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeLong(now);
        out.writeInt(rows.size());
        for (String row:rows) {
            if (!valid(row)) { throw new IOException("Invalid game row"); }
            byte[] utf8=row.getBytes(StandardCharsets.UTF_8);
            if (utf8.length>65536 || utf8.length>MAX_BYTES-bytes.size()-4) {
                throw new IOException("Snapshot too large");
            }
            out.writeInt(utf8.length);
            out.write(utf8);
        }
        out.flush();
        return bytes.toByteArray();
    }

    static List<String> decode(byte[] raw,long now) throws IOException {
        if(raw==null || raw.length>MAX_BYTES || raw.length<16 || now<=0) { throw new IOException("Invalid snapshot"); }
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(raw));
        if(in.readInt()!=MAGIC) { throw new IOException("Unknown snapshot version"); }
        long saved=in.readLong();
        if(saved<=0 || saved>now && saved-now>FUTURE_TOLERANCE
                || saved<=now && now-saved>MAX_AGE) { throw new IOException("Expired snapshot"); }
        int count=in.readInt();
        if(count<0 || count>MAX_ROWS) { throw new IOException("Invalid row count"); }
        List<String> rows=new ArrayList<>(count);
        for(int i=0;i<count;i++) {
            int n=in.readInt();
            if(n<=0 || n>65536 || n>in.available()) { throw new IOException("Invalid row length"); }
            byte[] data=new byte[n];
            in.readFully(data);
            String row=StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
            if(!valid(row)) { throw new IOException("Invalid game row"); }
            rows.add(row);
        }
        if(in.read()!=-1) { throw new IOException("Unexpected trailing data"); }
        return rows;
    }

    private static boolean valid(String row) {
        return row!=null && row.indexOf('\n')<0 && row.indexOf('\r')<0 && row.indexOf('\0')<0
                && PcGame.parse(row)!=null;
    }
}
