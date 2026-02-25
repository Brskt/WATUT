package com.corosus.watut.client;

import net.minecraft.client.renderer.rendertype.RenderType;

public interface ParticleRenderTypeOld {

    RenderType getRenderType();

    default boolean isTranslucent() {
        return true;
    }

}
