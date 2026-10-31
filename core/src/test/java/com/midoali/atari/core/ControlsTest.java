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
    @Test public void genesisSixButtonMappingIsDistinct() {
        assertEquals(1,SegaButtons.A);assertEquals(0,SegaButtons.B);assertEquals(8,SegaButtons.C);
        assertEquals(10,SegaButtons.X);assertEquals(9,SegaButtons.Y);assertEquals(11,SegaButtons.Z);
        int[] ids={SegaButtons.A,SegaButtons.B,SegaButtons.C,SegaButtons.X,SegaButtons.Y,SegaButtons.Z};
        assertEquals(6,java.util.Arrays.stream(ids).distinct().count());
        assertEquals(0,SegaButtons.sanitize(1<<20));
        int mask=SegaButtons.sanitize((1<<SegaButtons.C)|(1<<SegaButtons.RIGHT)|(1<<SegaButtons.Z));
        assertTrue(SegaButtons.pressed(mask,SegaButtons.C));assertTrue(SegaButtons.pressed(mask,SegaButtons.Z));
        assertEquals(mask,SegaButtons.cancelOpposites(mask));
        assertEquals(0,SegaButtons.cancelOpposites((1<<SegaButtons.LEFT)|(1<<SegaButtons.RIGHT)));
    }
    @Test public void layoutIsBoundedAndPersistenceModelCopies() {
        ControlLayout layout=new ControlLayout();layout.move(0,99,-99);assertEquals(.95f,layout.padX,.001);assertEquals(.12f,layout.padY,.001);
        layout.setScale(8);layout.setOpacity(-1);assertEquals(1.5f,layout.scale,.001);assertEquals(.3f,layout.opacity,.001);
        layout.sixButton=false;layout.showBorders=false;layout.autoFrameSkip=true;layout.setVideoMode(ControlLayout.VIDEO_ORIGINAL);layout.setDisplayMode(ControlLayout.DISPLAY_STRETCH);
        ControlLayout copy=layout.copy();layout.reset();assertEquals(1.5f,copy.scale,.001);assertEquals(.155f,layout.padX,.001);
        assertFalse(copy.sixButton);assertFalse(copy.showBorders);assertTrue(copy.autoFrameSkip);
        assertEquals(ControlLayout.VIDEO_ORIGINAL,copy.videoMode);assertEquals(ControlLayout.DISPLAY_STRETCH,copy.displayMode);
        assertFalse(layout.sixButton);assertEquals(ControlLayout.VIDEO_ORIGINAL,layout.videoMode);
        assertTrue(new ControlLayout().sixButton);assertEquals(ControlLayout.VIDEO_ENHANCED,new ControlLayout().videoMode);
    }
    @Test public void displayFitNeverCropsAndStretchFills() {
        int[] wide=DisplayMath.viewport(1280,578,4f/3f,false);
        assertEquals(578,wide[3]);assertTrue(wide[2]<=1280);assertEquals((1280-wide[2])/2,wide[0]);assertEquals(0,wide[1]);
        int[] tall=DisplayMath.viewport(480,800,4f/3f,false);
        assertEquals(480,tall[2]);assertTrue(tall[3]<=800);assertEquals(0,tall[0]);assertEquals((800-tall[3])/2,tall[1]);
        assertArrayEquals(new int[]{0,0,1280,578},DisplayMath.viewport(1280,578,4f/3f,true));
        assertArrayEquals(new int[]{0,0,0,0},DisplayMath.viewport(0,100,1.3f,false));
        int[] fallback=DisplayMath.viewport(100,100,0,false);assertArrayEquals(new int[]{0,0,100,100},fallback);
        assertArrayEquals(new float[]{0,1,1,1,0,0,1,0},DisplayMath.sourceCoordinates(320,224),0.0001f);
    }
}
