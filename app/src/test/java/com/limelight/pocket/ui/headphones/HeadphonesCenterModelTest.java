package com.limelight.pocket.ui.headphones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Cell;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Charge;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Level;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Mode;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Phase;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.State;

import org.junit.Test;

public class HeadphonesCenterModelTest {
    private static State live(Mode mode, Level level) {
        return new State(Phase.LIVE, "HUAWEI FreeBuds 7i", new Cell(80, false), new Cell(75, false),
                new Cell(40, true), mode, level, null, 1_000);
    }

    @Test
    public void onlyTheEarbudsReadbackMarksAModeActive() {
        State s = live(Mode.ANC, Level.DEEP);
        assertTrue(s.active(Mode.ANC));
        assertTrue(s.active(Level.DEEP));
        assertFalse(s.active(Level.LIGHT));
        assertFalse(s.active(Mode.OFF));
        assertTrue(s.controllable());

        State unread = new State(Phase.UNREACHABLE, "HUAWEI FreeBuds 7i", null, null, null, Mode.ANC, Level.DEEP,
                null, 1_000);
        assertFalse("a stale mode is not shown as active", unread.active(Mode.ANC));
        assertFalse(unread.controllable());
        assertFalse("nothing read", State.detecting().verified());
    }

    @Test
    public void aLevelOnlyExistsInAncAndCommandsWaitForTheOneInFlight() {
        State awareness = live(Mode.AWARENESS, Level.DEEP);
        assertNull(awareness.level);
        assertFalse(awareness.active(Level.DEEP));

        State applying = new State(Phase.APPLYING, "Buds", null, null, null, Mode.OFF, null, Mode.ANC, 1_000);
        assertTrue("the old mode stays marked until the earbuds confirm", applying.active(Mode.OFF));
        assertFalse(applying.controllable());
        assertEquals(Mode.ANC, applying.pending);
        assertNull("only a mode or a level can be pending",
                new State(Phase.APPLYING, "Buds", null, null, null, Mode.OFF, null, "x", 0).pending);
    }

    @Test
    public void readbackCodesMapToModesAndLevels() {
        assertEquals(Level.LIGHT, HeadphonesCenterModel.levelOf(0));
        assertEquals(Level.ADAPTIVE, HeadphonesCenterModel.levelOf(1));
        assertEquals(Level.BALANCED, HeadphonesCenterModel.levelOf(2));
        assertEquals(Level.DEEP, HeadphonesCenterModel.levelOf(3));
        assertNull("earbud-selected or unknown", HeadphonesCenterModel.levelOf(255));
        assertEquals(Mode.ANC, HeadphonesCenterModel.modeOf("ANC"));
        assertEquals(Mode.AWARENESS, HeadphonesCenterModel.modeOf("AWARENESS"));
        assertEquals(Mode.OFF, HeadphonesCenterModel.modeOf("OFF"));
        assertNull(HeadphonesCenterModel.modeOf("WIND"));
        assertNull(HeadphonesCenterModel.modeOf(null));
    }

    @Test
    public void batteriesAreClampedJudgedAndAged() {
        assertEquals(Charge.UNKNOWN, HeadphonesCenterModel.charge(Cell.UNKNOWN));
        assertEquals(Charge.CRITICAL, HeadphonesCenterModel.charge(new Cell(10, false)));
        assertEquals(Charge.LOW, HeadphonesCenterModel.charge(new Cell(19, false)));
        assertEquals(Charge.OK, HeadphonesCenterModel.charge(new Cell(20, false)));
        assertEquals(Integer.valueOf(100), new Cell(140, false).level);
        assertEquals(Integer.valueOf(0), new Cell(-3, false).level);
        assertEquals(12, HeadphonesCenterModel.ageSeconds(live(Mode.OFF, null), 13_500));
        assertEquals(-1, HeadphonesCenterModel.ageSeconds(State.detecting(), 13_500));
    }
}
