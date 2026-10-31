package com.midoali.atari.core;
import static org.junit.Assert.*;
import org.junit.Test;
public final class ControlsTest {
    @Test public void eightDirectionsAndDeadZone() {
        assertEquals(0,PadMath.direction(0,0,100));
        assertEquals(1<<SegaButtons.RIGHT,PadMath.direction(80,0,100));
        assertEquals(1<<SegaButtons.LEFT,PadMath.direction(-80,0,100));
        assertEquals(1<<SegaButtons.UP,PadMath.direction(0,-80,100));
        assertEquals(1<<SegaButtons.DOWN,PadMath.direction(0,80,100));
        assertEquals((1<<SegaButtons.UP)|(1<<SegaButtons.RIGHT),PadMath.direction(60,-60,100));
        assertEquals(0,PadMath.direction(150,0,100));
    }
    @Test public void genesisFaceMappingIsDistinct() {
        assertNotEquals(SegaButtons.A,SegaButtons.B);assertNotEquals(SegaButtons.B,SegaButtons.C);
        assertEquals(1,SegaButtons.A);assertEquals(0,SegaButtons.B);assertEquals(8,SegaButtons.C);
        assertEquals(0,SegaButtons.sanitize(1<<20));
        assertTrue(SegaButtons.pressed(SegaButtons.sanitize((1<<SegaButtons.C)|(1<<SegaButtons.RIGHT)),SegaButtons.C));
    }
    @Test public void layoutIsBoundedAndPersistenceModelCopies() {
        ControlLayout layout=new ControlLayout();layout.move(0,99,-99);assertEquals(.88f,layout.padX,.001);assertEquals(.22f,layout.padY,.001);
        layout.setScale(8);layout.setOpacity(-1);assertEquals(1.5f,layout.scale,.001);assertEquals(.3f,layout.opacity,.001);
        ControlLayout copy=layout.copy();layout.reset();assertEquals(1.5f,copy.scale,.001);assertEquals(.16f,layout.padX,.001);assertTrue(copy.bilinear);
    }
}
