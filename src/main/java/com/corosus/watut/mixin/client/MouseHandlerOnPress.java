package com.corosus.watut.mixin.client;

import com.corosus.watut.WatutMod;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerOnPress {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void onPress(long pWindowPointer, MouseButtonInfo input, int action, CallbackInfo info) {
        WatutMod.getPlayerStatusManagerClient().onMouse(action != 0);
    }
}
