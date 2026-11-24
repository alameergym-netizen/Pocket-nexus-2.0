package com.limelight.pocket.retro;

import com.limelight.pocket.home.HomeItem;

/** Source-aware launch entries in the unified catalog. No migration of legacy artwork IDs. */
public final class HorizonRetroCatalog {
    public static final int MAX_ROWS=30000;
    private HorizonRetroCatalog() {}
    public static HomeItem item(String source,RomScanner.Rom rom) {
        String root=HorizonRomSources.normalize(source);
        if(root==null || rom==null || !HorizonRetroIndex.valid(rom.system,rom.relPath)) { return null; }
        return new HomeItem(HomeItem.RETRO,RomScanner.searchName(rom.name),rom.system,rom.relPath,root,0);
    }
    public static String label(String system,String source) {
        String name=system==null?"":system;
        String root=HorizonRomSources.normalize(source);
        return root==null?name:name+" · "+root;
    }
}
