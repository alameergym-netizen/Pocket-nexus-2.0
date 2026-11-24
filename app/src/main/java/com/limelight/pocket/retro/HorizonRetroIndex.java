package com.limelight.pocket.retro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Bounded, versioned per-source scan index; does not change legacy game IDs. */
final class HorizonRetroIndex {
    static final int MAX_ROWS=100000;
    static final int MAX_BYTES=16*1024*1024;
    private static final int MAGIC=0x48524931; // HRI1
    private HorizonRetroIndex() {}
    static final class Row {
        final String system,relative;
        final boolean directory;
        Row(String system,String relative,boolean directory) {
            this.system=system;this.relative=relative;this.directory=directory;
        }
    }
    static boolean valid(String system,String relative) {
        if(system==null || !system.matches("[A-Za-z0-9_-]{1,128}")
                || relative==null || relative.isEmpty() || relative.length()>4096
                || relative.startsWith("/") || relative.indexOf('\\')>=0) { return false; }
        for(String part:relative.split("/",-1)) {
            if(part.isEmpty() || part.equals(".") || part.equals("..")) { return false; }
        }
        for(int i=0;i<relative.length();i++) {
            if(Character.isISOControl(relative.charAt(i))) { return false; }
        }
        return true;
    }
    static boolean inside(String parent,String child) {
        return parent!=null && child!=null && !parent.equals(child)
                && child.startsWith(parent.endsWith("/")?parent:parent+"/");
    }
    static String key(String root) throws Exception {
        String path=HorizonRomSources.normalize(root);
        if(path==null) { throw new IOException("Invalid source"); }
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8));
        StringBuilder name=new StringBuilder(64);
        for(byte b:hash) {
            int v=b&255;
            name.append(Character.forDigit(v>>>4,16)).append(Character.forDigit(v&15,16));
        }
        return name.toString();
    }
    static byte[] encode(String root,List<Row> rows) throws IOException {
        String normalized=HorizonRomSources.normalize(root);
        if(normalized==null || rows==null || rows.size()>MAX_ROWS) { throw new IOException("Invalid index"); }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        write(out,bytes,normalized);
        out.writeInt(rows.size());
        for(Row row:rows) {
            if(row==null || !valid(row.system,row.relative)) { throw new IOException("Invalid row"); }
            write(out,bytes,row.system);
            write(out,bytes,row.relative);
            out.writeByte(row.directory?1:0);
        }
        out.flush();
        if(bytes.size()>MAX_BYTES) { throw new IOException("Index too large"); }
        return bytes.toByteArray();
    }
    static List<Row> decode(String root,byte[] raw) throws IOException {
        String normalized=HorizonRomSources.normalize(root);
        if(normalized==null || raw==null || raw.length>MAX_BYTES) { throw new IOException("Invalid index"); }
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(raw));
        if(in.readInt()!=MAGIC || !normalized.equals(read(in))) { throw new IOException("Wrong source or version"); }
        int count=in.readInt();
        if(count<0 || count>MAX_ROWS) { throw new IOException("Invalid count"); }
        List<Row> rows=new ArrayList<>(count);
        for(int i=0;i<count;i++) {
            String system=read(in),relative=read(in);
            int kind=in.readUnsignedByte();
            if(!valid(system,relative) || kind>1) { throw new IOException("Invalid row"); }
            rows.add(new Row(system,relative,kind==1));
        }
        if(in.read()!=-1) { throw new IOException("Trailing bytes"); }
        return rows;
    }
    private static void write(DataOutputStream out,ByteArrayOutputStream bytes,String value) throws IOException {
        byte[] data=value.getBytes(StandardCharsets.UTF_8);
        if(data.length>16384 || data.length>MAX_BYTES-bytes.size()-5) { throw new IOException("Index too large"); }
        out.writeInt(data.length);out.write(data);
    }
    private static String read(DataInputStream in) throws IOException {
        int n=in.readInt();
        if(n<=0 || n>16384 || n>in.available()) { throw new IOException("Invalid field"); }
        byte[] data=new byte[n];in.readFully(data);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
    }
}
