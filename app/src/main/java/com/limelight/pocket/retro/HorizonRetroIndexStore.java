package com.limelight.pocket.retro;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;

/** Independent app-private indexes; only a completed encoding replaces the previous file. */
final class HorizonRetroIndexStore {
    private HorizonRetroIndexStore() {}
    private static AtomicFile file(Context c,File root,boolean create) throws Exception {
        File dir=new File(c.getNoBackupFilesDir(),"horizon_retro_index");
        if(!dir.isDirectory() && (!create || !dir.mkdirs() && !dir.isDirectory())) { return null; }
        return new AtomicFile(new File(dir,HorizonRetroIndex.key(root.getAbsolutePath())+".bin"));
    }
    static synchronized void save(Context c,File root,List<HorizonRetroIndex.Row> rows) {
        AtomicFile target=null;FileOutputStream out=null;
        try {
            byte[] data=HorizonRetroIndex.encode(root.getAbsolutePath(),rows);
            target=file(c,root,true);
            if(target==null) { return; }
            out=target.startWrite();out.write(data);target.finishWrite(out);out=null;
        } catch(Exception e) {
            if(target!=null && out!=null) { try { target.failWrite(out); } catch(RuntimeException ignored) {} }
        }
    }
    static synchronized List<HorizonRetroIndex.Row> load(Context c,File root) {
        AtomicFile target=null;
        try {
            target=file(c,root,false);
            if(target==null) { return null; }
            try(FileInputStream in=target.openRead()) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                byte[] chunk=new byte[8192];int n;
                while((n=in.read(chunk))!=-1) {
                    if(n>HorizonRetroIndex.MAX_BYTES-bytes.size()) { throw new java.io.IOException("Index too large"); }
                    bytes.write(chunk,0,n);
                }
                return HorizonRetroIndex.decode(root.getAbsolutePath(),bytes.toByteArray());
            }
        } catch(java.io.FileNotFoundException absent) { return null; }
        catch(Exception e) {
            if(target!=null) { try { target.delete(); } catch(RuntimeException ignored) {} }
            return null;
        }
    }
    static synchronized void clear(Context c,String path) {
        try {
            AtomicFile target=file(c,new File(path),false);
            if(target!=null) { target.delete(); }
        } catch(Exception ignored) {}
    }
}
