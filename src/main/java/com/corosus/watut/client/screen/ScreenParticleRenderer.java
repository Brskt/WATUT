package com.corosus.watut.client.screen;

import com.corosus.coroutil.util.CULog;
import com.corosus.watut.PlayerStatusManagerClient;
import com.corosus.watut.config.ConfigServerControlledSyncedToClient;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.OptionalInt;

public class ScreenParticleRenderer {

    public static boolean isRenderingParticleGUI = false;
    public static boolean isRenderingParticleGUI2 = false;

    //used on client with gui open side
    //used to capture raw copy of minecraft screen
    private MainTarget mainRenderTarget;

    //used to render a sized down and cropped version of the above raw copy
    private MainTarget mainRenderTargetScaledDown;

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
    }

    private RenderTarget savedMainRenderTarget;

    public void bind() {
        Minecraft mc = Minecraft.getInstance();
        savedMainRenderTarget = mc.mainRenderTarget;
        mc.mainRenderTarget = mainRenderTarget;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(mainRenderTarget.getColorTexture(), 0);
        // Clear depth to 1.0 so GUI fragments pass the depth test (LEQUAL)
        if (mainRenderTarget.getDepthTexture() != null) {
            encoder.clearDepthTexture(mainRenderTarget.getDepthTexture(), 1.0);
        }
    }

    public void unbind() {
        Minecraft mc = Minecraft.getInstance();
        if (savedMainRenderTarget != null) {
            mc.mainRenderTarget = savedMainRenderTarget;
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

    public void innerBlitCustomShader(int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float minU, float maxU, float minV, float maxV) {
        GpuTextureView inputView = resolveCaptureInputView();
        GpuTextureView outputView = mainRenderTargetScaledDown.getColorTextureView();
        if (inputView == null || outputView == null) return;

        RenderSystem.AutoStorageIndexBuffer seqBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = seqBuf.getBuffer(6);
        GpuBuffer quadBuffer = RenderSystem.getQuadVertexBuffer();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            GpuBuffer projBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut projection", 128,
                Std140Builder.onStack(stack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE)
                    .putMat4f(new Matrix4f().setOrtho(0.0f, widthScaledDown, 0.0f, heightScaledDown, 0.1f, 1000.0f))
                    .get()
            );
            GpuBuffer blurParamsBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut BlurParams", 128,
                Std140Builder.onStack(stack, 32)
                    .putVec2((float) widthScaledDown, (float) heightScaledDown)
                    .putFloat(0f)
                    .putFloat(0f)
                    .putVec2(minU, minV)
                    .putVec2(maxU, maxV)
                    .get()
            );
            GpuBuffer samplerInfoBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut SamplerInfo", 128,
                Std140Builder.onStack(stack, 16)
                    .putVec2((float) widthScaledDown, (float) heightScaledDown)
                    .putVec2((float) inputView.getWidth(0), (float) inputView.getHeight(0))
                    .get()
            );

            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projBuffer.slice(), ProjectionType.ORTHOGRAPHIC);
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "WATUT blit", outputView, OptionalInt.of(0))) {
                renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlur.getPipeline());
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("BlurParams", blurParamsBuffer);
                renderPass.setUniform("SamplerInfo", samplerInfoBuffer);
                renderPass.bindSampler("InSampler", inputView);
                renderPass.setVertexBuffer(0, quadBuffer);
                renderPass.setIndexBuffer(indexBuffer, seqBuf.type());
                renderPass.drawIndexed(0, 0, 6, 1);
            }
            RenderSystem.restoreProjectionMatrix();

            projBuffer.close();
            blurParamsBuffer.close();
            samplerInfoBuffer.close();
        }
    }

    public void innerBlitCustomShader2(int textureID, int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float minU, float maxU, float minV, float maxV) {
        // Legacy entrypoint kept for compatibility; textureID is ignored in the 1.21.6 GPU API path.
        innerBlitCustomShader(p_281399_, p_283222_, p_283615_, p_283430_, p_281729_, minU, maxU, minV, maxV);
    }

    public void innerBlitCustomShaderHorizontal(int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float minU, float maxU, float minV, float maxV) {
        GpuTextureView inputView = resolveCaptureInputView();
        GpuTextureView outputView = mainRenderTargetScaledDown.getColorTextureView();
        if (inputView == null || outputView == null) return;

        RenderSystem.AutoStorageIndexBuffer seqBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = seqBuf.getBuffer(6);
        GpuBuffer quadBuffer = RenderSystem.getQuadVertexBuffer();

        float blurLevel = (float)(RenderHelper.xaeroWorldMapTextureID != -1 ? 0 : ConfigServerControlledSyncedToClient.dynamicGuiBlurLevel);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            GpuBuffer projBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut projection", 128,
                Std140Builder.onStack(stack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE)
                    .putMat4f(new Matrix4f().setOrtho(0.0f, widthScaledDown, 0.0f, heightScaledDown, 0.1f, 1000.0f))
                    .get()
            );
            GpuBuffer blurParamsBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut BlurParams", 128,
                Std140Builder.onStack(stack, 32)
                    .putVec2((float) widthScaledDown, (float) heightScaledDown)
                    .putFloat(0f)
                    .putFloat(blurLevel)
                    .putVec2(minU, minV)
                    .putVec2(maxU, maxV)
                    .get()
            );
            GpuBuffer samplerInfoBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut SamplerInfo", 128,
                Std140Builder.onStack(stack, 16)
                    .putVec2((float) widthScaledDown, (float) heightScaledDown)
                    .putVec2((float) inputView.getWidth(0), (float) inputView.getHeight(0))
                    .get()
            );

            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projBuffer.slice(), ProjectionType.ORTHOGRAPHIC);
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "WATUT blur horizontal", outputView, OptionalInt.of(0))) {
                renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlurHorizontal.getPipeline());
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("BlurParams", blurParamsBuffer);
                renderPass.setUniform("SamplerInfo", samplerInfoBuffer);
                renderPass.bindSampler("InSampler", inputView);
                renderPass.setVertexBuffer(0, quadBuffer);
                renderPass.setIndexBuffer(indexBuffer, seqBuf.type());
                renderPass.drawIndexed(0, 0, 6, 1);
            }
            RenderSystem.restoreProjectionMatrix();

            projBuffer.close();
            blurParamsBuffer.close();
            samplerInfoBuffer.close();
        }
    }

    public void innerBlitCustomShaderVertical(int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float p_283247_, float p_282598_, float p_282883_, float p_283017_) {
        GpuTextureView targetView = mainRenderTargetScaledDown.getColorTextureView();
        if (targetView == null) return;

        RenderSystem.AutoStorageIndexBuffer seqBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = seqBuf.getBuffer(6);
        GpuBuffer quadBuffer = RenderSystem.getQuadVertexBuffer();

        float radius = (float) ConfigServerControlledSyncedToClient.dynamicGuiSizeRadiusInPixelsToShow;
        float blurLevel = (float)(RenderHelper.xaeroWorldMapTextureID != -1 ? 0 : ConfigServerControlledSyncedToClient.dynamicGuiBlurLevel);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            GpuBuffer projBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut projection", 128,
                Std140Builder.onStack(stack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE)
                    .putMat4f(new Matrix4f().setOrtho(0.0f, widthScaledDown, 0.0f, heightScaledDown, 0.1f, 1000.0f))
                    .get()
            );
            GpuBuffer blurParamsBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut BlurParams", 128,
                Std140Builder.onStack(stack, 32)
                    .putVec2((float) widthScaledDown, (float) heightScaledDown)
                    .putFloat(radius)
                    .putFloat(blurLevel)
                    .putVec2(0f, 0f)
                    .putVec2(1f, 1f)
                    .get()
            );
            GpuBuffer samplerInfoBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut SamplerInfo", 128,
                Std140Builder.onStack(stack, 16)
                    .putVec2((float) widthScaledDown, (float) heightScaledDown)
                    .putVec2((float) targetView.getWidth(0), (float) targetView.getHeight(0))
                    .get()
            );

            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projBuffer.slice(), ProjectionType.ORTHOGRAPHIC);
            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "WATUT blur vertical", targetView, OptionalInt.empty())) {
                renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlurVertical.getPipeline());
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("BlurParams", blurParamsBuffer);
                renderPass.setUniform("SamplerInfo", samplerInfoBuffer);
                renderPass.bindSampler("InSampler", targetView);
                renderPass.setVertexBuffer(0, quadBuffer);
                renderPass.setIndexBuffer(indexBuffer, seqBuf.type());
                renderPass.drawIndexed(0, 0, 6, 1);
            }
            RenderSystem.restoreProjectionMatrix();

            projBuffer.close();
            blurParamsBuffer.close();
            samplerInfoBuffer.close();
        }
    }

    //copy of GuiGraphics.innerBlit with PoseStack added - now uses blit pipeline
    public void innerBlit(ResourceLocation atlasLocation, int x1, int x2, int y1, int y2, int blitOffset, float minU, float maxU, float minV, float maxV) {
        //TODO: cursor rendering needs rework for 1.21.5 - using blit pipeline won't support custom UVs
        // For now this is a no-op, cursor won't render in capture
    }
}
