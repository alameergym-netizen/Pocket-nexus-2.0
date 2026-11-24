package com.limelight.pocket.pclib;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** App-private, no-backup, per-PC cache. Disk access is confined to library worker calls. */
final class HorizonLibraryStore {
    private HorizonLibraryStore() {}

    private static AtomicFile file(Context context,String uuid,boolean create) throws Exception {
        if(uuid==null || uuid.trim().isEmpty() || uuid.length()>512) { return null; }
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(uuid.getBytes(StandardCharsets.UTF_8));
        StringBuilder name=new StringBuilder(64);
        for(byte b:hash) {
            int v=b&255;
            name.append(Character.forDigit(v>>>4,16)).append(Character.forDigit(v&15,16));
        }
        File dir=new File(context.getNoBackupFilesDir(),"horizon_pc_library");
        if(!dir.isDirectory() && (!create || !dir.mkdirs() && !dir.isDirectory())) { return null; }
        return new AtomicFile(new File(dir,name.toString()+".bin"));
    }

    static synchronized void save(Context c,String uuid,List<String> rows) {
        AtomicFile target=null;
        FileOutputStream out=null;
        try {
            byte[] data=HorizonLibrarySnapshot.encode(rows,System.currentTimeMillis());
            target=file(c,uuid,true);
            if(target==null) { return; }
            out=target.startWrite();
            out.write(data);
            target.finishWrite(out);
            out=null;
        } catch(Exception e) {
            if(target!=null && out!=null) {
                try { target.failWrite(out); } catch(RuntimeException ignored) {}
            }
        }
    }

    static synchronized List<String> load(Context c,String uuid) {
        AtomicFile target=null;
        try {
            target=file(c,uuid,false);
            if(target==null) { return null; }
            try(FileInputStream in=target.openRead()) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                byte[] chunk=new byte[8192];
                int n;
                while((n=in.read(chunk))!=-1) {
                    if(n>HorizonLibrarySnapshot.MAX_BYTES-bytes.size()) {
                        throw new java.io.IOException("Snapshot too large");
                    }
                    bytes.write(chunk,0,n);
                }
                return HorizonLibrarySnapshot.decode(bytes.toByteArray(),System.currentTimeMillis());
            }
        } catch(java.io.FileNotFoundException absent) { return null; }
        catch(Exception invalid) {
            if(target!=null) { try { target.delete(); } catch(RuntimeException ignored) {} }
            return null;
        }
    }

    static synchronized void clear(Context c,String uuid) {
        try {
            AtomicFile target=file(c,uuid,false);
            if(target!=null) { target.delete(); }
        } catch(Exception ignored) {}
    }
}
