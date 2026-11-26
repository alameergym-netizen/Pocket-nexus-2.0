package com.limelight.pocket.device.headphones;

import static org.junit.Assert.assertEquals;

import com.limelight.pocket.ui.headphones.HeadphonesCenterModel;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Level;
import com.limelight.pocket.ui.headphones.HeadphonesCenterModel.Mode;

import org.junit.Test;

/** The view's choices map onto the tested protocol selections, and readbacks map back. */
public class HeadphonesPanelMappingTest {
    @Test
    public void ancFromTheMainRowIsTheEarbudSelectedLevel() {
        assertEquals(HeadphonesNoiseControl.Selection.ANC_AUTO, HeadphonesPanel.selectionOf(Mode.ANC));
        assertEquals(255, HeadphonesNoiseControl.Selection.ANC_AUTO.level);
        assertEquals(HeadphonesNoiseControl.Selection.AWARENESS, HeadphonesPanel.selectionOf(Mode.AWARENESS));
        assertEquals(HeadphonesNoiseControl.Selection.OFF, HeadphonesPanel.selectionOf(Mode.OFF));
    }

    @Test
    public void everyExplicitLevelReadsBackAsItself() {
        for (Level l : Level.values()) {
            HeadphonesNoiseControl.Selection s = HeadphonesPanel.selectionOf(l);
            assertEquals(HeadphonesReadback.NoiseMode.ANC, s.mode);
            assertEquals(l.name(), l, HeadphonesCenterModel.levelOf(s.level));
        }
    }

    @Test
    public void everyReadbackModeHasAViewMode() {
        for (HeadphonesReadback.NoiseMode m : HeadphonesReadback.NoiseMode.values()) {
            assertEquals(m.name(), m.name(), HeadphonesCenterModel.modeOf(m.name()).name());
        }
    }
}
