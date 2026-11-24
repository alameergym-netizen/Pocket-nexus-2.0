package com.limelight.pocket.home;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Source-bound Home shortcuts. Never silently retarget an ambiguous legacy shortcut. */
final class HorizonHomeRetroSource {
    private HorizonHomeRetroSource() {}

    static File choose(String bound,File active,List<String> remembered) {
        if (bound==null || bound.isEmpty()) {
            if (active==null || remembered==null || remembered.size()>1) { return null; }
            return readable(active)?active:null;
        }
        if (!bound.startsWith("/") || remembered==null || !remembered.contains(bound)) { return null; }
        File file=new File(bound);
        return readable(file)?file:null;
    }

    static boolean stillSelected(File original,String bound,File active,List<String> remembered) {
        File now=choose(bound,active,remembered);
        try { return now!=null && original.getCanonicalPath().equals(now.getCanonicalPath()); }
        catch(IOException | RuntimeException e) { return false; }
    }

    private static boolean readable(File file) {
        try { return file.isDirectory() && file.canRead(); }
        catch(RuntimeException e) { return false; }
    }

    static File resolve(File directory,String relative) {
        if(directory==null || relative==null || relative.isEmpty() || relative.startsWith("/")
                || relative.indexOf('\\')>=0) { return null; }
        for(String part:relative.split("/",-1)) {
            if(part.isEmpty() || part.equals(".") || part.equals("..")) { return null; }
            for(int i=0;i<part.length();i++) {
                if(Character.isISOControl(part.charAt(i))) { return null; }
            }
        }
        try {
            File file=new File(directory,relative);
            String parent=directory.getCanonicalPath(), child=file.getCanonicalPath();
            if(parent.equals(child) || !child.startsWith(parent.endsWith("/")?parent:parent+"/")) { return null; }
            return file.canRead() && (file.isFile() || file.isDirectory())?file:null;
        } catch(IOException | RuntimeException e) { return null; }
    }
}
