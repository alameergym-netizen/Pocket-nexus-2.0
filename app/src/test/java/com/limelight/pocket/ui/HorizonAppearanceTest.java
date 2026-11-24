package com.limelight.pocket.ui;
import org.junit.Test;
import static org.junit.Assert.*;

public class HorizonAppearanceTest {
    @Test public void densityDefaultsToClassic() {
        assertEquals(1f, HorizonAppearance.cardScale(-1,true,600),0.001f);
        assertEquals(1f, HorizonAppearance.cardScale(0,true,600),0.001f);
    }
    @Test public void compactAndLargeGeometry() {
        assertEquals(0.88f,HorizonAppearance.cardScale(1,true,400),0.001f);
        assertEquals(1.12f,HorizonAppearance.cardScale(2,false,400),0.001f);
        assertEquals(1.12f,HorizonAppearance.cardScale(2,true,480),0.001f);
    }
    @Test public void shortShelvesPreserveSpace() {
        assertEquals(1f,HorizonAppearance.cardScale(2,true,479),0.001f);
    }
    @Test public void cornersAreBounded() {
        assertEquals(4f,HorizonAppearance.radius(1,16,84),0.001f);
        assertEquals(22f,HorizonAppearance.radius(2,16,84),0.001f);
        assertEquals(10f,HorizonAppearance.radius(2,16,20),0.001f);
        assertEquals(16f,HorizonAppearance.radius(0,16,84),0.001f);
    }
}
