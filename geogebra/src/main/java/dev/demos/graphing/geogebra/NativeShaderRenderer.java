/*
 * GeoGebra - Dynamic Mathematics for Everyone
 * Copyright (c) GeoGebra GmbH, Altenbergerstr. 69, 4040 Linz, Austria
 * https://www.geogebra.org
 * 
 * This file is licensed by GeoGebra GmbH under the EUPL 1.2 licence and
 * may be used under the EUPL 1.2 in compatible projects (see Article 5
 * and the Appendix of EUPL 1.2 for details).
 * You may obtain a copy of the licence at:
 * https://interoperable-europe.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 * 
 * Note: The overall GeoGebra software package is free to use for
 * non-commercial purposes only.
 * See https://www.geogebra.org/license for full licensing details
 */

package dev.demos.graphing.geogebra;


import org.geogebra.common.geogebra3D.euclidian3D.EuclidianView3D;
import org.geogebra.common.geogebra3D.euclidian3D.openGL.GLBuffer;
import org.geogebra.common.geogebra3D.euclidian3D.openGL.GLBufferIndices;
import org.geogebra.common.geogebra3D.euclidian3D.openGL.Manager;
import org.geogebra.common.geogebra3D.euclidian3D.openGL.Renderer;
import org.geogebra.common.geogebra3D.euclidian3D.openGL.RendererImplShaders;
import org.geogebra.common.geogebra3D.main.FragmentShader;
import org.geogebra.common.geogebra3D.main.VertexShader;
import org.geogebra.common.jre.openGL.GLBufferIndicesJre;
import org.geogebra.common.jre.openGL.GLBufferJre;
import org.geogebra.common.util.debug.Log;


/**
 * Native GLES port of GeoGebra 5.4.920.0 RendererImplShadersD.
 * Pipeline, geometry, matrices and packed GLSL remain in the upstream engine.
 * GL calls and the web backend's texture/light configuration are adapted here.
 *
 * Renderer using shaders
 * 
 * @author mathieu
 *
 */
public class NativeShaderRenderer extends RendererImplShaders {

	private NativeGL gl;

	/**
	 * Constructor
	 * 
	 * @param renderer
	 *            GL renderer
	 * 
	 * @param view
	 *            view
	 * @param jogl
	 *            java openGL implementation
	 */
	public NativeShaderRenderer(Renderer renderer, EuclidianView3D view,
			NativeGL gl) {
		super(renderer, view);
		this.gl = gl;
		Log.debug("Renderer with shaders created (shaders checked ok)");

	}

	private NativeGL getGL() {

		return gl;
	}

	// private int normalMatrixLocation;

	private int[] vboHandles;

	@Override
    protected final void compileShadersProgram() {
        // These are the exact packed shaders used by the reference's WebGL backend.
        setVertShader(gl.compileShader(NativeGL.GL_VERTEX_SHADER, VertexShader.getVertexShaderShiny(true)));
        setFragShader(gl.compileShader(NativeGL.GL_FRAGMENT_SHADER, FragmentShader.getFragmentShaderShinyForPacking(true)));
    }

	@Override
	final protected Object glCreateProgram() {
		return gl.glCreateProgram();
	}

	@Override
	final protected void glAttachShader(Object shader) {
		gl.glAttachShader((Integer) shaderProgram,
				(Integer) shader);
	}

	@Override
	final protected void glBindAttribLocation(int index, String name) {
		gl.glBindAttribLocation((Integer) shaderProgram, index,
				name);
	}

	@Override
	final protected void glLinkProgram() {
		gl.glLinkProgram((Integer) shaderProgram);
        gl.checkProgram((Integer) shaderProgram);
        gl.glUseProgram((Integer) shaderProgram);
	}

	@Override
	final protected Object glGetUniformLocation(String name) {
		return gl.glGetUniformLocation((Integer) shaderProgram,
				name);
	}

	@Override
	final protected void createVBOs() {
		vboHandles = new int[GLSL_ATTRIB_SIZE];
		gl.glGenBuffers(GLSL_ATTRIB_SIZE, vboHandles, 0);
	}

	@Override
	final protected int getStoreBufferNumBytes(int length, int size) {
		return length * size * 4; // 4 bytes per float
	}

	@Override
	final protected void bindBuffer(int bufferType, int buffer) {
		gl.glBindBuffer(bufferType, vboHandles[buffer]);
	}

	@Override
	final protected int getGL_ELEMENT_ARRAY_BUFFER() {
		return NativeGL.GL_ELEMENT_ARRAY_BUFFER;
	}

	@Override
	final protected int getGL_ARRAY_BUFFER() {
		return NativeGL.GL_ARRAY_BUFFER;
	}

