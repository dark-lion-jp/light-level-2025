package com.dark_lion_jp.light_level_2025.mixin;

import com.dark_lion_jp.light_level_2025.LightLevel2025;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

  @Inject(method = "handleInputEvents", at = @At("HEAD"))
  private void handleInputEvents(CallbackInfo ci) {
    LightLevel2025.handleInputEvents();
  }
}
