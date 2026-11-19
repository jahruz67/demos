package dev.demos.graphing.plot

import dev.demos.graphing.geogebra.NativeGL
import java.nio.Buffer
import android.opengl.GLES20

internal class AndroidGL : NativeGL {
    var density = 1f
    var frameHeight = 1
    override fun glCreateProgram(): Int = GLES20.glCreateProgram()
    override fun glAttachShader(program: Int, shader: Int) = GLES20.glAttachShader(program, shader)
    override fun glBindAttribLocation(program: Int, index: Int, name: String) = GLES20.glBindAttribLocation(program, index, name)
    override fun glLinkProgram(program: Int) = GLES20.glLinkProgram(program)
    override fun glGetUniformLocation(program: Int, name: String): Int = GLES20.glGetUniformLocation(program, name)
    override fun glGenBuffers(count: Int, buffers: IntArray, offset: Int) = GLES20.glGenBuffers(count, buffers, offset)
    override fun glBindBuffer(target: Int, buffer: Int) = GLES20.glBindBuffer(target, buffer)
    override fun glVertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) = GLES20.glVertexAttribPointer(index, size, type, normalized, stride, offset)
    override fun glUniform3fv(location: Int, count: Int, values: FloatArray, offset: Int) = GLES20.glUniform3fv(location, count, values, offset)
    override fun glUniform3f(location: Int, x: Float, y: Float, z: Float) = GLES20.glUniform3f(location, x, y, z)
    override fun glEnableVertexAttribArray(index: Int) = GLES20.glEnableVertexAttribArray(index)
    override fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int) = GLES20.glBufferData(target, size, data, usage)
    override fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) = GLES20.glDrawElements(mode, count, type, offset)
    override fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, values: FloatArray, offset: Int) = GLES20.glUniformMatrix4fv(location, count, transpose, values, offset)
    override fun glUseProgram(program: Int) = GLES20.glUseProgram(program)
    override fun glDisableVertexAttribArray(index: Int) = GLES20.glDisableVertexAttribArray(index)
    override fun glDetachShader(program: Int, shader: Int) = GLES20.glDetachShader(program, shader)
    override fun glDeleteShader(shader: Int) = GLES20.glDeleteShader(shader)
    override fun glDeleteProgram(program: Int) = GLES20.glDeleteProgram(program)
    override fun glUniform4f(location: Int, a: Float, b: Float, c: Float, d: Float) = GLES20.glUniform4f(location, a, b, c, d)
    override fun glUniform4fv(location: Int, count: Int, values: FloatArray, offset: Int) = GLES20.glUniform4fv(location, count, values, offset)
    override fun glUniform2fv(location: Int, count: Int, values: FloatArray, offset: Int) = GLES20.glUniform2fv(location, count, values, offset)
    override fun glViewport(x: Int, y: Int, width: Int, height: Int) = GLES20.glViewport((x*density).toInt(), frameHeight-((y+height)*density).toInt(), (width*density).toInt(), (height*density).toInt())
    override fun glUniform1i(location: Int, value: Int) = GLES20.glUniform1i(location, value)
    override fun glUniform1fv(location: Int, count: Int, values: FloatArray, offset: Int) = GLES20.glUniform1fv(location, count, values, offset)
    override fun glCullFace(mode: Int) = GLES20.glCullFace(mode)
    override fun glStencilFunc(func: Int, ref: Int, mask: Int) = GLES20.glStencilFunc(func, ref, mask)
    override fun glDepthMask(flag: Boolean) = GLES20.glDepthMask(flag)
    override fun glColorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) = GLES20.glColorMask(r, g, b, a)
    override fun glClearColor(r: Float, g: Float, b: Float, a: Float) = GLES20.glClearColor(r, g, b, a)
    override fun glPolygonOffset(factor: Float, units: Float) = GLES20.glPolygonOffset(factor, units)
    override fun glGenTextures(count: Int, textures: IntArray, offset: Int) = GLES20.glGenTextures(count, textures, offset)
    override fun glBindTexture(target: Int, texture: Int) = GLES20.glBindTexture(target, texture)
    override fun glEnable(capability: Int) = GLES20.glEnable(capability)
    override fun glDisable(capability: Int) = GLES20.glDisable(capability)
    override fun glClear(mask: Int) = GLES20.glClear(mask)
    override fun glBindFramebuffer(target: Int, framebuffer: Int) = GLES20.glBindFramebuffer(target, framebuffer)
    override fun glBindRenderbuffer(target: Int, renderbuffer: Int) = GLES20.glBindRenderbuffer(target, renderbuffer)
    override fun glTexParameterf(target: Int, name: Int, value: Float) = GLES20.glTexParameterf(target, name, value)
    override fun glTexImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, border: Int, format: Int, type: Int, data: Buffer?) = GLES20.glTexImage2D(target, level, internalFormat, width, height, border, format, type, data)
    override fun glRenderbufferStorage(target: Int, format: Int, width: Int, height: Int) = GLES20.glRenderbufferStorage(target, format, width, height)
    override fun glGenRenderbuffers(count: Int, buffers: IntArray, offset: Int) = GLES20.glGenRenderbuffers(count, buffers, offset)
    override fun glGenFramebuffers(count: Int, buffers: IntArray, offset: Int) = GLES20.glGenFramebuffers(count, buffers, offset)
    override fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) = GLES20.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    override fun glFramebufferRenderbuffer(target: Int, attachment: Int, rendertarget: Int, renderbuffer: Int) = GLES20.glFramebufferRenderbuffer(target, attachment, rendertarget, renderbuffer)
    override fun glCheckFramebufferStatus(target: Int): Int = GLES20.glCheckFramebufferStatus(target)
    override fun glDepthFunc(func: Int) = GLES20.glDepthFunc(func)
    override fun glBlendFunc(source: Int, destination: Int) = GLES20.glBlendFunc(source, destination)
    override fun glGenerateMipmap(target: Int) = GLES20.glGenerateMipmap(target)
    override fun glDeleteTextures(count: Int, textures: IntArray, offset: Int) = GLES20.glDeleteTextures(count, textures, offset)
    override fun glDeleteBuffers(count: Int, buffers: IntArray, offset: Int) = GLES20.glDeleteBuffers(count, buffers, offset)
    override fun compileShader(type: Int, source: String): Int {
        val shader=GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader)
        val status=IntArray(1);GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
        check(status[0]!=0) { "GeoGebra shader: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }
    override fun checkProgram(program: Int) {
        val status=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0)
        check(status[0]!=0) { "GeoGebra program: ${GLES20.glGetProgramInfoLog(program)}" }
    }
}
