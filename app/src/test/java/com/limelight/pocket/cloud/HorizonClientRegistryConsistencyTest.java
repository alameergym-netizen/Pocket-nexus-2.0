package com.limelight.pocket.cloud;

import com.limelight.pocket.clients.HorizonClientRegistry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The registry describes the cloud apps the rest of Pocket Nexus already opens. */
public class HorizonClientRegistryConsistencyTest {
    private static void covered(String id, String pkg) {
        HorizonClientRegistry.Client c = HorizonClientRegistry.byId(id);
        assertEquals(HorizonClientRegistry.Mode.INSTALLED_APP, c.mode);
        assertTrue(id + " should recognise " + pkg, c.matches(pkg));
    }

    @Test
    public void officialCloudAppsMatchTheCloudServices() {
        for (String p : CloudService.XCLOUD.officialPackages) {
            covered("xbox-app", p);
        }
        for (String p : CloudService.GFN.officialPackages) {
            covered("gfn-app", p);
        }
        for (String p : CloudService.BOOSTEROID.officialPackages) {
            covered("boosteroid-app", p);
        }
        assertEquals(CloudService.XCLOUD.url, HorizonClientRegistry.byId("xcloud-player").link);
        assertEquals(CloudService.GFN.url, HorizonClientRegistry.byId("gfn-player").link);
        assertEquals(CloudService.BOOSTEROID.url, HorizonClientRegistry.byId("boosteroid-player").link);
    }

    @Test
    public void optionalCloudClientsMatchTheOnesOffered() {
        covered("better-xcloud", CloudClients.BETTER_XCLOUD_ANDROID);
        covered("xstreaming", "com.xstreaming");
        covered("ps-remote", "com.playstation.remoteplay");
    }
}
