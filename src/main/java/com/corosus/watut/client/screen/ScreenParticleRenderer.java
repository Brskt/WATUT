package com.corosus.watut.client.screen;

import com.corosus.coroutil.util.CULog;
import com.corosus.watut.PlayerStatusManagerClient;
import com.corosus.watut.config.ConfigServerControlledSyncedToClient;
import com.corosus.watut.mixin.client.MinecraftAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.OptionalInt;

public class ScreenParticleRenderer {
    private static final int UNIFORM_RING_BUFFER_USAGE = GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM;
    private static final int BLUR_PARAMS_UBO_SIZE = 32;
    private static final int SAMPLER_INFO_UBO_SIZE = 16;

    public static boolean isRenderingParticleGUI = false;
    public static boolean isRenderingParticleGUI2 = false;
    private RenderTarget savedMainRenderTarget;

    //used on client with gui open side
    //used to capture raw copy of minecraft screen
    private MainTarget mainRenderTarget;

    //used to render a sized down and cropped version of the above raw copy
    private MainTarget mainRenderTargetScaledDown;
    //intermediate ping-pong target to avoid sampling and writing the same texture in one pass
    private MainTarget mainRenderTargetScaledDownIntermediate;

    public int width;
    public int height;
    public static int defaultWidthScaledDown = 256;
    public static int defaultHeightScaledDown = 256;
    public static int bytesPerPixel = 4;
    public int widthScaledDown = defaultWidthScaledDown;
    public int heightScaledDown = defaultHeightScaledDown;
    public boolean needsInit = true;

    // Set temporarily during captureScreenAfterGuiRender to override the input source for blur passes
    private GpuTextureView captureSourceOverrideView = null;
    private MappableRingBuffer blurParamsUniformRing;
    private MappableRingBuffer samplerInfoUniformRing;

    private static ScreenParticleRenderer instance;

    public static ScreenParticleRenderer getInstance() {
        if (instance == null) {
            instance = new ScreenParticleRenderer();
        }
        return instance;
    }

    public void checkSetup() {
        if (needsInit) {
            needsInit = false;
            setup();
        }
    }

    public void setup() {
        closeUniformRings();
        Minecraft mc = Minecraft.getInstance();
        width = mc.getWindow().getWidth();
        height = mc.getWindow().getHeight();
        mainRenderTarget = new MainTarget(width, height);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(mainRenderTarget.getColorTexture(), 0);

        if (ConfigServerControlledSyncedToClient.dynamicGuiShowClientsEntireScreen) {
            widthScaledDown = width;
            heightScaledDown = height;
        } else {
            widthScaledDown = defaultWidthScaledDown;
            heightScaledDown = defaultHeightScaledDown;
        }

        mainRenderTargetScaledDown = new MainTarget(widthScaledDown, heightScaledDown);
        encoder.clearColorTexture(mainRenderTargetScaledDown.getColorTexture(), 0);
        mainRenderTargetScaledDownIntermediate = new MainTarget(widthScaledDown, heightScaledDown);
        encoder.clearColorTexture(mainRenderTargetScaledDownIntermediate.getColorTexture(), 0);
    }

    public synchronized void resize(int width, int height) {
        this.width = width;
        this.height = height;
        checkSetup();
        mainRenderTarget.resize(width, height);
        resizeScaledDown(width, height);
    }

    public void resizeScaledDown(int width, int height) {
        checkSetup();
        int widthToUse = defaultWidthScaledDown;
        int heightToUse = defaultHeightScaledDown;
        if (ConfigServerControlledSyncedToClient.dynamicGuiShowClientsEntireScreen) {
            widthToUse = width;
            heightToUse = height;
        }

        widthScaledDown = widthToUse;
        heightScaledDown = heightToUse;

        CULog.dbg("resizeScaledDown to " + widthToUse + " " + heightToUse);

        if (mainRenderTargetScaledDown.width != widthToUse || mainRenderTargetScaledDown.height != heightToUse) {
            mainRenderTargetScaledDown.resize(widthToUse, heightToUse);
        }
        if (mainRenderTargetScaledDownIntermediate.width != widthToUse || mainRenderTargetScaledDownIntermediate.height != heightToUse) {
            mainRenderTargetScaledDownIntermediate.resize(widthToUse, heightToUse);
        }
    }

