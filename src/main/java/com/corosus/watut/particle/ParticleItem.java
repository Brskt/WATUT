package com.corosus.watut.particle;

import com.corosus.coroutil.util.CULog;
import com.mojang.blaze3d.vertex.PoseStack;
import com.corosus.watut.client.ParticleRenderTypeOld;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.lang.reflect.Method;

public class ParticleItem extends ParticleRotating {

    public static HashSet<String> itemBlacklist = new HashSet<>();
    private static final ItemFeatureRenderer ITEM_FEATURE_RENDERER = new ItemFeatureRenderer();
    private static final ModelFeatureRenderer MODEL_FEATURE_RENDERER = new ModelFeatureRenderer();
    private static final ModelPartFeatureRenderer MODEL_PART_FEATURE_RENDERER = new ModelPartFeatureRenderer();
    private static final CustomFeatureRenderer CUSTOM_FEATURE_RENDERER = new CustomFeatureRenderer();
    private static boolean featureRendererMethodsInitialized;
    private static boolean featureRendererHasSplitPassMethods;
    private static Method itemFeatureRenderMethod;
    private static Method itemFeatureRenderSolidMethod;
    private static Method itemFeatureRenderTranslucentMethod;
    private static Method modelFeatureRenderMethod;
    private static Method modelFeatureRenderSolidMethod;
    private static Method modelFeatureRenderTranslucentMethod;
    private static Method modelPartFeatureRenderMethod;
    private static Method modelPartFeatureRenderSolidMethod;
    private static Method modelPartFeatureRenderTranslucentMethod;
    private static Method customFeatureRenderMethod;
    private static Method customFeatureRenderSolidMethod;
    private static Method customFeatureRenderTranslucentMethod;
    private static boolean particleMaterialMethodsInitialized;
    private static Method pickParticleMaterialMethod;
    private static Method pickParticleIconMethod;
    private static Method particleMaterialSpriteMethod;

    public ItemStackRenderState scratchItemStackRenderState;
    public ItemStack itemStack;
    private final RenderBuffers renderBuffers;
    private final SubmitNodeStorage submitNodeStorage = new SubmitNodeStorage();
    public float xFrom;
    public float yFrom;
    public float zFrom;
    public float xTo;
    public float yTo;
    public float zTo;

    public ParticleItem(ClientLevel pLevel, float brightness, ItemStack itemStack, RenderBuffers renderBuffers, float xFrom, float yFrom, float zFrom, float xTo, float yTo, float zTo) {
        super(pLevel, xFrom, yFrom, zFrom);
        this.lifetime = Integer.MAX_VALUE;
        this.gravity = 0.0F;
        this.setSize(0.2F, 0.2F);
        this.quadSize = 1F;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.xFrom = xFrom;
        this.yFrom = yFrom;
        this.zFrom = zFrom;
        this.xTo = xTo;
        this.yTo = yTo;
        this.zTo = zTo;
        this.setColor(this.getColorRed() * brightness, this.getColorGreen() * brightness, this.getColorBlue() * brightness);

        scratchItemStackRenderState = new ItemStackRenderState();
        Minecraft.getInstance()
                .getItemModelResolver()
                .updateForTopItem(this.scratchItemStackRenderState, itemStack, ItemDisplayContext.GROUND, level, null, 0);
        this.itemStack = itemStack;
        this.rotationYaw = pLevel.getRandom().nextFloat() * 360;
        this.renderBuffers = renderBuffers;
    }

    @Override
    public ParticleRenderTypeOld getRenderTypeOld() {
        return TERRAIN_SHEET_TRANSLUCENT_NO_FACE_CULL;
    }

    @Override
    protected Layer getLayer() {
        Layer layer = getLayerByStaticField("TRANSLUCENT_TERRAIN");
        if (layer != null) return layer;
        layer = getLayerByStaticField("TERRAIN");
        if (layer != null) return layer;
        return Layer.TRANSLUCENT;
    }

    public void setSize(float pWidth, float pHeight) {
        super.setSize(pWidth, pHeight);
    }

