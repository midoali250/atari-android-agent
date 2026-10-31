package com.midoali.atari;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import com.midoali.atari.core.ControlLayout;
import com.midoali.atari.core.PadMath;
import com.midoali.atari.core.SegaButtons;
import java.util.function.IntConsumer;

/** Multi-pointer controls. Every pointer contributes to the mask sent to the actual core. */
public final class TouchControlsView extends View {
    private static final int[] FACE_IDS = {SegaButtons.A, SegaButtons.B, SegaButtons.C};
    private static final String[] FACE_LABELS = {"A", "B", "C"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseArray<Integer> pointerMasks = new SparseArray<>();
    private final IntConsumer inputConsumer;
    private final Runnable layoutChanged;
    private ControlLayout layout;
    private boolean editing, interactive = true;
    private int currentMask, dragGroup = -1, dragPointer = -1;
    private float density, padRadius, faceRadius, startWidth, startHeight;
    private float dragOffsetX, dragOffsetY;

    public TouchControlsView(Context context, ControlLayout layout, IntConsumer input, Runnable changed) {
        super(context);
        this.layout = layout; inputConsumer = input; layoutChanged = changed;
        density = getResources().getDisplayMetrics().density;
        setContentDescription(context.getString(R.string.touch_controls_description));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    public void setLayout(ControlLayout value) { layout = value; invalidate(); }
    public void setEditing(boolean value) {
        editing = value; clearInput(); dragGroup = dragPointer = -1; invalidate();
    }
    public boolean isEditing() { return editing; }
    public void setInteractive(boolean value) { interactive = value; if (!value) clearInput(); invalidate(); }
    public int currentMask() { return currentMask; }
    public float centerX(int group) { return getWidth() * (group == 0 ? layout.padX : group == 1 ? layout.faceX : layout.startX); }
    public float centerY(int group) { return getHeight() * (group == 0 ? layout.padY : group == 1 ? layout.faceY : layout.startY); }
    public float faceX(int index) { return centerX(1) + (index - 1) * faceRadius * 2.25f; }
    public float faceY(int index) { return centerY(1) + (index == 1 ? -faceRadius * 0.42f : faceRadius * 0.22f); }
    public float faceRadius() { return faceRadius; }
    public float padRadius() { return padRadius; }
    public void clearInput() {
        pointerMasks.clear(); currentMask = 0; inputConsumer.accept(0); invalidate();
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float base = Math.min(height * 0.20f, width * 0.115f);
        padRadius = base * layout.scale;
        faceRadius = base * 0.38f * layout.scale;
        startWidth = 76 * density * layout.scale; startHeight = 34 * density * layout.scale;
    }
    public void refreshSizes() { onSizeChanged(getWidth(), getHeight(), 0, 0); invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0) return;
        int alpha = Math.round(layout.opacity * 255);
        float padX = centerX(0), padY = centerY(0), r = padRadius;
        // Cross-shaped Genesis-style D-pad, with visible diagonal touch regions.
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(alpha, 26, 31, 40));
        RectF crossV = new RectF(padX-r*0.36f, padY-r, padX+r*0.36f, padY+r);
        RectF crossH = new RectF(padX-r, padY-r*0.36f, padX+r, padY+r*0.36f);
        canvas.drawRoundRect(crossV, r*.14f, r*.14f, paint); canvas.drawRoundRect(crossH, r*.14f, r*.14f, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2*density);
        paint.setColor(editing ? Color.rgb(255,190,95) : Color.argb(alpha,135,151,168));
        canvas.drawCircle(padX,padY,r,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(alpha, 100,116,135));
        float arrow = r*.12f;
        drawTriangle(canvas,padX,padY-r*.72f,arrow,0);
        drawTriangle(canvas,padX+r*.72f,padY,arrow,90);
        drawTriangle(canvas,padX,padY+r*.72f,arrow,180);
        drawTriangle(canvas,padX-r*.72f,padY,arrow,270);
        paint.setColor(Color.argb(alpha,48,58,72)); canvas.drawCircle(padX,padY,r*.23f,paint);
        int[] ids = {SegaButtons.UP,SegaButtons.RIGHT,SegaButtons.DOWN,SegaButtons.LEFT};
        float[][] positions = {{padX,padY-r*.55f},{padX+r*.55f,padY},{padX,padY+r*.55f},{padX-r*.55f,padY}};
        for (int i=0;i<4;i++) if (pressed(ids[i])) {
            paint.setColor(Color.argb(160,255,184,107)); canvas.drawCircle(positions[i][0],positions[i][1],r*.22f,paint);
        }
        for (int i=0;i<3;i++) drawFace(canvas,faceX(i),faceY(i),FACE_LABELS[i],pressed(FACE_IDS[i]),alpha);
        float sx=centerX(2), sy=centerY(2);
        paint.setStyle(Paint.Style.FILL); paint.setColor(pressed(SegaButtons.START) ? Color.rgb(180,53,61) : Color.argb(alpha,37,42,50));
        canvas.drawRoundRect(new RectF(sx-startWidth/2,sy-startHeight/2,sx+startWidth/2,sy+startHeight/2),startHeight/2,startHeight/2,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(editing ? Color.rgb(255,190,95) : Color.argb(alpha,135,151,168));
        canvas.drawRoundRect(new RectF(sx-startWidth/2,sy-startHeight/2,sx+startWidth/2,sy+startHeight/2),startHeight/2,startHeight/2,paint);
        label(canvas,"START",sx,sy,11*density*layout.scale,Color.argb(alpha,235,238,243));
        if (editing) {
            label(canvas,getContext().getString(R.string.drag_controls),getWidth()/2f,30*density,15*density,Color.rgb(255,190,107));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density);
            canvas.drawCircle(centerX(1),centerY(1),faceRadius*3.8f,paint);
        }
    }
    private void drawFace(Canvas canvas,float x,float y,String text,boolean down,int alpha) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(down ? Color.rgb(160,40,52) : Color.argb(alpha,19,22,29));
        canvas.drawCircle(x,y+3*density,faceRadius,paint);
        paint.setColor(down ? Color.rgb(248,91,100) : Color.argb(alpha,190,48,61));
        canvas.drawCircle(x,y,faceRadius*.91f,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2*density); paint.setColor(Color.argb(alpha,248,133,142));
        canvas.drawCircle(x,y,faceRadius*.91f,paint);
        label(canvas,text,x,y,faceRadius*.70f,Color.WHITE);
    }
    private void label(Canvas canvas,String text,float x,float y,float size,int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true);
        canvas.drawText(text,x,y-(paint.ascent()+paint.descent())/2,paint); paint.setFakeBoldText(false);
    }
    private void drawTriangle(Canvas canvas,float x,float y,float size,float rotation) {
        canvas.save(); canvas.rotate(rotation,x,y);
        android.graphics.Path path=new android.graphics.Path(); path.moveTo(x,y-size); path.lineTo(x-size,y+size); path.lineTo(x+size,y+size); path.close();
        canvas.drawPath(path,paint); canvas.restore();
    }
    private boolean pressed(int id) { return SegaButtons.pressed(currentMask,id); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked(), index=event.getActionIndex(), pointer=event.getPointerId(index);
        if (action==MotionEvent.ACTION_CANCEL) { clearInput(); dragGroup=dragPointer=-1; if(editing)layoutChanged.run(); return true; }
        if (action==MotionEvent.ACTION_UP) {
            if (editing && dragPointer==pointer) { dragGroup=dragPointer=-1; layoutChanged.run(); }
            pointerMasks.remove(pointer); rebuildMask(); return true;
        }
        if (editing) return touchEdit(event,action,index,pointer);
        if (!interactive) return false;
        if (action==MotionEvent.ACTION_POINTER_UP) pointerMasks.remove(pointer);
        else {
            for (int i=0;i<event.getPointerCount();i++) {
                int id=event.getPointerId(i);
                if (action==MotionEvent.ACTION_POINTER_UP && id==pointer) continue;
                pointerMasks.put(id,hitMask(event.getX(i),event.getY(i)));
            }
        }
        rebuildMask(); return true;
    }
    private boolean touchEdit(MotionEvent event,int action,int index,int pointer) {
        float x=event.getX(index),y=event.getY(index);
        if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            int group=hitGroup(x,y);
            if (group>=0 && dragGroup<0) {
                dragGroup=group;dragPointer=pointer;dragOffsetX=x-centerX(group);dragOffsetY=y-centerY(group);
            }
        } else if (action==MotionEvent.ACTION_MOVE && dragGroup>=0) {
            int active=event.findPointerIndex(dragPointer);
            if (active>=0) { layout.move(dragGroup,(event.getX(active)-dragOffsetX)/getWidth(),(event.getY(active)-dragOffsetY)/getHeight());invalidate(); }
        } else if (action==MotionEvent.ACTION_POINTER_UP && pointer==dragPointer) {
            dragGroup=dragPointer=-1;layoutChanged.run();
        }
        return true;
    }
    private void rebuildMask() {
        int mask=0;
        for(int i=0;i<pointerMasks.size();i++) mask|=pointerMasks.valueAt(i);
        if ((mask&(1<<SegaButtons.UP))!=0 && (mask&(1<<SegaButtons.DOWN))!=0) mask&=~((1<<SegaButtons.UP)|(1<<SegaButtons.DOWN));
        if ((mask&(1<<SegaButtons.LEFT))!=0 && (mask&(1<<SegaButtons.RIGHT))!=0) mask&=~((1<<SegaButtons.LEFT)|(1<<SegaButtons.RIGHT));
        if(mask!=currentMask && mask!=0 && layout.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        currentMask=mask;inputConsumer.accept(mask);invalidate();
    }
    int hitMask(float x,float y) {
        int mask=PadMath.direction(x-centerX(0),y-centerY(0),padRadius);
        for(int i=0;i<3;i++) if(PadMath.inCircle(x,y,faceX(i),faceY(i),faceRadius*1.12f)) mask|=1<<FACE_IDS[i];
        if(Math.abs(x-centerX(2))<=startWidth/2 && Math.abs(y-centerY(2))<=startHeight/2) mask|=1<<SegaButtons.START;
        return mask;
    }
    private int hitGroup(float x,float y) {
        if(PadMath.inCircle(x,y,centerX(0),centerY(0),padRadius*1.2f)) return 0;
        if(PadMath.inCircle(x,y,centerX(1),centerY(1),faceRadius*3.8f)) return 1;
        if(Math.abs(x-centerX(2))<startWidth && Math.abs(y-centerY(2))<startHeight) return 2;
        return -1;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
