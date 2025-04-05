package com.dark_lion_jp.light_level_2025;

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
  private static final int TEXT_COLOR_DANGER = 0xFF4040;   // Red: Mobs can always spawn
  private static final int TEXT_COLOR_NEUTRAL = 0xFFFFFF; // White: Unknown or unsupported dimension
  private static final int TEXT_COLOR_SAFE = 0x40FF40;     // Green: Mobs cannot spawn
  private static final int TEXT_COLOR_WARNING = 0xFFFF40; // Yellow: Mobs can spawn at night in the OverWorld

  private static final float TEXT_OFFSET_Y = 0.1f;        // Vertical offset for the text position
  private static final float TEXT_SCALE_DEBUG =
      1 / 48f;   // Text scale when debug screen is enabled
  private static final float TEXT_SCALE_NORMAL = 1 / 32f;  // Normal text scale

  // Configuration constants for rendering range
  private static final int RENDERING_RANGE = 16;          // Horizontal rendering range around the player
  private static final int RENDERING_VERTICAL_RANGE = 8;  // Vertical rendering range around the player

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
   * Checks if the light level information should be rendered at the given position. This check
   * determines if a hostile mob could potentially spawn at this location based on the block's
   * properties.
   *
   * @param world           The current game world.
   * @param positionToCheck The position to check.
   * @return True if the light level should be rendered, false otherwise.
   */
  private static boolean shouldRenderLightLevel(World world, BlockPos positionToCheck) {
    // Check if the world is null. If so, no rendering should occur.
    if (world == null) {
      return false;
    }

    BlockState blockStateAtPositionToCheck = world.getBlockState(positionToCheck);

    BlockPos positionBelow = positionToCheck.down();
    BlockState blockStateBelow = world.getBlockState(positionBelow);
    Block blockBelow = blockStateBelow.getBlock();

    // Prevent rendering above explicitly excluded blocks.
    if (BLOCKS_TO_EXCLUDE_RENDERING.contains(blockBelow)) {
      return false;
    }

    // Check if the block below is opaque. Mobs cannot spawn on transparent blocks.
    if (!blockStateBelow.isOpaque()) {
      return false;
    }

    // Prevent rendering on blocks where mobs cannot stand to spawn due to a collision shape.
    if (!blockStateAtPositionToCheck.getCollisionShape(world, positionToCheck).isEmpty()) {
      return false;
    }

    // Check if the block below is in the exception list of blocks that allow mob spawning.
    if (BLOCKS_BELOW_ALLOWING_SPAWN_EXCEPTION.contains(blockBelow)) {
      return true;
    }

    // If the above conditions are not met, perform a more detailed check of the collision shape
    // of the top face of the block below.
    VoxelShape collisionShapeBelow = blockStateBelow.getCollisionShape(world,
        positionBelow);
    if (!collisionShapeBelow.getFace(Direction.UP).isEmpty()) {
      Box upFaceBoundingBox = collisionShapeBelow.getFace(Direction.UP).getBoundingBox();
      // Check if the top face covers the entire XZ area (from 0.0 to 1.0)
      boolean coversFullXZ = MathHelper.approximatelyEquals(upFaceBoundingBox.minX, 0.0D) &&
          MathHelper.approximatelyEquals(upFaceBoundingBox.maxX, 1.0D) &&
          MathHelper.approximatelyEquals(upFaceBoundingBox.minZ, 0.0D) &&
          MathHelper.approximatelyEquals(upFaceBoundingBox.maxZ, 1.0D);
      // Check if the top face has a significant Y extent (meaning it's not just a thin sliver)
      boolean hasSignificantY = upFaceBoundingBox.maxY > upFaceBoundingBox.minY;

      return coversFullXZ && hasSignificantY;
    }

    return false;
  }

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

    float additionalYOffset = 0.0f;

    // Get the visual shape of the block at the drawing position to determine its height.
    BlockState blockStateToDrawAt = world.getBlockState(positionToDrawAt);
    VoxelShape visualShape = blockStateToDrawAt.getOutlineShape(world, positionToDrawAt);
    // If the visual shape is not empty, get its bounding box and use the Y length as an additional offset.
    if (!visualShape.isEmpty()) {
      Box boundingBox = visualShape.getBoundingBox();
      additionalYOffset = (float) boundingBox.getLengthY();
    }

    // Translate to the drawing position above the block, incorporating the calculated Y offset.
    matrices.translate(
        positionToDrawAt.getX() + 0.5,
        positionToDrawAt.getY() + TEXT_OFFSET_Y + additionalYOffset,
        positionToDrawAt.getZ() + 0.5
    );

    // Rotate the text to face the camera.
    matrices.multiply(cameraRotation);

    // Scale the text.
    matrices.scale(textScale, -textScale, textScale);

    // Determine the text to render based on whether to show both block-light and sky-light levels.
    String textToRender;
    if (shouldShowBothValues) {
      textToRender = "■" + blockLightLevel + " ☀" + skyLightLevel;
    } else {
      textToRender = String.valueOf(blockLightLevel);
    }

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
    // If the world is null, return a neutral color.
    if (world == null) {
      return TEXT_COLOR_NEUTRAL;
    }

    net.minecraft.util.Identifier currentDimension = world.getRegistryKey().getValue();

    int textColor;
    // Determine the text color based on the dimension and light levels.
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
   * Renders the light levels around the player.
   *
   * @param worldRenderContext The world render context provided by Fabric.
   */
  public static void render(WorldRenderContext worldRenderContext) {
    // Check if the light level rendering is enabled in the configuration.
    if (!LightLevel2025.isEnabled()) {
      return;
    }

    MinecraftClient client = MinecraftClient.getInstance();
    // Ensure the client, player, world, and text renderer are not null.
    if (client == null || client.player == null || client.world == null
        || client.textRenderer == null) {
      return;
    }

    MatrixStack matrices = worldRenderContext.matrixStack();
    // Ensure the matrix stack is not null.
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
    float textScale;
    if (shouldShowBothValues) {
      textScale = TEXT_SCALE_DEBUG;
    } else {
      textScale = TEXT_SCALE_NORMAL;
    }

    matrices.push();
    matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

    BlockPos playerPosition = player.getBlockPos();
    BlockPos.Mutable positionToRenderAt = playerPosition.mutableCopy();
    int renderingRange = RENDERING_RANGE;
    int renderingVerticalRange = RENDERING_VERTICAL_RANGE;

    // Iterate through the blocks within the rendering range around the player.
    for (int xOffset = -renderingRange; xOffset <= renderingRange; xOffset++) {
      for (int zOffset = -renderingRange; zOffset <= renderingRange; zOffset++) {
        for (int yOffset = -renderingVerticalRange; yOffset <= renderingVerticalRange; yOffset++) {
          positionToRenderAt.set(
              playerPosition.getX() + xOffset,
              playerPosition.getY() + yOffset,
              playerPosition.getZ() + zOffset
          );

          // Skip blocks that are too far from the player to optimize performance.
          if (positionToRenderAt.getSquaredDistance(playerPosition)
              > renderingRange * renderingRange * 1.5) {
            continue;
          }

          // Check if the light level information should be rendered at this position.
          if (!shouldRenderLightLevel(world, positionToRenderAt)) {
            continue;
          }

          // Get the block-light and sky-light levels at the current position.
          int blockLightLevel = world.getLightLevel(LightType.BLOCK, positionToRenderAt);
          int skyLightLevel = world.getLightLevel(LightType.SKY, positionToRenderAt);
          // Determine the color of the text based on the light levels and dimension.
          int textColor = getTextColor(world, blockLightLevel, skyLightLevel);

          // Draw the light level text at the current position.
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
}