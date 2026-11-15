package dev.uwu.dimension;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class HeartwaterLocket extends Item {
    static final String ANCHOR = "uwudimension_return";
    HeartwaterLocket() { super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer player)) return InteractionResultHolder.sidedSuccess(stack, true);
        if (ReefLife.isReach(level) && player.isShiftKeyDown()) {
            BlockPos reef = ReefCharts.nearest(player.serverLevel(), player.blockPosition(), 0);
            player.displayClientMessage(Component.literal(reef == null ? "The locket cannot find reef waters nearby."
                    : "Blush Reef waters · X " + reef.getX() + "  Z " + reef.getZ()), false);
            player.getCooldowns().addCooldown(this, 80);
            return InteractionResultHolder.success(stack);
        }
        boolean entering = !ReefLife.isReach(level);
        var saved = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag anchor = saved.getCompound(ANCHOR);
        ResourceLocation key = ResourceLocation.tryParse(anchor.getString("dimension"));
        ServerLevel destination = entering ? player.server.getLevel(ReefLife.REACH) :
                key == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        boolean spawnFallback = !entering && (destination == null || ReefLife.isReach(destination) || !anchor.contains("feet"));
        if (spawnFallback) destination = player.server.overworld();
        if (destination == null) return fail(player, stack);

        GroveSites.Site site = entering ? ReachArrival.forTravel(destination) : null;
        BlockPos requested = entering ? (site.usable() ? site.pos(0,site.entryY()+1,site.lip()-9) : destination.getSharedSpawnPos())
                : spawnFallback ? destination.getSharedSpawnPos() : BlockPos.of(anchor.getLong("feet"));
        if (entering && site.usable() && ReachJournal.chapter(player) == 0) {
            requested = BlossomVillage.survey(destination, site, false).map(BlossomVillage.Plan::plaza).orElse(requested);
        }
        Optional<BlockPos> found = LocketLanding.nearby(destination, player, requested);
        boolean sheltered = spawnFallback || (entering && !site.usable());
        if (found.isEmpty()) {
            found = LocketLanding.surface(destination, player, requested);
            sheltered = true;
        }
        if (found.isEmpty() && !entering) {
            destination = player.server.overworld();
            BlockPos spawn = destination.getSharedSpawnPos();
            found = LocketLanding.nearby(destination, player, spawn);
            if (found.isEmpty()) found = LocketLanding.surface(destination, player, spawn);
            sheltered = true;
        }
        if (found.isEmpty()) return fail(player, stack);

        CompoundTag departure = new CompoundTag();
        if (entering) {
            departure.putString("dimension", level.dimension().location().toString());
            departure.putLong("feet", player.blockPosition().asLong());
            departure.putFloat("yaw", player.getYRot());
        }
        Vec3 land = Vec3.atBottomCenterOf(found.get());
        player.stopRiding();
        player.teleportTo(destination, land.x, land.y, land.z, entering ? site.turns()*90F : anchor.getFloat("yaw"), 0);
        if (player.serverLevel() != destination) return fail(player, stack);
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        if (sheltered) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 3, false, false));
            player.sendSystemMessage(Component.literal("The locket found sheltered footing. Its ward lasts five seconds."));
        }
        if (entering) saved.put(ANCHOR, departure);
        else saved.remove(ANCHOR);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, saved);
        player.getCooldowns().addCooldown(this, 45);
        if (entering) {
            player.displayClientMessage(Component.literal("UwU Dimension · Sakura Reach"), true);
            if (site.usable()) BlossomVillage.survey(destination, site, false).ifPresent(v -> player.sendSystemMessage(
                    Component.literal("Visit the Petal village bell at X " + v.plaza().getX() + ", Z " + v.plaza().getZ() + ".")));
            ReachJournal.arrive(player);
        }
        return InteractionResultHolder.success(stack);
    }

    private static InteractionResultHolder<ItemStack> fail(ServerPlayer player, ItemStack stack) {
        player.displayClientMessage(Component.literal("The locket found no safe landing. Try another place or contact a keeper."), false);
        return InteractionResultHolder.fail(stack);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.literal("Visit Sakura Reach, then return to your own departure point.").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.literal("Sneak-use inside for reef bearings.").withStyle(ChatFormatting.GRAY));
    }
}
