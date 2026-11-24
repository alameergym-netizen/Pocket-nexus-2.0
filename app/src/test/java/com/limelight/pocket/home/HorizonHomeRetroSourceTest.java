package com.limelight.pocket.home;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class HorizonHomeRetroSourceTest {
    @Rule public TemporaryFolder tmp=new TemporaryFolder();
    @Test public void explicitSourceDoesNotFollowActiveSource() throws Exception {
        File a=tmp.newFolder("a"),b=tmp.newFolder("b");
        assertEquals(a,HorizonHomeRetroSource.choose(a.getAbsolutePath(),b,
                Arrays.asList(a.getAbsolutePath(),b.getAbsolutePath())));
    }
    @Test public void legacyWithMultipleSourcesIsAmbiguous() throws Exception {
        File a=tmp.newFolder("a"),b=tmp.newFolder("b");
        assertNull(HorizonHomeRetroSource.choose("",a,Arrays.asList(a.getAbsolutePath(),b.getAbsolutePath())));
        assertEquals(a,HorizonHomeRetroSource.choose("",a,Collections.singletonList(a.getAbsolutePath())));
    }
    @Test public void forgottenOrMissingSourceIsRejected() throws Exception {
        File a=tmp.newFolder("a"),b=new File(tmp.getRoot(),"missing");
        assertNull(HorizonHomeRetroSource.choose(a.getAbsolutePath(),a,Collections.emptyList()));
        assertNull(HorizonHomeRetroSource.choose(b.getAbsolutePath(),a,Collections.singletonList(b.getAbsolutePath())));
    }
    @Test public void safeFileResolution() throws Exception {
        File root=tmp.newFolder("root"),game=new File(root,"Game.iso");
        assertTrue(game.createNewFile());
        assertEquals(game,HorizonHomeRetroSource.resolve(root,"Game.iso"));
        assertNull(HorizonHomeRetroSource.resolve(root,"../Game.iso"));
        assertNull(HorizonHomeRetroSource.resolve(root,"/Game.iso"));
        assertNull(HorizonHomeRetroSource.resolve(root,"missing.iso"));
    }
    @Test public void differentSourcesHaveDifferentHomeKeys() {
        HomeItem a=new HomeItem(HomeItem.RETRO,"Game","ps2","Game.iso","/storage/a/ROMs",0);
        HomeItem b=new HomeItem(HomeItem.RETRO,"Game","ps2","Game.iso","/storage/b/ROMs",0);
        HomeItem legacy=new HomeItem(HomeItem.RETRO,"Game","ps2","Game.iso","",0);
        assertNotEquals(a.key(),b.key());
        assertEquals("retro:ps2:Game.iso",legacy.key());
        assertEquals(a.key(),HomeItem.parse(a.toLine()).key());
    }
}
