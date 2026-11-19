package dev.demos.graphing.geogebra;

import org.geogebra.common.awt.*;
import org.geogebra.common.euclidian.DrawEquation;
import org.geogebra.common.kernel.kernelND.GeoElementND;
import org.geogebra.common.main.App;
import com.himamis.retex.renderer.share.TeXIcon;
import com.himamis.retex.renderer.share.platform.graphics.Image;

/** DrawEquationD's shared ReTeX path with native bitmap/path/font bindings on both platforms. */
final class NativeEquation extends DrawEquation {
    private static boolean initialized;
    @Override public synchronized void checkFirstCall(){if(!initialized){checkFirstCallStatic();initialized=true;}}
    @Override public GDimension drawEquation(App app,GeoElementND geo,GGraphics2D graphics,int x,int y,String value,GFont font,boolean serif,GColor fg,GColor bg,boolean cache,boolean again,Runnable callback){
        GDimension size=drawEquation(app,geo,new NativeTeX.Graphics((NativeAwtFactory.Graphics)graphics),x,y,value,font,serif,fg,bg,false,null,null);
        if(callback!=null)callback.run();return size;
    }
    @Override public GDimension measureEquation(App app,String text,GFont font,boolean serif){return measureEquationJLaTeXMath(app,text,font,serif,null,null);}
    @Override public Image getCachedDimensions(String text,GeoElementND geo,GColor color,GFont font,int style,int[] result){
        TeXIcon icon=createIcon(text,color,font.getSize()+3,style);
        NativeTeX.TeXImage image=new NativeTeX.TeXImage((NativeAwtFactory.Image)AwtFactory.getPrototype().newBufferedImage(icon.getIconWidth(),icon.getIconHeight(),1));
        icon.paintIcon(()->color,image.createGraphics2D(),0,0);result[0]=image.getWidth();result[1]=image.getHeight();return image;
    }
}
