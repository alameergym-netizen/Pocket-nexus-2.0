package com.limelight.pocket.retro;
import org.junit.Test;
import java.util.Arrays;
import java.util.ArrayList;
import static org.junit.Assert.*;
public class HorizonRomSourcesTest {
    @Test public void normalizesSeparatorsButNotTraversal() {
        assertEquals("/storage/1234/ROMs",HorizonRomSources.normalize("/storage//1234/ROMs/"));
        assertNull(HorizonRomSources.normalize("/storage/../ROMs"));
        assertNull(HorizonRomSources.normalize("ROMs"));
        assertNull(HorizonRomSources.normalize("/"));
        assertNull(HorizonRomSources.normalize("/storage/a\nb"));
    }
    @Test public void remembersDistinctPathsInSelectionOrder() {
        assertEquals(Arrays.asList("/storage/b/ROMs","/storage/a/ROMs"),
            HorizonRomSources.remember(Arrays.asList("/storage/a/ROMs","/storage/b/ROMs/"),"/storage/b/ROMs"));
    }
    @Test public void roundTrip() {
        assertEquals(Arrays.asList("/storage/a/ROMs","/storage/b/ROMs"),
            HorizonRomSources.decode(HorizonRomSources.encode(Arrays.asList("/storage/a/ROMs","/storage/b/ROMs"))));
    }
    @Test public void limitsRegistry() {
        ArrayList<String> roots=new ArrayList<>();
        for(int i=0;i<20;i++) { roots.add("/storage/"+i+"/ROMs"); }
        assertEquals(8,HorizonRomSources.remember(roots,"/storage/new/ROMs").size());
    }
    @Test public void invalidEntriesIgnored() {
        assertEquals(Arrays.asList("/storage/a"),HorizonRomSources.decode("relative\n/storage/a\n/storage/a/\n/../x"));
    }
}
