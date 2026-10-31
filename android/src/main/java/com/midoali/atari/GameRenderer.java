package com.midoali.atari;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import com.midoali.atari.core.ControlLayout;
import com.midoali.atari.core.DisplayMath;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Full-frame RGB565 renderer with original, smooth and bicubic-enhanced display modes. */
final class GameRenderer implements GLSurfaceView.Renderer {
    private static final String VERTEX =
            "attribute vec2 aPosition; attribute vec2 aTexCoord; varying vec2 vTexCoord;" +
            "void main(){gl_Position=vec4(aPosition,0.0,1.0);vTexCoord=aTexCoord;}";
    private static final String BASIC_FRAGMENT =
            "precision mediump float; varying vec2 vTexCoord; uniform sampler2D uTexture;" +
            "void main(){gl_FragColor=texture2D(uTexture,vTexCoord);}";
    // Four linear taps reconstruct a separable Catmull-Rom filter: smoother diagonals
    // without the blocky nearest-neighbour look or a heavy 16-tap shader.
    private static final String ENHANCED_FRAGMENT =
            "precision mediump float; varying vec2 vTexCoord; uniform sampler2D uTexture;" +
            "uniform vec2 uTextureSize; uniform int uMode;" +
            "vec2 w0(vec2 x){return -0.5*x*x*x+x*x-0.5*x;}" +
            "vec2 w1(vec2 x){return 1.5*x*x*x-2.5*x*x+1.0;}" +
            "vec2 w2(vec2 x){return -1.5*x*x*x+2.0*x*x+0.5*x;}" +
            "vec2 w3(vec2 x){return 0.5*x*x*x-0.5*x*x;}" +
            "void main(){" +
            "if(uMode!=2){gl_FragColor=texture2D(uTexture,vTexCoord);return;}" +
            "vec2 p=vTexCoord*uTextureSize-0.5; vec2 i=floor(p)+0.5; vec2 f=p-i;" +
            "vec2 a=w0(f); vec2 b=w1(f); vec2 c=w2(f); vec2 d=w3(f);" +
            "vec2 gx=a+b; vec2 gy=c+d;" +
            "vec2 ox=b/max(gx,vec2(0.0001))-1.0; vec2 oy=d/max(gy,vec2(0.0001))+1.0;" +
            "vec2 s0=(i+vec2(ox.x,ox.y))/uTextureSize; vec2 s1=(i+vec2(oy.x,ox.y))/uTextureSize;" +
            "vec2 s2=(i+vec2(ox.x,oy.y))/uTextureSize; vec2 s3=(i+vec2(oy.x,oy.y))/uTextureSize;" +
            "vec4 r=texture2D(uTexture,s0)*gx.x*gx.y+texture2D(uTexture,s1)*gx.x*gy.y" +
            "+texture2D(uTexture,s2)*gy.x*gx.y+texture2D(uTexture,s3)*gy.x*gy.y;" +
            "gl_FragColor=clamp(r,0.0,1.0);}";
    private final FloatBuffer quad = floats(new float[] {-1,-1, 1,-1, -1,1, 1,1});
    private final FloatBuffer tex = floats(new float[] {0,1, 1,1, 0,0, 1,0});
    private final ByteBuffer upload = ByteBuffer.allocateDirect(1024 * 576 * 2).order(ByteOrder.nativeOrder());
    private final EmulatorEngine engine;
    private int program, basicProgram, enhancedProgram, texture, position, texCoord;
    private int basicPosition, basicTexCoord, textureUniform, sizeUniform, modeUniform;
    private int textureWidth, textureHeight;
    private volatile int lastSerial = -1;
    private int surfaceWidth, surfaceHeight;
    private volatile int videoMode = ControlLayout.VIDEO_ENHANCED;
    private volatile int displayMode = ControlLayout.DISPLAY_FIT;
    private volatile int appliedVideoMode = -1, appliedFilter = -1, appliedDisplayMode = -1;
    private volatile int[] appliedViewport = new int[] {0,0,0,0};
    private volatile boolean enhancedAvailable;

