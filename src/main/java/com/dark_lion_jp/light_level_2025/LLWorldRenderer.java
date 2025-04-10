package com.dark_lion_jp.light_level_2025;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class LLWorldRenderer {

  private static Config config;

  private static final List<BlockCached> blocksCached = new ArrayList<>();
  private static int frameCounter = 0;

  private static class BlockCached {

    public final BlockPos position;
    public final String text;
    public final float textScale;
    public final int textColor;
    public final float textOffsetY;

    public BlockCached(BlockPos position, String text, float textScale, int textColor,
        float textYOffset) {
      this.position = position.toImmutable();
      this.text = text;
      this.textScale = textScale;
      this.textColor = textColor;
      this.textOffsetY = textYOffset;
    }
  }

  /**
   * Draws the light level text at the specified position.
   *
   * @param matrices       The matrix stack for rendering transformations.
   * @param textRenderer   The text renderer instance.
   * @param bufferSource   The vertex consumer provider.
   * @param positionToDraw The position to draw the text at.
   * @param cameraRotation The camera rotation.
   * @param textToDraw     The text to be drawn.
   * @param textScale      The scale of the text.
   * @param textColor      The color of the text.
   * @param textOffsetY    The Y offset for the text to avoid overlapping with blocks.
   */
  private static void drawLightLevelText(
      MatrixStack matrices,
      TextRenderer textRenderer,
      VertexConsumerProvider.Immediate bufferSource,
      BlockPos positionToDraw,
      Quaternionf cameraRotation,
      String textToDraw,
      float textScale,
      int textColor,
      float textOffsetY
  ) {
    matrices.push();

    // Translate to the drawing position.
    matrices.translate(
        positionToDraw.getX() + 0.5,
        positionToDraw.getY() + textOffsetY,
        positionToDraw.getZ() + 0.5
    );

    // Rotate the text to face the camera.
    matrices.multiply(cameraRotation);

    // Scale the text.
    matrices.scale(textScale, -textScale, textScale);

    // Draw the text with shadow.
    float textWidth = textRenderer.getWidth(textToDraw);
    Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
    textRenderer.draw(
        Text.of(textToDraw),
        -textWidth / 2.0f,
        -textRenderer.fontHeight / 2.0f,
        textColor,
        true,
        positionMatrix,
        bufferSource,
        TextRenderer.TextLayerType.NORMAL,
        0,
        LightmapTextureManager.MAX_LIGHT_COORDINATE
    );

    matrices.pop();
  }

  /**
   * Determines the color of the light level text based on the light levels and the current
   * dimension.
   *
   * @param world           The current game world.
   * @param blockLightLevel The block-light level.
   * @param skyLightLevel   The sky-light level.
   * @return The color code for the text.
   */
  private static int getTextColor(World world, int blockLightLevel, int skyLightLevel) {
    Config.Hex textColorHex;
    Identifier currentDimension = world.getRegistryKey().getValue();
    if (currentDimension.equals(World.OVERWORLD.getValue())) {
      if (blockLightLevel > 0) {
        textColorHex = config.text.color.safe;
      } else if (skyLightLevel > 7) {
        textColorHex = config.text.color.warning;
      } else {
        textColorHex = config.text.color.danger;
      }
    } else if (currentDimension.equals(World.NETHER.getValue())) {
      if (blockLightLevel > 11) {
        textColorHex = config.text.color.safe;
      } else {
        textColorHex = config.text.color.danger;
      }
    } else if (currentDimension.equals(World.END.getValue())) {
      if (blockLightLevel > 0) {
        textColorHex = config.text.color.safe;
      } else {
        textColorHex = config.text.color.danger;
      }
    } else {
      textColorHex = config.text.color.neutral;
    }
    return textColorHex.value;
  }

  /**
   * Calculates the necessary Y offset for the text to avoid overlapping with the block.
   *
   * @param blockBoundingBoxDrawAt The optional bounding box of the block.
   * @param textWidthScaled        The scaled width of the text.
   * @param textHeightScaled       The scaled height of the text.
   * @return The Y offset to apply to the text position.
   */
  private static float getTextOffsetY(Optional<Box> blockBoundingBoxDrawAt,
      float textWidthScaled,
      float textHeightScaled) {
    float textOffsetY = config.text.offset_y_base;

    // If the block has a visual shape, check for potential XZ-axis overlap at any rotation.
    if (blockBoundingBoxDrawAt.isPresent()) {
      float textMaxLength = (float) Math.hypot(textWidthScaled, textHeightScaled);
      boolean textOverlapped = blockBoundingBoxDrawAt.get().intersects(
          0.5f - textMaxLength / 2f,
          0,
          0.5f - textMaxLength / 2f,
          0.5f + textMaxLength / 2f,
          textHeightScaled,
          0.5f + textMaxLength / 2f
      );
      if (textOverlapped) {
        textOffsetY += (float) blockBoundingBoxDrawAt.get()
            .getLengthY(); // Add block height if overlap occurs.
      }
    }
    return textOffsetY;
  }

  /**
   * Renders the light levels around the player.
   *
   * @param worldRenderContext The world render context provided by Fabric.
   */
  public static void render(WorldRenderContext worldRenderContext) {
    if (!LightLevel2025.isEnabled()) {
      return;
    }

    MinecraftClient client = MinecraftClient.getInstance();
    if (client == null || client.player == null || client.world == null
        || client.textRenderer == null) {
      return;
    }

    MatrixStack matrices = worldRenderContext.matrixStack();
    if (matrices == null) {
      return;
    }

    config = LightLevel2025.getConfig();
    World world = client.world;
    Optional<Frustum> frustum = Optional.ofNullable(worldRenderContext.frustum());
    TextRenderer gameTextRenderer = client.textRenderer;
    PlayerEntity player = client.player;
    BlockPos playerPosition = player.getBlockPos();

    frameCounter++;
    if (frameCounter >= config.cache.update_interval_frames) {
      boolean shouldShowBothValues = client.getDebugHud().shouldShowDebugHud();
      updateRenderTargets(world, frustum, gameTextRenderer, playerPosition, shouldShowBothValues);
      frameCounter = 0;
    }

    Camera camera = worldRenderContext.camera();
    Vec3d cameraPosition = camera.getPos();
    matrices.push();
    matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

    VertexConsumerProvider.Immediate bufferSource = client.getBufferBuilders()
        .getEntityVertexConsumers();
    Quaternionf cameraRotation = new Quaternionf(camera.getRotation());
    for (BlockCached target : blocksCached) {
      drawLightLevelText(
          matrices,
          gameTextRenderer,
          bufferSource,
          target.position,
          cameraRotation,
          target.text,
          target.textScale,
          target.textColor,
          target.textOffsetY
      );
    }

    matrices.pop();
  }

  /**
   * Checks if the light level information should be rendered at the given position. This check
   * determines if a hostile mob could potentially spawn at this location based on the block's
   * properties and visibility within the frustum.
   *
   * @param world             The current game world.
   * @param frustum           The optional frustum for visibility checking.
   * @param positionToCheck   The position to check.
   * @param blockStateToCheck The block state at the position to check. check.
   * @return True if the light level should be rendered, false otherwise.
   */
  private static boolean shouldRenderLightLevel(World world,
      Optional<Frustum> frustum, BlockPos positionToCheck, BlockState blockStateToCheck) {
    BlockPos positionBelow = positionToCheck.down();
    BlockState blockStateBelow = world.getBlockState(positionBelow);
    Block blockBelow = blockStateBelow.getBlock();

    // Do not render light levels above excluded blocks.
    if (config.getBlockBlacklist().contains(blockBelow)) {
      return false;
    }

    // Do not render if the current position is not air-like (has a collision shape).
    if (!blockStateToCheck.getCollisionShape(world, positionToCheck).isEmpty()) {
      return false;
    }

    // Do not render if the block is outside the frustum.
    if (frustum.isPresent() && !frustum.get().isVisible(new Box(positionToCheck))) {
      return false;
    }

    // Exception for specific blocks below that allow spawning despite being non-full.
    if (config.getBlockWhitelist().contains(blockBelow)) {
      return true;
    }

    // Only render if the block below is opaque.
    if (!blockStateBelow.isOpaque()) {
      return false;
    }

    // Check if the block below has a full upward-facing surface for spawning.
    VoxelShape collisionShapeBelow = blockStateBelow.getCollisionShape(world, positionBelow);
    if (!collisionShapeBelow.isEmpty()) {
      for (int x = 0; x <= 1; x++) {
        for (int z = 0; z <= 1; z++) {
          Vec3d point = new Vec3d(x, 1, z);
          Optional<Vec3d> pointClosest = collisionShapeBelow.getClosestPointTo(point);
          // If the closest point is empty or not the corner itself, it's not a full surface.
          if (pointClosest.isEmpty() || !pointClosest.get().equals(point)) {
            return false;
          }
        }
      }

      return true;
    }

    return false;
  }

  private static void updateRenderTargets(World world, Optional<Frustum> frustum,
      TextRenderer textRenderer, BlockPos playerPosition, boolean shouldShowBothValues) {
    blocksCached.clear();

    BlockPos.Mutable positionToRenderAt = new BlockPos.Mutable();
    int renderRangeHorizontal = config.render_distance.horizontal;
    int renderRangeVertical = config.render_distance.vertical;
    double maxSquaredDistance = renderRangeHorizontal * renderRangeHorizontal * 1.5;

    for (int dx = -renderRangeHorizontal; dx <= renderRangeHorizontal; dx++) {
      for (int dz = -renderRangeHorizontal; dz <= renderRangeHorizontal; dz++) {
        for (int dy = -renderRangeVertical; dy <= renderRangeVertical; dy++) {
          positionToRenderAt.set(playerPosition.getX() + dx, playerPosition.getY() + dy,
              playerPosition.getZ() + dz);
          if (positionToRenderAt.getSquaredDistance(playerPosition) > maxSquaredDistance) {
            continue;
          }

          BlockState blockStateRenderAt = world.getBlockState(positionToRenderAt);
          if (!shouldRenderLightLevel(world, frustum, positionToRenderAt, blockStateRenderAt)) {
            continue;
          }

          int blockLightLevel = world.getLightLevel(LightType.BLOCK, positionToRenderAt);
          int skyLightLevel = world.getLightLevel(LightType.SKY, positionToRenderAt);
          int textColor = getTextColor(world, blockLightLevel, skyLightLevel);

          VoxelShape blockVisualShapeRenderAt = blockStateRenderAt.getOutlineShape(world,
              positionToRenderAt);
          Optional<Box> blockBoundingBoxRenderAt;
          if (blockVisualShapeRenderAt.isEmpty()) {
            blockBoundingBoxRenderAt = Optional.empty();
          } else {
            blockBoundingBoxRenderAt = Optional.of(blockVisualShapeRenderAt.getBoundingBox());
          }

          String textToRender;
          if (shouldShowBothValues) {
            textToRender = "■" + blockLightLevel + " ☀" + skyLightLevel;
          } else {
            textToRender = String.valueOf(blockLightLevel);
          }
          float textScale =
              shouldShowBothValues ? config.text.scale.debug : config.text.scale.normal;
          float textWidthScaled = textRenderer.getWidth(textToRender) * textScale;
          float textHeightScaled = textRenderer.fontHeight * textScale;
          float textOffsetY = getTextOffsetY(blockBoundingBoxRenderAt,
              textWidthScaled,
              textHeightScaled);

          blocksCached.add(
              new BlockCached(positionToRenderAt.toImmutable(), textToRender, textScale, textColor,
                  textOffsetY));
        }
      }
    }
  }
}
