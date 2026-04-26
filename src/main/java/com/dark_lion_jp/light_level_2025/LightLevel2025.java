package com.dark_lion_jp.light_level_2025;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class LightLevel2025 implements ClientModInitializer {

  private static final String TOGGLE_KEY_BIND = "key.light_level_2025.toggle";
  private static KeyMapping keyToggle;

  private static Config config;

  private static boolean enabled;

  public static void handleInputEvents() {
    while (keyToggle.consumeClick()) {
      enabled = !enabled;
    }
  }

  public static Config getConfig() {
    return config;
  }

  public static boolean isEnabled() {
    return enabled;
  }

  @Override
  public void onInitializeClient() {
    config = new Config().load();

    KeyMappingHelper.registerKeyMapping(
        keyToggle = new KeyMapping(TOGGLE_KEY_BIND, GLFW.GLFW_KEY_F9,
            KeyMapping.Category.GAMEPLAY));

    //This statement should work again in fabric 26.2 release :)
    //LevelRenderEvents.AFTER_ENTITIES.register(LLWorldRenderer::render);
  }
}