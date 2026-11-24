package com.limelight.pocket.cloud;
import com.limelight.pocket.core.HorizonLaunchRequests;
import com.limelight.pocket.core.HorizonLaunchTarget;
import com.limelight.pocket.home.HomeItem;
import org.junit.Test;
import static org.junit.Assert.*;
public class HorizonCloudDispatchTest {
    @Test public void homeAndCatalogShareIdentity() {
        assertEquals(HorizonLaunchTarget.cloud("xcloud","https://example.org/game"),
                HorizonLaunchTarget.from(new HomeItem(HomeItem.CLOUD,"Title","xcloud","https://example.org/game","id",0)));
        assertEquals(HorizonLaunchTarget.cloud("xcloud",null),HorizonLaunchTarget.cloud("xcloud",""));
    }
    @Test public void repeatedDispatchIsSuppressed() {
        HorizonLaunchRequests r=new HorizonLaunchRequests();
        assertTrue(HorizonCloudDispatch.claim(r,"xcloud",null,100));
        assertFalse(HorizonCloudDispatch.claim(r,"xcloud","",200));
        assertTrue(HorizonCloudDispatch.claim(r,"xcloud",null,800));
    }
    @Test public void differentServicesAndGamesAreSeparate() {
        HorizonLaunchRequests r=new HorizonLaunchRequests();
        assertTrue(HorizonCloudDispatch.claim(r,"xcloud","one",100));
        assertTrue(HorizonCloudDispatch.claim(r,"xcloud","two",101));
        assertTrue(HorizonCloudDispatch.claim(r,"gfn","two",102));
    }
    @Test public void newCloudDispatchInvalidatesPendingRetro() {
        HorizonLaunchRequests r=new HorizonLaunchRequests();
        long old=r.begin(HorizonLaunchTarget.from(new HomeItem(HomeItem.RETRO,"Game","ps2","game.iso","sd",0)),100);
        assertTrue(HorizonCloudDispatch.claim(r,"xcloud",null,101));
        assertFalse(r.claim(old,102));
    }
    @Test public void dispatchIsNotConfirmedGameplay() {
        for(HorizonCloudDispatch.Result result:HorizonCloudDispatch.Result.values()) {
            assertFalse(result.gameplayConfirmed());
            assertEquals(result!=HorizonCloudDispatch.Result.REJECTED,result.dispatched());
        }
    }
    @Test public void invalidServiceDoesNotConsumeExistingRequest() {
        HorizonLaunchRequests r=new HorizonLaunchRequests();
        long old=r.begin(HorizonLaunchTarget.pc("pc",1),100);
        assertFalse(HorizonCloudDispatch.claim(r,"",null,101));
        assertTrue(r.claim(old,102));
    }
}
