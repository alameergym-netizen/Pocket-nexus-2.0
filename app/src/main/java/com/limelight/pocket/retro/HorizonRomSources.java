package com.limelight.pocket.retro;

import java.util.ArrayList;
import java.util.List;

/** Explicitly remembered ROM folders, not a combined library or volume identity. */
final class HorizonRomSources {
    static final String KEY="horizon_rom_sources";
    static final int LIMIT=8;
    private HorizonRomSources() {}
    static String normalize(String path) {
        if(path==null || path.length()>4096 || !path.startsWith("/") || path.indexOf('\0')>=0
                || path.indexOf('\n')>=0 || path.indexOf('\r')>=0 || path.indexOf('\t')>=0) { return null; }
        StringBuilder out=new StringBuilder();
        for(String part:path.split("/")) {
            if(part.isEmpty()) { continue; }
            if(part.equals(".") || part.equals("..")) { return null; }
            out.append('/').append(part);
        }
        return out.length()==0?null:out.toString();
    }
    static List<String> remember(List<String> old,String path) {
        List<String> out=new ArrayList<>();
        String normalized=normalize(path);
        if(normalized!=null) { out.add(normalized); }
        if(old!=null) {
            for(String p:old) {
                String n=normalize(p);
                if(n!=null && !out.contains(n) && out.size()<LIMIT) { out.add(n); }
            }
        }
        return out;
    }
    static List<String> decode(String encoded) {
        List<String> out=new ArrayList<>();
        if(encoded==null || encoded.length()>LIMIT*4097) { return out; }
        for(String p:encoded.split("\n",-1)) {
            String n=normalize(p);
            if(n!=null && !out.contains(n) && out.size()<LIMIT) { out.add(n); }
        }
        return out;
    }
    static String encode(List<String> roots) {
        StringBuilder out=new StringBuilder();
        for(String path:remember(roots,null)) {
            if(out.length()>0) { out.append('\n'); }
            out.append(path);
        }
        return out.toString();
    }
}
