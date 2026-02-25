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
import net.minecraft.client.renderer.entity.ItemRenderer;
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

public class ParticleItem extends ParticleRotating {

    public static HashSet<String> itemBlacklist = new HashSet<>();
    private static final ModelFeatureRenderer MODEL_FEATURE_RENDERER = new ModelFeatureRenderer();
    private static final ModelPartFeatureRenderer MODEL_PART_FEATURE_RENDERER = new ModelPartFeatureRenderer();
    private static final CustomFeatureRenderer CUSTOM_FEATURE_RENDERER = new CustomFeatureRenderer();

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
        return Layer.TERRAIN;
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

        int light = this.getLightColor(pPartialTicks);

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
            TextureAtlasSprite icon = this.scratchItemStackRenderState.pickParticleIcon(this.random);
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
        for (SubmitNodeStorage.ItemSubmit itemSubmit : collection.getItemSubmits()) {
            PoseStack itemPose = new PoseStack();
            itemPose.last().set(itemSubmit.pose());
            ItemRenderer.renderItem(
                    itemSubmit.displayContext(),
                    itemPose,
                    renderBuffers.bufferSource(),
                    itemSubmit.lightCoords(),
                    itemSubmit.overlayCoords(),
                    itemSubmit.tintLayers(),
                    itemSubmit.quads(),
                    itemSubmit.renderType(),
                    itemSubmit.foilType()
            );

            if (itemSubmit.outlineColor() != 0) {
                OutlineBufferSource outlineBuffers = renderBuffers.outlineBufferSource();
                outlineBuffers.setColor(itemSubmit.outlineColor());
                ItemRenderer.renderItem(
                        itemSubmit.displayContext(),
                        itemPose,
                        outlineBuffers,
                        itemSubmit.lightCoords(),
                        itemSubmit.overlayCoords(),
                        itemSubmit.tintLayers(),
                        itemSubmit.quads(),
                        itemSubmit.renderType(),
                        ItemStackRenderState.FoilType.NONE
                );
            }
        }

        MODEL_FEATURE_RENDERER.render(collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource());
        MODEL_PART_FEATURE_RENDERER.render(collection, renderBuffers.bufferSource(), renderBuffers.outlineBufferSource(), renderBuffers.crumblingBufferSource());
        CUSTOM_FEATURE_RENDERER.render(collection, renderBuffers.bufferSource());
    }

}
