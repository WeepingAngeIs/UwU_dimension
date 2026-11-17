package dev.uwu.test;

import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.GlitchBow;
import dev.uwu.dimension.HeartArrow;
import dev.uwu.dimension.RelicFlight;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Real item entry points, native ticks, accepted/rejected damage and custody in the hosted disposable arena. */
public final class BlossomArtsProof {
    private static ChestProof.Visitor visitor;
    private static ChestProof.Visitor glitchVisitor;
    private static ChestProof.Visitor leavingVisitor;
    private static ServerPlayer glitchOwner;
    private static ServerPlayer leavingOwner;
    private static long cleanupStarted;
    private static ServerPlayer owner;
    private static int phase;
    private static long ready, started;
    private static BlockPos floor;
    private static RelicFlight healing;
    private static UUID originalFlower;
    private static Husk rooted, frozen, windTarget, windBystander, enterWater;
    private static float windStart, bystanderStart;
    private static final List<Entity> actors = new ArrayList<>();
    private static final String FLOWER = "uwu.art_flower";

    private static void require(boolean yes, String message) {
        if (!yes) throw new AssertionError("Blossom Arts: " + message);
    }

    private static Object invoke(Class<?> ownerType, String name, Class<?>[] types, Object receiver, Object... args) throws Exception {
        Method method = ownerType.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }

    private static Class<?> arts() throws Exception { return Class.forName("dev.uwu.dimension.BlossomArts"); }

    private static Husk mob(ServerLevel level, double x, double z) {
        Husk mob = EntityType.HUSK.create(level);
        require(mob != null, "missing Husk type");
        mob.moveTo(x, floor.getY() + 1, z, 0, 0);
        mob.setNoAi(true); mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
        mob.setHealth(200);
        require(level.addFreshEntity(mob), "failed to add disposable target");
        actors.add(mob);
        return mob;
    }

    private static RelicFlight throwRelic(ServerLevel level, Arsenal.Relic relic) {
        ItemStack stack = new ItemStack(Arsenal.WEAPONS.get(relic).get());
        owner.getCooldowns().removeCooldown(stack.getItem());
        owner.getPersistentData().remove("uwu.scythe_flight");
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.use(level, owner, InteractionHand.MAIN_HAND);
        if (relic == Arsenal.Relic.VESPER_SCYTHE) {
            stack.releaseUsing(level, owner, stack.getUseDuration(owner) - 12);
            owner.stopUsingItem();
        }
        var flights = level.getEntitiesOfClass(RelicFlight.class, owner.getBoundingBox().inflate(5), f -> f.getOwner() == owner && !f.returning() && !f.landed());
        require(flights.size() == 1, "throw did not produce exactly one outbound " + relic + ": " + flights.size());
        RelicFlight flight = flights.getFirst();
        require(owner.getMainHandItem().isEmpty() && flight.physical(), "actual weapon remained in hand");
        actors.add(flight);
        return flight;
    }

    private static void impact(RelicFlight flight, Husk target) throws Exception {
        flight.setPos(target.position().add(0, .8, -.5));
        invoke(RelicFlight.class, "onHitEntity", new Class<?>[]{EntityHitResult.class}, flight, new EntityHitResult(target));
    }

    private static void land(ServerLevel level, RelicFlight flight, Vec3 spot) throws Exception {
        BlockHitResult hit = new BlockHitResult(spot, Direction.UP, BlockPos.containing(spot.add(0, -.01, 0)), false);
        invoke(RelicFlight.class, "onHitBlock", new Class<?>[]{BlockHitResult.class}, flight, hit);
        require(flight.landed(), "physical throw failed to embed");
    }

