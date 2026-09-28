package com.example.core.render

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import kotlin.math.log2
import kotlin.math.max

/**
 * Applies a preset to a bitmap. Uses OpenGL ES 3 on its own thread (EGL contexts belong to one thread)
 * and falls back to [CpuRenderer] if the GPU path cannot start or fails.
 */
class PresetEngine {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "jrlink-render") }
    private val dispatcher = thread.asCoroutineDispatcher()
    private var gl: GlRenderer? = null
    private var glFailed = false

    /** Renders [src] with [params] at [src]'s size. Never returns [src] itself. */
    suspend fun render(src: Bitmap, params: RenderParams): Bitmap = withContext(dispatcher) {
        if (params.isIdentity) return@withContext src.copy(Bitmap.Config.ARGB_8888, false)
        if (!glFailed) {
            try {
                val renderer = gl ?: GlRenderer().also { gl = it }
                return@withContext renderer.render(src, params)
            } catch (t: Throwable) {
                Log.w(TAG, "GPU render failed, using CPU", t)
                glFailed = true
                runCatching { gl?.release() }
                gl = null
            }
        }
        cpu(src, params)
    }

    fun release() {
        thread.execute { runCatching { gl?.release() }; gl = null }
        thread.shutdown()
    }

    private fun cpu(src: Bitmap, params: RenderParams): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = CpuRenderer.render(px, w, h, params)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val TAG = "PresetEngine"
    }
}

/** One EGL pbuffer context, one program; textures are recreated per call (photos differ in size). */
private class GlRenderer {
    private val display: EGLDisplay
    private val context: EGLContext
    private val surface: EGLSurface
    private val program: Int
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    val maxTexture: Int

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT_KHR */,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "eglChooseConfig" }
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }
        surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent" }

        program = link(compile(GLES30.GL_VERTEX_SHADER, PresetShader.VERTEX), compile(GLES30.GL_FRAGMENT_SHADER, PresetShader.FRAGMENT))
        val max = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, max, 0)
        maxTexture = max[0]
    }

    fun render(src: Bitmap, p: RenderParams): Bitmap {
        EGL14.eglMakeCurrent(display, surface, surface, context)
        // Very large photos are rendered at the largest size the GPU accepts.
        val scale = minOf(1f, maxTexture.toFloat() / max(src.width, src.height))
        val input = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        val w = input.width; val h = input.height

        val tex = IntArray(3)
        GLES30.glGenTextures(3, tex, 0)
        val (srcTex, lutTex, outTex) = Triple(tex[0], tex[1], tex[2])

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, input, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        clampEdges()

        val lutBytes = ByteBuffer.allocateDirect(256 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until 256) {
            for (c in 0..2) lutBytes.put((p.lut[i * 3 + c].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
            lutBytes.put(0xFF.toByte())
        }
        lutBytes.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 256, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, lutBytes)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        clampEdges()

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, outTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, outTex, 0)
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) { "framebuffer" }

        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(program)
        setUniforms(p, w, h, srcTex, lutTex)
        val pos = GLES30.glGetAttribLocation(program, "aPos")
        GLES30.glEnableVertexAttribArray(pos)
        GLES30.glVertexAttribPointer(pos, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        val pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        val error = GLES30.glGetError()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, fbo, 0)
        GLES30.glDeleteTextures(3, tex, 0)
        if (input !== src) input.recycle()
        check(error == GLES30.GL_NO_ERROR) { "GL error $error" }

        pixels.position(0)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(pixels) }
    }

    private fun setUniforms(p: RenderParams, w: Int, h: Int, srcTex: Int, lutTex: Int) {
        fun loc(name: String) = GLES30.glGetUniformLocation(program, name)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
        GLES30.glUniform1i(loc("uSrc"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lutTex)
        GLES30.glUniform1i(loc("uLut"), 1)

        GLES30.glUniform2f(loc("uSize"), w.toFloat(), h.toFloat())
        // Blur radii follow the CPU renderer: 1/2 and 1/16 of the image.
        GLES30.glUniform1f(loc("uSmallLod"), 1f)
        GLES30.glUniform1f(loc("uLargeLod"), log2(16f))

        GLES30.glUniform3f(loc("uWb"), p.wbR, p.wbG, p.wbB)
        GLES30.glUniform1f(loc("uExposure"), p.exposure)
        GLES30.glUniform1f(loc("uContrast"), p.contrast)
        GLES30.glUniform1f(loc("uHighlights"), p.highlights)
        GLES30.glUniform1f(loc("uShadows"), p.shadows)
        GLES30.glUniform1f(loc("uWhites"), p.whites)
        GLES30.glUniform1f(loc("uBlacks"), p.blacks)
        GLES30.glUniform1f(loc("uVibrance"), p.vibrance)
        GLES30.glUniform1f(loc("uSaturation"), p.saturation)
        GLES30.glUniform3fv(loc("uHsl"), 8, p.hsl, 0)
        GLES30.glUniform4fv(loc("uGrading"), 4, p.grading, 0)
        GLES30.glUniform1f(loc("uBalance"), p.gradingBalance)
        GLES30.glUniform1f(loc("uBlending"), p.gradingBlending)
        GLES30.glUniform2fv(loc("uCalib"), 3, p.calibration, 0)
        GLES30.glUniform1f(loc("uShadowTint"), p.shadowTint)
        GLES30.glUniform1i(loc("uMono"), if (p.monochrome) 1 else 0)
        GLES30.glUniform1fv(loc("uGray"), 8, p.grayMixer, 0)
        GLES30.glUniform1f(loc("uTexture"), p.texture)
        GLES30.glUniform1f(loc("uClarity"), p.clarity)
        GLES30.glUniform1f(loc("uDehaze"), p.dehaze)
        GLES30.glUniform1f(loc("uSharpen"), p.sharpen)
        GLES30.glUniform1f(loc("uMasking"), p.sharpenMasking)
        GLES30.glUniform1f(loc("uNoise"), p.noiseReduction)
        GLES30.glUniform1f(loc("uVignette"), p.vignette)
        GLES30.glUniform1f(loc("uVigMid"), p.vignetteMidpoint)
        GLES30.glUniform1f(loc("uVigFeather"), p.vignetteFeather)
        GLES30.glUniform1f(loc("uVigRound"), p.vignetteRoundness)
        GLES30.glUniform1f(loc("uGrain"), p.grain)
        GLES30.glUniform1f(loc("uGrainSize"), p.grainSize)
    }

    private fun clampEdges() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "shader: " + GLES30.glGetShaderInfoLog(shader) }
        return shader
    }

    private fun link(vs: Int, fs: Int): Int {
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "link: " + GLES30.glGetProgramInfoLog(p) }
        return p
    }

    fun release() {
        GLES30.glDeleteProgram(program)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}
