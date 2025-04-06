package com.dark_lion_jp.light_level_2025;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class LLWorldRenderer {

  // Configuration constants for text rendering
  private static final int TEXT_COLOR_DANGER = 0xFF4040;   // Red: Indicates that mobs can always spawn.
  private static final int TEXT_COLOR_NEUTRAL = 0xFFFFFF; // White: Used for unknown or unsupported dimensions.
  private static final int TEXT_COLOR_SAFE = 0x40FF40;     // Green: Indicates that mobs cannot spawn.
  private static final int TEXT_COLOR_WARNING = 0xFFFF40; // Yellow: Indicates that mobs can spawn at night in the OverWorld.

  private static final float TEXT_OFFSET_Y_BASE = 0.1f;    // Base vertical offset for the text position.
  private static final float TEXT_SCALE_FOR_DEBUG =
      1 / 48f;   // Text scale used when the debug screen is enabled.
  private static final float TEXT_SCALE_NORMAL = 1 / 32f;  // Normal text scale.

  // Configuration constants for rendering distance
  private static final int RENDER_DISTANCE_HORIZONTAL = 16;    // Horizontal rendering range around the player.
  private static final int RENDER_DISTANCE_VERTICAL = 8;      // Vertical rendering range around the player.

  // List of blocks that should not have light levels rendered above them
  private static final List<Block> BLOCKS_EXCLUDED_FROM_RENDERING = Arrays.asList(
      Blocks.AIR,
      Blocks.BARRIER,
      Blocks.BEDROCK,
      Blocks.COMMAND_BLOCK,
      Blocks.CHAIN_COMMAND_BLOCK,
      Blocks.REPEATING_COMMAND_BLOCK
  );

  // List of blocks below that exceptionally allow mob spawning
  private static final List<Block> BLOCKS_BELOW_ALLOWING_SPAWN_EXCEPTION = Arrays.asList(
      Blocks.MUD,
      Blocks.SLIME_BLOCK,
      Blocks.SOUL_SAND
  );

  /**
   * Draws the light level text at the specified position.
   *
   * @param matrices               The matrix stack for rendering transformations.
   * @param textRenderer           The text renderer instance.
   * @param bufferSource           The vertex consumer provider.
   * @param positionToDraw         The position to draw the text at.
   * @param blockBoundingBoxDrawAt The optional bounding box of the block at the drawing position.
   * @param cameraRotation         The camera rotation.
   * @param blockLightLevel        The block-light level at the position.
   * @param skyLightLevel          The sky-light level at the position.
   * @param shouldShowBothValues   Whether to show both block-light and sky-light levels.
   * @param textColor              The color of the text.
   * @param textScale              The scale of the text.
   */
  private static void drawLightLevelText(
      MatrixStack matrices,
      TextRenderer textRenderer,
      VertexConsumerProvider.Immediate bufferSource,
      BlockPos positionToDraw,
      Optional<Box> blockBoundingBoxDrawAt,
      Quaternionf cameraRotation,
      int blockLightLevel,
      int skyLightLevel,
      boolean shouldShowBothValues,
      int textColor,
      float textScale
  ) {
    matrices.push();

    // Determine the text to render.
    String textToRender;
    if (shouldShowBothValues) {
      textToRender = "■" + blockLightLevel + " ☀" + skyLightLevel;
    } else {
      textToRender = String.valueOf(blockLightLevel);
    }
    float textWidthScaled = textRenderer.getWidth(textToRender) * textScale;
    float textHeightScaled = textRenderer.fontHeight * textScale;

    // Calculate the base Y offset for the text, considering potential overlap with the block.
    float textOffsetY = getTextOffsetY(blockBoundingBoxDrawAt,
        textWidthScaled,
        textHeightScaled);

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
    float textWidth = textRenderer.getWidth(textToRender);
    Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
    textRenderer.draw(
        Text.of(textToRender),
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
    int textColor;
    Identifier currentDimension = world.getRegistryKey().getValue();
    if (currentDimension.equals(World.OVERWORLD.getValue())) {
      if (blockLightLevel > 0) {
        textColor = TEXT_COLOR_SAFE;
      } else if (skyLightLevel > 7) {
        textColor = TEXT_COLOR_WARNING;
      } else {
        textColor = TEXT_COLOR_DANGER;
      }
    } else if (currentDimension.equals(World.NETHER.getValue())) {
      if (blockLightLevel > 11) {
        textColor = TEXT_COLOR_SAFE;
      } else {
        textColor = TEXT_COLOR_DANGER;
      }
    } else if (currentDimension.equals(World.END.getValue())) {
      if (blockLightLevel > 0) {
        textColor = TEXT_COLOR_SAFE;
      } else {
        textColor = TEXT_COLOR_DANGER;
      }
    } else {
      textColor = TEXT_COLOR_NEUTRAL;
    }
    return textColor;
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
    float textOffsetY = TEXT_OFFSET_Y_BASE;

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

    Optional<Frustum> frustum = Optional.ofNullable(worldRenderContext.frustum());

    MatrixStack matrices = worldRenderContext.matrixStack();
    if (matrices == null) {
      return;
    }

    World world = client.world;
    TextRenderer gameTextRenderer = client.textRenderer;
    VertexConsumerProvider.Immediate bufferSource = client.getBufferBuilders()
        .getEntityVertexConsumers();

    Camera camera = worldRenderContext.camera();
    Vec3d cameraPosition = camera.getPos();
    Quaternionf cameraRotation = new Quaternionf(camera.getRotation());

    matrices.push();
    matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

    boolean shouldShowBothValues = client.getDebugHud().shouldShowDebugHud();
    float textScale = shouldShowBothValues ? TEXT_SCALE_FOR_DEBUG : TEXT_SCALE_NORMAL;

    PlayerEntity player = client.player;
    BlockPos playerPosition = player.getBlockPos();
    BlockPos.Mutable positionToRenderAt = playerPosition.mutableCopy();
    int renderRangeHorizontal = RENDER_DISTANCE_HORIZONTAL;
    int renderRangeVertical = RENDER_DISTANCE_VERTICAL;

    for (int offsetX = -renderRangeHorizontal; offsetX <= renderRangeHorizontal; offsetX++) {
      for (int offsetZ = -renderRangeHorizontal; offsetZ <= renderRangeHorizontal;
          offsetZ++) {
        for (int offsetY = -renderRangeVertical; offsetY <= renderRangeVertical; offsetY++) {
          positionToRenderAt.set(
              playerPosition.getX() + offsetX,
              playerPosition.getY() + offsetY,
              playerPosition.getZ() + offsetZ
          );

          if (positionToRenderAt.getSquaredDistance(playerPosition)
              > renderRangeHorizontal * renderRangeHorizontal * 1.5) {
            continue;
          }

          // Get the visual shape of the block at the drawing position.
          BlockState blockStateRenderAt = world.getBlockState(positionToRenderAt);
          VoxelShape visualShapeRenderAt = blockStateRenderAt.getOutlineShape(world,
              positionToRenderAt);
          Optional<Box> blockBoundingBoxRenderAt;
          if (!visualShapeRenderAt.isEmpty()) {
            blockBoundingBoxRenderAt = Optional.of(visualShapeRenderAt.getBoundingBox());
          } else {
            blockBoundingBoxRenderAt = Optional.empty();
          }
          if (!shouldRenderLightLevel(world, frustum, positionToRenderAt,
              blockStateRenderAt, blockBoundingBoxRenderAt)) {
            continue;
          }

          int blockLightLevel = world.getLightLevel(LightType.BLOCK, positionToRenderAt);
          int skyLightLevel = world.getLightLevel(LightType.SKY, positionToRenderAt);
          int textColor = getTextColor(world, blockLightLevel, skyLightLevel);

          drawLightLevelText(
              matrices,
              gameTextRenderer,
              bufferSource,
              positionToRenderAt,
              blockBoundingBoxRenderAt,
              cameraRotation,
              blockLightLevel,
              skyLightLevel,
              shouldShowBothValues,
              textColor,
              textScale
          );
        }
      }
    }

    matrices.pop();
  }

  /**
   * Checks if the light level information should be rendered at the given position. This check
   * determines if a hostile mob could potentially spawn at this location based on the block's
   * properties and visibility within the frustum.
   *
   * @param world                   The current game world.
   * @param frustum                 The optional frustum for visibility checking.
   * @param positionToCheck         The position to check.
   * @param blockStateToCheck       The block state at the position to check.
   * @param blockBoundingBoxToCheck The optional bounding box of the block at the position to
   *                                check.
   * @return True if the light level should be rendered, false otherwise.
   */
  private static boolean shouldRenderLightLevel(World world,
      Optional<Frustum> frustum, BlockPos positionToCheck, BlockState blockStateToCheck,
      Optional<Box> blockBoundingBoxToCheck) {
    BlockPos positionBelow = positionToCheck.down();
    BlockState blockStateBelow = world.getBlockState(positionBelow);
    Block blockBelow = blockStateBelow.getBlock();

    // Do not render light levels above excluded blocks.
    if (BLOCKS_EXCLUDED_FROM_RENDERING.contains(blockBelow)) {
      return false;
    }

    // Only render if the block below is opaque.
    if (!blockStateBelow.isOpaque()) {
      return false;
    }

    // Do not render if the current position is not air-like (has a collision shape).
    if (!blockStateToCheck.getCollisionShape(world, positionToCheck).isEmpty()) {
      return false;
    }

    // Do not render if the block is outside the frustum.
    if (frustum.isPresent() && blockBoundingBoxToCheck.isPresent() && !frustum.get()
        .isVisible(blockBoundingBoxToCheck.get())) {
      return false;
    }

    // Exception for specific blocks below that allow spawning despite being non-full.
    if (BLOCKS_BELOW_ALLOWING_SPAWN_EXCEPTION.contains(blockBelow)) {
      return true;
    }

    // Check if the block below has a full upward-facing surface for spawning.
    VoxelShape collisionShapeBelow = blockStateBelow.getCollisionShape(world,
        positionBelow);
    if (!collisionShapeBelow.getFace(Direction.UP).isEmpty()) {
      Box upFaceBoundingBox = collisionShapeBelow.getFace(Direction.UP).getBoundingBox();
      boolean coversFullXZ = MathHelper.approximatelyEquals(upFaceBoundingBox.minX, 0.0D) &&
          MathHelper.approximatelyEquals(upFaceBoundingBox.maxX, 1.0D) &&
          MathHelper.approximatelyEquals(upFaceBoundingBox.minZ, 0.0D) &&
          MathHelper.approximatelyEquals(upFaceBoundingBox.maxZ, 1.0D);
      boolean hasSignificantY = upFaceBoundingBox.maxY > upFaceBoundingBox.minY;

      return coversFullXZ && hasSignificantY;
    }

    return false;
  }
}