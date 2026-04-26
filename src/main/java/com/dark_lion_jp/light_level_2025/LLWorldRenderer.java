package com.dark_lion_jp.light_level_2025;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

public class LLWorldRenderer {

    private static Config config;

    private static final List<BlockCached> blocksCached = new ArrayList<>();
    private static int frameCounter = 20;

    private static int lastPlayerx = 0;
    private static int lastPlayerz = 0;

    private static class BlockCached {

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

    private static void drawLightLevelText(
            PoseStack matrices,
            Font textRenderer,
            MultiBufferSource.BufferSource bufferSource,
            BlockPos positionToDraw,
            Quaternionf cameraRotation,
            String textToDraw,
            float textScale,
            int textColor,
            float textOffsetY
    ) {
        matrices.pushPose();

        matrices.translate(
                positionToDraw.getX() + 0.5,
                positionToDraw.getY() + textOffsetY,
                positionToDraw.getZ() + 0.5
        );

        matrices.mulPose(cameraRotation);

        matrices.scale(textScale, -textScale, textScale);

        float textWidth = textRenderer.width(textToDraw);
        Matrix4f positionMatrix = matrices.last().pose();
        textRenderer.drawInBatch(
                textToDraw,
                -textWidth / 2.0f,
                -textRenderer.lineHeight / 2.0f,
                textColor,
                true,
                positionMatrix,
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                0xF000F0
        );

        matrices.popPose();
    }

    private static int getTextColor(Level world, int blockLightLevel, int skyLightLevel) {
        Config.Hex textColorHex;
        if (world.dimension().equals(Level.OVERWORLD)) {
            if (blockLightLevel > 0) {
                textColorHex = config.text.color.safe;
            }else if (skyLightLevel > 7) {
                textColorHex = config.text.color.warning;
            } else {
                textColorHex = config.text.color.danger;
            }
        } else if (world.dimension().equals(Level.NETHER)) {
            if (blockLightLevel > 11) {
                textColorHex = config.text.color.safe;
            } else {
                textColorHex = config.text.color.danger;
            }
        } else if (world.dimension().equals(Level.END)) {
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

    private static float getTextOffsetY(Optional<AABB> blockBoundingBoxDrawAt,
                                        float textWidthScaled,
                                        float textHeightScaled) {
        float textOffsetY = config.text.offset_y_base;

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
                textOffsetY += (float) blockBoundingBoxDrawAt.get().getYsize();
            }
        }
        return textOffsetY;
    }

    private static boolean isLightLevelSafe(Level world, int blockLightLevel) {
        if (world.dimension().equals(Level.OVERWORLD)) {
            return blockLightLevel > 0;
        } else if (world.dimension().equals(Level.NETHER)) {
            return blockLightLevel > 11;
        } else if (world.dimension().equals(Level.END)) {
            return blockLightLevel > 0;
        } else {
            return false;
        }
    }

