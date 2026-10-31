package com.dark_lion_jp.light_level_2025;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

public class LLWorldRenderer {

  private static Config config;

  private static final List<BlockCached> blocksCached = new ArrayList<>();
  private static final RenderStateDataKey<List<LabelRenderState>> LIGHT_LEVELS =
      RenderStateDataKey.create(() -> "light-level-2025:light-levels");
  private static int frameCounter = 0;

  /**
   * Represents a cached block with its light level text properties.
   */
  private static final class BlockCached {

    public final BlockPos position;
    public final String text;
    public final float textScale;
    public final int textColor;
    public final float textOffsetY;

    public BlockCached(BlockPos position, String text, float textScale, int textColor,
        float textYOffset) {
      this.position = position.immutable();
      this.text = text;
      this.textScale = textScale;
      this.textColor = textColor;
      this.textOffsetY = textYOffset;
    }
  }

  private record LabelRenderState(BlockCached block, FormattedCharSequence text,
      float textWidth, float textHeight) {
  }

  /**
   * Draws the light level text at the specified position in the world.
   *
   * @param matrices       The matrix stack for rendering transformations.
   * @param submitNodes    The collector for queued text rendering.
   * @param label          The immutable label and font metrics prepared during extraction.
   * @param cameraRotation The current camera rotation to make the text face the viewer.
   */
  private static void drawLightLevelText(
      PoseStack matrices,
      SubmitNodeCollector submitNodes,
      LabelRenderState label,
      Quaternionf cameraRotation
  ) {
    BlockCached target = label.block();
    matrices.pushPose();

    // Translate to the drawing position (center of the block, adjusted by Y offset).
    matrices.translate(
        target.position.getX() + 0.5,
        target.position.getY() + target.textOffsetY,
        target.position.getZ() + 0.5
    );

    // Rotate the text to face the camera.
    matrices.rotate(cameraRotation);

    // Scale the text. Y-axis is inverted to orient text correctly.
    matrices.scale(target.textScale, -target.textScale, target.textScale);

    // Draw the text with shadow.
    submitNodes.submitText(
        matrices,
        -label.textWidth() / 2.0f,
        -label.textHeight() / 2.0f,
        label.text(),
        true,
        Font.DisplayMode.NORMAL,
        LightCoordsUtil.FULL_BRIGHT,
        target.textColor,
        0,
        0
    );

    matrices.popPose();
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
  private static int getTextColor(Level world, int blockLightLevel, int skyLightLevel) {
    Config.Hex textColorHex;
    Identifier currentDimension = world.dimension().identifier();
    if (currentDimension.equals(Level.OVERWORLD.identifier())) {
      if (blockLightLevel > 0) {
        textColorHex = config.text.color.safe;
      } else if (skyLightLevel > 7) {
        textColorHex = config.text.color.warning;
      } else {
        textColorHex = config.text.color.danger;
      }
    } else if (currentDimension.equals(Level.NETHER.identifier())) {
      if (blockLightLevel > 11) {
        textColorHex = config.text.color.safe;
      } else {
        textColorHex = config.text.color.danger;
      }
    } else if (currentDimension.equals(Level.END.identifier())) {
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
  private static float getTextOffsetY(Optional<AABB> blockBoundingBoxDrawAt,
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
        textOffsetY += (float) blockBoundingBoxDrawAt.get().getYsize();
      }
    }
    return textOffsetY;
  }

  /**
   * Determines if a given position's light level is "safe" for mob spawning, considering the
   * specific dimension.
   *
   * @param world           The current game world.
   * @param blockLightLevel The block light level at the position.
   * @return True if the light level is considered safe, false otherwise.
   */
  private static boolean isLightLevelSafe(Level world, int blockLightLevel) {
    // Retrieve the current dimension's identifier
    Identifier currentDimension = world.dimension().identifier();

    // Check safety rules based on the dimension
    if (currentDimension.equals(Level.OVERWORLD.identifier())) {
      // In Overworld, light level > 0 is safe
      return blockLightLevel > 0;
    } else if (currentDimension.equals(Level.NETHER.identifier())) {
      // In Nether, light level > 11 is safe
      return blockLightLevel > 11;
    } else if (currentDimension.equals(Level.END.identifier())) {
      // In The End, light level > 0 is safe
      return blockLightLevel > 0;
    } else {
      // For any other dimension, assume unsafe
      return false;
    }
  }

  /**
   * Extracts light levels while the world and camera may be safely accessed.
   *
   * @param worldRenderContext The world extraction context provided by Fabric.
   */
  public static void extract(LevelExtractionContext worldRenderContext) {
    FabricRenderState renderState = (FabricRenderState) worldRenderContext.levelState();
    renderState.setData(LIGHT_LEVELS, null);
    if (!LightLevel2025.isEnabled()) {
      return;
    }

    Minecraft client = Minecraft.getInstance();
    if (client == null || client.player == null || client.level == null
        || client.font == null) {
      return;
    }

    config = LightLevel2025.getConfig();
    Level world = client.level;
    Optional<Frustum> frustum = Optional.ofNullable(
        worldRenderContext.levelState().cameraRenderState.cullFrustum);
    Font gameTextRenderer = client.font;
    Player player = client.player;
    BlockPos playerPosition = player.blockPosition();
    Vec3 cameraPosition = worldRenderContext.levelState().cameraRenderState.pos;

    frameCounter++;
    // Update cached render targets at a configured interval.
    if (frameCounter >= config.cache.update_interval_frames) {
      boolean shouldShowBothValues = client.getDebugOverlay().showDebugScreen();
      updateRenderTargets(world, player, frustum, gameTextRenderer, playerPosition, cameraPosition,
          shouldShowBothValues);
      frameCounter = 0;
    }

    // Keep font centering current each frame, independently of the block-selection cache.
    List<LabelRenderState> labels = new ArrayList<>(blocksCached.size());
    for (BlockCached target : blocksCached) {
      String visualText = gameTextRenderer.isBidirectional()
          ? gameTextRenderer.bidirectionalShaping(target.text) : target.text;
      labels.add(new LabelRenderState(target,
          FormattedCharSequence.forward(visualText, Style.EMPTY),
          gameTextRenderer.width(target.text), gameTextRenderer.lineHeight));
    }

    // Publish an immutable snapshot; drawing must not read the live world or mutable cache.
    renderState.setData(LIGHT_LEVELS, List.copyOf(labels));
  }

  /** Queues extracted labels after entity submissions at COLLECT_SUBMITS in 26.3. */
  public static void render(LevelRenderContext worldRenderContext) {
    List<LabelRenderState> labels = ((FabricRenderState) worldRenderContext.levelState())
        .getData(LIGHT_LEVELS);
    PoseStack matrices = worldRenderContext.poseStack();
    if (labels == null || matrices == null) {
      return;
    }

    Vec3 cameraPosition = worldRenderContext.levelState().cameraRenderState.pos;
    matrices.pushPose();
    // Translate the rendering origin to the camera's position for correct world-space rendering.
    matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

    SubmitNodeCollector submitNodes = worldRenderContext.submitNodeCollector();
    Quaternionf cameraRotation = new Quaternionf(
        worldRenderContext.levelState().cameraRenderState.orientation);
    // Draw all cached light level texts.
    for (LabelRenderState label : labels) {
      drawLightLevelText(
          matrices,
          submitNodes,
          label,
          cameraRotation
      );
    }

    matrices.popPose();
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
  private static boolean shouldRenderLightLevel(Level world, Player player,
      Optional<Frustum> frustum, Vec3 cameraPosition, BlockPos positionToCheck,
      BlockState blockStateToCheck) {
    BlockPos positionBelow = positionToCheck.below();
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
    if (frustum.isPresent() && !frustum.get().isVisible(new AABB(positionToCheck))) {
      return false;
    }

    // Perform a raycast from the camera to the target block to check for line-of-sight obstruction.
    ClipContext raycastContextOfCenter = new ClipContext(
        cameraPosition,
        Vec3.atCenterOf(positionToCheck),
        ClipContext.Block.COLLIDER,
        ClipContext.Fluid.NONE,
        player
    );
    ClipContext raycastContextOfBottomCenter = new ClipContext(
        cameraPosition,
        Vec3.atBottomCenterOf(positionToCheck),
        ClipContext.Block.COLLIDER,
        ClipContext.Fluid.NONE,
        player
    );
    BlockHitResult hitResultOfCenter = world.clip(raycastContextOfCenter);
    BlockHitResult hitResultOfBottomCenter = world.clip(raycastContextOfBottomCenter);
    // If the raycast hits a different block before reaching the target block,
    // and that hit block is opaque, then the view is obstructed.
    if (
        !hitResultOfCenter.getBlockPos().equals(positionToCheck) &&
            world.getBlockState(hitResultOfCenter.getBlockPos()).canOcclude() &&
            !hitResultOfBottomCenter.getBlockPos().equals(positionToCheck) &&
            world.getBlockState(hitResultOfBottomCenter.getBlockPos()).canOcclude()
    ) {
      return false;
    }

    // Allow rendering for specific blocks below that are whitelisted, regardless of their opacity.
    if (config.getBlockWhitelist().contains(blockBelow)) {
      return true;
    }

    // Only render if the block below is opaque, typically allowing mob spawning.
    if (!blockStateBelow.canOcclude()) {
      return false;
    }

    // Check if the block below has a full upward-facing surface, essential for mob spawning.
    VoxelShape collisionShapeBelow = blockStateBelow.getCollisionShape(world, positionBelow);
    if (!collisionShapeBelow.isEmpty()) {
      // Check all four corners of the top surface.
      for (int x = 0; x <= 1; x++) {
        for (int z = 0; z <= 1; z++) {
          Vec3 point = new Vec3(x, 1, z); // Points at the top corners of the block's local space
          Optional<Vec3> pointClosest = collisionShapeBelow.closestPointTo(point);
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
  private static void updateRenderTargets(Level world, Player player,
      Optional<Frustum> frustum,
      Font textRenderer, BlockPos playerPosition, Vec3 cameraPosition,
      boolean shouldShowBothValues) {
    blocksCached.clear();

    BlockPos.MutableBlockPos positionToRenderAt = new BlockPos.MutableBlockPos();
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
          if (positionToRenderAt.distSqr(playerPosition) > maxSquaredDistance) {
            continue;
          }

          // Get block light level and skip rendering if it's safe and the option is enabled.
          int blockLightLevel = world.getBrightness(LightLayer.BLOCK, positionToRenderAt);
          if (config.text.hide_safe && isLightLevelSafe(world, blockLightLevel)) {
            continue;
          }

          BlockState blockStateRenderAt = world.getBlockState(positionToRenderAt);
          // Check if the light level should be rendered at this position based on various criteria.
          if (!shouldRenderLightLevel(world, player, frustum, cameraPosition, positionToRenderAt,
              blockStateRenderAt)) {
            continue;
          }

          // Get sky light level and determine text color.
          int skyLightLevel = world.getBrightness(LightLayer.SKY, positionToRenderAt);
          int textColor = getTextColor(world, blockLightLevel, skyLightLevel);

          // Get the bounding box of the block at the render position for text offset calculation.
          VoxelShape blockVisualShapeRenderAt = blockStateRenderAt.getShape(world,
              positionToRenderAt);
          Optional<AABB> blockBoundingBoxRenderAt;
          if (blockVisualShapeRenderAt.isEmpty()) {
            blockBoundingBoxRenderAt = Optional.empty();
          } else {
            blockBoundingBoxRenderAt = Optional.of(blockVisualShapeRenderAt.bounds());
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
          float textWidthScaled = textRenderer.width(textToRender) * textScale;
          float textHeightScaled = textRenderer.lineHeight * textScale;
          float textOffsetY = getTextOffsetY(blockBoundingBoxRenderAt,
              textWidthScaled,
              textHeightScaled);

          // Add the block to the cached list for rendering.
          blocksCached.add(
              new BlockCached(positionToRenderAt.immutable(), textToRender, textScale, textColor,
                  textOffsetY));
        }
      }
    }
  }
}