    public void bind() {
        MinecraftAccessor mcAccessor = (MinecraftAccessor) Minecraft.getInstance();
        savedMainRenderTarget = mcAccessor.watut$getMainRenderTarget();
        mcAccessor.watut$setMainRenderTarget(mainRenderTarget);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(mainRenderTarget.getColorTexture(), 0);
        // Clear depth to 1.0 so GUI fragments pass the depth test (LEQUAL)
        if (mainRenderTarget.getDepthTexture() != null) {
            encoder.clearDepthTexture(mainRenderTarget.getDepthTexture(), 1.0);
        }
    }

    public void unbind() {
        if (savedMainRenderTarget != null) {
            ((MinecraftAccessor) Minecraft.getInstance()).watut$setMainRenderTarget(savedMainRenderTarget);
            savedMainRenderTarget = null;
        }
    }

    public void bindScaledDown() {
        // No-op in 1.21.5 - scaled down target is written to via RenderPass
    }

    public void unbindScaledDown() {
        // No-op in 1.21.5 - scaled down target is written to via RenderPass
    }

    public MainTarget getMainRenderTarget() {
        return mainRenderTarget;
    }

    public MainTarget getMainRenderTargetScaledDown() {
        return mainRenderTargetScaledDown;
    }

    public void setMainRenderTarget(MainTarget mainRenderTarget) {
        this.mainRenderTarget = mainRenderTarget;
    }

    public void setCaptureSourceOverride(GpuTextureView sourceView) {
        this.captureSourceOverrideView = sourceView;
    }

    public void clearCaptureSourceOverride() {
        this.captureSourceOverrideView = null;
    }

    private GpuTextureView resolveCaptureInputView() {
        if (captureSourceOverrideView != null) {
            return captureSourceOverrideView;
        }
        if (mainRenderTarget == null) {
            return null;
        }
        return mainRenderTarget.getColorTextureView();
    }

    private void ensureUniformRings() {
        if (blurParamsUniformRing == null) {
            blurParamsUniformRing = new MappableRingBuffer(
                    () -> "watut BlurParams",
                    UNIFORM_RING_BUFFER_USAGE,
                    BLUR_PARAMS_UBO_SIZE
            );
        }
        if (samplerInfoUniformRing == null) {
            samplerInfoUniformRing = new MappableRingBuffer(
                    () -> "watut SamplerInfo",
                    UNIFORM_RING_BUFFER_USAGE,
                    SAMPLER_INFO_UBO_SIZE
            );
        }
    }

    private void closeUniformRings() {
        if (blurParamsUniformRing != null) {
            blurParamsUniformRing.close();
            blurParamsUniformRing = null;
        }
        if (samplerInfoUniformRing != null) {
            samplerInfoUniformRing.close();
            samplerInfoUniformRing = null;
        }
    }

    private GpuBuffer writeBlurParamsUniformBuffer(CommandEncoder encoder, float radius, float blurLevel, float minU, float minV, float maxU, float maxV) {
        ensureUniformRings();
        GpuBuffer buffer = blurParamsUniformRing.currentBuffer();
        try (GpuBuffer.MappedView mappedView = encoder.mapBuffer(buffer, false, true)) {
            Std140Builder.intoBuffer(mappedView.data())
                    .putVec2((float) widthScaledDown, (float) heightScaledDown)
                    .putFloat(radius)
                    .putFloat(blurLevel)
                    .putVec2(minU, minV)
                    .putVec2(maxU, maxV);
        }
        return buffer;
    }

    private GpuBuffer writeSamplerInfoUniformBuffer(CommandEncoder encoder, GpuTextureView inputView) {
        ensureUniformRings();
        GpuBuffer buffer = samplerInfoUniformRing.currentBuffer();
        try (GpuBuffer.MappedView mappedView = encoder.mapBuffer(buffer, false, true)) {
            Std140Builder.intoBuffer(mappedView.data())
                    .putVec2((float) widthScaledDown, (float) heightScaledDown)
                    .putVec2((float) inputView.getWidth(0), (float) inputView.getHeight(0));
        }
        return buffer;
    }

    private void advanceUniformRings() {
        if (blurParamsUniformRing != null) {
            blurParamsUniformRing.rotate();
        }
        if (samplerInfoUniformRing != null) {
            samplerInfoUniformRing.rotate();
        }
    }