    public static void render(Matrix4fc modelViewMatrix) {
        if (!LightLevel2025.isEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        config = LightLevel2025.getConfig();
        Level world = client.level;
        Font gameTextRenderer = client.font;
        Player player = client.player;
        BlockPos playerPosition = player.blockPosition();
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.position();

        PoseStack matrices = new PoseStack();
        matrices.last().pose().mul(new Matrix4f(modelViewMatrix));

        int currentX = playerPosition.getX() >> 4;
        int currentZ = playerPosition.getZ() >> 4;

        if(currentZ != lastPlayerz || currentX != lastPlayerx) {
            lastPlayerx = currentX;
            lastPlayerz = currentZ;
            frameCounter = config.cache.update_interval_frames;
        }

        frameCounter++;
        if (frameCounter >= config.cache.update_interval_frames) {
            boolean shouldShowBothValues = client.getDebugOverlay().showDebugScreen();
            Frustum frustum = camera.getCullFrustum();

            updateRenderTargets(world, player, Optional.of(frustum), gameTextRenderer, playerPosition, cameraPosition, shouldShowBothValues);
            frameCounter = 0;
        }

        matrices.pushPose();
        matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
        Quaternionf cameraRotation = new Quaternionf(camera.rotation());
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

        matrices.popPose();
    }

    private static boolean shouldRenderLightLevel(Level world, Player player,
                                                  Optional<Frustum> frustum, Vec3 cameraPosition, BlockPos positionToCheck,
                                                  BlockState blockStateToCheck) {
        BlockPos positionBelow = positionToCheck.below();
        BlockState blockStateBelow = world.getBlockState(positionBelow);
        Block blockBelow = blockStateBelow.getBlock();

        if (config.getBlockBlacklist().contains(blockBelow)) {
            return false;
        }

        if (!blockStateToCheck.getCollisionShape(world, positionToCheck).isEmpty()) {
            return false;
        }

        if (frustum.isPresent() && !frustum.get().isVisible(new AABB(positionToCheck))) {
            return false;
        }

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
        if (
                !hitResultOfCenter.getBlockPos().equals(positionToCheck) &&
                        world.getBlockState(hitResultOfCenter.getBlockPos()).isRedstoneConductor(world, hitResultOfCenter.getBlockPos()) &&
                        !hitResultOfBottomCenter.getBlockPos().equals(positionToCheck) &&
                        world.getBlockState(hitResultOfBottomCenter.getBlockPos()).isRedstoneConductor(world, hitResultOfBottomCenter.getBlockPos())
        ) {
            return false;
        }

        if (config.getBlockWhitelist().contains(blockBelow)) {
            return true;
        }

        if (!blockStateBelow.isRedstoneConductor(world, positionBelow)) {
            return false;
        }

        var collisionShapeBelow = blockStateBelow.getCollisionShape(world, positionBelow);
        if (!collisionShapeBelow.isEmpty()) {
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    Vec3 point = new Vec3(x, 1, z);
                    Optional<Vec3> pointClosest = collisionShapeBelow.closestPointTo(point);
                    if (pointClosest.isEmpty() || !pointClosest.get().equals(point)) {
                        return false;
                    }
                }
            }
            return true;
        }

        return false;
    }

    private static void updateRenderTargets(Level world, Player player, Optional<Frustum> frustum,  Font textRenderer, BlockPos playerPosition, Vec3 cameraPosition, boolean shouldShowBothValues) {
        blocksCached.clear();

        BlockPos.MutableBlockPos positionToRenderAt = new BlockPos.MutableBlockPos();
        int renderRangeHorizontal = config.render_distance.horizontal;
        int renderRangeVertical = config.render_distance.vertical;
        double maxSquaredDistance = renderRangeHorizontal * renderRangeHorizontal * 1.5;

        for (int dx = -renderRangeHorizontal; dx <= renderRangeHorizontal; dx++) {
            for (int dz = -renderRangeHorizontal; dz <= renderRangeHorizontal; dz++) {
                for (int dy = -renderRangeVertical; dy <= renderRangeVertical; dy++) {
                    positionToRenderAt.set(playerPosition.getX() + dx, playerPosition.getY() + dy,
                            playerPosition.getZ() + dz);

                    if (!world.isLoaded(positionToRenderAt)) {
                        continue;
                    }

                    if (positionToRenderAt.distSqr(playerPosition) > maxSquaredDistance) {
                        continue;
                    }

                    int blockLightLevel = world.getLightEngine().getRawBrightness(positionToRenderAt, 0);
                    if (config.text.hide_safe && isLightLevelSafe(world, blockLightLevel)) {
                        continue;
                    }

                    BlockState blockStateRenderAt = world.getBlockState(positionToRenderAt);
                    if (!shouldRenderLightLevel(world, player, frustum, cameraPosition, positionToRenderAt,
                            blockStateRenderAt)) {
                        continue;
                    }

                    int skyLightLevel = world.getBrightness(LightLayer.SKY, positionToRenderAt) - world.getSkyDarken();
                    int textColor = getTextColor(world, blockLightLevel, skyLightLevel);

                    var blockVisualShapeRenderAt = blockStateRenderAt.getShape(world, positionToRenderAt);
                    Optional<AABB> blockBoundingBoxRenderAt;
                    if (blockVisualShapeRenderAt.isEmpty()) {
                        blockBoundingBoxRenderAt = Optional.empty();
                    } else {
                        blockBoundingBoxRenderAt = Optional.of(blockVisualShapeRenderAt.bounds());
                    }

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

                    blocksCached.add(
                            new BlockCached(positionToRenderAt.immutable(), textToRender, textScale, textColor,
                                    textOffsetY));
                }
            }
        }
    }
}