package com.corosus.watut;

public class ShaderRegistry {

    public static ShaderRegistry instance;

    public static void init() {

        //PlayerStatusManagerClient.particle = new ShaderProgramBlur("core/particle"); // Unused in 1.21.5 - particle rendering uses vanilla RenderType now
        PlayerStatusManagerClient.positionTexBlur = new ShaderProgramBlur("core/position_tex_blur");
        PlayerStatusManagerClient.positionTexBlurHorizontal = new ShaderProgramBlur("core/position_tex_blur_horizontal");
        PlayerStatusManagerClient.positionTexBlurVertical = new ShaderProgramBlur("core/position_tex_blur_vertical");

    }

    public static void reloadShaders() {
        // In 1.21.5, RenderPipeline manages shader compilation internally
        // No uniform cache to invalidate
    }

}
