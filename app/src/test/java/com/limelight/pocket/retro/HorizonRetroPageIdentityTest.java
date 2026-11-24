package com.limelight.pocket.retro;
import com.limelight.pocket.home.HomeItem;
import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;
public class HorizonRetroPageIdentityTest {
    private HomeItem item(String root) { return new HomeItem(HomeItem.RETRO,"Game","ps2","Game.iso",root,0); }
    private int id() { return RomScanner.idFor("ps2","Game.iso"); }
    @Test public void preservesSourceAndRelativePath() {
        HomeItem original=item("/storage/a/ROMs");
        HomeItem restored=HorizonRetroPageIdentity.read(original.toLine(),id());
        assertNotNull(restored);
        assertEquals(original.key(),restored.key());
        assertTrue(HorizonRetroPageIdentity.matchesRoot(restored,new File("/storage/a/ROMs")));
        assertFalse(HorizonRetroPageIdentity.matchesRoot(restored,new File("/storage/b/ROMs")));
    }
    @Test public void rejectsWrongIdAndKind() {
        assertNull(HorizonRetroPageIdentity.read(item("/storage/a").toLine(),id()+1));
        assertNull(HorizonRetroPageIdentity.read(new HomeItem(HomeItem.PC,"Game","ps2","Game.iso","",0).toLine(),id()));
    }
    @Test public void rejectsInvalidRelativePathAndRoot() {
        HomeItem bad=new HomeItem(HomeItem.RETRO,"Game","ps2","../Game.iso","/storage/a",0);
        assertNull(HorizonRetroPageIdentity.read(bad.toLine(),RomScanner.idFor("ps2","../Game.iso")));
        assertNull(HorizonRetroPageIdentity.read(item("/storage/../a").toLine(),id()));
    }
    @Test public void legacyRemainsUnbound() {
        HomeItem legacy=HorizonRetroPageIdentity.read(item("").toLine(),id());
        assertNotNull(legacy);
        assertFalse(HorizonRetroPageIdentity.matchesRoot(legacy,new File("/storage/a")));
    }
}
