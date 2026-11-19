package dev.demos.graphing.geogebra;

import java.util.*;
import org.geogebra.common.awt.*;
import org.geogebra.common.awt.font.*;
import org.geogebra.ggbjdk.java.awt.geom.AffineTransform;
import org.geogebra.ggbjdk.java.awt.geom.Rectangle2D;
import com.himamis.retex.renderer.share.platform.FactoryProvider;
import com.himamis.retex.renderer.share.platform.font.*;
import com.himamis.retex.renderer.share.platform.graphics.*;

/** Native platform binding for the exact ReTeX parser, font metrics and layout in GeoGebra. */
final class NativeTeX extends FactoryProvider {
    private final NativeText text;
    private NativeTeX(NativeText text){this.text=text;}
    static synchronized void install(NativeText text){if(FactoryProvider.getInstance()==null)FactoryProvider.setInstance(new NativeTeX(text));}
    @Override protected FontFactory createFontFactory(){return new FontFactory(){
        public Font createFont(String name,int style,int size){return new NativeFont(name,style,size);}
        public TextLayout createTextLayout(String value,Font font,FontRenderContext context){
            NativeFont nativeFont=(NativeFont)font;
            double[] metrics=text.measure(value,nativeFont.name,nativeFont.style,nativeFont.size);
            return new TextLayout(){
                public GRectangle2D getBounds(){return new Rectangle2D.Double(metrics[1],metrics[2],metrics[3],metrics[4]);}
                public void draw(Graphics2DInterface graphics,int x,int y){graphics.setFont(font);graphics.drawChars(value.toCharArray(),0,value.length(),x,y);}
            };
        }
        public TextAttributeProvider createTextAttributeProvider(){return new TextAttributeProvider(){
            private final Map<String,TextAttribute> attributes=new HashMap<>();
            public TextAttribute getTextAttribute(String name){return attributes.computeIfAbsent(name,k->new TextAttribute(){});}
            public Integer getTextAttributeValue(String name){return 1;}
        };}
        public FontLoader createFontLoader(){return name->new NativeFont("retex:"+name,0,FontLoader.FONT_SCALE_FACTOR);}
        public int getFontScaleFactor(){return FontLoader.FONT_SCALE_FACTOR;}
    };}
    @Override protected GraphicsFactory createGraphicsFactory(){return new GraphicsFactory(){
        public GColor createColor(int r,int g,int b,int a){return GColor.newColor(r,g,b,a);}
        public Image createImage(int width,int height,int type){return new TeXImage(new NativeAwtFactory.Image(width,height,text));}
    };}
    static final class NativeFont implements Font {
        final String name;final int style,size;
        NativeFont(String name,int style,int size){this.name=name;this.style=style;this.size=size;}
        public Font deriveFont(Map<TextAttribute,Object> map){return this;}// Shared ReTeX requests kerning/ligatures; native text shaping already enables both.
        public Font deriveFont(int style){return new NativeFont(name,style,size);}
        public boolean isEqual(Font font){return font instanceof NativeFont && name.equals(((NativeFont)font).name) && size==((NativeFont)font).size && style==((NativeFont)font).style;}
        public int getScale(){return FontLoader.FONT_SCALE_FACTOR;}
    }
    static final class TeXImage implements Image {
        final NativeAwtFactory.Image image;
        TeXImage(NativeAwtFactory.Image image){this.image=image;}
        public int getWidth(){return image.getWidth();}public int getHeight(){return image.getHeight();}
        public Graphics2DInterface createGraphics2D(){return new Graphics((NativeAwtFactory.Graphics)image.createGraphics());}
    }
    static final class Graphics implements Graphics2DInterface {
        private final NativeAwtFactory.Graphics graphics;
        private final Deque<GAffineTransform> stack=new ArrayDeque<>();
        private NativeFont font=new NativeFont("sans-serif",0,12);
        Graphics(NativeAwtFactory.Graphics graphics){this.graphics=graphics;}
        public void setStroke(GBasicStroke stroke){graphics.setStroke(stroke);}
        public GBasicStroke getStroke(){return graphics.nativeStroke();}
        public void setColor(GColor color){graphics.setColor(color);}
        public GColor getColor(){return graphics.getColor();}
        public GAffineTransform getTransform(){return graphics.nativeTransform();}
        public void saveTransform(){GAffineTransform copy=new AffineTransform();copy.concatenate(graphics.nativeTransform());stack.push(copy);}
        public void restoreTransform(){graphics.nativeTransform(stack.pop());}
        public Font getFont(){return font;}
        public void setFont(Font font){this.font=(NativeFont)font;graphics.setFont(new NativeAwtFactory.Font(this.font.name,this.font.style,this.font.size));}
        public void fillRect(double x,double y,double width,double height){graphics.fill(new Rectangle2D.Double(x,y,width,height));}
        public void fill(GShape shape){graphics.fill(shape);}public void draw(GShape shape){graphics.draw(shape);}
        public void drawChars(char[] data,int offset,int length,int x,int y){graphics.drawString(new String(data,offset,length),x,y);}
        private GShape arc(int x,int y,int w,int h,int start,int extent){GArc2D arc=AwtFactory.getPrototype().newArc2D();arc.setArc(x,y,w,h,start,extent,GArc2D.OPEN);return arc;}
        public void drawArc(int x,int y,int w,int h,int start,int extent){graphics.draw(arc(x,y,w,h,start,extent));}
        public void fillArc(int x,int y,int w,int h,int start,int extent){graphics.fill(arc(x,y,w,h,start,extent));}
        public void translate(double x,double y){graphics.translate(x,y);}public void scale(double x,double y){graphics.scale(x,y);}
        public void rotate(double theta,double x,double y){graphics.nativeTransform().rotate(theta,x,y);}
        public void rotate(double theta){graphics.nativeTransform().rotate(theta);}
        public void drawImage(Image image,int x,int y){graphics.drawImage(((TeXImage)image).image,x,y);}
        public void drawImage(Image image,GAffineTransform transform){saveTransform();graphics.transform(transform);drawImage(image,0,0);restoreTransform();}
        public FontRenderContext getFontRenderContext(){return ()->font;}
        public void setRenderingHint(int key,int value){} // Native text/path rasterization is antialiased.
        public int getRenderingHint(int key){return 1;}
        public void dispose(){}
    }
}
