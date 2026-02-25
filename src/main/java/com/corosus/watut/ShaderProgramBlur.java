package com.corosus.watut;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class ShaderProgramBlur {

    private RenderPipeline pipeline;

    //TODO: not all the shaders have these, between having to refresh them and this issue, might be worth just using "compiledShaderProgram.safeGetUniform("resolution");" when using shader program
    //but the lookup each time might be costly, so maybe just a dynamic cached map we invalidate when shaders are reloaded to make sure it gets the new uniform address?

    public ShaderProgramBlur(String resourcePath) {

        Identifier shaderLoc = Identifier.fromNamespaceAndPath(WatutMod.MODID, resourcePath);
        pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(WatutMod.MODID, "pipeline/" + resourcePath.replace("/", "_")))
                .withVertexShader(shaderLoc)
                .withFragmentShader(shaderLoc)
                .withSampler("InSampler")
                .withUniform("BlurParams", UniformType.UNIFORM_BUFFER)
                .withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER)
                .build());

    }

    public RenderPipeline getPipeline() {
        return pipeline;
    }
}
