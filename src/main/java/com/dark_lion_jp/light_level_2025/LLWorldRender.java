package com.dark_lion_jp.light_level_2025;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class LLWorldRender {

  private static void render(MatrixStack matrices, Camera camera,
      VertexConsumerProvider.Immediate bufferSource) {
    if (!LightLevel2025.isEnabled()) {
      return;
    }

    MinecraftClient client = MinecraftClient.getInstance();
    if (client == null || client.player == null || client.world == null
        || client.textRenderer == null) {
      return;
    }

    var player = client.player;
    var world = client.world;
    var textRenderer = client.textRenderer;
    var cameraPos = camera.getPos();

    boolean showBothValues = client.getDebugHud().shouldShowDebugHud();
    float scale = showBothValues ? 1 / 48f : 1 / 32f;

    Quaternionf cameraRotation = new Quaternionf(camera.getRotation());

    matrices.push();
    matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

    BlockPos playerPos = player.getBlockPos();
    BlockPos.Mutable mutablePos = playerPos.mutableCopy();

    int renderRange = 16;
    int verticalRange = 8;

    for (int xOff = -renderRange; xOff <= renderRange; xOff++) {
      for (int zOff = -renderRange; zOff <= renderRange; zOff++) {
        for (int yOff = -verticalRange; yOff <= verticalRange; yOff++) {
          mutablePos.set(playerPos.getX() + xOff, playerPos.getY() + yOff, playerPos.getZ() + zOff);
          if (mutablePos.getSquaredDistance(playerPos) > renderRange * renderRange * 1.5) {
            continue;
          }

          BlockPos posDown = mutablePos.down();
          var stateDown = world.getBlockState(posDown);
          var stateCurrent = world.getBlockState(mutablePos);
          if (!stateDown.isSolidBlock(world, posDown) || stateCurrent.isSolidBlock(world,
              mutablePos)) {
            continue;
          }

          int blockLight = world.getLightLevel(LightType.BLOCK, mutablePos);
          int skyLight = world.getLightLevel(LightType.SKY, mutablePos);
          int color;
          if (blockLight == 0) {
            if (skyLight == 0) {
              color = 0xFF4040;
            } else {
              color = 0xFFFF40;
            }
          } else {
            color = 0x40FF40;
          }

          matrices.push();

          matrices.translate(mutablePos.getX() + 0.5, mutablePos.getY() + 0.1,
              mutablePos.getZ() + 0.5);

          matrices.multiply(cameraRotation);

          matrices.scale(scale, -scale, scale);

          String textToShow;
          if (showBothValues) {
            textToShow = "■" + blockLight + " ☀" + skyLight;
          } else {
            textToShow = String.valueOf(blockLight);
          }
          float textWidth = textRenderer.getWidth(textToShow);

          Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

          textRenderer.draw(
              Text.of(textToShow),
              -textWidth / 2.0f,
              -textRenderer.fontHeight / 2.0f,
              color,
              false,
              positionMatrix,
              bufferSource,
              TextRenderer.TextLayerType.NORMAL,
              0,
              LightmapTextureManager.MAX_LIGHT_COORDINATE
          );

          matrices.pop();
        }
      }
    }

    matrices.pop();
  }

  public static void renderWorldLast(WorldRenderContext wrc) {
    VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders()
        .getEntityVertexConsumers();

    render(wrc.matrixStack(), wrc.camera(), immediate);
  }
}