package dev.demos.graphing.geogebra;
import org.geogebra.common.awt.GPoint;
import org.geogebra.common.euclidian.event.*;

/** Coordinates are logical pixels, matching PointerEvent in the reference. */
public final class NativePointer extends AbstractEvent {
    private final int x,y,button,modifiers,clicks;private final boolean touch;
    final long timeMillis;
    public NativePointer(int x,int y,int button,int modifiers,int clicks,boolean touch){this(x,y,button,modifiers,clicks,touch,System.nanoTime()/1000000L);}
    public NativePointer(int x,int y,int button,int modifiers,int clicks,boolean touch,long timeMillis){this.x=x;this.y=y;this.button=button;this.modifiers=modifiers;this.clicks=clicks;this.touch=touch;this.timeMillis=timeMillis;}
    public GPoint getPoint(){return new GPoint(x,y);}
    public int getX(){return x;}public int getY(){return y;}
    public boolean isAltDown(){return (modifiers&1)!=0;}
    public boolean isShiftDown(){return (modifiers&2)!=0;}
    public boolean isControlDown(){return (modifiers&4)!=0;}
    public boolean isMetaDown(){return (modifiers&8)!=0;}
    public boolean isRightClick(){return button==2;}public boolean isMiddleClick(){return button==1;}
    public int getClickCount(){return clicks;}public boolean isPopupTrigger(){return false;}
    public PointerEventType getType(){return touch?PointerEventType.TOUCH:PointerEventType.MOUSE;}
    public void release(){}
}