	@Override
	protected void vertexAttribPointer(int attrib, int size) {
		gl.glVertexAttribPointer(attrib, size, NativeGL.GL_FLOAT, false,
				0, 0);
	}

	@Override
	protected void glUniform3fv(Object location, float[] values) {
		gl.glUniform3fv((Integer) location, 1, values, 0);
	}

	@Override
	protected void glUniform3f(Object location, float x, float y, float z) {
		gl.glUniform3f((Integer) location, x, y, z);
	}

	@Override
	protected void glEnableVertexAttribArray(int attrib) {
		gl.glEnableVertexAttribArray(attrib);
	}

	@Override
	protected void glBufferData(int numBytes, GLBuffer fb) {
		gl.glBufferData(NativeGL.GL_ARRAY_BUFFER, numBytes,
				((GLBufferJre) fb).getBuffer(), NativeGL.GL_STREAM_DRAW);

	}

	@Override
	protected void glBufferDataIndices(int numBytes, GLBufferIndices arrayI) {
		gl.glBufferData(NativeGL.GL_ELEMENT_ARRAY_BUFFER, numBytes,
				((GLBufferIndicesJre) arrayI).getBuffer(),
				NativeGL.GL_STREAM_DRAW);

	}

	@Override
	public void draw(Manager.Type type, int length) {

		gl.glDrawElements(getGLType(type), length,
				NativeGL.GL_UNSIGNED_SHORT, 0);
	}

	@Override
	protected int getGLType(Manager.Type type) {
		switch (type) {
		case TRIANGLE_STRIP:
			return NativeGL.GL_TRIANGLE_STRIP;
		case TRIANGLE_FAN:
			return NativeGL.GL_TRIANGLE_STRIP;
		case TRIANGLES:
			return NativeGL.GL_TRIANGLES;
		case LINE_LOOP:
			return NativeGL.GL_LINE_LOOP;
		case LINE_STRIP:
			return NativeGL.GL_LINE_STRIP;
		}

		return 0;
	}

	@Override
	protected final void glUniformMatrix4fv(Object location, float[] values) {
		gl.glUniformMatrix4fv((Integer) location, 1, false,
				values, 0);
	}

	@Override
	final protected void glUseProgram(Object program) {
		gl.glUseProgram((Integer) program);
	}

	@Override
	final protected void glDisableVertexAttribArray(int attrib) {
		gl.glDisableVertexAttribArray(attrib);
	}

	@Override
	final protected void glDetachAndDeleteShader(Object program,
			Object shader) {
		gl.glDetachShader((Integer) program, (Integer) shader);
		gl.glDeleteShader((Integer) shader);
	}

	@Override
	final protected void glDeleteProgram(Object program) {
		gl.glDeleteProgram((Integer) program);
	}

	@Override
	final protected void glUniform4f(Object location, float a, float b, float c,
			float d) {
		gl.glUniform4f((Integer) location, a, b, c, d);
	}

	@Override
	final protected void glUniform4fv(Object location, float[] values) {
		gl.glUniform4fv((Integer) location, 1, values, 0);
	}

	@Override
	final protected void glUniform2fv(Object location, float[] values) {
		gl.glUniform2fv((Integer) location, 1, values, 0);
	}

	@Override
	public void setColorMaterial() {
		// Shader uniforms replace fixed-function color material, as in RendererImplShadersW.

	}

	@Override
	protected void glViewPort(int width, int height) {
		gl.glViewport(0, 0, width, height);
	}

	@Override
	protected void glUniform1i(Object location, int value) {
		gl.glUniform1i((Integer) location, value);
	}

	@Override
	protected void glUniform1fv(Object location, int length, float[] values) {
		gl.glUniform1fv((Integer) location, length, values, 0);
	}

	@Override
	public float[] getLightPosition() {
		return Renderer.LIGHT_POSITION_W;
	}

	@Override
	final protected void glCullFace(int flag) {
		getGL().glCullFace(flag);
	}

	@Override
	final protected int getGL_FRONT() {
		return NativeGL.GL_FRONT;
	}

	@Override
	final protected int getGL_BACK() {
		return NativeGL.GL_BACK;
	}

	@Override
	public void setBufferLeft() {
		// The reference uses a single color buffer.
	}

	@Override
	public void setBufferRight() {
		// The reference uses a single color buffer.
	}

	@Override
	public void setStencilFunc(int value) {
		getGL().glStencilFunc(NativeGL.GL_EQUAL, value, 0xFF);
	}

	@Override
	final protected void glDepthMask(boolean flag) {
		getGL().glDepthMask(flag);
	}

	@Override
	public void setColorMask(boolean r, boolean g, boolean b, boolean a) {
		getGL().glColorMask(r, g, b, a);
	}

