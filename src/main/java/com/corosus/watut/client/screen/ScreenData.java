package com.corosus.watut.client.screen;

import com.corosus.watut.WatutMod;
import com.corosus.watut.client.ParticleRenderTypeOld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenData {
    private static final RenderPipeline TRANSLUCENT_PARTICLE_NO_CULL = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
                    .withLocation("pipeline/watut_translucent_particle_no_cull")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build());

    private volatile ByteBuffer texturePixelData = null;
    private volatile ByteBuffer decompressionBuffer = null;
    private final AtomicBoolean isBufferReady = new AtomicBoolean(false);
    private byte[] texturePixelDataPartial = null;

    //private byte[] texturePixelDataDiff = null;

    private long gameTicksSinceFirstPacket = 0;
    private int lastIndexReceived = 0;
    private int currentReceiveCaptureSequence = -1;
    private int lastAppliedCaptureSequence = -1;
    private long gameTicksSinceLastScreenSend = 0;
    private long gameTicksSinceLastScreenReceiveAndRender = 0;
    private int texturePixelDataCaptureSequence = 0;

    private ParticleRenderTypeOld particleRenderType;

    private boolean needsNewRenderFromPixelData = false;

    //used for communicating from outside screen render hook to inside it
    private boolean needsNewRenderToPixelData = false;

    private DynamicTexture image = null;
    private int width = ScreenParticleRenderer.defaultWidthScaledDown;
    private int height = ScreenParticleRenderer.defaultHeightScaledDown;

    //since gui states are kinda old system and require specifically adding support for a screen, we use this instead to track true differences now
    private Screen lastScreen;

    private Identifier textureLocation = null;
    private RenderType cachedRenderType = null;

    public static boolean testing = false;

    public void initClient() {
        this.particleRenderType = new ParticleRenderTypeOld() {
            @Override
            public RenderType getRenderType() {
                if (cachedRenderType != null) {
                    return cachedRenderType;
                }
                // Fallback when texture not yet registered
                return RenderTypes.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
            }

            public String toString() {
                return "DYNAMIC_TEXTURE";
            }
        };

        //WatutMod.instance().addParticleRenderType(particleRenderType);

    }

    public void registerTexture() {
        if (image != null) {
            if (textureLocation == null) {
                textureLocation = Identifier.fromNamespaceAndPath(WatutMod.MODID, "dynamic/screen_" + System.identityHashCode(this));
            }
            Minecraft.getInstance().getTextureManager().register(textureLocation, image);
            cachedRenderType = RenderType.create(
                    "watut_translucent_particle_no_cull_dynamic_" + System.identityHashCode(this),
                    RenderSetup.builder(TRANSLUCENT_PARTICLE_NO_CULL)
                            .bufferSize(1536)
                            .withTexture("Sampler0", textureLocation)
                            .setOutputTarget(OutputTarget.MAIN_TARGET)
                            .useLightmap()
                            .createRenderSetup()
            );
        }
    }

    public void invalidateCachedRenderType() {
        cachedRenderType = null;
    }

    public ByteBuffer getTexturePixelData() {
        return texturePixelData;
    }

    public void freeTexturePixelData() {
        if (texturePixelData != null) {
            MemoryUtil.memFree(texturePixelData);
        }
    }

    public void setTexturePixelData(ByteBuffer texturePixelData) {
        this.texturePixelData = texturePixelData;
    }

    public ParticleRenderTypeOld getParticleRenderType() {
        return particleRenderType;
    }

    public void setParticleRenderType(ParticleRenderTypeOld particleRenderType) {
        this.particleRenderType = particleRenderType;
    }

    public synchronized boolean needsNewRenderFromPixelData() {
        return needsNewRenderFromPixelData;
    }

    public synchronized void markNeedsNewRenderFromPixelData(boolean needsNewRender) {
        this.needsNewRenderFromPixelData = needsNewRender;
    }

    public boolean isNeedsNewRenderToPixelData() {
        return needsNewRenderToPixelData;
    }

    public void setNeedsNewRenderToPixelData(boolean needsNewRenderToPixelData) {
        this.needsNewRenderToPixelData = needsNewRenderToPixelData;
    }

    public byte[] getTexturePixelDataPartial() {
        return texturePixelDataPartial;
    }

    public void setTexturePixelDataPartial(byte[] texturePixelDataPartial) {
        this.texturePixelDataPartial = texturePixelDataPartial;
    }

    public long getGameTicksSinceFirstPacket() {
        return gameTicksSinceFirstPacket;
    }

    public void setGameTicksSinceFirstPacket(long gameTicksSinceFirstPacket) {
        this.gameTicksSinceFirstPacket = gameTicksSinceFirstPacket;
    }

    public int getLastIndexReceived() {
        return lastIndexReceived;
    }

    public void setLastIndexReceived(int lastIndexReceived) {
        this.lastIndexReceived = lastIndexReceived;
    }

    public long getGameTicksSinceLastScreenSend() {
        return gameTicksSinceLastScreenSend;
    }

    public void setGameTicksSinceLastScreenSend(long gameTicksSinceLastScreenSend) {
        this.gameTicksSinceLastScreenSend = gameTicksSinceLastScreenSend;
    }

    public long getGameTicksSinceLastScreenReceiveAndRender() {
        return gameTicksSinceLastScreenReceiveAndRender;
    }

    public void setGameTicksSinceLastScreenReceiveAndRender(long gameTicksSinceLastScreenReceiveAndRender) {
        this.gameTicksSinceLastScreenReceiveAndRender = gameTicksSinceLastScreenReceiveAndRender;
    }

    public int getCurrentReceiveCaptureSequence() {
        return currentReceiveCaptureSequence;
    }

    public void setCurrentReceiveCaptureSequence(int currentReceiveCaptureSequence) {
        this.currentReceiveCaptureSequence = currentReceiveCaptureSequence;
    }

    public int getLastAppliedCaptureSequence() {
        return lastAppliedCaptureSequence;
    }

    public void setLastAppliedCaptureSequence(int lastAppliedCaptureSequence) {
        this.lastAppliedCaptureSequence = lastAppliedCaptureSequence;
    }

    public int getTexturePixelDataCaptureSequence() {
        return texturePixelDataCaptureSequence;
    }

    public void setTexturePixelDataCaptureSequence(int texturePixelDataCaptureSequence) {
        this.texturePixelDataCaptureSequence = texturePixelDataCaptureSequence;
    }

    public AtomicBoolean getIsBufferReady() {
        return isBufferReady;
    }

    public ByteBuffer getDecompressionBuffer() {
        return decompressionBuffer;
    }

    public void setDecompressionBuffer(ByteBuffer decompressionBuffer) {
        this.decompressionBuffer = decompressionBuffer;
    }

    public DynamicTexture getImage() {
        return image;
    }

    public void setImage(DynamicTexture image) {
        this.image = image;
    }

    public void closeImage() {
        if (this.image != null) {
            this.image.close();
        }
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public Screen getLastScreen() {
        return lastScreen;
    }

    public void setLastScreen(Screen lastScreen) {
        this.lastScreen = lastScreen;
    }

    /*public byte[] getTexturePixelDataDiff() {
        return texturePixelDataDiff;
    }

    public void setTexturePixelDataDiff(byte[] texturePixelDataDiff) {
        this.texturePixelDataDiff = texturePixelDataDiff;
    }*/
}
