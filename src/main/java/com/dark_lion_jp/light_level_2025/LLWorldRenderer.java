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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class LLWorldRenderer {

  private static Config config;

  private static final List<BlockCached> blocksCached = new ArrayList<>();
  private static int frameCounter = 0;

  /**
   * Represents a cached block with its light level text properties.
   */
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
   * Draws the light level text at the specified position in the world.
   *
   * @param matrices       The matrix stack for rendering transformations.
   * @param textRenderer   The text renderer instance.
   * @param bufferSource   The vertex consumer provider for immediate rendering.
   * @param positionToDraw The block position to draw the text at.
   * @param cameraRotation The current camera rotation to make the text face the viewer.
   * @param textToDraw     The string content of the light level.
   * @param textScale      The scale factor for the text.
   * @param textColor      The color of the text (AARRGGBB format).
   * @param textOffsetY    The Y offset for the text to prevent visual overlap with the block.
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

    // Translate to the drawing position (center of the block, adjusted by Y offset).
    matrices.translate(
        positionToDraw.getX() + 0.5,
        positionToDraw.getY() + textOffsetY,
        positionToDraw.getZ() + 0.5
    );

    // Rotate the text to face the camera.
    matrices.multiply(cameraRotation);

    // Scale the text. Y-axis is inverted to orient text correctly.
    matrices.scale(textScale, -textScale, textScale);

    // Draw the text with shadow.
    float textWidth = textRenderer.getWidth(textToDraw);
    Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
    textRenderer.draw(
        textToDraw,
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
   * Determines the color of the light level text based on block and sky light levels, considering
   * the current dimension.
   *
   * @param world           The current game world.
   * @param blockLightLevel The block light level at the position.
   * @param skyLightLevel   The sky light level at the position.
   * @return The color code for the text in AARRGGBB format.
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
   * Calculates the necessary Y offset for the text to prevent it from visually overlapping with the
   * block it's drawn above.
   *
   * @param blockBoundingBoxDrawAt The optional bounding box of the block below the text.
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
        // If overlap occurs, move text up by the block's height.
        textOffsetY += (float) blockBoundingBoxDrawAt.get().getLengthY();
      }
    }
    return textOffsetY;
  }

  /**
   * Renders the light levels around the player. This is the main rendering entry point.
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
    Camera camera = worldRenderContext.camera();
    Vec3d cameraPosition = camera.getPos();

    frameCounter++;
    // Update cached render targets at a configured interval.
    if (frameCounter >= config.cache.update_interval_frames) {
      boolean shouldShowBothValues = client.getDebugHud().shouldShowDebugHud();
      updateRenderTargets(world, player, frustum, gameTextRenderer, playerPosition, cameraPosition,
          shouldShowBothValues);
      frameCounter = 0;
    }

    matrices.push();
    // Translate the rendering origin to the camera's position for correct world-space rendering.
    matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

    VertexConsumerProvider.Immediate bufferSource = client.getBufferBuilders()
        .getEntityVertexConsumers();
    Quaternionf cameraRotation = new Quaternionf(camera.getRotation());
    // Draw all cached light level texts.
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
   * Checks if the light level information should be rendered at a given position. This involves
   * checks for block properties, frustum visibility, and line-of-sight.
   *
   * @param world             The current game world.
   * @param player            The player entity, used for raycasting (to ignore self).
   * @param frustum           The optional frustum for camera visibility checking.
   * @param cameraPosition    The current position of the camera.
   * @param positionToCheck   The block position to check for rendering.
   * @param blockStateToCheck The block state at the position to check.
   * @return True if the light level should be rendered, false otherwise.
   */
  private static boolean shouldRenderLightLevel(World world, PlayerEntity player,
      Optional<Frustum> frustum, Vec3d cameraPosition, BlockPos positionToCheck,
      BlockState blockStateToCheck) {
    BlockPos positionBelow = positionToCheck.down();
    BlockState blockStateBelow = world.getBlockState(positionBelow);
    Block blockBelow = blockStateBelow.getBlock();

    // Do not render light levels above blocks in the blacklist.
    if (config.getBlockBlacklist().contains(blockBelow)) {
      return false;
    }

    // Do not render if the current position is not air-like (i.e., has a collision shape).
    if (!blockStateToCheck.getCollisionShape(world, positionToCheck).isEmpty()) {
      return false;
    }

    // Do not render if the block is outside the camera's frustum.
    if (frustum.isPresent() && !frustum.get().isVisible(new Box(positionToCheck))) {
      return false;
    }

    // Perform a raycast from the camera to the target block to check for line-of-sight obstruction.
    RaycastContext raycastContext = new RaycastContext(
        cameraPosition,
        Vec3d.ofCenter(positionToCheck),
        RaycastContext.ShapeType.COLLIDER,
        RaycastContext.FluidHandling.NONE,
        player
    );
    BlockHitResult hitResult = world.raycast(raycastContext);
    // If the raycast hits a different block before reaching the target block,
    // and that hit block is opaque, then the view is obstructed.
    if (
        !hitResult.getBlockPos().equals(positionToCheck) &&
            world.getBlockState(hitResult.getBlockPos()).isOpaque()
    ) {
      return false;
    }

    // Allow rendering for specific blocks below that are whitelisted, regardless of their opacity.
    if (config.getBlockWhitelist().contains(blockBelow)) {
      return true;
    }

    // Only render if the block below is opaque, typically allowing mob spawning.
    if (!blockStateBelow.isOpaque()) {
      return false;
    }

    // Check if the block below has a full upward-facing surface, essential for mob spawning.
    VoxelShape collisionShapeBelow = blockStateBelow.getCollisionShape(world, positionBelow);
    if (!collisionShapeBelow.isEmpty()) {
      // Check all four corners of the top surface.
      for (int x = 0; x <= 1; x++) {
        for (int z = 0; z <= 1; z++) {
          Vec3d point = new Vec3d(x, 1, z); // Points at the top corners of the block's local space
          Optional<Vec3d> pointClosest = collisionShapeBelow.getClosestPointTo(point);
          // If the closest point on the collision shape isn't the corner itself,
          // it indicates a non-full surface.
          if (pointClosest.isEmpty() || !pointClosest.get().equals(point)) {
            return false;
          }
        }
      }
      return true;
    }

    return false;
  }

  /**
   * Updates the list of blocks where light level text should be rendered. This involves iterating
   * through blocks around the player and applying rendering criteria.
   *
   * @param world                The current game world.
   * @param player               The player entity.
   * @param frustum              The camera frustum for visibility checks.
   * @param textRenderer         The text renderer instance.
   * @param playerPosition       The current block position of the player.
   * @param cameraPosition       The current position of the camera.
   * @param shouldShowBothValues True if both block and sky light levels should be displayed (debug
   *                             mode), false otherwise.
   */
  private static void updateRenderTargets(World world, PlayerEntity player,
      Optional<Frustum> frustum,
      TextRenderer textRenderer, BlockPos playerPosition, Vec3d cameraPosition,
      boolean shouldShowBothValues) {
    blocksCached.clear();

    BlockPos.Mutable positionToRenderAt = new BlockPos.Mutable();
    int renderRangeHorizontal = config.render_distance.horizontal;
    int renderRangeVertical = config.render_distance.vertical;
    // Max squared distance to limit block iteration to a sphere, slightly extended to cover corners.
    double maxSquaredDistance = renderRangeHorizontal * renderRangeHorizontal * 1.5;

    // Iterate through blocks within the defined rendering range.
    for (int dx = -renderRangeHorizontal; dx <= renderRangeHorizontal; dx++) {
      for (int dz = -renderRangeHorizontal; dz <= renderRangeHorizontal; dz++) {
        for (int dy = -renderRangeVertical; dy <= renderRangeVertical; dy++) {
          positionToRenderAt.set(playerPosition.getX() + dx, playerPosition.getY() + dy,
              playerPosition.getZ() + dz);

          // Skip blocks outside the spherical rendering range.
          if (positionToRenderAt.getSquaredDistance(playerPosition) > maxSquaredDistance) {
            continue;
          }

          BlockState blockStateRenderAt = world.getBlockState(positionToRenderAt);
          // Check if the light level should be rendered at this position based on various criteria.
          if (!shouldRenderLightLevel(world, player, frustum, cameraPosition, positionToRenderAt,
              blockStateRenderAt)) {
            continue;
          }

          // Get light levels and determine text color.
          int blockLightLevel = world.getLightLevel(LightType.BLOCK, positionToRenderAt);
          int skyLightLevel = world.getLightLevel(LightType.SKY, positionToRenderAt);
          int textColor = getTextColor(world, blockLightLevel, skyLightLevel);

          // Get the bounding box of the block at the render position for text offset calculation.
          VoxelShape blockVisualShapeRenderAt = blockStateRenderAt.getOutlineShape(world,
              positionToRenderAt);
          Optional<Box> blockBoundingBoxRenderAt;
          if (blockVisualShapeRenderAt.isEmpty()) {
            blockBoundingBoxRenderAt = Optional.empty();
          } else {
            blockBoundingBoxRenderAt = Optional.of(blockVisualShapeRenderAt.getBoundingBox());
          }

          // Format the text string and calculate its scale and offset.
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

          // Add the block to the cached list for rendering.
          blocksCached.add(
              new BlockCached(positionToRenderAt.toImmutable(), textToRender, textScale, textColor,
                  textOffsetY));
        }
      }
    }
  }
}