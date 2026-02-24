package com.corosus.watut.mixin.client;

import com.corosus.watut.client.screen.RenderHelper;
import com.corosus.watut.client.screen.ScreenParticleRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;

import java.util.List;

/**
 * Renders a screen-only isolated GuiRenderState to WATUT's offscreen target before the frame ends.
 * The isolated state is built earlier by RenderHelper from Screen.renderWithTooltip() only (no HUD).
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererCaptureMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedReplayFailure = false;

    @Mutable
    @Shadow @Final
    private GuiRenderState renderState;

    @Shadow @Final
    private List<?> draws;

    @Shadow @Final
    private List<?> meshesToDraw;

    @Invoker("prepare")
    protected abstract void watut$invokePrepare();

    @Invoker("draw")
    protected abstract void watut$invokeDraw(GpuBufferSlice fogBuffer);

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;draw(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void watut$replayGuiToOffscreenTarget(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (RenderHelper.performingOwnRender) return;
        if (!RenderHelper.pendingCapture) return;
        if (RenderHelper.pendingGuiOnlyCapturePrepared) return;
        if (!RenderHelper.useDynamicGUISystem()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen == null) return;

        ScreenParticleRenderer spr = ScreenParticleRenderer.getInstance();
        spr.checkSetup();
        GuiRenderState screenOnlyState = RenderHelper.consumePendingScreenOnlyCaptureRenderState();
        if (screenOnlyState == null) {
            return;
        }

        try {
            // Reuse the existing GuiRenderer instance (Fabric hooks constructor and rejects late instances).
            GuiRenderState originalRenderState = this.renderState;
            spr.bind();
            RenderHelper.performingOwnRender = true;
            try {
                this.draws.clear();
                this.meshesToDraw.clear();
                this.renderState = screenOnlyState;
                this.watut$invokePrepare();
                this.watut$invokeDraw(fogBuffer);
            } finally {
                this.renderState = originalRenderState;
                RenderHelper.performingOwnRender = false;
            }
            RenderHelper.pendingGuiOnlyCapturePrepared = true;
        } catch (Throwable t) {
            // Keep capture disabled for this frame if replay fails.
            RenderHelper.pendingGuiOnlyCapturePrepared = false;
            if (!loggedReplayFailure) {
                loggedReplayFailure = true;
                LOGGER.error("WATUT GUI-only replay failed in GuiRendererCaptureMixin (logging once)", t);
            }
        } finally {
            spr.unbind();
        }
    }
}
