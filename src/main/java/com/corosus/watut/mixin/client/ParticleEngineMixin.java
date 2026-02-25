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

    private static final boolean WATUT_HAS_ADD_PARTICLES_PASS = watut$hasLevelRendererMethod("addParticlesPass");

    private static boolean watut$hasLevelRendererMethod(String name) {
        for (java.lang.reflect.Method method : LevelRenderer.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "addParticlesPass", at = @At("HEAD"))
    private void watut$diagAddParticlesPassHead(CallbackInfo ci) {
    }

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

    @Inject(method = "addMainPass", at = @At("HEAD"), require = 0)
    private void watut$diagAddMainPassHead(CallbackInfo ci) {
    }

    @Redirect(
            method = "addMainPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V"
            ),
            require = 0
    )
    private void watut$wrapMainPassRenderer(FramePass pass, Runnable vanillaMainPassRenderer) {
        if (WATUT_HAS_ADD_PARTICLES_PASS) {
            pass.executes(vanillaMainPassRenderer);
            return;
        }

        pass.executes(() -> {
            vanillaMainPassRenderer.run();
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
