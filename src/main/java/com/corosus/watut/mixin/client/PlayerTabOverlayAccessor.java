package com.corosus.watut.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {

    @Invoker("renderPingIcon")
    void watut$invokeRenderPingIcon(GuiGraphics guiGraphics, int x, int width, int y, PlayerInfo playerInfo);
}
