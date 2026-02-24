package com.corosus.watut.mixin.client;

import com.corosus.watut.PlayerStatusManagerClient;
import com.mojang.blaze3d.framegraph.FramePass;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class ParticleEngineMixin {

    @Redirect(
            method = "addParticlesPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V"
            )
    )
    private void watut$wrapParticlesPassRenderer(FramePass pass, Runnable vanillaParticlesPassRenderer) {
        // Inject into the named particles pass setup and wrap its renderer runnable so WATUT renders
        // in the same GPU pass as vanilla particles, without depending on synthetic lambda method names.
        pass.executes(() -> {
            vanillaParticlesPassRenderer.run();
            watut$renderCustomParticles();
        });
    }

    private void watut$renderCustomParticles() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        PlayerStatusManagerClient.getParticleEngine().render(camera, partialTick, bufferSource);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        PlayerStatusManagerClient.getParticleEngine().tick();
    }
}
