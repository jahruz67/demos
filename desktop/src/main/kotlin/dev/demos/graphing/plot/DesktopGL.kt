package dev.demos.graphing.plot

import dev.demos.graphing.geogebra.NativeGL
import java.nio.Buffer
import com.jogamp.opengl.GL2ES2

internal class DesktopGL(private val gl: GL2ES2) : NativeGL {
    var density = 1f
    var frameHeight = 1
    override fun glCreateProgram(): Int = gl.glCreateProgram()
    override fun glAttachShader(program: Int, shader: Int) = gl.glAttachShader(program, shader)
    override fun glBindAttribLocation(program: Int, index: Int, name: String) = gl.glBindAttribLocation(program, index, name)
    override fun glLinkProgram(program: Int) = gl.glLinkProgram(program)
    override fun glGetUniformLocation(program: Int, name: String): Int = gl.glGetUniformLocation(program, name)
    override fun glGenBuffers(count: Int, buffers: IntArray, offset: Int) = gl.glGenBuffers(count, buffers, offset)
    override fun glBindBuffer(target: Int, buffer: Int) = gl.glBindBuffer(target, buffer)
    override fun glVertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) = gl.glVertexAttribPointer(index, size, type, normalized, stride, offset.toLong())
    override fun glUniform3fv(location: Int, count: Int, values: FloatArray, offset: Int) = gl.glUniform3fv(location, count, values, offset)
    override fun glUniform3f(location: Int, x: Float, y: Float, z: Float) = gl.glUniform3f(location, x, y, z)
    override fun glEnableVertexAttribArray(index: Int) = gl.glEnableVertexAttribArray(index)
    override fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int) = gl.glBufferData(target, size.toLong(), data, usage)
    override fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) = gl.glDrawElements(mode, count, type, offset.toLong())
    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, values: FloatArray, offset: Int) = gl.glUniformMatrix4fv(location, count, transpose, values, offset)
    override fun glUseProgram(program: Int) = gl.glUseProgram(program)
    override fun glDisableVertexAttribArray(index: Int) = gl.glDisableVertexAttribArray(index)
    override fun glDetachShader(program: Int, shader: Int) = gl.glDetachShader(program, shader)
    override fun glDeleteShader(shader: Int) = gl.glDeleteShader(shader)
    override fun glDeleteProgram(program: Int) = gl.glDeleteProgram(program)
    override fun glUniform4f(location: Int, a: Float, b: Float, c: Float, d: Float) = gl.glUniform4f(location, a, b, c, d)
    override fun glUniform4fv(location: Int, count: Int, values: FloatArray, offset: Int) = gl.glUniform4fv(location, count, values, offset)
    override fun glUniform2fv(location: Int, count: Int, values: FloatArray, offset: Int) = gl.glUniform2fv(location, count, values, offset)
    override fun glViewport(x: Int, y: Int, width: Int, height: Int) = gl.glViewport((x*density).toInt(), frameHeight-((y+height)*density).toInt(), (width*density).toInt(), (height*density).toInt())
    override fun glUniform1i(location: Int, value: Int) = gl.glUniform1i(location, value)
    override fun glUniform1fv(location: Int, count: Int, values: FloatArray, offset: Int) = gl.glUniform1fv(location, count, values, offset)
    override fun glCullFace(mode: Int) = gl.glCullFace(mode)
    override fun glStencilFunc(func: Int, ref: Int, mask: Int) = gl.glStencilFunc(func, ref, mask)
    override fun glDepthMask(flag: Boolean) = gl.glDepthMask(flag)
    override fun glColorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) = gl.glColorMask(r, g, b, a)
    override fun glClearColor(r: Float, g: Float, b: Float, a: Float) = gl.glClearColor(r, g, b, a)
    override fun glPolygonOffset(factor: Float, units: Float) = gl.glPolygonOffset(factor, units)
    override fun glGenTextures(count: Int, textures: IntArray, offset: Int) = gl.glGenTextures(count, textures, offset)
    override fun glBindTexture(target: Int, texture: Int) = gl.glBindTexture(target, texture)
    override fun glEnable(capability: Int) = gl.glEnable(capability)
    override fun glDisable(capability: Int) = gl.glDisable(capability)
    override fun glClear(mask: Int) = gl.glClear(mask)
    override fun glBindFramebuffer(target: Int, framebuffer: Int) = gl.glBindFramebuffer(target, framebuffer)
    override fun glBindRenderbuffer(target: Int, renderbuffer: Int) = gl.glBindRenderbuffer(target, renderbuffer)
    override fun glTexParameterf(target: Int, name: Int, value: Float) = gl.glTexParameterf(target, name, value)
    override fun glTexImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, border: Int, format: Int, type: Int, data: Buffer?) = gl.glTexImage2D(target, level, internalFormat, width, height, border, format, type, data)
    override fun glRenderbufferStorage(target: Int, format: Int, width: Int, height: Int) = gl.glRenderbufferStorage(target, format, width, height)
    override fun glGenRenderbuffers(count: Int, buffers: IntArray, offset: Int) = gl.glGenRenderbuffers(count, buffers, offset)
    override fun glGenFramebuffers(count: Int, buffers: IntArray, offset: Int) = gl.glGenFramebuffers(count, buffers, offset)
    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) = gl.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    override fun glFramebufferRenderbuffer(target: Int, attachment: Int, rendertarget: Int, renderbuffer: Int) = gl.glFramebufferRenderbuffer(target, attachment, rendertarget, renderbuffer)
    override fun glCheckFramebufferStatus(target: Int): Int = gl.glCheckFramebufferStatus(target)
    override fun glDepthFunc(func: Int) = gl.glDepthFunc(func)
    override fun glBlendFunc(source: Int, destination: Int) = gl.glBlendFunc(source, destination)
    override fun glGenerateMipmap(target: Int) = gl.glGenerateMipmap(target)
    override fun glDeleteTextures(count: Int, textures: IntArray, offset: Int) = gl.glDeleteTextures(count, textures, offset)
    override fun glDeleteBuffers(count: Int, buffers: IntArray, offset: Int) = gl.glDeleteBuffers(count, buffers, offset)
    override fun compileShader(type: Int, source: String): Int {
        val shader=gl.glCreateShader(type)
        val adapted=source.replace("precision mediump float;", "#ifdef GL_ES\nprecision mediump float;\n#endif")
        gl.glShaderSource(shader,1,arrayOf(adapted),intArrayOf(adapted.length),0)
        gl.glCompileShader(shader)
        val status=IntArray(1);gl.glGetShaderiv(shader,GL2ES2.GL_COMPILE_STATUS,status,0)
        if(status[0]==0) {
            val length=IntArray(1);gl.glGetShaderiv(shader,GL2ES2.GL_INFO_LOG_LENGTH,length,0)
            val log=ByteArray(length[0]);gl.glGetShaderInfoLog(shader,log.size,null,0,log,0)
            gl.glDeleteShader(shader);error("GeoGebra shader: ${String(log)}")
        }
        return shader
    }
    override fun checkProgram(program: Int) {
        val status=IntArray(1);gl.glGetProgramiv(program,GL2ES2.GL_LINK_STATUS,status,0)
        if(status[0]==0) {
            val length=IntArray(1);gl.glGetProgramiv(program,GL2ES2.GL_INFO_LOG_LENGTH,length,0)
            val log=ByteArray(length[0]);gl.glGetProgramInfoLog(program,log.size,null,0,log,0)
            error("GeoGebra program: ${String(log)}")
        }
    }
}