    GameRenderer(EmulatorEngine engine) { this.engine = engine; }
    void setBilinear(boolean enabled) { setVideoMode(enabled ? ControlLayout.VIDEO_SMOOTH : ControlLayout.VIDEO_ORIGINAL); }
    void setVideoMode(int mode) { videoMode = Math.max(0, Math.min(2, mode)); }
    void setDisplayMode(int mode) { displayMode = mode == ControlLayout.DISPLAY_STRETCH ? 1 : 0; }
    int appliedFilter() { return appliedFilter; }
    int appliedVideoMode() { return appliedVideoMode; }
    int appliedDisplayMode() { return appliedDisplayMode; }
    int[] appliedViewport() { return appliedViewport.clone(); }
    boolean enhancedAvailable() { return enhancedAvailable; }
    int frameSerial() { return lastSerial; }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        basicProgram = program(VERTEX, BASIC_FRAGMENT);
        enhancedProgram = basicProgram;
        try { enhancedProgram = program(VERTEX, ENHANCED_FRAGMENT); enhancedAvailable = true; }
        catch (RuntimeException exception) { Log.w("SegaRenderer", "Enhanced shader unavailable", exception); enhancedAvailable = false; }
        basicPosition = GLES20.glGetAttribLocation(basicProgram, "aPosition");
        basicTexCoord = GLES20.glGetAttribLocation(basicProgram, "aTexCoord");
        int[] ids = new int[1]; GLES20.glGenTextures(1, ids, 0); texture = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 2);
        textureWidth = 0; textureHeight = 0; lastSerial = -1;
        GLES20.glClearColor(0,0,0,1);
    }
    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width; surfaceHeight = height;
    }
    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        EmulatorEngine.Frame frame = engine.frame();
        if (frame == null || !frame.acquire()) return;
        try {
            int mode = videoMode;
            boolean enhanced = mode == ControlLayout.VIDEO_ENHANCED && enhancedAvailable;
            program = enhanced ? enhancedProgram : basicProgram;
            position = enhanced ? GLES20.glGetAttribLocation(program, "aPosition") : basicPosition;
            texCoord = enhanced ? GLES20.glGetAttribLocation(program, "aTexCoord") : basicTexCoord;
            GLES20.glUseProgram(program);
            int filter = mode == ControlLayout.VIDEO_ORIGINAL ? GLES20.GL_NEAREST : GLES20.GL_LINEAR;
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
            if (frame.serial != lastSerial) {
                int bytes = frame.width * frame.height * 2;
                upload.clear(); upload.put(frame.pixels, 0, bytes); upload.flip();
                if (frame.width != textureWidth || frame.height != textureHeight) {
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, frame.width, frame.height,
                            0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, upload);
                    textureWidth = frame.width; textureHeight = frame.height;
                } else {
                    GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, frame.width, frame.height,
                            GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, upload);
                }
                lastSerial = frame.serial;
            }
            int[] viewport = DisplayMath.viewport(surfaceWidth, surfaceHeight, frame.aspect,
                    displayMode == ControlLayout.DISPLAY_STRETCH);
            GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(texCoord);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad);
            GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 0, tex);
            if (enhanced) {
                textureUniform = GLES20.glGetUniformLocation(program, "uTexture");
                sizeUniform = GLES20.glGetUniformLocation(program, "uTextureSize");
                modeUniform = GLES20.glGetUniformLocation(program, "uMode");
                GLES20.glUniform1i(textureUniform, 0);
                GLES20.glUniform2f(sizeUniform, textureWidth, textureHeight);
                GLES20.glUniform1i(modeUniform, ControlLayout.VIDEO_ENHANCED);
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(texCoord);
            appliedFilter = filter; appliedVideoMode = enhanced ? ControlLayout.VIDEO_ENHANCED :
                    (mode == ControlLayout.VIDEO_ORIGINAL ? ControlLayout.VIDEO_ORIGINAL : ControlLayout.VIDEO_SMOOTH);
            appliedDisplayMode = displayMode; appliedViewport = viewport;
        } finally { frame.release(); }
    }
    private static FloatBuffer floats(float[] values) {
        return (FloatBuffer) ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(values).position(0);
    }
    private static int program(String vertex, String fragment) {
        int vs = shader(GLES20.GL_VERTEX_SHADER, vertex), fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment);
        int id = GLES20.glCreateProgram(); GLES20.glAttachShader(id, vs); GLES20.glAttachShader(id, fs);
        GLES20.glLinkProgram(id); int[] ok = new int[1];
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) { String log = GLES20.glGetProgramInfoLog(id); GLES20.glDeleteProgram(id); throw new IllegalStateException(log); }
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs); return id;
    }
    private static int shader(int type, String source) {
        int id = GLES20.glCreateShader(type); GLES20.glShaderSource(id, source); GLES20.glCompileShader(id);
        int[] ok = new int[1]; GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) { String log = GLES20.glGetShaderInfoLog(id); GLES20.glDeleteShader(id); throw new IllegalStateException(log); }
        return id;
    }
}
