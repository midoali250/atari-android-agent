package com.midoali.atari;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Actual GPU sampling switch: GL_LINEAR for bilinear, GL_NEAREST for sharp pixels. */
final class GameRenderer implements GLSurfaceView.Renderer {
    private final EmulatorEngine engine;
    private volatile boolean bilinear;
    private volatile int appliedFilter = -1;
    private int texture, program;
    private int surfaceWidth, surfaceHeight, textureWidth, textureHeight, lastSerial = -1;
    private final FloatBuffer quad = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    GameRenderer(EmulatorEngine engine, boolean bilinear) { this.engine = engine; this.bilinear = bilinear; }
    void setBilinear(boolean value) { bilinear = value; }
    int appliedFilter() { return appliedFilter; }
    int frameSerial() { return lastSerial; }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.025f, 0.035f, 0.055f, 1f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        program = program("attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv; void main(){gl_Position=vec4(aPos,0.,1.);vUv=aUv;}",
                "precision mediump float; varying vec2 vUv; uniform sampler2D uFrame; void main(){gl_FragColor=texture2D(uFrame,vUv);}");
        int[] textures = new int[1]; GLES20.glGenTextures(1, textures, 0); texture = textures[0];
        textureWidth = textureHeight = 0; lastSerial = -1; appliedFilter = -1;
        quad.clear(); quad.put(new float[]{-1,-1,0,1, 1,-1,1,1, -1,1,0,0, 1,1,1,0}); quad.position(0);
    }
    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width; surfaceHeight = height;
    }
    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        EmulatorEngine.Frame frame = engine.frame();
        if (frame == null || frame.width <= 0 || frame.height <= 0) return;
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        int filter = bilinear ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
        if (filter != appliedFilter) {
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            appliedFilter = filter;
        }
        ByteBuffer pixels = ByteBuffer.wrap(frame.pixels).order(ByteOrder.nativeOrder());
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        if (textureWidth != frame.width || textureHeight != frame.height) {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, frame.width, frame.height,
                    0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, pixels);
            textureWidth = frame.width; textureHeight = frame.height;
        } else if (frame.serial != lastSerial) {
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, frame.width, frame.height,
                    GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, pixels);
        }
        lastSerial = frame.serial;
        // Genesis output is displayed at 4:3, without stretching or cropping.
        float viewAspect = (float)surfaceWidth / surfaceHeight;
        float gameAspect = 4f / 3f;
        int width = surfaceWidth, height = surfaceHeight;
        if (viewAspect > gameAspect) width = Math.round(surfaceHeight * gameAspect);
        else height = Math.round(surfaceWidth / gameAspect);
        GLES20.glViewport((surfaceWidth-width)/2, (surfaceHeight-height)/2, width, height);
        GLES20.glUseProgram(program);
        int pos = GLES20.glGetAttribLocation(program, "aPos");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        quad.position(0); GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad);
        quad.position(2); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, quad);
        GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(uv);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uFrame"), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }
    private static int program(String vertexSource, String fragmentSource) {
        int vertex = shader(GLES20.GL_VERTEX_SHADER, vertexSource), fragment = shader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int result = GLES20.glCreateProgram(); GLES20.glAttachShader(result, vertex); GLES20.glAttachShader(result, fragment);
        GLES20.glLinkProgram(result); int[] status = new int[1];
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(result));
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment); return result;
    }
    private static int shader(int type, String source) {
        int shader = GLES20.glCreateShader(type); GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
        int[] status = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader));
        return shader;
    }
}
