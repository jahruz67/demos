package dev.demos.graphing.geogebra;

import org.geogebra.common.euclidian.MoveMode;
import org.geogebra.common.euclidian.controller.MouseTouchGestureController;
import org.geogebra.common.euclidian.event.AbstractEvent;
import org.geogebra.common.geogebra3D.euclidian3D.EuclidianController3D;
import org.geogebra.common.jre.headless.EuclidianController3DNoGui;
import org.geogebra.common.kernel.Kernel;
import org.geogebra.common.main.App;
import org.geogebra.common.util.MyMath;

/** DOM-free port of EuclidianController3DW, including its two-finger arbitration. */
final class NativeController extends EuclidianController3D {
    private int oldCenterX3D,oldCenterY3D;
    private long pressTime;
    NativeController(App app,Kernel kernel){super(app);setKernel(kernel);}
    @Override public void setView(org.geogebra.common.euclidian.EuclidianView view){super.setView(view);setView3D(view);}
    @Override protected void resetToolTipManager(){}
    @Override public void wrapMousePressed(AbstractEvent event){
        if(event instanceof NativePointer)pressTime=((NativePointer)event).timeMillis;
        super.wrapMousePressed(event);
    }
    @Override public void wrapMouseDragged(AbstractEvent event,boolean capture){
        // DOM events run synchronously; native events cross the GL worker queue.
        // Backdate the original controller clock by the actual input duration so
        // its 100 ms drag threshold survives a batch of coalesced native moves.
        if(event instanceof NativePointer)lastMousePressedTime=System.currentTimeMillis()-Math.max(0,((NativePointer)event).timeMillis-pressTime);
        super.wrapMouseDragged(event,capture);
    }
    @Override protected void processMouseMoved(AbstractEvent event){super.processMouseMoved(event);processMouseMoved();}
    @Override public void update(){} // Shader picking is synchronous, exactly as in the web controller.
    @Override protected void updateSelectionRectangle(boolean keepRatio){}
    @Override public void twoTouchStart(double x1,double y1,double x2,double y2){
        oldCenterX3D=(int)(x1+x2)/2;oldCenterY3D=(int)(y1+y2)/2;
        twoTouchStartCommon(x1,y1,x2,y2);
    }
    @Override public void twoTouchMove(double x1,double y1,double x2,double y2){
        int centerX=(int)((x1+x2)/2),centerY=(int)((y1+y2)/2);
        double newZoomDistance=0,zoomDiff=0;
        if(getOldDistance()>0){newZoomDistance=MyMath.length(x1-x2,y1-y2);zoomDiff=Math.abs(newZoomDistance-getOldDistance());if(zoomDiff<MINIMAL_PIXEL_DIFFERENCE_FOR_ZOOM)zoomDiff=0;}
        double centerDiff=MyMath.length(oldCenterX3D-centerX,oldCenterY3D-centerY);
        if(centerDiff<=MouseTouchGestureController.MIN_MOVE)centerDiff=0;
        if(2*centerDiff>zoomDiff){
            getView().rememberOrigins();getView().setCoordSystemFromMouseMove(centerX-oldCenterX3D,centerY-oldCenterY3D,MoveMode.ROTATE_VIEW);
            viewRotationOccurred=true;getView().repaintView();
            oldCenterX3D=centerX;oldCenterY3D=centerY;setOldDistance(newZoomDistance);
        }else if(zoomDiff>0){
            onPinch(centerX,centerY,newZoomDistance/getOldDistance());
            oldCenterX3D=centerX;oldCenterY3D=centerY;setOldDistance(newZoomDistance);
        }
    }
}
