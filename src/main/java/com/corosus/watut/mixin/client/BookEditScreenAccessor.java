package com.corosus.watut.mixin.client;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookEditScreen.class)
public interface BookEditScreenAccessor {

    @Accessor("page")
    MultiLineEditBox watut$getPage();
}
