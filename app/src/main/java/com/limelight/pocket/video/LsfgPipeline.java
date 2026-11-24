package com.limelight.pocket.video;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;

import com.limelight.LimeLog;
import com.limelight.framegen.FrameGen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Lossless Scaling on the stream, inside the app (no screen capture, no overlay): the decoder
 * draws into a texture; each picture is copied (GPU, a fraction of a millisecond) into an RGBA
 * hardware buffer; lsfg-vk makes the frames in between from the last two, on the user's own
 * Lossless.dll; and they're shown on the stream's own surface through Vulkan, paced to the
 * screen. Anything that fails at the start leaves the stream exactly as it is without it.
 */
@TargetApi(29)
public final class LsfgPipeline implements SurfaceTexture.OnFrameAvailableListener {

    public interface Listener {
        /** The surface the decoder draws into (on the main thread). */
        void onDecoderSurface(Surface surface);

        /** It couldn't start (why, in words): the stream goes on without it (on the main thread). */
        void onUnavailable(String why);
    }

    private static final String VERTEX =
            "attribute vec4 aPos;\n"
                    + "attribute vec4 aTex;\n"
                    + "uniform mat4 uTex;\n"
                    + "varying vec2 vTex;\n"
                    + "void main() {\n"
                    + "  gl_Position = aPos;\n"
                    + "  vTex = (uTex * aTex).xy;\n"
                    + "}\n";
    private static final String FRAGMENT =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "varying vec2 vTex;\n"
                    + "uniform samplerExternalOES uSrc;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = vec4(texture2D(uSrc, vTex).rgb, 1.0);\n"
                    + "}\n";

    /** For the stream's overlay and menu. */
    public static volatile boolean active;

    /**
     * The one pipeline the native generator belongs to (it has one context): a stream that starts
     * while the one before is still closing takes it over, and the old one then leaves it alone.
     */
    private static LsfgPipeline owner;
    private static final Object OWNER = new Object();
    public static volatile int multiplierNow;

    private final Context context;
    private final int width;
    private final int height;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float[] texMatrix = new float[16];

    private HandlerThread glThread;
    private Handler gl;
    private HandlerThread readerThread;
    private ImageReader reader;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private int program;
    private int oesTexture;
    private int aPos;
    private int aTex;
    private int uTex;
    private FloatBuffer quad;
    private SurfaceTexture surfaceTexture;
    private Surface decoderSurface;
    private volatile boolean released;

    public LsfgPipeline(Context context, int streamWidth, int streamHeight) {
        this.context = context.getApplicationContext();
        this.width = streamWidth;
        this.height = streamHeight;
    }

    /**
     * Starts it, showing on output (the stream's surface, outW x outH on screen). The listener
     * gets the decoder's surface, or why it couldn't start.
     */
    public void start(final Surface output, final int outW, final int outH, final float refreshHz, final Listener l) {
        glThread = new HandlerThread("pocket-lsfg-gl");
        glThread.start();
        gl = new Handler(glThread.getLooper());
        gl.post(() -> {
            String why = null;
            try {
                why = startNative(output, outW, outH, refreshHz);
                if (why == null) {
                    startReader();
                    startGl();
                }
            } catch (Throwable t) {
                why = t.getMessage() != null ? t.getMessage() : t.toString();
            }
            if (why != null) {
                LimeLog.warning("LSFG: unavailable: " + why);
                final String reason = why;
                shutdown();
                main.post(() -> l.onUnavailable(reason));
                return;
            }
            active = true;
            final Surface s = decoderSurface;
            main.post(() -> l.onDecoderSurface(s));
        });
    }

    /** The stream's surface changed size (or came back). */
    public void setOutput(Surface output, int outW, int outH) {
        if (released || owner != this) {
            return;
        }
        try {
            FrameGen.setOutputSurface(output, outW, outH);
        } catch (Throwable t) {
            LimeLog.warning("LSFG: setOutputSurface: " + t);
        }
    }

    /** On, or shown as it comes (the frames pass straight through). */
    public void setBypass(boolean bypass) {
        if (!released && active) {
            try {
                FrameGen.setBypass(bypass);
            } catch (Throwable ignored) {
            }
        }
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        active = false;
        Handler h = gl;
        if (h != null) {
            h.post(this::shutdown);
            h.post(() -> {
                if (glThread != null) {
                    glThread.quitSafely();
                }
            });
        }
        else {
            shutdown();
        }
    }

    // ---- The native side ----------------------------------------------------------------------