    public void tick() {
        super.tick();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= 6) {
            this.remove();
        } else {
            this.move(this.xd, this.yd, this.zd);
        }
    }

    public void render(VertexConsumer pBuffer, Camera pRenderInfo, float pPartialTicks) {
        if (itemBlacklist.contains(this.itemStack.getItem().toString())) return;

        Vec3 vec3 = pRenderInfo.position();
        float f = (float)(Mth.lerp(pPartialTicks, this.xo, this.x));
        float f1 = (float)(Mth.lerp(pPartialTicks, this.yo, this.y));
        float f2 = (float)(Mth.lerp(pPartialTicks, this.zo, this.z));

        float lerp = ((float)this.age + pPartialTicks) / 3.0F;
        double x = Mth.lerp(lerp, f, xTo);
        double y = Mth.lerp(lerp, f1, yTo);
        double z = Mth.lerp(lerp, f2, zTo);

        if (this.age >= 3) {
            x = xTo;
            y = yTo;
            z = zTo;
        }

        x = x - vec3.x();
        y = y - vec3.y();
        z = z - vec3.z();

        int light = this.getPackedLightCompat(pPartialTicks);

        Quaternionf quaternion = new Quaternionf(0, 0, 0, 1);
        quaternion.mul(Axis.YP.rotationDegrees(this.rotationYaw));

        PoseStack pose = new PoseStack();
        pose.pushPose();
        pose.translate(x, y - 0.15, z);
        pose.scale(quadSize, quadSize, quadSize);
        pose.rotateAround(quaternion, 0, 1, 0);

        try {
            submitNodeStorage.clear();
            this.scratchItemStackRenderState.submit(pose, submitNodeStorage, light, OverlayTexture.NO_OVERLAY, 0);

            for (SubmitNodeCollection collection : submitNodeStorage.getSubmitsPerOrder().values()) {
                renderCollection(collection);
            }

            renderBuffers.crumblingBufferSource().endBatch();
            renderBuffers.bufferSource().endBatch();
            renderBuffers.outlineBufferSource().endOutlineBatch();
        } catch (Exception exception) {
            CULog.err("ERROR, exception trying to render item: " + this.itemStack.getItem().toString() + " - adding to ParticleItem render blacklist for this minecraft session");
            itemBlacklist.add(this.itemStack.getItem().toString());
            TextureAtlasSprite icon = pickParticleSpriteCompat();
            if (icon != null) {
                this.setSprite(icon);
            }
            super.render(pBuffer, pRenderInfo, pPartialTicks);
            exception.printStackTrace();
        } finally {
            submitNodeStorage.endFrame();
        }
    }

    private void renderCollection(SubmitNodeCollection collection) {
        initFeatureRendererMethods();
        try {
            if (featureRendererHasSplitPassMethods) {
                itemFeatureRenderSolidMethod.invoke(ITEM_FEATURE_RENDERER, collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource());
                modelFeatureRenderSolidMethod.invoke(MODEL_FEATURE_RENDERER, collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource());
                modelPartFeatureRenderSolidMethod.invoke(MODEL_PART_FEATURE_RENDERER, collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource());
                customFeatureRenderSolidMethod.invoke(CUSTOM_FEATURE_RENDERER, collection, renderBuffers.bufferSource());

                itemFeatureRenderTranslucentMethod.invoke(ITEM_FEATURE_RENDERER, collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource());
                modelFeatureRenderTranslucentMethod.invoke(MODEL_FEATURE_RENDERER, collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource());
                modelPartFeatureRenderTranslucentMethod.invoke(MODEL_PART_FEATURE_RENDERER, collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource());
                customFeatureRenderTranslucentMethod.invoke(CUSTOM_FEATURE_RENDERER, collection, renderBuffers.bufferSource());
            } else {
                itemFeatureRenderMethod.invoke(ITEM_FEATURE_RENDERER, collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource());
                modelFeatureRenderMethod.invoke(MODEL_FEATURE_RENDERER, collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource());
                modelPartFeatureRenderMethod.invoke(MODEL_PART_FEATURE_RENDERER, collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource());
                customFeatureRenderMethod.invoke(CUSTOM_FEATURE_RENDERER, collection, renderBuffers.bufferSource());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to replay ItemStackRenderState submit collection", e);
        }
    }

    private TextureAtlasSprite pickParticleSpriteCompat() {
        initParticleMaterialMethods();
        try {
            if (pickParticleMaterialMethod != null) {
                Object material = pickParticleMaterialMethod.invoke(this.scratchItemStackRenderState, this.random);
                if (material != null) {
                    if (particleMaterialSpriteMethod == null) {
                        particleMaterialSpriteMethod = material.getClass().getMethod("sprite");
                    }
                    Object sprite = particleMaterialSpriteMethod.invoke(material);
                    if (sprite instanceof TextureAtlasSprite textureAtlasSprite) {
                        return textureAtlasSprite;
                    }
                }
            }
            if (pickParticleIconMethod != null) {
                Object sprite = pickParticleIconMethod.invoke(this.scratchItemStackRenderState, this.random);
                if (sprite instanceof TextureAtlasSprite textureAtlasSprite) {
                    return textureAtlasSprite;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void initParticleMaterialMethods() {
        if (particleMaterialMethodsInitialized) return;
        particleMaterialMethodsInitialized = true;
        try {
            pickParticleMaterialMethod = ItemStackRenderState.class.getMethod("pickParticleMaterial", net.minecraft.util.RandomSource.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            pickParticleIconMethod = ItemStackRenderState.class.getMethod("pickParticleIcon", net.minecraft.util.RandomSource.class);
        } catch (NoSuchMethodException ignored) {
        }
    }

    private static void initFeatureRendererMethods() {
        if (featureRendererMethodsInitialized) return;
        featureRendererMethodsInitialized = true;
        try {
            itemFeatureRenderSolidMethod = ItemFeatureRenderer.class.getMethod("renderSolid", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, OutlineBufferSource.class);
            itemFeatureRenderTranslucentMethod = ItemFeatureRenderer.class.getMethod("renderTranslucent", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, OutlineBufferSource.class);
            modelFeatureRenderSolidMethod = ModelFeatureRenderer.class.getMethod("renderSolid", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, OutlineBufferSource.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class);
            modelFeatureRenderTranslucentMethod = ModelFeatureRenderer.class.getMethod("renderTranslucent", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, OutlineBufferSource.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class);
            modelPartFeatureRenderSolidMethod = ModelPartFeatureRenderer.class.getMethod("renderSolid", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, OutlineBufferSource.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class);
            modelPartFeatureRenderTranslucentMethod = ModelPartFeatureRenderer.class.getMethod("renderTranslucent", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, OutlineBufferSource.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class);
            customFeatureRenderSolidMethod = CustomFeatureRenderer.class.getMethod("renderSolid", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class);
            customFeatureRenderTranslucentMethod = CustomFeatureRenderer.class.getMethod("renderTranslucent", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class);
            featureRendererHasSplitPassMethods = true;
            return;
        } catch (NoSuchMethodException ignored) {
            featureRendererHasSplitPassMethods = false;
        }

        try {
            itemFeatureRenderMethod = ItemFeatureRenderer.class.getMethod("render", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, OutlineBufferSource.class);
            modelFeatureRenderMethod = ModelFeatureRenderer.class.getMethod("render", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, OutlineBufferSource.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class);
            modelPartFeatureRenderMethod = ModelPartFeatureRenderer.class.getMethod("render", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, OutlineBufferSource.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class);
            customFeatureRenderMethod = CustomFeatureRenderer.class.getMethod("render", SubmitNodeCollection.class, net.minecraft.client.renderer.MultiBufferSource.BufferSource.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unsupported item feature renderer API", e);
        }
    }

    private static Layer getLayerByStaticField(String fieldName) {
        try {
            Object value = Layer.class.getField(fieldName).get(null);
            if (value instanceof Layer layer) {
                return layer;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

}
