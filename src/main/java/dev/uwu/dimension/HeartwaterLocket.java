package dev.uwu.dimension;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class HeartwaterLocket extends Item {
    static final ResourceKey<Level> HEARTWATER = ResourceKey.create(Registries.DIMENSION, UwUDimension.id("heartwater"));

    HeartwaterLocket() { super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer player)) return InteractionResultHolder.sidedSuccess(stack, true);
        var server = player.server;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ServerLevel destination;
        Vec3 spot;
        float yaw;
        if (level.dimension().equals(HEARTWATER)) {
            ResourceLocation origin = ResourceLocation.tryParse(tag.getString("origin"));
            destination = origin == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, origin));
            if (destination == null || destination.dimension().equals(HEARTWATER)) {
                destination = server.overworld();
                spot = Vec3.atBottomCenterOf(destination.getSharedSpawnPos());
            } else {
                spot = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            }
            destination.getChunkAt(BlockPos.containing(spot));
            AABB body = player.getBoundingBox().move(spot.subtract(player.position()));
            if (!destination.noCollision(body)) {
                spot = Vec3.atBottomCenterOf(destination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(spot)));
            }
            yaw = tag.getFloat("yaw");
        } else {
            destination = server.getLevel(HEARTWATER);
            if (destination == null) return InteractionResultHolder.fail(stack);
            tag.putString("origin", level.dimension().location().toString());
            tag.putDouble("x", player.getX()); tag.putDouble("y", player.getY()); tag.putDouble("z", player.getZ());
            tag.putFloat("yaw", player.getYRot());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            spot = new Vec3(0.5, 81, 0.5);
            yaw = 0;
        }
        destination.getChunkAt(BlockPos.containing(spot));
        player.stopRiding();
        player.teleportTo(destination, spot.x, spot.y, spot.z, yaw, 0);
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.getCooldowns().addCooldown(this, 45);
        if (destination.dimension().equals(HEARTWATER))
            player.displayClientMessage(Component.literal("UwU Dimension  ·  Heartwater  ·  Where the blossoms slip behind the falls"), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.literal("Use to visit Heartwater. Use again to retrace your steps.").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
