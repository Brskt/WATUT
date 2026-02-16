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
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

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

    public void innerBlitCustomShader(int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float minU, float maxU, float minV, float maxV) {
        GpuTexture inputTexture = mainRenderTarget.getColorTexture();
        GpuTexture outputTexture = mainRenderTargetScaledDown.getColorTexture();
        if (inputTexture == null || outputTexture == null) return;

        RenderSystem.AutoStorageIndexBuffer seqBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = seqBuf.getBuffer(6);
        GpuBuffer quadBuffer = RenderSystem.getQuadVertexBuffer();

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, widthScaledDown, 0.0f, heightScaledDown, 0.1f, 1000.0f), ProjectionType.ORTHOGRAPHIC);
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(outputTexture, OptionalInt.of(0))) {
            renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlur.getPipeline());
            renderPass.bindSampler("InSampler", inputTexture);
            renderPass.setUniform("resolution", (float)widthScaledDown, (float)heightScaledDown);
            renderPass.setUniform("radius", 0f);
            renderPass.setUniform("InCropMin", minU, minV);
            renderPass.setUniform("InCropMax", maxU, maxV);
            renderPass.setUniform("OutSize", (float)widthScaledDown, (float)heightScaledDown);
            renderPass.setVertexBuffer(0, quadBuffer);
            renderPass.setIndexBuffer(indexBuffer, seqBuf.type());
            renderPass.drawIndexed(0, 6);
        }
        RenderSystem.restoreProjectionMatrix();
    }

    public void innerBlitCustomShader2(int textureID, int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float minU, float maxU, float minV, float maxV) {
        GpuTexture inputTexture = mainRenderTarget.getColorTexture();
        GpuTexture outputTexture = mainRenderTargetScaledDown.getColorTexture();
        if (inputTexture == null || outputTexture == null) return;

        RenderSystem.AutoStorageIndexBuffer seqBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = seqBuf.getBuffer(6);
        GpuBuffer quadBuffer = RenderSystem.getQuadVertexBuffer();

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, widthScaledDown, 0.0f, heightScaledDown, 0.1f, 1000.0f), ProjectionType.ORTHOGRAPHIC);
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(outputTexture, OptionalInt.of(0))) {
            renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlur.getPipeline());
            renderPass.bindSampler("InSampler", inputTexture);
            renderPass.setUniform("resolution", (float)widthScaledDown, (float)heightScaledDown);
            renderPass.setUniform("radius", 0f);
            renderPass.setUniform("InCropMin", minU, minV);
            renderPass.setUniform("InCropMax", maxU, maxV);
            renderPass.setUniform("OutSize", (float)widthScaledDown, (float)heightScaledDown);
            renderPass.setVertexBuffer(0, quadBuffer);
            renderPass.setIndexBuffer(indexBuffer, seqBuf.type());
            renderPass.drawIndexed(0, 6);
        }
        RenderSystem.restoreProjectionMatrix();
    }

    public void innerBlitCustomShaderHorizontal(int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float minU, float maxU, float minV, float maxV) {
        GpuTexture inputTexture;
        if (RenderHelper.xaeroWorldMapTextureID != -1) {
            //TODO: xaero integration needs rework for 1.21.5 GPU abstraction
            inputTexture = mainRenderTarget.getColorTexture();
        } else {
            inputTexture = mainRenderTarget.getColorTexture();
        }
        GpuTexture outputTexture = mainRenderTargetScaledDown.getColorTexture();
        if (inputTexture == null || outputTexture == null) return;

        RenderSystem.AutoStorageIndexBuffer seqBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = seqBuf.getBuffer(6);
        GpuBuffer quadBuffer = RenderSystem.getQuadVertexBuffer();

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, widthScaledDown, 0.0f, heightScaledDown, 0.1f, 1000.0f), ProjectionType.ORTHOGRAPHIC);
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(outputTexture, OptionalInt.of(0))) {
            renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlurHorizontal.getPipeline());
            renderPass.bindSampler("InSampler", inputTexture);
            renderPass.setUniform("blurLevel", (float)(RenderHelper.xaeroWorldMapTextureID != -1 ? 0 : ConfigServerControlledSyncedToClient.dynamicGuiBlurLevel));
            renderPass.setUniform("InCropMin", minU, minV);
            renderPass.setUniform("InCropMax", maxU, maxV);
            renderPass.setUniform("OutSize", (float)widthScaledDown, (float)heightScaledDown);
            renderPass.setVertexBuffer(0, quadBuffer);
            renderPass.setIndexBuffer(indexBuffer, seqBuf.type());
            renderPass.drawIndexed(0, 6);
        }
        RenderSystem.restoreProjectionMatrix();
    }

    public void innerBlitCustomShaderVertical(int p_281399_, int p_283222_, int p_283615_, int p_283430_, int p_281729_, float p_283247_, float p_282598_, float p_282883_, float p_283017_) {
        GpuTexture inputTexture = mainRenderTargetScaledDown.getColorTexture();
        GpuTexture outputTexture = mainRenderTargetScaledDown.getColorTexture();
        if (inputTexture == null || outputTexture == null) return;

        RenderSystem.AutoStorageIndexBuffer seqBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = seqBuf.getBuffer(6);
        GpuBuffer quadBuffer = RenderSystem.getQuadVertexBuffer();

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, widthScaledDown, 0.0f, heightScaledDown, 0.1f, 1000.0f), ProjectionType.ORTHOGRAPHIC);
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(outputTexture, OptionalInt.empty())) {
            renderPass.setPipeline(PlayerStatusManagerClient.positionTexBlurVertical.getPipeline());
            renderPass.bindSampler("InSampler", inputTexture);
            renderPass.setUniform("resolution", (float)widthScaledDown, (float)heightScaledDown);
            renderPass.setUniform("radius", (float) ConfigServerControlledSyncedToClient.dynamicGuiSizeRadiusInPixelsToShow);
            renderPass.setUniform("blurLevel", (float)(RenderHelper.xaeroWorldMapTextureID != -1 ? 0 : ConfigServerControlledSyncedToClient.dynamicGuiBlurLevel));
            renderPass.setUniform("OutSize", (float)widthScaledDown, (float)heightScaledDown);
            renderPass.setVertexBuffer(0, quadBuffer);
            renderPass.setIndexBuffer(indexBuffer, seqBuf.type());
            renderPass.drawIndexed(0, 6);
        }
        RenderSystem.restoreProjectionMatrix();
    }

    //copy of GuiGraphics.innerBlit with PoseStack added - now uses blit pipeline
    public void innerBlit(ResourceLocation atlasLocation, int x1, int x2, int y1, int y2, int blitOffset, float minU, float maxU, float minV, float maxV) {
        //TODO: cursor rendering needs rework for 1.21.5 - using blit pipeline won't support custom UVs
        // For now this is a no-op, cursor won't render in capture
    }
}
