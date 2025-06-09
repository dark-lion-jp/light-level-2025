package com.dark_lion_jp.light_level_2025;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.impl.client.keybinding.KeyBindingRegistryImpl;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class LightLevel2025 implements ClientModInitializer {

  private static final String KEY_BIND_CATEGORY = "key.light_level_2025.category";
  private static final String TOGGLE_KEY_BIND = "key.light_level_2025.toggle";
  private static KeyBinding keyToggle;

  private static Config config;

  private static boolean enabled;

  public static void handleInputEvents() {
    while (keyToggle.wasPressed()) {
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

    KeyBindingRegistryImpl.addCategory(KEY_BIND_CATEGORY);
    KeyBindingHelper.registerKeyBinding(
        keyToggle = new KeyBinding(TOGGLE_KEY_BIND, GLFW.GLFW_KEY_F9, KEY_BIND_CATEGORY));

    WorldRenderEvents.AFTER_ENTITIES.register(LLWorldRenderer::render);
  }
}