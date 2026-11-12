package nayomy.visuals.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import nayomy.visuals.module.AtmosphereModule;
import nayomy.visuals.module.WetWorldModule;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Независимый OpenGL-рендерер Wet World (по образцу AtmospherePostPipeline):
 * постпроцесс по кадру мира — копия цвета + глубина основного буфера.
 */
public final class WetWorldPipeline implements AutoCloseable {

    private static final WetWorldPipeline INSTANCE = new WetWorldPipeline();
    public static WetWorldPipeline getInstance() { return INSTANCE; }
    private WetWorldPipeline() {}
    public void reset() { failed = false; }

    private int program, vao, copyTexture, copyFbo, targetFbo, linearSampler, nearestSampler;
    private int width, height;
    private boolean failed;
    private final Map<String, Integer> uniforms = new HashMap<>();

    // состояние, которое должно меняться плавно между кадрами
    private double ripplePhase;
    private float outside = 1f;
    private long lastNs = System.nanoTime();

    public void render(Camera camera, Matrix4f projection) {
        Minecraft client = Minecraft.getInstance();
        WetWorldModule module = WetWorldModule.INSTANCE;
        if (!module.enabled || failed || client.level == null || !camera.isInitialized()) return;
        if (camera.getFluidInCamera() != FogType.NONE) return;

        // время считаем всегда, чтобы рябь не «прыгала» при смене скорости или включении
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) * 1e-9f);
        lastNs = now;
        ripplePhase = (ripplePhase + dt * module.rippleSpeed) % 20000.0;

        float rain = Math.max(0f, Math.min(1f, client.level.getRainLevel(camera.getPartialTickTime())));
        float strength = module.reflection;
        if (module.rainOnly) {
            if (rain <= 0.01f) return;
            strength *= rain;
        }

        RenderTarget framebuffer = client.getMainRenderTarget();
        if (!(framebuffer.getColorTexture() instanceof GlTexture color)
                || !(framebuffer.getDepthTexture() instanceof GlTexture depth)) return;
        if (framebuffer.width < 2 || framebuffer.height < 2) return;

        // освещённость неба в точке камеры: в помещении отражается не небо, а темнота (плавно)
        float targetOutside = client.level.getBrightness(LightLayer.SKY, camera.blockPosition()) / 15f;
        outside += (targetOutside - outside) * Math.min(1f, dt * 4f);

        try (GlStateGuard ignored = new GlStateGuard(); MemoryStack stack = MemoryStack.stackPush()) {
            if (program == 0) initialize();
            resize(framebuffer.width, framebuffer.height);

            glBindFramebuffer(GL_FRAMEBUFFER, targetFbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.glId(), 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("WetWorld target framebuffer is incomplete");
            glBindFramebuffer(GL_READ_FRAMEBUFFER, targetFbo);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, copyFbo);
            glDisable(GL_SCISSOR_TEST);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, targetFbo);
            glViewport(0, 0, width, height);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_BLEND);
            glDisable(GL_CULL_FACE);
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_FRAMEBUFFER_SRGB);
            glDisable(GL_RASTERIZER_DISCARD);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glColorMask(true, true, true, true);
            glDepthMask(false);
            glUseProgram(program);
            glBindVertexArray(vao);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, copyTexture);
            glBindSampler(0, linearSampler);          // цвет — линейная фильтрация для чистых отражений
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, depth.glId());
            glBindSampler(1, nearestSampler);         // глубина — только nearest
            glUniform1i(location("uScene"), 0);
            glUniform1i(location("uDepth"), 1);

            // матрицы: проекция реального кадра, view->world = только поворот камеры
            FloatBuffer matrix = stack.mallocFloat(16);
            new Matrix4f(projection).invert().get(matrix);
            glUniformMatrix4fv(location("uInvProjection"), false, matrix);
            projection.get(matrix);
            glUniformMatrix4fv(location("uProjection"), false, matrix);
            new Matrix4f().rotation(camera.rotation()).get(matrix);
            glUniformMatrix4fv(location("uViewRot"), false, matrix);
            var position = camera.position();
            glUniform3f(location("uCamera"), (float) position.x, (float) position.y, (float) position.z);
            glUniform2f(location("uResolution"), width, height);

            // качество
            glUniform1i(location("uQuality"), module.quality);
            glUniform1i(location("uMaxSteps"), module.steps());
            glUniform1i(location("uBinarySteps"), module.binarySteps());
            f("uMaxDist", module.maxDist());
            f("uStepSize", module.stepSize());

            // внешний вид
            f("uStrength", strength);
            f("uDarkening", module.darkening);
            f("uPhase", (float) ripplePhase);
            f("uRippleOn", module.ripples ? 1f : 0f);

            // туман дальности — по дистанции прорисовки клиента
            float fogEnd = Math.max(32, client.options.getEffectiveRenderDistance() * 16);
            f("uFogStart", fogEnd * 0.75f);
            f("uFogEnd", fogEnd);

            applySky(client.level.getDayTime(), rain);

            glDrawArrays(GL_TRIANGLES, 0, 3);
        } catch (Exception error) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger(WetWorldPipeline.class).error("WetWorld renderer disabled after an error", error);
            close();
        }
    }

    /** Цвета неба/солнца для отражений: берём палитру Atmosphere, если он включён, иначе — дневное небо по умолчанию. */
    private void applySky(long dayTime, float rain) {
        float[] top = {.08f, .15f, .28f};
        float[] horizon = {.22f, .30f, .42f};
        float[] sunColor = {.85f, .88f, .95f};

        // высота солнца в ваниле: полдень в 6000 (вверху), горизонт на 0 и 12000
        double cycle = Math.floorMod(dayTime, 24000L) / 24000.0;
        float sunHeight = (float) Math.cos(2 * Math.PI * (cycle - .25));
        float[] sunDir = {(float) Math.cos(2 * Math.PI * cycle), sunHeight, .2f};
        float daylight = smooth(-.1f, .25f, sunHeight);

        AtmosphereModule atmosphere = AtmosphereModule.INSTANCE;
        if (atmosphere.enabled) {
            AtmosphereModule.VisualState s = atmosphere.parameters(dayTime);
            top = s.skyTop().clone();
            horizon = s.skyHorizon().clone();
            sunColor = s.sunColor();
            sunDir = s.sunDirection();   // совпадает с тем, где солнце рисует сам Atmosphere
        }

        // дождь серит небо и гасит блик солнца
        float[] gray = {.30f, .32f, .36f};
        float grey = rain * .6f;
        for (int i = 0; i < 3; i++) {
            top[i] = top[i] + (gray[i] * .6f - top[i]) * grey;
            horizon[i] = horizon[i] + (gray[i] - horizon[i]) * grey;
        }

        float len = (float) Math.sqrt(sunDir[0] * sunDir[0] + sunDir[1] * sunDir[1] + sunDir[2] * sunDir[2]);
        if (len < 1e-4f) len = 1f;

        float skyLight = .06f + .94f * daylight * outside;
        rgb("uSkyTop", top);
        rgb("uSkyHorizon", horizon);
        rgb("uSunColor", sunColor);
        glUniform3f(location("uSunDir"), sunDir[0] / len, sunDir[1] / len, sunDir[2] / len);
        f("uSkyLight", skyLight);
        f("uSunPower", daylight * outside * (1f - rain));
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private void initialize() throws IOException {
        int vertex = shader(GL_VERTEX_SHADER, "wet_world.vsh");
        int fragment = 0;
        try {
            fragment = shader(GL_FRAGMENT_SHADER, "wet_world.fsh");
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
                throw new IllegalStateException(glGetProgramInfoLog(program));
        } finally {
            glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
        vao = glGenVertexArrays();
        copyFbo = glGenFramebuffers();
        targetFbo = glGenFramebuffers();
        copyTexture = glGenTextures();
        linearSampler = sampler(GL_LINEAR);
        nearestSampler = sampler(GL_NEAREST);
    }

    private static int sampler(int filter) {
        int s = glGenSamplers();
        glSamplerParameteri(s, GL_TEXTURE_MIN_FILTER, filter);
        glSamplerParameteri(s, GL_TEXTURE_MAG_FILTER, filter);
        glSamplerParameteri(s, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(s, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(s, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        return s;
    }

    private static int shader(int type, String name) throws IOException {
        String source;
        try (var stream = WetWorldPipeline.class.getResourceAsStream("/assets/nayomy-visuals/wetworld/" + name)) {
            if (stream == null) throw new IOException("Missing shader " + name);
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            glDeleteShader(id);
            throw new IllegalStateException(name + ": " + log);
        }
        return id;
    }

    private void resize(int w, int h) {
        if (width == w && height == h) return;
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, copyTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindFramebuffer(GL_FRAMEBUFFER, copyFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, copyTexture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("WetWorld copy framebuffer is incomplete");
        width = w;
        height = h;
    }

    private int location(String name) { return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(program, n)); }
    private void f(String name, float v) { glUniform1f(location(name), v); }
    private void rgb(String name, float[] v) { glUniform3f(location(name), v[0], v[1], v[2]); }

    @Override
    public void close() {
        if (program != 0) glDeleteProgram(program);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (copyFbo != 0) glDeleteFramebuffers(copyFbo);
        if (targetFbo != 0) glDeleteFramebuffers(targetFbo);
        if (copyTexture != 0) glDeleteTextures(copyTexture);
        if (linearSampler != 0) glDeleteSamplers(linearSampler);
        if (nearestSampler != 0) glDeleteSamplers(nearestSampler);
        program = vao = copyFbo = targetFbo = copyTexture = linearSampler = nearestSampler = width = height = 0;
        uniforms.clear();
    }
}
