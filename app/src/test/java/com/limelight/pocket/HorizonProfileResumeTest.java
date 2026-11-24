package com.limelight.pocket;

import android.content.Context;
import android.content.Intent;
import com.limelight.Game;
import com.limelight.pocket.core.HorizonProfileOwner;
import com.limelight.pocket.pclib.PcLaunchTicket;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class HorizonProfileResumeTest {
    private Intent game(Context c, String pc, int id) {
        return new Intent(c, Game.class).putExtra(Game.EXTRA_PC_UUID, pc)
                .putExtra(Game.EXTRA_HOST, "192.0.2.1").putExtra(Game.EXTRA_APP_ID, id);
    }
    @Test public void resumeRestoresProfileButNeverReplaysCommandToken() {
        Context c = RuntimeEnvironment.getApplication();
        Intent initial = game(c, "fixture", 1).putExtra(PcLaunchTicket.EXTRA_TOKEN, "test-only-token");
        HorizonProfileOwner owner = ResumeTicket.consumeProfileOwner(initial, 777);
        ResumeTicket.remember(c, initial, owner);
        ResumeTicket.arm(c, ResumeTicket.REASON_QUALITY, 10000, 60, -1);
        Intent resumed = ResumeTicket.take(c);
        assertNotNull(resumed);
        assertTrue(ResumeTicket.claimDispatch(resumed));
        assertFalse(resumed.hasExtra(PcLaunchTicket.EXTRA_TOKEN));
        assertEquals(10000, resumed.getIntExtra(ResumeTicket.EXTRA_BITRATE, 0));
        assertEquals(777, ResumeTicket.consumeProfileOwner(resumed, 0).profileId);
        // Metadata is consumed, not propagated into unrelated copies of the intent.
        assertEquals(1, ResumeTicket.consumeProfileOwner(resumed, 0).profileId);
        assertNull(ResumeTicket.take(c));
    }

    @Test public void newDirectStreamDoesNotInheritPriorDesktopProfile() {
        Context c = RuntimeEnvironment.getApplication();
        Intent desktop = game(c, "fixture", 1);
        ResumeTicket.remember(c, desktop, ResumeTicket.consumeProfileOwner(desktop, 777));
        Intent direct = game(c, "fixture", 2);
        ResumeTicket.remember(c, direct, ResumeTicket.consumeProfileOwner(direct, 0));
        ResumeTicket.arm(c, ResumeTicket.REASON_SUSPEND, 0, 0, -1);
        Intent resumed = ResumeTicket.take(c);
        assertNotNull(resumed);
        assertTrue(ResumeTicket.claimDispatch(resumed));
        assertEquals(2, ResumeTicket.consumeProfileOwner(resumed, 0).profileId);
    }
}