    public static boolean tick(ServerLevel level, boolean restart) throws Exception {
        if (restart) return restart(level);
        // These connected probe visitors lack PlayerList's normal packet-loop doTick call.
        for (ServerPlayer player : new ServerPlayer[]{owner, glitchOwner, leavingOwner})
            if (player != null && player.isAlive() && !player.isRemoved()) player.doTick();
        if (phase == 0) {
            for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) level.setChunkForced(x, z, true);
            ready = level.getGameTime() + 40; phase = 1; return false;
        }
        if (phase == 1) {
            if (level.getGameTime() < ready) return false;
            for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
                level.getChunk(x, z);
                if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x, z))) return false;
            }
            floor = new BlockPos(4, level.getMaxBuildHeight() - 24, 4);
            for (int x = 0; x < 28; x++) for (int z = 0; z < 20; z++)
                level.setBlockAndUpdate(new BlockPos(x, floor.getY(), z), Blocks.STONE.defaultBlockState());
            visitor = new ChestProof.Visitor(level, floor.above(), "PetalTester", UUID.randomUUID());
            owner = visitor.player; owner.setPos(4.5, floor.getY() + 1, 4.5); owner.setNoGravity(true);
            owner.getFoodData().setFoodLevel(6);
            level.addNewPlayer(owner);
            require(owner.getMaxHealth() == 20, "test owner max health changed");
            owner.getPersistentData().putInt("uwu.scythe_element", 3);
            canceledSpawn(level);
            healing = throwRelic(level, Arsenal.Relic.VESPER_SCYTHE);
            land(level, healing, owner.position().add(.35, 0, 0));
            var field = owner.getPersistentData().getCompound(FLOWER);
            require(field.hasUUID("cast") && healing.element() == 0, "rose at-feet landing failed to create field");
            originalFlower = field.getUUID("cast");
            long until = field.getLong("until");
            landAttempt(level, healing, owner.position());
            require(owner.getPersistentData().getCompound(FLOWER).getLong("until") == until, "duplicate landing extended field");
            // Keep the owner's heal subject near the field while other ability impacts run further away.
            rooted = mob(level, 8.5, 4.5);
            RelicFlight axe = throwRelic(level, Arsenal.Relic.BLOSSOM_GREATSWORD);
            impact(axe, rooted);
            require(rooted.getPersistentData().contains("uwu.art_root"), "accepted axe hit did not root");
            Husk immune = mob(level, 9.5, 4.5); immune.setInvulnerable(true);
            RelicFlight rejected = throwRelic(level, Arsenal.Relic.BLOSSOM_GREATSWORD);
            impact(rejected, immune);
            require(!immune.getPersistentData().contains("uwu.art_root"), "invulnerable target received roots");
            var blockedTag = healing.saveWithoutId(new CompoundTag());
            blockedTag.putInt("element", 2); blockedTag.putBoolean("physical_weapon", false); blockedTag.putBoolean("landed", false);
            RelicFlight blockedFire = dev.uwu.dimension.RangedRelics.FLIGHT.get().create(level);
            blockedFire.load(blockedTag); blockedFire.setUUID(UUID.randomUUID()); blockedFire.setOwner(owner);
            impact(blockedFire, immune);
            require(!immune.isOnFire() && !immune.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "rejected native hit still applied elemental control/fire");
            blockedFire.discard();
            frozen = mob(level, 10.5, 4.5);
            var ranged = Class.forName("dev.uwu.dimension.RangedRelics");
            require((boolean) invoke(ranged, "strike", new Class<?>[]{ServerLevel.class, net.minecraft.world.entity.player.Player.class,
                    net.minecraft.world.entity.LivingEntity.class, Arsenal.Relic.class, Vec3.class, Entity.class}, null,
                    level, owner, frozen, Arsenal.Relic.FROST_WHIP, owner.position(), null), "ice contact rejected");
            require(frozen.getPersistentData().contains("uwu.art_ice") && frozen.getTicksFrozen() > 0, "freeze cage lifecycle absent");
            Husk tide = mob(level, 11.5, 7.5);
            level.setWeatherParameters(6000, 0, false, false);
            RelicFlight trident = throwRelic(level, Arsenal.Relic.TIDELACE_TRIDENT);
            impact(trident, tide);
            require(tide.getHealth() <= 174.01F, "clear-weather Tidelace hit lacked folded lightning damage: " + tide.getHealth());
            require(level.getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, tide.getBoundingBox().inflate(8)).isEmpty()
                    && !tide.isOnFire(), "magical lightning created destructive vanilla lightning/fire");
            Husk entry = mob(level, 19.5, 5.5);
            ItemStack bow = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.RIPPLE_BOW).get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, bow);
            bow.use(level, owner, InteractionHand.MAIN_HAND); bow.releaseUsing(level, owner, 72000 - 20); owner.stopUsingItem();
            HeartArrow arrow = level.getEntitiesOfClass(HeartArrow.class, owner.getBoundingBox().inflate(4), a -> a.getOwner() == owner).getFirst();
            Vec3 puddleAt = new Vec3(19.5, floor.getY() + 1, 5.5);
            invoke(HeartArrow.class, "onHitBlock", new Class<?>[]{BlockHitResult.class}, arrow,
                    new BlockHitResult(puddleAt, Direction.UP, BlockPos.containing(puddleAt.add(0, -.01, 0)), false));
            require(owner.getPersistentData().contains("uwu.art_puddle"), "floor arrow missed water field");
            enterWater = mob(level, 25.5, 5.5);
            windTarget = mob(level, 15.5, 13.5); windBystander = mob(level, 16.5, 13.5);
            var entryDef = CounterArsenal.entry("counter_tempest");
            require(entryDef != null, "tempest exact ID missing");
            ItemStack wand = new ItemStack(CounterArsenal.ITEMS.get(entryDef.id()).get());
            owner.getCooldowns().removeCooldown(wand.getItem()); owner.setItemInHand(InteractionHand.MAIN_HAND, wand); wand.use(level, owner, InteractionHand.MAIN_HAND);
            RelicFlight wind = level.getEntitiesOfClass(RelicFlight.class, owner.getBoundingBox().inflate(5), f -> f.getOwner() == owner && !f.landed() && !f.returning()).getFirst();
            actors.add(wind); impact(wind, windTarget);
            require(owner.getPersistentData().contains("uwu.art_vortex"), "accepted Tempest hit missed vortex");
            windStart = windTarget.getHealth(); bystanderStart = windBystander.getHealth();
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            glitchVisitor = new ChestProof.Visitor(level, floor.above(), "GlitchPetals", UUID.randomUUID());
            glitchOwner = glitchVisitor.player; glitchOwner.setPos(4.5, floor.getY() + 1, 12.5); glitchOwner.setNoGravity(true);
            level.addNewPlayer(glitchOwner);
            ItemStack glitch = new ItemStack(dev.uwu.dimension.UwUDimension.GLITCH.get());
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, glitch,
                    data -> data.putInt("uwu_form", GlitchBow.FORMS.indexOf("vesper_scythe")));
            glitchOwner.getPersistentData().putInt("uwu.scythe_element", 3);
            glitchOwner.setItemInHand(InteractionHand.MAIN_HAND, glitch);
            glitch.use(level, glitchOwner, InteractionHand.MAIN_HAND); glitch.releaseUsing(level, glitchOwner, 72000 - 12); glitchOwner.stopUsingItem();
            var glitchFlight = level.getEntitiesOfClass(RelicFlight.class, glitchOwner.getBoundingBox().inflate(4), f -> f.getOwner() == glitchOwner).getFirst();
            actors.add(glitchFlight); land(level, glitchFlight, glitchOwner.position().add(.3, 0, 0));
            require(glitchOwner.getPersistentData().contains(FLOWER), "saved Glitch Vesper did not create healing flower");
            owner.setHealth(10); started = level.getGameTime(); phase = 2; return false;
        }
        long age = level.getGameTime() - started;
        if (age >= 10 && enterWater.getX() > 24) enterWater.setPos(20.5, floor.getY() + 1, 5.5);
        if (phase == 2 && age >= 22) {
            if (!healing.isRemoved() && age < 50) {
                if (age % 5 == 2) System.out.println("BLOSSOM CATCH WAIT age=" + age + " nativeTicks=" + healing.tickCount + " landed=" + healing.landed()
                        + " returning=" + healing.returning() + " flight=" + healing.position() + " owner=" + owner.position());
                return false;
            }
            require(healing.isRemoved(), "healing scythe failed bounded native embed/catch wait; nativeTicks=" + healing.tickCount
                    + " landed=" + healing.landed() + " returning=" + healing.returning() + " flight=" + healing.position() + " owner=" + owner.position());
            var scythe = Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get();
            ItemStack returned = ItemStack.EMPTY;
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = owner.getInventory().getItem(slot);
                if (candidate.is(scythe)) { returned = candidate; owner.getInventory().setItem(slot, ItemStack.EMPTY); break; }
            }
            require(!returned.isEmpty() && owner.getInventory().countItem(scythe) == 0, "return failed or duplicate inventory scythe");
            require(!owner.getCooldowns().isOnCooldown(scythe), "one follow-up cooldown was not opened at catch");
            ItemStack otherScythe = new ItemStack(scythe);
            owner.setItemInHand(InteractionHand.MAIN_HAND, otherScythe);
            otherScythe.use(level, owner, InteractionHand.MAIN_HAND); otherScythe.releaseUsing(level, owner, 72000 - 12); owner.stopUsingItem();
            require(owner.getMainHandItem() == otherScythe && !owner.getMainHandItem().isEmpty(), "unrelated stack spent another scythe's credit");
            owner.setItemInHand(InteractionHand.MAIN_HAND, returned);
            returned.use(level, owner, InteractionHand.MAIN_HAND); returned.releaseUsing(level, owner, 72000 - 12); owner.stopUsingItem();
            var second = level.getEntitiesOfClass(RelicFlight.class, owner.getBoundingBox().inflate(5), f -> f.getOwner() == owner && !f.landed() && !f.returning()).getFirst();
            actors.add(second);
            require(second.getPersistentData().getBoolean("uwu.flower_followup") && owner.getCooldowns().isOnCooldown(scythe), "single follow-up did not consume credit and restore normal cooldown");
            land(level, second, owner.position().add(.25, 0, 0));
            var field = owner.getPersistentData().getCompound(FLOWER);
            require(field.getUUID("cast").equals(originalFlower) && field.getBoolean("spent"), "follow-up recreated or extended field");
            require(enterWater.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "late entry did not receive bounded puddle slow");
            ItemStack caughtGlitch = glitchOwner.getMainHandItem();
            require(caughtGlitch.getItem() instanceof GlitchBow && GlitchBow.form(caughtGlitch).equals("vesper_scythe"), "Glitch rerolled before follow-up catch");
            require(!glitchOwner.getCooldowns().isOnCooldown(caughtGlitch.getItem()), "Glitch follow-up cooldown was not opened");
            caughtGlitch.use(level, glitchOwner, InteractionHand.MAIN_HAND);
            caughtGlitch.releaseUsing(level, glitchOwner, 72000 - 12); glitchOwner.stopUsingItem();
            var glitchFollowup = level.getEntitiesOfClass(RelicFlight.class, glitchOwner.getBoundingBox().inflate(4), f -> f.getOwner() == glitchOwner && !f.returning() && !f.landed()).getFirst();
            actors.add(glitchFollowup);
            require(glitchFollowup.getPersistentData().getBoolean("uwu.flower_followup") && glitchOwner.getCooldowns().isOnCooldown(caughtGlitch.getItem()), "Glitch pending-action exception lost or renewed the credit");
            land(level, glitchFollowup, glitchOwner.position().add(.3, 0, 0));
            phase = 3;
        }
        if (phase == 3 && age >= 86) {
            require(!owner.getPersistentData().contains(FLOWER) && !owner.getPersistentData().contains("uwu.art_puddle")
                    && !owner.getPersistentData().contains("uwu.art_vortex"), "expired field leaked");
            require(!rooted.getPersistentData().contains("uwu.art_root") && !frozen.getPersistentData().contains("uwu.art_ice"), "target geometry leaked past expiry");
            require(owner.getHealth() >= 13.9F && owner.getHealth() <= 14.1F, "field healed wrong total: " + owner.getHealth());
            require(windStart - windTarget.getHealth() >= 1.9F && windStart - windTarget.getHealth() <= 3.1F,
                    "wind primary target not hit two/three times: " + (windStart - windTarget.getHealth()));
            require(Math.abs(bystanderStart - windBystander.getHealth() - 3) < .1F,
                    "wind did not damage fresh target exactly three times: " + (bystanderStart - windBystander.getHealth()));
            require(level.getBlockState(floor).is(Blocks.STONE), "spell replaced terrain");
            leavingVisitor = new ChestProof.Visitor(level, floor.above(), "LeavingPetals", UUID.randomUUID());
            leavingOwner = leavingVisitor.player; leavingOwner.setPos(4.5, floor.getY() + 1, 16.5); leavingOwner.setNoGravity(true);
            level.addNewPlayer(leavingOwner);
            leavingOwner.getPersistentData().putInt("uwu.scythe_element", 3);
            ItemStack leavingScythe = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get());
            leavingOwner.setItemInHand(InteractionHand.MAIN_HAND, leavingScythe);
            leavingScythe.use(level, leavingOwner, InteractionHand.MAIN_HAND);
            leavingScythe.releaseUsing(level, leavingOwner, 72000 - 12); leavingOwner.stopUsingItem();
            var leavingFlight = level.getEntitiesOfClass(RelicFlight.class, leavingOwner.getBoundingBox().inflate(4), f -> f.getOwner() == leavingOwner).getFirst();
            actors.add(leavingFlight); land(level, leavingFlight, leavingOwner.position().add(.3, 0, 0));
            cleanupStarted = level.getGameTime(); phase = 4; return false;
        }
        if (phase == 4 && level.getGameTime() - cleanupStarted >= 22) {
            ItemStack authorized = leavingOwner.getMainHandItem();
            CompoundTag active = leavingOwner.getPersistentData().getCompound(FLOWER);
            require(active.getLong("until") > level.getGameTime() && active.getBoolean("caught") && !active.getBoolean("spent"),
                    "logout test did not have a live field and caught unspent credit");
            require((boolean) invoke(arts(), "flowerCredit", new Class<?>[]{net.minecraft.world.entity.player.Player.class, ItemStack.class}, null,
                    leavingOwner, authorized), "logout test had no usable credit");
            long gate = leavingOwner.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getLong("uwu.art_flower_throw_until");
            invoke(arts(), "logout", new Class<?>[]{net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class}, null,
                    new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(leavingOwner));
            require(!leavingOwner.getPersistentData().contains(FLOWER) && gate > level.getServer().overworld().getGameTime()
                    && leavingOwner.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getLong("uwu.art_flower_throw_until") == gate,
                    "active logout retained field or erased ordinary cooldown");
            require(!(boolean) invoke(arts(), "flowerCredit", new Class<?>[]{net.minecraft.world.entity.player.Player.class, ItemStack.class}, null,
                    leavingOwner, authorized) && !(boolean) invoke(arts(), "mayThrow", new Class<?>[]{net.minecraft.world.entity.player.Player.class, ItemStack.class}, null,
                    leavingOwner, authorized), "logout regenerated a same-stack early cast");
            // Snapshot a new, actually spent follow-up immediately before stopping this server.
            owner.getInventory().clearContent(); owner.getPersistentData().putInt("uwu.scythe_element", 3);
            healing = throwRelic(level, Arsenal.Relic.VESPER_SCYTHE);
            land(level, healing, owner.position().add(.3, 0, 0));
            cleanupStarted = level.getGameTime(); phase = 5; return false;
        }
        if (phase == 5 && level.getGameTime() - cleanupStarted >= 22) {
            ItemStack authorized = owner.getMainHandItem();
            require((boolean) invoke(arts(), "flowerCredit", new Class<?>[]{net.minecraft.world.entity.player.Player.class, ItemStack.class}, null,
                    owner, authorized), "restart setup did not reach a genuine caught credit");
            authorized.use(level, owner, InteractionHand.MAIN_HAND);
            authorized.releaseUsing(level, owner, 72000 - 12); owner.stopUsingItem();
            var savedFollowup = level.getEntitiesOfClass(RelicFlight.class, owner.getBoundingBox().inflate(4), f -> f.getOwner() == owner && !f.landed() && !f.returning()).getFirst();
            actors.add(savedFollowup); land(level, savedFollowup, owner.position().add(.3, 0, 0));
            require(owner.getPersistentData().getCompound(FLOWER).getBoolean("spent"), "restart fixture did not spend the real credit");
            // Re-open each now-expired special gate using its production admission path,
            // so the saved fixture contains live deadlines across the actual server restart.
            var names = java.util.List.of("roots", "vortex", "puddle");
            int[] durations = {100, 120, 80};
            for (int index = 0; index < names.size(); index++)
                require((boolean) invoke(arts(), "gate", new Class<?>[]{ServerLevel.class, net.minecraft.world.entity.player.Player.class, String.class, int.class}, null,
                        level, owner, names.get(index), durations[index]), "expired special gate did not reopen before save: " + names.get(index));
            net.minecraft.nbt.NbtIo.writeCompressed(owner.saveWithoutId(new CompoundTag()), java.nio.file.Path.of("polish-effects-owner.dat"));
            net.minecraft.nbt.NbtIo.writeCompressed(savedFollowup.saveWithoutId(new CompoundTag()), java.nio.file.Path.of("polish-effects-flight.dat"));
            for (String key : java.util.List.of("roots", "vortex", "puddle")) {
                var persisted = owner.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
                require(persisted.contains("uwu.art_gate_" + key), "special deadline missing from death-persistent state: " + key);
            }
            for (Entity actor : actors) actor.discard();
            leavingOwner.discard(); leavingVisitor.close();
            glitchOwner.discard(); glitchVisitor.close();
            owner.discard(); visitor.close();
            System.out.println("UWU BLOSSOM ARTS PASS real throw/embed/catch/single-credit, four heal pulses, roots, frost cage, clear-weather non-destructive lightning, water entry, timed wind, cleanup");
            return true;
        }
        require(age < 200, "effects fixture timed out");
        return false;
    }

    private static void landAttempt(ServerLevel level, RelicFlight flight, Vec3 at) throws Exception {
        invoke(RelicFlight.class, "onHitBlock", new Class<?>[]{BlockHitResult.class}, flight,
                new BlockHitResult(at, Direction.UP, BlockPos.containing(at.add(0, -.01, 0)), false));
    }

    private static void canceledSpawn(ServerLevel level) {
        ItemStack stack = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> reject = event -> {
            if (event.getEntity() instanceof RelicFlight) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(reject);
        try {
            stack.use(level, owner, InteractionHand.MAIN_HAND); stack.releaseUsing(level, owner, 72000 - 12); owner.stopUsingItem();
            require(owner.getMainHandItem() == stack && stack.getDamageValue() == 0, "canceled spawn consumed the owned item");
            require(owner.getPersistentData().getInt("uwu.scythe_element") == 3
                    && !owner.getPersistentData().contains("uwu.scythe_flight"), "canceled spawn cycled or locked the scythe");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(reject); }
    }

    private static boolean restart(ServerLevel level) throws Exception {
        CompoundTag state = net.minecraft.nbt.NbtIo.readCompressed(java.nio.file.Path.of("polish-effects-owner.dat"), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        CompoundTag flightState = net.minecraft.nbt.NbtIo.readCompressed(java.nio.file.Path.of("polish-effects-flight.dat"), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        try (var visitor = new ChestProof.Visitor(level, new BlockPos(4, level.getMaxBuildHeight() - 23, 4), "PetalTester", state.getUUID("UUID"))) {
            var player = visitor.player; player.load(state);
            CompoundTag field = player.getPersistentData().getCompound(FLOWER);
            require(field.getBoolean("spent") && field.getBoolean("caught"), "saved/restarted player regained follow-up credit");
            CompoundTag persisted = player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            require(persisted.getLong("uwu.art_flower_throw_until") > level.getServer().overworld().getGameTime(),
                    "restart snapshot's ordinary throw cooldown elapsed before this check");
            CompoundTag custody = persisted.getCompound("uwudimension.relic_returns");
            require(custody.contains(flightState.getUUID("UUID").toString()), "serialized actual follow-up has no custody entry");
            ItemStack stack = ItemStack.parseOptional(level.registryAccess(), custody.getCompound(flightState.getUUID("UUID").toString()).getCompound("Stack"));
            CompoundTag itemData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            require(!stack.isEmpty() && itemData.hasUUID("uwu.art_stack") && field.getUUID("stack").equals(itemData.getUUID("uwu.art_stack")),
                    "restart test did not use the original matching weapon identity");
            require(!(boolean) invoke(arts(), "flowerCredit", new Class<?>[]{net.minecraft.world.entity.player.Player.class, ItemStack.class}, null, player, stack),
                    "matching original weapon regained spent credit across restart");
            require(!(boolean) invoke(arts(), "mayThrow", new Class<?>[]{net.minecraft.world.entity.player.Player.class, ItemStack.class}, null, player, stack),
                    "saved owner cooldown failed to reject an early matching-weapon throw");
            // Death-persistent special gates continue to deny the same family after a clone/rejoin.
            for (String key : java.util.List.of("roots", "vortex", "puddle")) {
                require(persisted.getLong("uwu.art_gate_" + key) > level.getServer().overworld().getGameTime(),
                        "serialized special gate was not live after restart: " + key);
                require(!(boolean) invoke(arts(), "gate", new Class<?>[]{ServerLevel.class, net.minecraft.world.entity.player.Player.class, String.class, int.class}, null,
                        level, player, key, 100), "restored special gate opened early: " + key);
            }
            var otherLevel = level.getServer().overworld();
            try (var changed = new ChestProof.Visitor(otherLevel, new BlockPos(0, 300, 0), "PetalTester", state.getUUID("UUID"))) {
                var restored = changed.player; restored.load(state); restored.setPos(0, 300, 0); restored.tickCount = 20;
                var recoverType = Class.forName("dev.uwu.dimension.RelicReturns");
                invoke(recoverType, "recover", new Class<?>[]{net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class}, null,
                        new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(restored));
                var scythe = Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get();
                require(restored.getInventory().countItem(scythe) == 1, "changed-dimension recovery failed to deliver exactly one actual reserved weapon");
                var oldVisual = dev.uwu.dimension.RangedRelics.FLIGHT.get().create(level);
                oldVisual.load(flightState); oldVisual.setOwner(restored); oldVisual.tick();
                require(oldVisual.isRemoved() && restored.getInventory().countItem(scythe) == 1, "late saved projectile duplicated recovered custody");
            }
        }
        System.out.println("UWU BLOSSOM RESTART PASS matching original identity, consumed credit, owner cooldown, single reserved custody and stale visual");
        return true;
    }
}
