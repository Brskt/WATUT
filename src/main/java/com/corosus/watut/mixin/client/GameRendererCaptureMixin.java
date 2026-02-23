package com.corosus.watut.mixin.client;

import com.corosus.watut.client.screen.RenderHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into GameRenderer.render() AFTER GuiRenderer has finished rendering the GUI to the GPU.
 *
 * In 1.21.6, GUI rendering is two-phase:
 * 1. screen.renderWithTooltip() fills GuiRenderState (no GPU work)
 * 2. GuiRenderer.render() actually draws everything to the main render target
 *
 * WATUT's screen capture must happen after phase 2, so we inject at TAIL of GameRenderer.render()
 * where the main render target contains the complete frame (3D world + GUI).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererCaptureMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void afterGuiRender(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        RenderHelper.captureScreenAfterGuiRender();
    }
}