	@Override
	public void setClearColor(float r, float g, float b, float a) {
		getGL().glClearColor(r, g, b, a);
	}

	@Override
	public void setPolygonOffset(float factor, float units) {
		getGL().glPolygonOffset(factor, units);
	}

	@Override
	public void genTextures2D(int number, int[] index) {
		getGL().glGenTextures(number, index, 0);
	}

	@Override
	public void bindTexture(int index) {
		getGL().glBindTexture(NativeGL.GL_TEXTURE_2D, index);
	}

	@Override
	public void glEnable(int flag) {
		getGL().glEnable(flag);
	}

	@Override
	public void glDisable(int flag) {
		getGL().glDisable(flag);
	}

	@Override
	public final void enableMultisample() {
		// Multisampling is selected when the native context is created.
	}

	@Override
	public final void disableMultisample() {
		// Matches RendererImplShadersW, which does not toggle multisampling.
	}

	@Override
	public int getGL_BLEND() {
		return NativeGL.GL_BLEND;
	}

	@Override
	public int getGL_CULL_FACE() {
		return NativeGL.GL_CULL_FACE;
	}

	@Override
	public void glClear(int flag) {
		getGL().glClear(flag);
	}

	@Override
	public int getGL_COLOR_BUFFER_BIT() {
		return NativeGL.GL_COLOR_BUFFER_BIT;
	}

	@Override
	public int getGL_DEPTH_BUFFER_BIT() {
		return NativeGL.GL_DEPTH_BUFFER_BIT;
	}

	@Override
	public int getGL_DEPTH_TEST() {
		return NativeGL.GL_DEPTH_TEST;
	}

	@Override
	protected void bindFramebuffer(Object id) {
		getGL().glBindFramebuffer(NativeGL.GL_FRAMEBUFFER, (Integer) id);
	}

	@Override
	protected void bindRenderbuffer(Object id) {
		getGL().glBindRenderbuffer(NativeGL.GL_RENDERBUFFER, (Integer) id);
	}

	@Override
	protected void unbindFramebuffer() {
		bindFramebuffer(0);
	}

	@Override
	protected void unbindRenderbuffer() {
		bindRenderbuffer(0);
	}

	@Override
	protected void textureParametersNearest() {
		getGL().glTexParameterf(NativeGL.GL_TEXTURE_2D, NativeGL.GL_TEXTURE_MAG_FILTER,
				NativeGL.GL_NEAREST);
		getGL().glTexParameterf(NativeGL.GL_TEXTURE_2D, NativeGL.GL_TEXTURE_MIN_FILTER,
				NativeGL.GL_NEAREST);
	}

	@Override
	protected void textureImage2DForBuffer(int width, int height) {
		getGL().glTexImage2D(NativeGL.GL_TEXTURE_2D, 0, NativeGL.GL_RGBA, width, height, 0,
				NativeGL.GL_RGBA, NativeGL.GL_UNSIGNED_BYTE, null);
	}

	@Override
	protected void renderbufferStorage(int width, int height) {
		getGL().glRenderbufferStorage(NativeGL.GL_RENDERBUFFER,
				NativeGL.GL_DEPTH_COMPONENT16, width, height);
	}

	private int[] tmp = new int[1];

	@Override
	protected Object genRenderbuffer() {
		getGL().glGenRenderbuffers(1, tmp, 0);
		return tmp[0];
	}

	@Override
	protected Object genFramebuffer() {
		getGL().glGenFramebuffers(1, tmp, 0);
		return tmp[0];
	}

	@Override
	protected void framebuffer(Object colorId, Object depthId) {
		getGL().glFramebufferTexture2D(NativeGL.GL_FRAMEBUFFER,
				NativeGL.GL_COLOR_ATTACHMENT0, NativeGL.GL_TEXTURE_2D,
				(Integer) colorId, 0);
		getGL().glFramebufferRenderbuffer(NativeGL.GL_FRAMEBUFFER,
				NativeGL.GL_DEPTH_ATTACHMENT, NativeGL.GL_RENDERBUFFER,
				(Integer) depthId);
	}

	@Override
	protected boolean checkFramebufferStatus() {
		return getGL().glCheckFramebufferStatus(
				NativeGL.GL_FRAMEBUFFER) == NativeGL.GL_FRAMEBUFFER_COMPLETE;
	}

	@Override
	protected void glResetProgram() {
		glUseProgram(0);
	}

	@Override
	public void createDummyTexture() {
		int[] texture = new int[1];
        genTextures2D(1, texture);
        bindTexture(texture[0]);
        gl.glTexImage2D(NativeGL.GL_TEXTURE_2D, 0, NativeGL.GL_RGBA, 2, 2, 0,
                NativeGL.GL_RGBA, NativeGL.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.allocateDirect(16));
	}

}
