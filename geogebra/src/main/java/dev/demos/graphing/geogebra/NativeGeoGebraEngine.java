package dev.demos.graphing.geogebra;

import java.util.*;
import org.geogebra.common.factories.UtilFactory;
import org.geogebra.common.jre.util.UtilFactoryJre;
import org.geogebra.common.kernel.ModeSetter;
import org.geogebra.common.io.XMLStringBuilder;
import org.geogebra.common.plugin.GgbAPI;
import org.geogebra.common.util.*;
import org.geogebra.common.util.debug.Log;

/** One thread owns the kernel, camera/controller, geometry buffers and native renderer. */
public final class NativeGeoGebraEngine implements AutoCloseable {
    private static final ThreadLocal<List<FrameTimer>> TIMERS=ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<Double> INPUT_TIME=new ThreadLocal<>();
    private double pointerEpoch;
    private double lastInputTime;
    private final NativeApp app;
    private final NativeView view;
    private final NativeController controller;
    private final NativeRenderer renderer;
    private final GgbAPI api;
    public NativeGeoGebraEngine(NativeText text){this(text,null);}
    public NativeGeoGebraEngine(NativeText text,NativeCas cas){
        UtilFactory.setPrototypeIfNull(new UtilFactoryJre(){
            public HttpRequest newHttpRequest(){throw new UnsupportedOperationException("The native viewport uses packaged resources");}
            public Prover newProver(){return null;}
            public double getMillisecondTime(){Double input=INPUT_TIME.get();return input==null?System.nanoTime()/1e6:input;}
            public GTimer newTimer(GTimerListener listener,int delay){FrameTimer timer=new FrameTimer(listener,delay);TIMERS.get().add(timer);return timer;}
        });
        NativeTeX.install(text);
        app=new NativeApp(new NativeAwtFactory(text),cas);
        // Editing an incomplete expression must not throw the test host's AssertionError.
        Log.setLogger(new Log(){public void print(Level level,Object message){if(level==Level.ERROR&&!"file has 3D objects".equals(String.valueOf(message)))System.err.println("GeoGebra: "+message);}});
        view=app.nativeView();
        view.setBackground(org.geogebra.common.awt.GColor.WHITE);
        controller=(NativeController)view.getEuclidianController();
        renderer=(NativeRenderer)view.getRenderer();
        api=app.getGgbApi();
        controller.setMode(0,ModeSetter.DOCK_PANEL);
        api.setErrorDialogsActive(false);
    }
    public void initialize(NativeGL gl,int width,int height){renderer.initialize(gl);resize(width,height);view.settingsChanged(app.getSettings().getEuclidian(3));renderer.drawScene();}
    public GgbAPI api(){return api;}
    public boolean isAnimating(){return view.isAnimated();}
    public boolean isSettling(){return view.isAnimated()&&!view.isRotAnimatedContinue();}
    public boolean needsAnimationFrame(){return view.isAnimated() || TIMERS.get().stream().anyMatch(FrameTimer::isRunning);}
    public void resize(int width,int height){view.resize(Math.max(1,width),Math.max(1,height));}
    public void draw(){
        long now=System.nanoTime();
        for(FrameTimer timer:new ArrayList<>(TIMERS.get()))timer.tick(now);
        renderer.drawScene();
    }
    public void bounds(double xmin,double xmax,double ymin,double ymax,double zmin,double zmax){api.setCoordSystem(xmin,xmax,ymin,ymax,zmin,zmax,false);}
    public void settings(boolean grid,boolean x,boolean y,boolean z){api.setGridVisible(3,grid);api.setAxesVisible(3,x,y,z);}
    public void axisAppearance(boolean numbers,boolean labels,String x,String y,String z){
        org.geogebra.common.main.settings.EuclidianSettings settings=app.getSettings().getEuclidian(3);
        String[] names={x,y,z};
        settings.beginBatch();
        for(int axis=0;axis<3;axis++){
            settings.setShowAxisNumbers(axis,numbers);
            settings.setAxisLabel(axis,labels?names[axis]:"");
        }
        settings.endBatch();
    }
    public void focus(boolean focused){view.setNativeFocus(focused);}
    public void key(String name,boolean down,int modifiers){app.key(name,down,modifiers);}
    public void pointer(int action,NativePointer event){
        // A release can arrive in the same native batch as the final drag. Apply
        // that upstream camera step before release starts/stops continuous spin.
        if(action==2)view.updateAnimation();
        if(action==0 || pointerEpoch==0)pointerEpoch=System.nanoTime()/1e6-event.timeMillis;
        double inputTime=pointerEpoch+event.timeMillis;
        if(action!=0)inputTime=Math.max(inputTime,lastInputTime+.001);
        lastInputTime=inputTime;
        INPUT_TIME.set(inputTime);
        try {
        switch(action){
        case 0: controller.wrapMouseMoved(event);controller.wrapMousePressed(event);if(!event.isRightClick())controller.prepareModeForFreehand();break;
        case 1: controller.wrapMouseDragged(event,true);break;
        case 2: controller.wrapMouseReleased(event);controller.resetModeAfterFreehand();break;
        case 3: controller.wrapMouseMoved(event);break;
        case 4: controller.wrapMouseExited(event);break;
        default: throw new IllegalArgumentException("Unknown pointer action");
        }
        } finally {INPUT_TIME.remove();}
    }
    public void wheel(int x,int y,double delta,boolean shiftOrMeta,boolean alt){controller.wrapMouseWheelMoved(x,y,delta,shiftOrMeta,alt);}
    public void twoTouchStart(double x1,double y1,double x2,double y2){controller.twoTouchStart(x1,y1,x2,y2);}
    public void twoTouchMove(double x1,double y1,double x2,double y2){
        // The browser applies the previous one-step camera animation between input
        // events. Compose may deliver several moves together; commit that same
        // animation before the controller remembers the next pair of origins.
        view.updateAnimation();
        controller.twoTouchMove(x1,y1,x2,y2);
    }
    public void twoTouchEnd(){view.updateAnimation();controller.twoTouchEnd();}
    public double[] camera(){return new double[]{view.getAngleA(),view.getAngleB(),view.getXscale(),view.getXZero(),view.getYZero(),view.getZZero()};}
    public String cameraXml(){
        XMLStringBuilder xml=new XMLStringBuilder();
        view.getXML(xml,false);
        return "<geogebra format=\"5.0\">"+xml+"</geogebra>";
    }
    public String sceneXml(){return api.getXML();}
    public void restoreCamera(String xml){if(xml!=null&&!xml.isEmpty()){view.stopAnimation();app.setXML(xml,false);}}
    @Override public void close(){renderer.close();TIMERS.remove();INPUT_TIME.remove();}
    private static final class FrameTimer implements GTimer {
        private final GTimerListener listener;private int delay;private long due;private boolean repeat;
        FrameTimer(GTimerListener listener,int delay){this.listener=listener;this.delay=delay;}
        public void start(){repeat=false;due=System.nanoTime()+delay*1000000L;}
        public void startRepeat(){start();repeat=true;}
        public void stop(){due=0;}public boolean isRunning(){return due!=0;}
        public void setDelay(int delay){this.delay=delay;}
        void tick(long now){if(due!=0&&now>=due){due=repeat?now+delay*1000000L:0;listener.onRun();}}
    }
}
