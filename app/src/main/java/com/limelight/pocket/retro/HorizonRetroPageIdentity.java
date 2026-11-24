package com.limelight.pocket.retro;

import com.limelight.pocket.home.HomeItem;
import java.io.File;

/** Carry explicit source and relative game identity across the game-page activity boundary. */
public final class HorizonRetroPageIdentity {
    private HorizonRetroPageIdentity() {}
    public static HomeItem read(String line,int appId) {
        if(line==null || line.length()>16384 || line.indexOf('\n')>=0 || line.indexOf('\r')>=0
                || line.indexOf('\0')>=0 || line.split("\t",-1).length!=6) { return null; }
        HomeItem item=HomeItem.parse(line);
        if(item==null || !HomeItem.RETRO.equals(item.kind)
                || !HorizonRetroIndex.valid(item.a,item.b)
                || RomScanner.idFor(item.a,item.b)!=appId) { return null; }
        if(!item.c.isEmpty() && !item.c.equals(HorizonRomSources.normalize(item.c))) { return null; }
        return item;
    }
    public static boolean matchesRoot(HomeItem item,File root) {
        return item!=null && root!=null && !item.c.isEmpty()
                && item.c.equals(HorizonRomSources.normalize(root.getAbsolutePath()));
    }
}
