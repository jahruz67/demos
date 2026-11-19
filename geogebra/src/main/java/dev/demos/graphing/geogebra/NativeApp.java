package dev.demos.graphing.geogebra;

import java.util.List;
import org.geogebra.common.awt.AwtFactory;
import org.geogebra.common.euclidian.EuclidianView;
import org.geogebra.common.euclidian.EuclidianConstants;
import org.geogebra.common.euclidian3D.EuclidianView3DInterface;
import org.geogebra.common.jre.headless.*;
import org.geogebra.common.jre.kernel.commands.CommandDispatcher3DJre;
import org.geogebra.common.kernel.Kernel;
import org.geogebra.common.kernel.commands.CommandDispatcher;
import org.geogebra.common.kernel.geos.GeoElement;
import org.geogebra.common.main.*;
import org.geogebra.common.main.settings.DefaultSettings;
import org.geogebra.common.main.settings.config.AppConfigGraphing3D;

/** Native host for the same shared kernel/configuration used by appName:"3d". */
final class NativeApp extends AppCommon {
    private NativeView view;
    private NativeKeyDispatcher keys;
    private NativeEquation equation;
    @Override public org.geogebra.common.euclidian.DrawEquation getDrawEquation(){if(equation==null)equation=new NativeEquation();return equation;}
    NativeApp(AwtFactory factory,NativeCas cas){
        super(new LocalizationCommon(3),factory,new AppConfigGraphing3D());
        if(cas!=null)setCASFactory(new NativeCasFactory(cas));
        setAppletFlag(true);
        setRightClickEnabled(false);
        setLabelDragsEnabled(false);
        setShiftDragZoomEnabled(true);
        setAllowPopups(false);
        setFontSize(16,true);
    }
    @Override protected EuclidianView newEuclidianView(boolean[] axes,boolean grid){
        getSettings().getEuclidian(1).setPreferredSize(AwtFactory.getPrototype().newDimension(800,600));
        return new EuclidianViewNoGui(getEuclidianController(),1,getSettings().getEuclidian(1),createGraphics()){
            @Override public void repaint(){} // The 3D kernel keeps a hidden algebra/2D model, with no second renderer.
        };
    }
    @Override public DefaultSettings getDefaultSettings(){return new DefaultSettings(){
        public int getAppFontSize(){return 16;}public int getGuiFontSize(){return -1;}
    };}
    @Override protected AppCompanion newAppCompanion(){return new App3DCompanionHeadless(this);}
    @Override public CommandDispatcher newCommandDispatcher(Kernel kernel){return new CommandDispatcher3DJre(kernel);}
    @Override public boolean is3D(){return true;}
    @Override public EuclidianView getActiveEuclidianView(){return view==null?super.getActiveEuclidianView():view;}
    @Override public EuclidianView3DInterface getEuclidianView3D(){
        if(view==null)view=new NativeView(new NativeController(this,kernel),getSettings().getEuclidian(3));
        return view;
    }
    @Override protected void getViewsXML(org.geogebra.common.io.XMLStringBuilder sb,boolean preferences){
        super.getViewsXML(sb,preferences);
        if(view!=null)view.getXML(sb,preferences);
    }
    NativeView nativeView(){return (NativeView)getEuclidianView3D();}
    @Override public boolean isEuclidianView3Dinited(){return view!=null;}
    @Override public boolean showView(int id){return id==VIEW_EUCLIDIAN3D;}
    @Override public void setActiveView(int id){}
    @Override public GlobalKeyDispatcher getGlobalKeyDispatcher(){
        if(keys==null)keys=new NativeKeyDispatcher(this);
        return keys;
    }
    void key(String name,boolean down,int modifiers){((NativeKeyDispatcher)getGlobalKeyDispatcher()).dispatch(name,down,modifiers);}
}
