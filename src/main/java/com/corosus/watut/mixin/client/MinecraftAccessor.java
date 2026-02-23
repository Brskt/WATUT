package com.corosus.watut.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("mainRenderTarget")
    RenderTarget watut$getMainRenderTarget();

    @Mutable
    @Accessor("mainRenderTarget")
    void watut$setMainRenderTarget(RenderTarget renderTarget);
}
