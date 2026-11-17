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
    private static ServerPlayer glitchOwner;
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
        if (phase == 0) {
            for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) level.setChunkForced(x, z, true);
            ready = level.getGameTime() + 40; phase = 1; return false;
        }
        if (phase == 1) {
            if (level.getGameTime() < ready) return false;
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
            require(healing.isRemoved(), "healing scythe failed to finish 16-tick embed and catch");
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
            CompoundTag serialized = owner.saveWithoutId(new CompoundTag());
            net.minecraft.nbt.NbtIo.writeCompressed(serialized, java.nio.file.Path.of("polish-effects-owner.dat"));
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
            // Logout invalidates field/credit, while the existing throw cooldown is not removed.
            invoke(arts(), "logout", new Class<?>[]{net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class}, null,
                    new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(owner));
            for (Entity actor : actors) actor.discard();
            glitchOwner.discard(); glitchVisitor.close();
            owner.discard(); visitor.close();
            System.out.println("UWU BLOSSOM ARTS PASS real throw/embed/catch/single-credit, four heal pulses, roots, frost cage, clear-weather non-destructive lightning, water entry, timed wind, cleanup");
            return true;
        }
        require(age < 160, "effects fixture timed out");
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
        try (var visitor = new ChestProof.Visitor(level, new BlockPos(4, level.getMaxBuildHeight() - 23, 4), "PetalTester", state.getUUID("UUID"))) {
            var player = visitor.player; player.load(state);
            CompoundTag field = player.getPersistentData().getCompound(FLOWER);
            require(field.getBoolean("spent"), "saved/restarted player regained follow-up credit");
            ItemStack stack = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get());
            require(!(boolean) invoke(arts(), "flowerCredit", new Class<?>[]{net.minecraft.world.entity.player.Player.class, ItemStack.class}, null, player, stack),
                    "new stack or restart bypassed spent credit");
        }
        System.out.println("UWU BLOSSOM RESTART PASS consumed credit survives player serialization");
        return true;
    }
}
