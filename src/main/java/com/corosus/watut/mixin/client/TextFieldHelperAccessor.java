package com.corosus.watut.mixin.client;

import net.minecraft.client.gui.font.TextFieldHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(TextFieldHelper.class)
public interface TextFieldHelperAccessor {

    @Accessor("getMessageFn")
    Supplier<String> watut$getMessageFn();
}
