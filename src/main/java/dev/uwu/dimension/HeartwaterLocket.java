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
        if (level.dimension().equals(ReefLife.REACH) && player.isShiftKeyDown()) {
            BlockPos reef = ReefCharts.nearest(player.serverLevel(), player.blockPosition(), 0);
            player.displayClientMessage(Component.literal(reef==null ? "The locket cannot find reef waters nearby."
                    : "Blush Reef waters · X " + reef.getX() + "  Z " + reef.getZ()), false);
            player.getCooldowns().addCooldown(this,80);
            return InteractionResultHolder.success(stack);
        }
        if (level.dimension().equals(HEARTWATER) || level.dimension().equals(ReefLife.REACH)) {
            ResourceLocation origin = ResourceLocation.tryParse(tag.getString("origin"));
            destination = origin == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, origin));
            if (destination == null || destination.dimension().equals(HEARTWATER) || destination.dimension().equals(ReefLife.REACH)) {
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
            boolean legacy=player.isShiftKeyDown();
            destination = server.getLevel(legacy ? HEARTWATER : ReefLife.REACH);
            if (destination == null) return InteractionResultHolder.fail(stack);
            tag.putString("origin", level.dimension().location().toString());
            tag.putDouble("x", player.getX()); tag.putDouble("y", player.getY()); tag.putDouble("z", player.getZ());
            tag.putFloat("yaw", player.getYRot());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (legacy) {
                BlockPos oldGate=new BlockPos(0,81,0);
                destination.getChunkAt(oldGate);
                if(!destination.getBlockState(oldGate.below()).isSolid()
                        || !destination.getBlockState(oldGate).getCollisionShape(destination,oldGate).isEmpty()
                        || !destination.getBlockState(oldGate.above()).getCollisionShape(destination,oldGate.above()).isEmpty())
                    oldGate=destination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,oldGate);
                spot = Vec3.atBottomCenterOf(oldGate);
                yaw = 0;
            } else {
                spot = Vec3.atBottomCenterOf(GroveSites.arrival(destination));
                yaw = GroveSites.first(destination).turns()*90F;
            }
        }
        destination.getChunkAt(BlockPos.containing(spot));
        player.stopRiding();
        player.teleportTo(destination, spot.x, spot.y, spot.z, yaw, 0);
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.getCooldowns().addCooldown(this, 45);
        if (destination.dimension().equals(ReefLife.REACH)) {
            player.displayClientMessage(Component.literal("UwU Dimension  ·  Blossom Reach"), true);
            BlockPos plaza=BlossomVillage.first(destination,false).plaza();
            player.displayClientMessage(Component.literal("The Petal village bell is at X " + plaza.getX() + ", Z " + plaza.getZ() + "."),false);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.literal("Use to visit Blossom Reach, then use again to return.").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.literal("Sneak-use outside for old Heartwater, or inside the Reach for reef bearings.").withStyle(ChatFormatting.GRAY));
    }
}
