package dev.demos.graphing.geogebra;

import org.geogebra.common.awt.*;
import org.geogebra.common.awt.font.GTextLayout;
import org.geogebra.ggbjdk.factories.AwtFactoryHeadless;
import org.geogebra.ggbjdk.java.awt.geom.AffineTransform;
import org.geogebra.ggbjdk.java.awt.geom.Rectangle2D;

/** Platform-independent GeoGebra shapes with real native fonts and label textures. */
public final class NativeAwtFactory extends AwtFactoryHeadless {
    private final NativeText text;
    public NativeAwtFactory(NativeText text) {this.text=text;}
    @Override public GFont newFont(String name,int style,int size) {return new Font(name,style,size);}
    @Override public GBufferedImage newBufferedImage(int w,int h,double ratio) {return new Image(w,h,text);}
    @Override public GBufferedImage createBufferedImage(int w,int h,boolean alpha) {return new Image(w,h,text);}
    @Override public GTextLayout newTextLayout(String value,GFont font,GFontRenderContext context) {
        double[] m=text.measure(value,font.getFontName(),font.getStyle(),font.getSize());
        return new GTextLayout() {
            public double getAdvance(){return m[0];}
            public GRectangle2D getBounds(){return new Rectangle2D.Double(m[1],m[2],m[3],m[4]);}
            public double getAscent(){return m[5];}
            public double getDescent(){return m[6];}
            public void draw(GGraphics2D graphics,int x,int y){graphics.setFont(font);graphics.drawString(value,x,y);}
        };
    }
    @Override public GAlphaComposite newAlphaComposite(double alpha) {return new GAlphaComposite(){};}
    @Override public GGradientPaint newGradientPaint(double x1,double y1,GColor c1,double x2,double y2,GColor c2){return null;}
    @Override public GPaint newTexturePaint(GBufferedImage image,GRectangle rect){return null;}
    @Override public GPaint newTexturePaint(MyImage image,GRectangle rect){return null;}

    static final class Font extends GFont {
        private final String name;private final int style,size;
        Font(String name,int style,int size){this.name=name;this.style=style;this.size=size;}
        public int getStyle(){return style;}
        public int getSize(){return size;}
        public boolean isItalic(){return (style&ITALIC)!=0;}
        public boolean isBold(){return (style&BOLD)!=0;}
        public int canDisplayUpTo(String value){return -1;}
        public GFont deriveFont(int style,int size){return new Font(name,style,size);}
        public GFont deriveFont(int style,double size){return new Font(name,style,(int)size);}
        public GFont deriveFont(int style){return new Font(name,style,size);}
        public String getFontName(){return name;}
    }
    public static final class Image implements GBufferedImage {
        public final byte[] rgba;
        private final int width,height;
        private final NativeText text;
        Image(int width,int height,NativeText text){this.width=Math.max(1,width);this.height=Math.max(1,height);this.text=text;rgba=new byte[this.width*this.height*4];}
        public int getWidth(){return width;}
        public int getHeight(){return height;}
        public GGraphics2D createGraphics(){return new Graphics(this,text);}
        public GBufferedImage getSubimage(int x,int y,int w,int h){
            if(x<0||y<0||w<0||h<0||x+w>width||y+h>height)throw new IllegalArgumentException("Image bounds");
            Image image=new Image(w,h,text);
            for(int row=0;row<h;row++)System.arraycopy(rgba,4*((row+y)*width+x),image.rgba,4*row*w,w*4);
            return image;
        }
        public void flush(){}
        public String getBase64(){throw new UnsupportedOperationException("Export uses the native graph framebuffer");}
    }
    static final class Graphics extends GGraphicsCommon {
        private final Image image;private final NativeText text;
        private GFont font=new Font("sans-serif",PLAIN_STYLE,12);
        private static final int PLAIN_STYLE=0;
        private GColor color=GColor.BLACK;
        private GAffineTransform transform=new AffineTransform();
        private GBasicStroke stroke=new org.geogebra.ggbjdk.java.awt.DefaultBasicStroke(1);
        GAffineTransform nativeTransform(){return transform;}
        void nativeTransform(GAffineTransform value){transform=value;}
        private double[] matrix(){return new double[]{transform.getScaleX(),transform.getShearY(),transform.getShearX(),transform.getScaleY(),transform.getTranslateX(),transform.getTranslateY()};}
        private int argb(){return color.getAlpha()<<24|color.getRed()<<16|color.getGreen()<<8|color.getBlue();}
        @Override public void setStroke(GBasicStroke stroke){this.stroke=stroke;}
        GBasicStroke nativeStroke(){return stroke;}
        @Override public void draw(GShape shape){path(shape,false);}
        @Override public void fill(GShape shape){path(shape,true);}
        @Override public void fillRect(int x,int y,int width,int height){fill(new Rectangle2D.Double(x,y,width,height));}
        @Override public void drawRect(int x,int y,int width,int height){draw(new Rectangle2D.Double(x,y,width,height));}
        @Override public void drawLine(int x1,int y1,int x2,int y2){
            org.geogebra.common.awt.GGeneralPath path=AwtFactory.getPrototype().newGeneralPath();path.moveTo(x1,y1);path.lineTo(x2,y2);draw(path);
        }
        @Override public void drawImage(GBufferedImage source,int x,int y){
            GAffineTransform saved=transform;GAffineTransform next=new AffineTransform();next.concatenate(transform);next.translate(x,y);transform=next;
            Image data=(Image)source;text.image(image.rgba,image.width,image.height,data.rgba,data.width,data.height,matrix());transform=saved;
        }
        private void path(GShape shape,boolean fill){
            GPathIterator iterator=shape.getPathIterator(null);java.util.ArrayList<Integer> types=new java.util.ArrayList<>();java.util.ArrayList<Double> points=new java.util.ArrayList<>();
            double[] coordinates=new double[6];
            while(!iterator.isDone()){types.add(iterator.currentSegment(coordinates));for(double value:coordinates)points.add(value);iterator.next();}
            int[] typeArray=types.stream().mapToInt(Integer::intValue).toArray();double[] pointArray=points.stream().mapToDouble(Double::doubleValue).toArray();
            text.path(image.rgba,image.width,image.height,typeArray,pointArray,iterator.getWindingRule(),fill,stroke.getLineWidth(),stroke.getEndCap(),stroke.getLineJoin(),stroke.getMiterLimit(),stroke.getDashArray(),argb(),matrix());
        }
        Graphics(Image image,NativeText text){this.image=image;this.text=text;}
        @Override public GFont getFont(){return font;}
        @Override public void setFont(GFont font){this.font=font;}
        @Override public void setColor(GColor color){this.color=color;}
        @Override public GColor getColor(){return color;}
        @Override public GFontRenderContext getFontRenderContext(){return new GFontRenderContext(){};}
        @Override public void translate(double x,double y){transform.translate(x,y);}
        @Override public void scale(double x,double y){transform.scale(x,y);}
        @Override public void transform(GAffineTransform next){transform.concatenate(next);}
        @Override public void drawString(String value,int x,int y){drawString(value,(double)x,(double)y);}
        @Override public void drawString(String value,double x,double y){
            GAffineTransform t=new AffineTransform();t.concatenate(transform);t.translate(x,y);
            int argb=color.getAlpha()<<24|color.getRed()<<16|color.getGreen()<<8|color.getBlue();
            text.draw(image.rgba,image.width,image.height,value,font.getFontName(),font.getStyle(),font.getSize(),argb,
                new double[]{t.getScaleX(),t.getShearY(),t.getShearX(),t.getScaleY(),t.getTranslateX(),t.getTranslateY()});
        }
    }
}
