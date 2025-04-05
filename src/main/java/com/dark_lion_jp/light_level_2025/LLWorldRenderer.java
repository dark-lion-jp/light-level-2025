package com.dark_lion_jp.light_level_2025;

import java.lang.Math;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
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

  // Configuration constants for rendering range
  private static final int RENDER_RANGE_HORIZONTAL = 16;    // Horizontal rendering range around the player.
  private static final int RENDER_RANGE_VERTICAL = 8;      // Vertical rendering range around the player.

  // List of blocks that should not have light levels rendered above them
  private static final List<Block> BLOCKS_TO_EXCLUDE_RENDERING = Arrays.asList(
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
   * @param world                The current game world.
   * @param matrices             The matrix stack for rendering transformations.
   * @param textRenderer         The text renderer instance.
   * @param bufferSource         The vertex consumer provider.
   * @param positionToDrawAt     The position to draw the text at.
   * @param cameraRotation       The camera rotation.
   * @param blockLightLevel      The block-light level at the position.
   * @param skyLightLevel        The sky-light level at the position.
   * @param textColor            The color of the text.
   * @param shouldShowBothValues Whether to show both block-light and sky-light levels.
   * @param textScale            The scale of the text.
   */
  private static void drawLightLevelText(
      World world,
      MatrixStack matrices,
      TextRenderer textRenderer,
      VertexConsumerProvider.Immediate bufferSource,
      BlockPos positionToDrawAt,
      Quaternionf cameraRotation,
      int blockLightLevel,
      int skyLightLevel,
      int textColor,
      boolean shouldShowBothValues,
      float textScale
  ) {
    matrices.push();

    // Get the visual shape of the block at the drawing position.
    BlockState blockState = world.getBlockState(positionToDrawAt);
    VoxelShape visualShape = blockState.getOutlineShape(world, positionToDrawAt);

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
    float textOffsetY = getTextOffsetY(visualShape, textWidthScaled, textHeightScaled);

    // Translate to the drawing position.
    matrices.translate(
        positionToDrawAt.getX() + 0.5,
        positionToDrawAt.getY() + textOffsetY,
        positionToDrawAt.getZ() + 0.5
    );

    // Rotate the text to face the camera.
    matrices.multiply(cameraRotation);

    // Scale the text.
    matrices.scale(textScale, -textScale, textScale);

    float textWidth = textRenderer.getWidth(textToRender);
    Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

    // Draw the text with shadow.
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
    if (world == null) {
      return TEXT_COLOR_NEUTRAL;
    }

    net.minecraft.util.Identifier currentDimension = world.getRegistryKey().getValue();

    int textColor;
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
   * @param visualShape      The visual shape of the block.
   * @param textWidthScaled  The scaled width of the text.
   * @param textHeightScaled The scaled height of the text.
   * @return The Y offset to apply to the text position.
   */
  private static float getTextOffsetY(VoxelShape visualShape, float textWidthScaled,
      float textHeightScaled) {
    float textOffsetY = TEXT_OFFSET_Y_BASE;

    // If the block has a visual shape, check for potential XZ-axis overlap at any rotation.
    if (!visualShape.isEmpty()) {
      Box blockBoundingBox = visualShape.getBoundingBox();
      float textMaxLength = (float) Math.sqrt(
          Math.pow(textWidthScaled, 2) + Math.pow(textHeightScaled, 2));
      boolean textOverlapped = blockBoundingBox.intersects(
          0.5f - textMaxLength,
          0,
          0.5f - textMaxLength,
          0.5f + textMaxLength,
          textHeightScaled,
          0.5f + textMaxLength
      );

      if (textOverlapped) {
        textOffsetY += (float) blockBoundingBox.getLengthY(); // Add block height if overlap occurs.
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

    Camera camera = worldRenderContext.camera();
    VertexConsumerProvider.Immediate bufferSource = client.getBufferBuilders()
        .getEntityVertexConsumers();
    PlayerEntity player = client.player;
    World world = client.world;
    TextRenderer gameTextRenderer = client.textRenderer;
    Vec3d cameraPosition = camera.getPos();
    Quaternionf cameraRotation = new Quaternionf(camera.getRotation());

    boolean shouldShowBothValues = client.getDebugHud().shouldShowDebugHud();
    float textScale = shouldShowBothValues ? TEXT_SCALE_FOR_DEBUG : TEXT_SCALE_NORMAL;

    matrices.push();
    matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

    BlockPos playerPosition = player.getBlockPos();
    BlockPos.Mutable positionToRenderAt = playerPosition.mutableCopy();
    int renderRangeHorizontal = RENDER_RANGE_HORIZONTAL;
    int renderRangeVertical = RENDER_RANGE_VERTICAL;

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

          if (!shouldRenderLightLevel(world, positionToRenderAt)) {
            continue;
          }

          int blockLightLevel = world.getLightLevel(LightType.BLOCK, positionToRenderAt);
          int skyLightLevel = world.getLightLevel(LightType.SKY, positionToRenderAt);
          int textColor = getTextColor(world, blockLightLevel, skyLightLevel);

          drawLightLevelText(
              world,
              matrices,
              gameTextRenderer,
              bufferSource,
              positionToRenderAt,
              cameraRotation,
              blockLightLevel,
              skyLightLevel,
              textColor,
              shouldShowBothValues,
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
   * properties.
   *
   * @param world           The current game world.
   * @param positionToCheck The position to check.
   * @return True if the light level should be rendered, false otherwise.
   */
  private static boolean shouldRenderLightLevel(World world, BlockPos positionToCheck) {
    if (world == null) {
      return false;
    }

    BlockState blockStateAtPositionToCheck = world.getBlockState(positionToCheck);

    BlockPos positionBelow = positionToCheck.down();
    BlockState blockStateBelow = world.getBlockState(positionBelow);
    Block blockBelow = blockStateBelow.getBlock();

    if (BLOCKS_TO_EXCLUDE_RENDERING.contains(blockBelow)) {
      return false;
    }

    if (!blockStateBelow.isOpaque()) {
      return false;
    }

    if (!blockStateAtPositionToCheck.getCollisionShape(world, positionToCheck).isEmpty()) {
      return false;
    }

    if (BLOCKS_BELOW_ALLOWING_SPAWN_EXCEPTION.contains(blockBelow)) {
      return true;
    }

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