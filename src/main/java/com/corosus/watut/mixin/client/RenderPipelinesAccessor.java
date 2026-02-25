package com.corosus.watut.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {

    @Accessor("PARTICLE_SNIPPET")
    static RenderPipeline.Snippet watut$getParticleSnippet() {
        throw new UnsupportedOperationException();
    }

    @Invoker("register")
    static RenderPipeline watut$invokeRegister(RenderPipeline pipeline) {
        throw new UnsupportedOperationException();
    }
}