    private static GpuSampler getGuiBlurSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public void innerBlitCustomShader(int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float minU, float maxU, float minV, float maxV) {
        GpuTextureView inputView = resolveCaptureInputView();
        GpuTextureView outputView = mainRenderTargetScaledDown.getColorTextureView();
        if (inputView == null || outputView == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBuffer blurParamsBuffer = writeBlurParamsUniformBuffer(encoder, 0f, 0f, minU, minV, maxU, maxV);
        GpuBuffer samplerInfoBuffer = writeSamplerInfoUniformBuffer(encoder, inputView);

        try {
            try (RenderPass renderPass = encoder.createRenderPass(() -> "WATUT blit", outputView, OptionalInt.of(0))) {
                renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlur.getPipeline());
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("BlurParams", blurParamsBuffer);
                renderPass.setUniform("SamplerInfo", samplerInfoBuffer);
                // 1.21.11+: GlRenderPass.bindTexture(..., null) removes the binding (unbinds the texture).
                // Always provide an explicit sampler here or the blur/blit pass will sample stale data.
                renderPass.bindTexture("InSampler", inputView, getGuiBlurSampler());
                renderPass.draw(0, 3);
            }
        } finally {
            advanceUniformRings();
        }
    }

    public void innerBlitCustomShaderHorizontal(int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float minU, float maxU, float minV, float maxV) {
        GpuTextureView inputView = resolveCaptureInputView();
        GpuTextureView outputView = mainRenderTargetScaledDownIntermediate.getColorTextureView();
        if (inputView == null || outputView == null) return;

        float blurLevel = (float)(RenderHelper.xaeroGuiMapCaptureActive ? 0 : ConfigServerControlledSyncedToClient.dynamicGuiBlurLevel);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBuffer blurParamsBuffer = writeBlurParamsUniformBuffer(encoder, 0f, blurLevel, minU, minV, maxU, maxV);
        GpuBuffer samplerInfoBuffer = writeSamplerInfoUniformBuffer(encoder, inputView);

        try {
            try (RenderPass renderPass = encoder.createRenderPass(() -> "WATUT blur horizontal", outputView, OptionalInt.of(0))) {
                renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlurHorizontal.getPipeline());
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("BlurParams", blurParamsBuffer);
                renderPass.setUniform("SamplerInfo", samplerInfoBuffer);
                renderPass.bindTexture("InSampler", inputView, getGuiBlurSampler());
                renderPass.draw(0, 3);
            }
        } finally {
            advanceUniformRings();
        }
    }

    public void innerBlitCustomShaderVertical(int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float p_283247_, float p_282598_, float p_282883_, float p_283017_) {
        GpuTextureView inputView = mainRenderTargetScaledDownIntermediate.getColorTextureView();
        GpuTextureView outputView = mainRenderTargetScaledDown.getColorTextureView();
        if (inputView == null || outputView == null) return;

        float radius = (float) ConfigServerControlledSyncedToClient.dynamicGuiSizeRadiusInPixelsToShow;
        float blurLevel = (float)(RenderHelper.xaeroGuiMapCaptureActive ? 0 : ConfigServerControlledSyncedToClient.dynamicGuiBlurLevel);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBuffer blurParamsBuffer = writeBlurParamsUniformBuffer(encoder, radius, blurLevel, 0f, 0f, 1f, 1f);
        GpuBuffer samplerInfoBuffer = writeSamplerInfoUniformBuffer(encoder, inputView);

        try {
            try (RenderPass renderPass = encoder.createRenderPass(() -> "WATUT blur vertical", outputView, OptionalInt.empty())) {
                renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlurVertical.getPipeline());
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("BlurParams", blurParamsBuffer);
                renderPass.setUniform("SamplerInfo", samplerInfoBuffer);
                renderPass.bindTexture("InSampler", inputView, getGuiBlurSampler());
                renderPass.draw(0, 3);
            }
        } finally {
            advanceUniformRings();
        }
    }

    //copy of GuiGraphics.innerBlit with PoseStack added - now uses blit pipeline
    public void innerBlit(Identifier atlasLocation, int x1, int x2, int y1, int y2, int blitOffset, float minU, float maxU, float minV, float maxV) {
        //TODO: cursor rendering needs rework for 1.21.5 - using blit pipeline won't support custom UVs
        // For now this is a no-op, cursor won't render in capture
    }
}