    private String startNative(Surface output, int outW, int outH, float refreshHz) {
        if (!FrameGen.load()) {
            return FrameGen.loadError();
        }
        String cache = LsfgSetup.cache(context).getAbsolutePath();
        int multiplier = LsfgSetup.multiplier(context);
        boolean fp16 = false;
        try {
            fp16 = FrameGen.isFramegenFp16Supported(cache);
        } catch (Throwable ignored) {
        }
        synchronized (OWNER) {
            if (owner != null && owner != this) {
                owner.released = true;
                FrameGen.setOutputSurface(null, 0, 0);
                FrameGen.destroyContext();
            }
            owner = this;
        }
        // The output first (the render loop presents there), then the generator itself.
        // Pacing as LSFG-Android's "low latency": a stream wants its frames soon, not late.
        FrameGen.setOutputSurface(output, outW, outH);
        int rc = FrameGen.initContext(cache, width, height, multiplier, LsfgSetup.flowScale(context),
                LsfgSetup.performance(context), false, false, fp16,
                false, 0, 1, 0.5f, 1.0f, 0f, true,
                false, 0, 0.5f, 0.5f, 0f, 0f,
                false, 0, 0, 1.0f, 0.5f, 0.5f,
                0, 0.2f, 3.0f, 1.5f, 2);
        if (rc != FrameGen.OK) {
            FrameGen.setOutputSurface(null, 0, 0);
            return LsfgSetup.explain(context, rc);
        }
        if (refreshHz > 1) {
            FrameGen.setVsyncPeriodNs(Math.round(1_000_000_000.0 / refreshHz));
        }
        multiplierNow = multiplier;
        return null;
    }

    private void startReader() {
        readerThread = new HandlerThread("pocket-lsfg-in");
        readerThread.start();
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 5,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
        reader.setOnImageAvailableListener(r -> {
            Image img;
            try {
                img = r.acquireNextImage();
            } catch (RuntimeException e) {
                return;
            }
            if (img == null) {
                return;
            }
            try {
                HardwareBuffer hb = img.getHardwareBuffer();
                if (hb != null) {
                    if (!released) {
                        FrameGen.pushFrame(hb, img.getTimestamp());
                    }
                    hb.close();
                }
            } catch (Throwable t) {
                LimeLog.warning("LSFG: pushFrame: " + t);
            } finally {
                img.close();
            }
        }, new Handler(readerThread.getLooper()));
    }

    // ---- The copy: decoder texture -> RGBA buffer -----------------------------------------------

    private void startGl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw new IllegalStateException("EGL unavailable");
        }
        int[] attribs = {
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            throw new IllegalStateException("No EGL config");
        }
        eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], reader.getSurface(),
                new int[]{EGL14.EGL_NONE}, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT || eglSurface == EGL14.EGL_NO_SURFACE
                || !EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) {
            throw new IllegalStateException("EGL surface on the frame buffer failed");
        }
        // Every frame as it comes (the generator paces the screen, not this copy)
        EGL14.eglSwapInterval(display, 0);

        program = link(VERTEX, FRAGMENT);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aTex = GLES20.glGetAttribLocation(program, "aTex");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
        float[] v = {
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f,
        };
        quad = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(v).position(0);

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        oesTexture = tex[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        surfaceTexture = new SurfaceTexture(oesTexture);
        surfaceTexture.setDefaultBufferSize(width, height);
        surfaceTexture.setOnFrameAvailableListener(this, gl);
        decoderSurface = new Surface(surfaceTexture);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        if (released || eglSurface == EGL14.EGL_NO_SURFACE) {
            return;
        }
        try {
            st.updateTexImage();
            st.getTransformMatrix(texMatrix);
            GLES20.glViewport(0, 0, width, height);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
            GLES20.glUniformMatrix4fv(uTex, 1, false, texMatrix, 0);
            quad.position(0);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(aPos);
            quad.position(2);
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(aTex);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, st.getTimestamp());
            EGL14.eglSwapBuffers(display, eglSurface);
        } catch (RuntimeException e) {
            LimeLog.warning("LSFG: copy failed: " + e);
        }
    }

    private static int link(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new IllegalStateException("Copy program: " + GLES20.glGetProgramInfoLog(p));
        }
        return p;
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new IllegalStateException("Copy shader: " + GLES20.glGetShaderInfoLog(s));
        }
        return s;
    }

    // ---- Ending ---------------------------------------------------------------------------------

    /** Everything off and let go (on the GL thread, or wherever nothing was started). */
    private void shutdown() {
        synchronized (OWNER) {
            if (owner == this) {
                active = false;
                try {
                    FrameGen.setOutputSurface(null, 0, 0);
                } catch (Throwable ignored) {
                }
                try {
                    FrameGen.destroyContext();
                } catch (Throwable ignored) {
                }
                owner = null;
            }
        }
        if (surfaceTexture != null) {
            surfaceTexture.setOnFrameAvailableListener(null);
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, eglSurface);
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, eglContext);
            }
            EGL14.eglTerminate(display);
        }
        eglSurface = EGL14.EGL_NO_SURFACE;
        eglContext = EGL14.EGL_NO_CONTEXT;
        display = EGL14.EGL_NO_DISPLAY;
        if (decoderSurface != null) {
            decoderSurface.release();
            decoderSurface = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (readerThread != null) {
            readerThread.quitSafely();
            readerThread = null;
        }
    }
}
