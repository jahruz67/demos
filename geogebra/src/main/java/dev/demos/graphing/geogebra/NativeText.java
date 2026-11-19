package dev.demos.graphing.geogebra;

/** Native font measurement and rasterization. Pixels use RGBA byte order. */
public interface NativeText {
    /** advance, left bearing, top bearing, ink width, ink height, ascent, descent */
    double[] measure(String text,String family,int style,int size);
    void draw(byte[] rgba,int width,int height,String text,String family,int style,int size,int argb,double[] matrix);
    void path(byte[] rgba,int width,int height,int[] types,double[] points,int winding,boolean fill,
        double stroke,int cap,int join,double miter,double[] dash,int argb,double[] matrix);
    void image(byte[] rgba,int width,int height,byte[] source,int sourceWidth,int sourceHeight,double[] matrix);
}

