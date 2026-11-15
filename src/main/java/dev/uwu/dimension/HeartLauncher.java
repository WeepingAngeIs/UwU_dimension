package dev.uwu.dimension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The launcher and the bounded server-owned excavation queue. */
final class HeartLauncher {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "uwudimension");
    static final Supplier<Item> LAUNCHER = ITEMS.register("heart_launcher", LauncherItem::new);
    static final Supplier<Item> HEART = ITEMS.register("launcher_heart", () -> new Item(new Item.Properties()));
    static final Supplier<EntityType<HeartShot>> SHOT = ENTITIES.register("launcher_heart",
        () -> EntityType.Builder.<HeartShot>of(HeartShot::new, MobCategory.MISC)
            .sized(.25F, .25F).clientTrackingRange(12).updateInterval(1).build("uwudimension:launcher_heart"));
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue EXCAVATION;
    private static final ModConfigSpec.IntValue RADIUS;
    private static final Queue<BreakJob> PENDING = new ArrayDeque<>();
    static {
        var builder = new ModConfigSpec.Builder();
        EXCAVATION = builder.comment("Enable Heart Launcher terrain excavation. Claims and block break events still apply.")
            .define("terrainExcavation", false);
        RADIUS = builder.comment("Maximum radius of the launcher impact in blocks.").defineInRange("blastRadius", 3, 1, 4);
        SPEC = builder.build();
    }

    static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(LAUNCHER.get());
        });
        NeoForge.EVENT_BUS.addListener(HeartLauncher::tick);
    }

    static final class LauncherItem extends Item {
        LauncherItem() {
            super(new Item.Properties().durability(1024).rarity(Rarity.EPIC)
                .component(net.minecraft.core.component.DataComponents.TOOL,
                    Tiers.DIAMOND.createToolProperties(BlockTags.MINEABLE_WITH_PICKAXE)));
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack held = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(held);
            if (level instanceof ServerLevel server) {
                HeartShot projectile = SHOT.get().create(server);
                if (projectile == null) return InteractionResultHolder.fail(held);
                projectile.setOwner(player);
                projectile.setItem(new ItemStack(HEART.get()));
                projectile.setPos(player.getX(), player.getEyeY() - .12, player.getZ());
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 2.0F, 0);
                if (!server.addFreshEntity(projectile)) return InteractionResultHolder.fail(held);
                Vec3 recoil = player.getLookAngle().multiply(1, 0, 1).normalize().scale(-.18);
                player.push(recoil.x, 0, recoil.z);
                player.hurtMarked = true;
                held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                player.getCooldowns().addCooldown(this, 12);
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity user) {
            if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0)
                stack.hurtAndBreak(1, user, EquipmentSlot.MAINHAND);
            return true;
        }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tips, TooltipFlag flag) {
            tips.add(Component.literal("Fires a heart with a 12-tick recoil. Server owners can enable rock excavation."));
        }
    }

    public static final class HeartShot extends ThrowableItemProjectile {
        private double distance;
        HeartShot(EntityType<? extends HeartShot> type, Level level) { super(type, level); }
        @Override protected Item getDefaultItem() { return HEART.get(); }
        @Override public void tick() {
            Vec3 before = position();
            if (level() instanceof ServerLevel && distance + getDeltaMovement().length() > 96) {
                double remaining = 96 - distance;
                if (remaining <= 0) { discard(); return; }
                setDeltaMovement(getDeltaMovement().normalize().scale(remaining));
            }
            if (level() instanceof ServerLevel server && !server.hasChunkAt(BlockPos.containing(before.add(getDeltaMovement())))) {
                discard();
                return;
            }
            super.tick();
            distance += before.distanceTo(position());
            if (level() instanceof ServerLevel server && isAlive()) {
                if (distance >= 96 || tickCount > 160) { discard(); return; }
                if (tickCount % 4 == 0)
                    HeartCombatGlow.charmHearts(server, getX(), getY(), getZ(), 1, .05, .05, .05, 0);
            }
        }
        @Override protected void onHitEntity(EntityHitResult hit) {
            super.onHitEntity(hit);
            impact(hit.getLocation());
        }
        @Override protected void onHitBlock(BlockHitResult hit) {
            super.onHitBlock(hit);
            impact(hit.getLocation());
        }
        private void impact(Vec3 at) {
            if (!(level() instanceof ServerLevel level) || !isAlive()) return;
            if (getOwner() instanceof ServerPlayer owner) {
                HeartCombatGlow.charmHearts(level, at.x, at.y + .25, at.z, 16, .8, .25, .8, .01);
                int radius = SPEC.isLoaded() ? RADIUS.get() : 3;
                var bounds = new AABB(at, at).inflate(radius);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                        e -> e.position().distanceToSqr(at) <= radius * radius && RangedRelics.enemy(owner, e))) {
                    if (level.hasChunkAt(target.blockPosition()) && SakuraAccess.mayInteract(owner, target.blockPosition()))
                        target.hurt(owner.damageSources().playerAttack(owner), 8);
                }
                if (SPEC.isLoaded() && EXCAVATION.get()) queue(level, owner, BlockPos.containing(at), radius);
            }
            discard();
        }
        @Override public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            tag.putDouble("uwu_distance", distance);
        }
        @Override public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            distance = Math.max(0, tag.getDouble("uwu_distance"));
        }
    }

    private record BreakJob(ServerLevel level, UUID owner, ArrayDeque<BlockPos> positions) {}
    private static void queue(ServerLevel level, ServerPlayer owner, BlockPos center, int radius) {
        if (PENDING.size() >= 64) return;
        var candidates = new ArrayList<BlockPos>();
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (position.distSqr(center) <= radius * radius && level.hasChunkAt(position)
                    && level.getBlockState(position).is(BlockTags.MINEABLE_WITH_PICKAXE))
                candidates.add(position.immutable());
        }
        candidates.sort(Comparator.comparingDouble(position -> position.distSqr(center)));
        PENDING.add(new BreakJob(level, owner.getUUID(), new ArrayDeque<>(candidates.subList(0, Math.min(32, candidates.size())))));
    }
    private static void tick(ServerTickEvent.Post event) {
        int budget = 8;
        while (budget > 0 && !PENDING.isEmpty()) {
            BreakJob job = PENDING.peek();
            if (job.level().getServer() != event.getServer()) { PENDING.remove(); continue; }
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(job.owner());
            if (owner == null || owner.serverLevel() != job.level() || !owner.getMainHandItem().is(LAUNCHER.get())
                    || !SPEC.isLoaded() || !EXCAVATION.get()) { PENDING.remove(); continue; }
            BlockPos pos = job.positions().poll();
            if (pos == null) { PENDING.remove(); continue; }
            budget--;
            BlockState state = job.level().hasChunkAt(pos) ? job.level().getBlockState(pos) : null;
            if (state != null && state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.getDestroySpeed(job.level(), pos) >= 0
                    && SakuraAccess.mayInteract(owner, pos))
                owner.gameMode.destroyBlock(pos);
        }
    }
}

