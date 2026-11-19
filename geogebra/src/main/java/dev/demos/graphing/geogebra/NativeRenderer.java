package dev.demos.graphing.geogebra;

import java.nio.ByteBuffer;
import java.util.HashSet;
import org.geogebra.common.awt.*;
import org.geogebra.common.geogebra3D.euclidian3D.EuclidianView3D;
import org.geogebra.common.geogebra3D.euclidian3D.draw.DrawableTexture3D;
import org.geogebra.common.geogebra3D.euclidian3D.openGL.*;
import org.geogebra.common.jre.openGL.GLFactoryJre;

/** Runs GeoGebra's original render passes against the native GLES context. */
final class NativeRenderer extends Renderer implements AutoCloseable {
    private NativeGL gl;
    private final HashSet<Integer> textures=new HashSet<>();
    NativeRenderer(EuclidianView3D view){
        super(view,RendererType.SHADER);
        GLFactory.setPrototypeIfNull(new GLFactoryJre());
        geometryManager=new ManagerShaders(this,view);
    }
    void initialize(NativeGL gl){this.gl=gl;setRendererImpl(new NativeShaderRenderer(this,view3D,gl));init();}
    @Override protected Textures newTextures(){return new TexturesShaders(this);}
    @Override protected void doStartAR(){}
    @Override protected void setDepthFunc(){gl.glDepthFunc(NativeGL.GL_LEQUAL);}
    @Override protected void enablePolygonOffsetFill(){gl.glEnable(NativeGL.GL_POLYGON_OFFSET_FILL);}
    @Override protected void setBlendFunc(){gl.glBlendFunc(NativeGL.GL_SRC_ALPHA,NativeGL.GL_ONE_MINUS_SRC_ALPHA);}
    @Override public Object getCanvas(){return this;}
    @Override public void setLineWidth(double width){} // All strokes are upstream triangle geometry.
    @Override public void enableTextures2D(){getRendererImpl().enableTextures();}
    @Override public void disableTextures2D(){getRendererImpl().disableTextures();}
    @Override public GBufferedImage createBufferedImage(DrawableTexture3D label){
        label.setDimensionPowerOfTwo(firstPowerOfTwoGreaterThan(label.getWidth()),firstPowerOfTwoGreaterThan(label.getHeight()));
        return AwtFactory.getPrototype().newBufferedImage(label.getWidthPowerOfTwo(),label.getHeightPowerOfTwo(),1);
    }
    @Override public void createAlphaTexture(DrawableTexture3D label,GBufferedImage image){
        NativeAwtFactory.Image pixels=(NativeAwtFactory.Image)image;
        int texture=label.getTextureIndex();
        if(texture<0){texture=texture();label.setTextureIndex(texture);}
        upload(texture,pixels.getWidth(),pixels.getHeight(),pixels.rgba);
        int minX=pixels.getWidth(),minY=pixels.getHeight(),maxX=0,maxY=0;
        for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){
            if(pixels.rgba[(y*pixels.getWidth()+x)*4+3]!=0){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        }
        label.setPickingDimension(minX,minY,Math.max(0,maxX-minX+1),Math.max(0,maxY-minY+1));
    }
    private int texture(){int[] id=new int[1];gl.glGenTextures(1,id,0);textures.add(id[0]);return id[0];}
    private void upload(int texture,int width,int height,byte[] rgba){
        gl.glBindTexture(NativeGL.GL_TEXTURE_2D,texture);
        ByteBuffer data=ByteBuffer.allocateDirect(rgba.length);data.put(rgba).rewind();
        gl.glTexImage2D(NativeGL.GL_TEXTURE_2D,0,NativeGL.GL_RGBA,width,height,0,NativeGL.GL_RGBA,NativeGL.GL_UNSIGNED_BYTE,data);
        gl.glGenerateMipmap(NativeGL.GL_TEXTURE_2D);
    }
    @Override public int createAlphaTexture(int width,int height,byte[] alpha){
        byte[] rgba=new byte[alpha.length*4];for(int i=0;i<alpha.length;i++)rgba[4*i+3]=alpha[i];
        int id=texture();upload(id,width,height,rgba);return id;
    }
    @Override public void textureImage2D(int width,int height,byte[] alpha){
        ByteBuffer rgba=ByteBuffer.allocateDirect(alpha.length*4);for(int i=0;i<alpha.length;i++)rgba.put(4*i+3,alpha[i]);
        gl.glTexImage2D(NativeGL.GL_TEXTURE_2D,0,NativeGL.GL_RGBA,width,height,0,NativeGL.GL_RGBA,NativeGL.GL_UNSIGNED_BYTE,rgba);
    }
    @Override public void setTextureLinear(){filter(NativeGL.GL_LINEAR);}
    @Override public void setTextureNearest(){filter(NativeGL.GL_NEAREST);}
    private void filter(int value){gl.glTexParameterf(NativeGL.GL_TEXTURE_2D,NativeGL.GL_TEXTURE_MIN_FILTER,value);gl.glTexParameterf(NativeGL.GL_TEXTURE_2D,NativeGL.GL_TEXTURE_MAG_FILTER,value);}
    @Override public void resumeAnimator(){}
    @Override public void setARShouldRestart(){}
    @Override public void close(){
        if(gl!=null){getRendererImpl().dispose();int[] ids=textures.stream().mapToInt(Integer::intValue).toArray();gl.glDeleteTextures(ids.length,ids,0);textures.clear();}
    }
}
