package dev.uwu.dimension;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Short-lived spell fields belong to their caster, use loaded cells, and never place terrain. */
@EventBusSubscriber(modid = "uwudimension")
public final class BlossomArts {
    private static final String FLOWER = "uwu.art_flower";
    private static final String PUDDLE = "uwu.art_puddle";
    private static final String VORTEX = "uwu.art_vortex";
    private static final String ROOT = "uwu.art_root";
    private static final String ICE = "uwu.art_ice";
    private static final String STACK = "uwu.art_stack";
    private static final String THROW_GATE = "uwu.art_flower_throw_until";
    private static final DustParticleOptions ROSE = dust(1, .54F, .75F);
    private static final DustParticleOptions MINT = dust(.4F, .95F, .62F);
    private static final DustParticleOptions AQUA = dust(.48F, .88F, 1);
    private static final DustParticleOptions IVORY = dust(1, .94F, .75F);
    private static final DustParticleOptions VIOLET = dust(.73F, .48F, 1);

    private BlossomArts() {}

    private static DustParticleOptions dust(float r, float g, float b) {
        return new DustParticleOptions(new org.joml.Vector3f(r, g, b), .65F);
    }

    static UUID identity(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.hasUUID(STACK)) return data.getUUID(STACK);
        UUID id = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(STACK, id));
        return id;
    }

    private static boolean sameStack(CompoundTag field, ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return field.hasUUID("stack") && data.hasUUID(STACK) && field.getUUID("stack").equals(data.getUUID(STACK));
    }

    private static boolean live(Player owner, CompoundTag field) {
        return owner.isAlive() && !field.isEmpty() && owner.level().dimension().location().toString().equals(field.getString("world"))
                && owner.level().getGameTime() < field.getLong("until");
    }

    static boolean pinFlower(Player owner, ItemStack stack) {
        CompoundTag field = owner.getPersistentData().getCompound(FLOWER);
        return live(owner, field) && sameStack(field, stack);
    }

    static boolean flowerCredit(Player owner, ItemStack stack) {
        if (owner.level().isClientSide) return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getLong("uwu.flower_credit_until") > owner.level().getGameTime();
        CompoundTag field = owner.getPersistentData().getCompound(FLOWER);
        return pinFlower(owner, stack) && !field.getBoolean("spent") && field.getBoolean("caught");
    }

    /** Called once the reserved stack is delivered; it never manufactures an extra scythe. */
    static void caught(Player owner, ItemStack stack) {
        CompoundTag field = owner.getPersistentData().getCompound(FLOWER);
        if (!live(owner, field) || !sameStack(field, stack) || field.getBoolean("spent") || field.getBoolean("caught")) return;
        field.putBoolean("caught", true);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putLong("uwu.flower_credit_until", field.getLong("until")));
        owner.getCooldowns().removeCooldown(stack.getItem());
        owner.getPersistentData().remove("uwu.scythe_flight");
    }

    /** Consume only after an actual projectile spawn succeeded. The following throw keeps its ordinary cooldown. */
    static void launched(Player owner, ItemStack stack, RelicFlight flight) {
        flight.getPersistentData().putLong("uwu.art_launched", owner.level().getGameTime());
        if (flight.relic() == Arsenal.Relic.VESPER_SCYTHE && flight.counter().isEmpty() && flight.physical())
            saved(owner).putLong(THROW_GATE, owner.level().getServer().overworld().getGameTime() + 60);
        if (!flowerCredit(owner, stack)) return;
        owner.getPersistentData().getCompound(FLOWER).putBoolean("spent", true);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.remove("uwu.flower_credit_until"));
        flight.getPersistentData().putBoolean("uwu.flower_followup", true);
    }

    static void flowerLanding(ServerLevel level, Player owner, RelicFlight flight, BlockHitResult hit) {
        if (!flight.physical() || flight.wave() || flight.relic() != Arsenal.Relic.VESPER_SCYTHE || !flight.counter().isEmpty()
                || flight.element() != 0 || flight.getPersistentData().getBoolean("uwu.flower_followup")
                || hit.getDirection() != Direction.UP || live(owner, owner.getPersistentData().getCompound(FLOWER))) return;
        Vec3 at = hit.getLocation();
        if (at.subtract(owner.position()).horizontalDistanceSqr() > 2.25 || Math.abs(at.y - owner.getY()) > 1
                || !canUse(level, owner, hit.getBlockPos()) || !level.getBlockState(hit.getBlockPos()).isFaceSturdy(level, hit.getBlockPos(), Direction.UP)) return;
        CompoundTag field = field(level, at, 80);
        field.putUUID("stack", identity(flight.getItem()));
        field.putUUID("cast", flight.getUUID());
        field.putLong("normalReady", flight.getPersistentData().getLong("uwu.art_launched") + 60);
        owner.getPersistentData().put(FLOWER, field);
        RelicReturns.deferGlitch(owner, flight, 80);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .7F, 1.5F);
    }

    private static CompoundTag field(ServerLevel level, Vec3 at, int duration) {
        CompoundTag field = new CompoundTag();
        field.putString("world", level.dimension().location().toString());
        field.putDouble("x", at.x); field.putDouble("y", at.y); field.putDouble("z", at.z);
        field.putLong("start", level.getGameTime()); field.putLong("until", level.getGameTime() + duration);
        return field;
    }

    private static CompoundTag saved(Player owner) {
        CompoundTag data = owner.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    static boolean mayThrow(Player owner, ItemStack stack) {
        return owner.level().isClientSide || !GlitchBow.isRelic(stack, Arsenal.Relic.VESPER_SCYTHE) || flowerCredit(owner, stack)
                || saved(owner).getLong(THROW_GATE) <= owner.level().getServer().overworld().getGameTime();
    }

    private static Vec3 center(CompoundTag field) {
        return new Vec3(field.getDouble("x"), field.getDouble("y"), field.getDouble("z"));
    }

    private static boolean canUse(ServerLevel level, Player owner, BlockPos pos) {
        return owner.level() == level && level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && (!(owner instanceof ServerPlayer player) || SakuraAccess.mayInteract(player, pos));
    }

    static boolean allowedTarget(ServerLevel level, Player owner, LivingEntity target) {
        return RangedRelics.enemy(owner, target) && canUse(level, owner, target.blockPosition());
    }

    private static boolean hostile(ServerLevel level, Player owner, LivingEntity target) {
        return (!(target instanceof Player) || level.getServer().isPvpAllowed())
                && (target instanceof Player || target.getType().getCategory() == MobCategory.MONSTER)
                && allowedTarget(level, owner, target) && owner.distanceToSqr(target) <= 4096
                && clearPath(level, owner.getEyePosition(), target.getEyePosition());
    }

    /** Damage is folded into the original accepted hit so native hurt resistance remains authoritative. */
    static float lightningBonus(Player owner, LivingEntity target, Arsenal.Relic relic, Entity source) {
        return relic == Arsenal.Relic.TIDELACE_TRIDENT && source instanceof RelicFlight flight && flight.physical()
                && !flight.wave() && (target.getType().getCategory() == MobCategory.MONSTER
                    || target instanceof Player && owner.level().getServer().isPvpAllowed())
                && allowedTarget((ServerLevel) owner.level(), owner, target) ? 3F : 0F;
    }

    static void hit(ServerLevel level, Player owner, LivingEntity target, Arsenal.Relic relic, Entity source) {
        if (!canUse(level, owner, target.blockPosition())) return;
        Vec3 at = target.position();
        switch (relic) {
            case BLOSSOM_GREATSWORD -> {
                if (source instanceof RelicFlight flight && flight.physical() && hostile(level, owner, target)
                        && gate(level, owner, "roots", 100)) {
                    CompoundTag root = field(level, at, 34); root.putUUID("owner", owner.getUUID());
                    target.getPersistentData().put(ROOT, root);
                    boolean boss = target.getMaxHealth() > 200;
                    GlitchCompletions.applyEffect(target, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, boss || target instanceof Player ? 2 : 10));
                }
            }
            case FROST_WHIP -> {
                if (target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) target.getPersistentData().put(ICE, field(level, at, 60));
            }
            case TIDELACE_TRIDENT -> {
                if (source instanceof RelicFlight flight && flight.physical() && (target instanceof Player || target.getType().getCategory() == MobCategory.MONSTER)) lightning(level, target);
            }
            case BELLHEART_AXE -> ring(level, at.add(0, .1, 0), 2, IVORY, 12);
            case LOCKKEEPER_AXE -> ring(level, at.add(0, .25, 0), .8, AQUA, 8);
            case ROSE_DAGGER -> point(level, MagicGlowParticles.FLOWER.get(), at.add(0, target.getBbHeight() * .7, 0), 0);
            default -> { }
        }
    }

    static void counterHit(ServerLevel level, Player owner, LivingEntity target, CounterArsenal.Entry entry) {
        if (entry == null || !allowedTarget(level, owner, target)) return;
        ParticleOptions tint = switch (entry.school()) {
            case "QUENCH", "CLEANSE" -> AQUA;
            case "ANCHOR", "GUARD" -> MINT;
            case "REAP", "BANISH", "SILENCE" -> VIOLET;
            default -> ROSE;
        };
        Vec3 at = target.position().add(0, target.getBbHeight() * .45, 0);
        // Each ward uses a different mark silhouette; these marks report an accepted weapon hit.
        if (List.of("ANCHOR", "STAGGER", "GUARD").contains(entry.school())) {
            for (int i = -1; i <= 1; i++) line(level, at.add(i * .22, -.3, 0), at.add(i * .22, .3, 0), tint, 5);
        } else if (List.of("SHATTER", "FEINT", "SEVER").contains(entry.school())) {
            line(level, at.add(-.4, -.25, 0), at.add(.4, .25, 0), tint, 8);
        } else ring(level, at, .5, tint, 10);
        if (entry.id().equals("counter_tempest") && hostile(level, owner, target) && gate(level, owner, "vortex", 120))
            owner.getPersistentData().put(VORTEX, field(level, target.position(), 40));
    }

    private static boolean gate(ServerLevel level, Player owner, String key, int ticks) {
        String name = "uwu.art_gate_" + key;
        long now = level.getServer().overworld().getGameTime();
        CompoundTag saved = saved(owner);
        // Migrate an already active beta13 deadline without opening a respawn/logout bypass.
        long prior = Math.max(saved.getLong(name), owner.getPersistentData().getLong(name));
        if (prior > saved.getLong(name)) saved.putLong(name, prior);
        owner.getPersistentData().remove(name);
        if (prior > now) return false;
        saved.putLong(name, now + ticks);
        return true;
    }

    static void waterImpact(ServerLevel level, Player owner, Vec3 impact) {
        BlockPos floor = BlockPos.containing(impact.add(0, -.08, 0));
        if (!canUse(level, owner, floor) || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                || live(owner, owner.getPersistentData().getCompound(PUDDLE)) || !gate(level, owner, "puddle", 80)) return;
        owner.getPersistentData().put(PUDDLE, field(level, impact, 40));
    }

    static void waterRecipient(ServerLevel level, LivingEntity target) {
        ring(level, target.position().add(0, .08, 0), Math.min(.75, target.getBbWidth()), ArtParticles.WATER.get(), 10);
    }

    @SubscribeEvent public static void ownerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        Player owner = event.getEntity();
        for (String key : List.of(FLOWER, PUDDLE, VORTEX)) {
            CompoundTag state = owner.getPersistentData().getCompound(key);
            if (state.isEmpty()) continue;
            if (!live(owner, state) || !canUse(level, owner, BlockPos.containing(center(state))) || owner.distanceToSqr(center(state)) > 4096) {
                owner.getPersistentData().remove(key); continue;
            }
            if (state.contains("lastTick") && state.getLong("lastTick") == level.getGameTime()) continue;
            state.putLong("lastTick", level.getGameTime());
            int age = (int) (level.getGameTime() - state.getLong("start"));
            if (key.equals(FLOWER)) flowerTick(level, owner, state, age);
            else if (key.equals(PUDDLE)) puddleTick(level, owner, state, age);
            else vortexTick(level, owner, state, age);
        }
    }

    private static boolean clearPath(ServerLevel level, Vec3 a, Vec3 b) {
        int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) * 2));
        for (int step = 0; step <= steps; step++)
            if (!level.hasChunkAt(BlockPos.containing(a.lerp(b, (double) step / steps)))) return false;
        return level.clip(new net.minecraft.world.level.ClipContext(a, b,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static void flowerTick(ServerLevel level, Player owner, CompoundTag field, int age) {
        Vec3 at = center(field);
        if (age % 4 == 0) {
            double unfold = Math.min(1, (age + 1) / 6.0) * Math.min(1, (field.getLong("until") - level.getGameTime()) / 10.0);
            for (int i = 0; i < 30; i++) {
                double angle = i * Math.PI * 2 / 30;
                double radius = unfold * (1.65 + .85 * Math.cos(5 * angle));
                Vec3 p = groundPoint(level, at.add(radius * Math.cos(angle), 0, radius * Math.sin(angle)), at.y);
                if (p != null) point(level, i % 6 == 0 ? MagicGlowParticles.FLOWER.get() : ROSE, p, i);
            }
            if (!field.getBoolean("spent")) point(level, MINT, at.add(0, .3, 0), 0);
        }
        if (field.getInt("pulses") >= 4 || level.getGameTime() < field.getLong("nextPulse")) return;
        field.putInt("pulses", field.getInt("pulses") + 1);
        field.putLong("nextPulse", level.getGameTime() + 20);
        int count = 0;
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.5, 1, 2.5));
        targets.sort(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(at)));
        for (LivingEntity target : targets) {
            boolean ally = target == owner || target instanceof TamableAnimal pet && pet.isOwnedBy(owner)
                    || target instanceof Player other && owner.isAlliedTo(other) && other.getTeam() != null;
            if (!ally || !target.isAlive() || target.getHealth() >= target.getMaxHealth()
                    || target.position().subtract(at).horizontalDistanceSqr() > 6.25 || Math.abs(target.getY() - at.y) > 1
                    || !canUse(level, owner, target.blockPosition()) || !clearPath(level, at.add(0, .3, 0), target.position().add(0, .3, 0))) continue;
            target.heal(1);
            point(level, MINT, target.position().add(0, .4, 0), 0);
            if (++count >= 6) break;
        }
    }

    private static Vec3 groundPoint(ServerLevel level, Vec3 query, double baseY) {
        BlockPos pos = BlockPos.containing(query.add(0, .25, 0));
        if (!level.hasChunkAt(pos)) return null;
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos cell = pos.offset(0, dy, 0);
            var shape = level.getBlockState(cell).getCollisionShape(level, cell);
            if (shape.isEmpty()) continue;
            double top = cell.getY() + shape.max(Direction.Axis.Y);
            if (Math.abs(top - baseY) <= .5) return new Vec3(query.x, top + .035, query.z);
        }
        return null;
    }

    private static void puddleTick(ServerLevel level, Player owner, CompoundTag field, int age) {
        Vec3 at = center(field);
        if (age % 4 == 0) {
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI * 2 / 24;
                Vec3 p = groundPoint(level, at.add(3 * Math.cos(angle), 0, 3 * Math.sin(angle)), at.y);
                if (p != null) point(level, i % 4 == 0 ? ArtParticles.WATER.get() : AQUA, p, i);
            }
            ring(level, at.add(0, .07, 0), (age % 12) / 4.0, ArtParticles.WATER.get(), 12);
        }
        ListTag touched = field.getList("touched", Tag.TAG_STRING);
        if (touched.size() >= 4) return;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3, 1, 3))) {
            if (!hostile(level, owner, target) || target.position().subtract(at).horizontalDistanceSqr() > 9
                    || Math.abs(target.getY() - at.y) > 1 || touched.contains(net.minecraft.nbt.StringTag.valueOf(target.getStringUUID()))
                    || !clearPath(level, at.add(0, .3, 0), target.position().add(0, .3, 0))) continue;
            touched.add(net.minecraft.nbt.StringTag.valueOf(target.getStringUUID()));
            GlitchCompletions.applyEffect(target, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            waterRecipient(level, target);
            if (touched.size() >= 4) break;
        }
        field.put("touched", touched);
    }

    private static void vortexTick(ServerLevel level, Player owner, CompoundTag field, int age) {
        Vec3 at = center(field);
        if (age < 6) { if (age % 2 == 0) ring(level, at.add(0, .05, 0), 2, IVORY, 18); return; }
        if (age % 3 == 0) for (int i = 0; i < 24; i++) {
            double height = (i % 8) / 7.0 * 2.2;
            double angle = age * .3 + i * Math.PI * 2 / 8;
            double radius = 1.7 - height * .45;
            point(level, i % 3 == 0 ? MagicGlowParticles.FLOWER.get() : i % 3 == 1 ? ArtParticles.WIND.get() : IVORY,
                    at.add(radius * Math.cos(angle), height, radius * Math.sin(angle)), i);
        }
        ListTag selected = field.getList("targets", Tag.TAG_INT_ARRAY);
        if (!field.getBoolean("selected")) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2, 1, 2))) {
                if (target.position().subtract(at).horizontalDistanceSqr() <= 4 && hostile(level, owner, target)
                        && target.getMaxHealth() <= 200 && clearPath(level, at.add(0, .4, 0), target.position().add(0, .4, 0))) {
                    selected.add(net.minecraft.nbt.NbtUtils.createUUID(target.getUUID()));
                    if (selected.size() >= 4) break;
                }
            }
            field.put("targets", selected); field.putBoolean("selected", true);
        }
        for (Tag id : selected) {
            if (!(level.getEntity(net.minecraft.nbt.NbtUtils.loadUUID(id)) instanceof LivingEntity target)
                    || !hostile(level, owner, target) || target.distanceToSqr(at) > 16 || target.isPassenger()
                    || !clearPath(level, at.add(0, .5, 0), target.position().add(0, .5, 0))) continue;
            double strength = 1 - Math.min(1, target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            if (age < 36) {
                Vec3 pull = at.subtract(target.position()).multiply(1, 0, 1).normalize().scale(.035 * strength);
                BlockPos destination = BlockPos.containing(target.position().add(pull.normalize().scale(.6)));
                if (level.hasChunkAt(destination) && level.getBlockState(destination.below()).isFaceSturdy(level, destination.below(), Direction.UP)
                        && !level.getBlockState(destination.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                        && !level.getBlockState(destination.below()).is(net.minecraft.world.level.block.Blocks.CACTUS)
                        && !level.getBlockState(destination.below()).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                        && !level.getBlockState(destination.below()).is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                        && level.getFluidState(destination).isEmpty() && level.getBlockState(destination).isAir()) {
                    target.push(pull.x, 0, pull.z); target.hurtMarked = true;
                }
            }
            if (age == 8 || age == 20 || age == 32) target.hurt(owner.damageSources().playerAttack(owner), 1);
            if (age == 36 && level.noCollision(target, target.getBoundingBox().move(0, 1, 0))) {
                target.push(0, .55 * strength, 0); target.hurtMarked = true;
            }
        }
    }

    @SubscribeEvent public static void targetTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level)) return;
        CompoundTag root = target.getPersistentData().getCompound(ROOT);
        if (!root.isEmpty()) {
            int age = (int) (level.getGameTime() - root.getLong("start"));
            Player owner = root.hasUUID("owner") ? level.getPlayerByUUID(root.getUUID("owner")) : null;
            if (!level.dimension().location().toString().equals(root.getString("world"))
                    || level.getGameTime() >= root.getLong("until") || owner == null || !hostile(level, owner, target)
                    || target.position().distanceToSqr(center(root)) > 4) target.getPersistentData().remove(ROOT);
            else {
                if (age < 28) GlitchCompletions.applyEffect(target, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2,
                        target.getMaxHealth() > 200 || target instanceof Player ? 2 : 10));
                if (target.tickCount % 3 == 0) {
                    double height = .55 * Math.min(1, (age + 1) / 6.0) * Math.min(1, (34 - age) / 6.0);
                    for (int i = 0; i < 9; i++) {
                        double angle = i * Math.PI * 2 / 9;
                        Vec3 at = target.position().add(Math.cos(angle) * .42, height * (i % 3) / 2, Math.sin(angle) * .42);
                        point(level, ArtParticles.GRASS.get(), at, i);
                    }
                }
            }
        }
        CompoundTag ice = target.getPersistentData().getCompound(ICE);
        if (ice.isEmpty()) return;
        int age = (int) (level.getGameTime() - ice.getLong("start"));
        if (!level.dimension().location().toString().equals(ice.getString("world")) || !target.isAlive()
                || level.getGameTime() >= ice.getLong("until") || !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            target.getPersistentData().remove(ICE); return;
        }
        if (target.tickCount % 4 == 0) for (int side = 0; side < 4; side++) {
            double a = side * Math.PI / 2 + Math.PI / 4;
            Vec3 bottom = target.position().add(Math.cos(a) * (target.getBbWidth() * .5 + .25), .05,
                    Math.sin(a) * (target.getBbWidth() * .5 + .25));
            line(level, bottom, bottom.add(0, Math.min(target.getBbHeight(), (age + 1) * .3), 0), ArtParticles.ICE.get(), 5);
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clearFields(event.getEntity()); }
    /** Vanilla cooldowns are per item type. Opening one stack's credit cannot open a second copy. */
    @SubscribeEvent public static void use(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        Player owner = event.getEntity();
        if (owner.level().isClientSide || !GlitchBow.isRelic(event.getItemStack(), Arsenal.Relic.VESPER_SCYTHE)) return;
        if (!mayThrow(owner, event.getItemStack())) event.setCanceled(true);
    }
    @SubscribeEvent public static void cloned(PlayerEvent.Clone event) { clearFields(event.getEntity()); clearFields(event.getOriginal()); }
    private static void clearFields(Player owner) {
        for (String key : List.of(FLOWER, PUDDLE, VORTEX)) owner.getPersistentData().remove(key);
    }

    private static void lightning(ServerLevel level, LivingEntity target) {
        Vec3 end = target.position().add(0, target.getBbHeight() * .65, 0);
        double top = end.y + 5;
        for (int y = 1; y <= 5; y++) {
            BlockPos p = BlockPos.containing(end.add(0, y, 0));
            if (!level.hasChunkAt(p) || !level.getBlockState(p).isAir()) { top = end.y + y - .2; break; }
        }
        Vec3 previous = new Vec3(end.x, top, end.z);
        for (int i = 1; i <= 6; i++) {
            double t = i / 6.0;
            Vec3 next = new Vec3(end.x + (i == 6 ? 0 : Math.sin(i * 2.7) * .32), top + (end.y - top) * t,
                    end.z + (i == 6 ? 0 : Math.cos(i * 2.1) * .24));
            line(level, previous, next, AQUA, 5);
            if (i == 2 || i == 4) line(level, next, next.add(.5, -.35, .2), IVORY, 4);
            previous = next;
        }
        point(level, ParticleTypes.ELECTRIC_SPARK, end, 0);
        level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, .35F, 1.7F);
    }

    private static void ring(ServerLevel level, Vec3 at, double radius, ParticleOptions effect, int points) {
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            point(level, effect, at.add(radius * Math.cos(angle), 0, radius * Math.sin(angle)), i);
        }
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions effect, int points) {
        for (int i = 0; i <= points; i++) point(level, effect, from.lerp(to, (double) i / points), i);
    }

    private static void point(ServerLevel level, ParticleOptions effect, Vec3 at, int ordinal) {
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(at) > 1024) continue;
            int quality = ReachLife.preference(viewer, "sakura_effects", 2);
            if (ordinal % (quality == 0 ? 4 : quality == 1 ? 2 : 1) != 0) continue;
            CompoundTag budget = viewer.getPersistentData();
            long tick = level.getServer().overworld().getGameTime();
            if (budget.getLong("uwu.art_budget_tick") != tick) {
                budget.putLong("uwu.art_budget_tick", tick); budget.putInt("uwu.art_budget_used", 0);
            }
            int used = budget.getInt("uwu.art_budget_used");
            if (used >= (quality == 0 ? 16 : quality == 1 ? 48 : 128)) continue;
            budget.putInt("uwu.art_budget_used", used + 1);
            level.sendParticles(viewer, effect, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
}
