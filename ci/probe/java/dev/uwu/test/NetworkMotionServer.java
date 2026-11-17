package dev.uwu.test;

import dev.uwu.dimension.HeartArrow;
import dev.uwu.dimension.RelicFlight;
import dev.uwu.dimension.RibbonLash;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Real mouse input, real tracked projectiles and target effects on a dedicated TCP server. */
final class NetworkMotionServer {
    private record Scene(String name, String item, String action, float pitch, double height, String effect) {}
    private static final Scene[] SCENES = {
        new Scene("chakram-wall", "counter_iron_katana", "use", 0, 113, ""),
        new Scene("wave-level", "vesper_scythe", "attack", 0, 113, ""),
        new Scene("wave-uphill", "vesper_scythe", "attack", -45, 113, ""),
        new Scene("wave-downhill", "vesper_scythe", "attack", 35, 121, ""),
        new Scene("wave-vertical", "vesper_scythe", "attack", -90, 113, ""),
        new Scene("root-impact", "blossom_greatsword", "use", 5, 113, "uwudimension:rising_grass"),
        new Scene("ice-cage", "frost_whip", "use", 5, 113, "uwudimension:ice_shard"),
        new Scene("wind-vortex", "counter_tempest", "use", 5, 113, "uwudimension:wind_petal"),
        new Scene("trident-lightning", "tidelace_trident", "use", 5, 113, "minecraft:electric_spark"),
        new Scene("ripple-impact", "ripple_bow", "draw", 20, 113, "uwudimension:water_bead")
    };
    private static final Path SYNC = Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR", "."));
    private static final ConcurrentHashMap<String,AtomicInteger> PARTICLES = new ConcurrentHashMap<>();
    private static final List<Entity> CASTS = new ArrayList<>();
    private static ServerPlayer archer, witness;
    private static Zombie target;
    private static boolean ready, issued, go, finished, emptyHand, returned, rooted, iced, vortex, puddle;
    private static int scene, elapsed, landedTicks;
    private static float beforeHealth;
    private static long started;
    private static Vec3 expectedDirection, firstFlight, lastFlight;
    private static double directionDot;
    private static UUID serial;
    private static String lastPhase;

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError("MOTION NETWORK scene=" + scene + " " + detail);
    }

    private static void task(String state, String phase) throws Exception {
        Scene current = SCENES[scene];
        Properties p = new Properties();
        p.setProperty("id", Integer.toString(scene));
        p.setProperty("name", current.name());
        p.setProperty("item", current.item());
        p.setProperty("action", current.action());
        p.setProperty("pitch", Float.toString(current.pitch()));
        p.setProperty("state", state);
        p.setProperty("phase", phase);
        p.setProperty("shooter", Integer.toString(archer.getId()));
        p.setProperty("target", Integer.toString(target == null ? -1 : target.getId()));
        p.setProperty("projectile", Integer.toString(CASTS.isEmpty() ? -1 : CASTS.getFirst().getId()));
        Path temp = SYNC.resolve("motion-task.tmp");
        try (var writer = Files.newBufferedWriter(temp)) { p.store(writer, "Actual input and live entity state"); }
        Files.move(temp, SYNC.resolve("motion-task.properties"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        lastPhase = phase;
    }

    private static void observeParticles() {
        var channel = witness.connection.getConnection().channel();
        channel.eventLoop().execute(() -> channel.pipeline().addBefore("packet_handler", "uwu_motion_particles", new ChannelDuplexHandler() {
            @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                if (message instanceof ClientboundLevelParticlesPacket packet) {
                    String name = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString();
                    PARTICLES.computeIfAbsent(name, unused -> new AtomicInteger()).incrementAndGet();
                }
                super.write(context, message, promise);
            }
        }));
    }

    private static void castJoined(EntityJoinLevelEvent event) {
        if (!go || event.getLevel().isClientSide || event.loadedFromDisk()) return;
        Entity entity = event.getEntity();
        boolean ours = entity instanceof RelicFlight flight && flight.getOwner() == archer
                || entity instanceof RibbonLash lash && lash.owner() == archer
                || entity instanceof HeartArrow arrow && arrow.getOwner() == archer;
        if (ours) CASTS.add(entity);
    }

    private static int countWeapon() {
        int result = 0;
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", SCENES[scene].item()));
        for (int i = 0; i < archer.getInventory().getContainerSize(); i++) {
            ItemStack stack = archer.getInventory().getItem(i);
            if (stack.is(item)) {
                require(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getUUID("motion_serial").equals(serial), "returned item lost its original metadata");
                result += stack.getCount();
            }
        }
        return result;
    }

    static void tick(MinecraftServer server) throws Exception {
        if (finished) return;
        if (!ready) {
            archer = server.getPlayerList().getPlayers().stream().filter(p -> p.getGameProfile().getName().equals("WishArcher")).findFirst().orElse(null);
            witness = server.getPlayerList().getPlayers().stream().filter(p -> p.getGameProfile().getName().equals("WishWitness")).findFirst().orElse(null);
            if (archer == null || witness == null) return;
            var level = server.overworld();
            server.getPlayerList().op(archer.getGameProfile());
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule doMobSpawning false");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule doDaylightCycle false");
            level.setDayTime(18000);
            for (int x = -18; x <= 18; x++) for (int z = -8; z <= 25; z++)
                level.setBlockAndUpdate(new BlockPos(x,112,z), Blocks.STONE.defaultBlockState());
            archer.setGameMode(GameType.SURVIVAL);
            archer.getAbilities().mayfly = true;
            archer.getAbilities().flying = true;
            archer.onUpdateAbilities();
            witness.setGameMode(GameType.SPECTATOR);
            observeParticles();
            NeoForge.EVENT_BUS.addListener(NetworkMotionServer::castJoined);
            started = System.nanoTime();
            ready = true;
            System.out.println("MOTION NETWORK READY dedicated TCP players and live projectile observer");
            return;
        }
        require(System.nanoTime() - started < 480_000_000_000L, "eight minute scene deadline");
        if (scene == SCENES.length) {
            Files.writeString(SYNC.resolve("motion-complete"), "passed " + SCENES.length);
            finished = true;
            System.out.println("MOTION NETWORK PASS 10 physical-input scenes, real spawned casts, tracked direction, embed/catch, target effects and witness particles");
            return;
        }
        Scene current = SCENES[scene];
        if (!issued) {
            for (Entity cast : CASTS) if (cast.isAlive()) cast.discard();
            CASTS.clear();
            PARTICLES.clear();
            if (target != null) { target.discard(); target = null; }
            for (int x = -2; x <= 2; x++) for (int y = 113; y <= 123; y++)
                levelBlock(server, x, y, 8, scene == 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            archer.stopUsingItem();
            archer.getInventory().clearContent();
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("uwudimension", current.item())).orElseThrow();
            archer.getCooldowns().removeCooldown(item);
            archer.getPersistentData().remove("uwu.scythe_flight");
            ItemStack stack = new ItemStack(item);
            serial = UUID.randomUUID();
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putUUID("motion_serial", serial));
            archer.setItemInHand(InteractionHand.MAIN_HAND, stack);
            archer.teleportTo(server.overworld(), .5, current.height(), .5, 0, current.pitch());
            archer.setDeltaMovement(Vec3.ZERO);
            archer.fallDistance = 0;
            witness.teleportTo(server.overworld(), 4.5, current.height() + 1, 3.5, 130, 0);
            if (!current.effect().isEmpty()) {
                target = EntityType.ZOMBIE.create(server.overworld());
                require(target != null, "target factory");
                target.setNoAi(true);
                target.setPersistenceRequired();
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                target.setHealth(1000);
                target.moveTo(.5, 113, 5.5, 180, 0);
                require(server.overworld().addFreshEntity(target), "target insertion");
                beforeHealth = target.getHealth();
            }
            archer.containerMenu.broadcastChanges();
            expectedDirection = Vec3.directionFromRotation(current.pitch(), 0);
            firstFlight = lastFlight = null;
            directionDot = -1;
            emptyHand = returned = rooted = iced = vortex = puddle = false;
            elapsed = landedTicks = 0;
            task("prepare", "ready");
            issued = true;
            go = false;
            return;
        }
        if (!go) {
            if (!Files.exists(SYNC.resolve("motion-ready-" + scene + "-archer"))
                    || !Files.exists(SYNC.resolve("motion-ready-" + scene + "-witness"))) return;
            task("go", "input");
            go = true;
            return;
        }
        elapsed++;
        String phase = "waiting";
        if (!CASTS.isEmpty()) {
            Entity cast = CASTS.getFirst();
            if (cast instanceof RelicFlight flight) {
                if (flight.wave() && flight.isAlive()) {
                    if (firstFlight == null) firstFlight = flight.position();
                    lastFlight = flight.position();
                    if (flight.getDeltaMovement().lengthSqr() > .01)
                        directionDot = flight.getDeltaMovement().normalize().dot(expectedDirection);
                    phase = "wave";
                } else if (flight.isAlive()) {
                    emptyHand |= archer.getMainHandItem().isEmpty();
                    if (flight.landed() && !flight.returning()) { landedTicks++; phase = "embedded"; }
                    else phase = flight.returning() ? "returning" : "outbound";
                } else {
                    returned |= countWeapon() == 1;
                    phase = "caught";
                }
            } else phase = cast.isAlive() ? "cast" : "impact";
        }
        if (target != null) {
            rooted |= target.getPersistentData().contains("uwu.art_root");
            iced |= target.getPersistentData().contains("uwu.art_ice");
            vortex |= archer.getPersistentData().contains("uwu.art_vortex");
            puddle |= archer.getPersistentData().contains("uwu.art_puddle");
            if (target.getHealth() < beforeHealth || puddle) phase = "effect";
        }
        if (!phase.equals(lastPhase)) task("go", phase);
        if (elapsed < 120) return;
        require(elapsed < 360, "client capture did not complete");
        if (!Files.exists(SYNC.resolve("motion-done-" + scene + "-archer"))
                || !Files.exists(SYNC.resolve("motion-done-" + scene + "-witness"))) return;
        require(CASTS.size() == 1, "expected one actual cast, got " + CASTS.size());
        require(countWeapon() == 1, "weapon lost or duplicated after cast");
        if (CASTS.getFirst() instanceof RelicFlight physical && physical.physical())
            require(!physical.isAlive(), "physical weapon was still in flight after the return window");
        if (scene == 0) {
            require(emptyHand && returned, "chakram did not leave and return to its hand");
            require(landedTicks >= 15 && landedTicks <= 17, "block embed did not hold 16 ticks, observed=" + landedTicks);
            require(CASTS.stream().noneMatch(Entity::isAlive), "chakram remained active after catch");
        }
        if (current.action().equals("attack")) {
            require(CASTS.getFirst() instanceof RelicFlight f && f.wave(), "left-click failed to produce actual scythe wave");
            require(directionDot > .98, "wave does not follow actual aim, dot=" + directionDot);
            require(firstFlight != null && lastFlight.subtract(firstFlight).lengthSqr() > 2, "wave never travelled");
        }
        if (target != null) {
            if (!current.action().equals("draw")) require(target.getHealth() < beforeHealth, "cast did not hit actual monster");
            require(PARTICLES.containsKey(current.effect()), "server did not send expected particle " + current.effect() + " got=" + PARTICLES.keySet());
            Properties received = new Properties();
            try (var reader = Files.newBufferedReader(SYNC.resolve("motion-client-" + scene + "-witness.properties"))) { received.load(reader); }
            require(Integer.parseInt(received.getProperty(current.effect(), "0")) > 0, "witness did not actually receive effect " + current.effect());
            require(scene != 5 || rooted, "root state never activated");
            require(scene != 6 || iced, "ice state never activated");
            require(scene != 7 || vortex, "vortex state never activated");
            require(scene != 9 || puddle, "water state never activated");
        }
        var drops = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(-20,110,-10,20,145,30),
                e -> e.getItem().is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", current.item()))));
        require(drops.isEmpty(), "cast produced an extra dropped weapon");
        Properties evidence = new Properties();
        evidence.setProperty("scene", current.name());
        evidence.setProperty("actualCasts", Integer.toString(CASTS.size()));
        evidence.setProperty("emptyHand", Boolean.toString(emptyHand));
        evidence.setProperty("returned", Boolean.toString(returned));
        evidence.setProperty("embedSamples", Integer.toString(landedTicks));
        evidence.setProperty("waveAimDot", Double.toString(directionDot));
        evidence.setProperty("health", target == null ? "n/a" : beforeHealth + " -> " + target.getHealth());
        evidence.setProperty("states", "root=" + rooted + ",ice=" + iced + ",vortex=" + vortex + ",water=" + puddle);
        PARTICLES.forEach((name,count) -> evidence.setProperty(name, count.toString()));
        try (var writer = Files.newBufferedWriter(SYNC.resolve("motion-evidence-" + scene + ".properties"))) { evidence.store(writer, "Server observations from actual client input"); }
        System.out.println("MOTION NETWORK SCENE PASS " + scene + " " + current.name() + " casts=" + CASTS.size() + " embed=" + landedTicks + " aimDot=" + directionDot + " particles=" + PARTICLES.keySet());
        scene++;
        issued = false;
        go = false;
    }

    private static void levelBlock(MinecraftServer server, int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
        server.overworld().setBlockAndUpdate(new BlockPos(x,y,z), state);
    }
}
