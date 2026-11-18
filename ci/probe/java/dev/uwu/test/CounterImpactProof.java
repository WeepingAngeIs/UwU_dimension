package dev.uwu.test;

import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.RangedRelics;
import dev.uwu.dimension.RelicFlight;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Captures real server particles from twelve accepted impacts and the delayed FEINT damage path. */
final class CounterImpactProof {
    private static final String MARK = "uwu.counter_impact_art";
    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError("Counter art: " + message);
    }

    static void run(ServerLevel level, ServerPlayer owner) throws Exception {
        long time = level.getGameTime();
        Map<String, CounterArsenal.Entry> schools = new java.util.LinkedHashMap<>();
        for (var entry : CounterArsenal.ROSTER) schools.putIfAbsent(entry.school(), entry);
        require(schools.size() == 12, "school coverage changed");
        List<ClientboundLevelParticlesPacket> captured = new ArrayList<>();
        Map<String, String> signatures = new HashMap<>();
        Map<String, java.util.Set<String>> types = new HashMap<>();
        try (var observer = new ChestProof.Visitor(level, owner.blockPosition(), "ArtObserver", UUID.randomUUID())) {
            ServerPlayer viewer = observer.player;
            viewer.setPos(owner.getX(), owner.getY() + 2, owner.getZ()); viewer.setNoGravity(true);
            Connection wire = observer.channel.pipeline().get(Connection.class);
            viewer.connection = new ServerGamePacketListenerImpl(level.getServer(), wire, viewer,
                    CommonListenerCookie.createInitial(viewer.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) { if (packet instanceof ClientboundLevelParticlesPacket particles) captured.add(particles); }
                @Override public void send(Packet<?> packet, PacketSendListener callback) { send(packet); }
            };
            level.addNewPlayer(viewer);
            for (var entry : schools.values()) {
                captured.clear(); viewer.getPersistentData().putInt("uwu.art_budget_used", 0);
                Husk target = target(level, owner);
                try {
                    RelicFlight impact = impact(level, owner, target, entry);
                    if (entry.school().equals("FEINT")) {
                        require(!target.getPersistentData().contains(MARK) && captured.isEmpty(), "FEINT emitted an impact before its scheduled damage");
                        level.getServer().getWorldData().overworldData().setGameTime(time + 14);
                        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(target));
                    }
                    require(target.getPersistentData().getCompound(MARK).getString("school").equals(entry.school()), "accepted hit missed " + entry.school());
                    List<String> points = new ArrayList<>();
                    java.util.Set<String> emitted = new HashSet<>();
                    for (ClientboundLevelParticlesPacket packet : captured) {
                        String kind = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString();
                        emitted.add(kind);
                        points.add(kind + String.format(Locale.ROOT, "|%.3f,%.3f,%.3f", packet.getX() - target.getX(), packet.getY() - target.getY(), packet.getZ() - target.getZ()));
                    }
                    require(!points.isEmpty(), "no observed particle composition for " + entry.school());
                    points.sort(String::compareTo);
                    signatures.put(entry.school(), String.join("\n", points)); types.put(entry.school(), emitted);
                    CompoundTag mark = target.getPersistentData().getCompound(MARK);
                    long start = mark.getLong("start");
                    level.getServer().getWorldData().overworldData().setGameTime(start + 6);
                    captured.clear(); viewer.getPersistentData().putInt("uwu.art_budget_used", 0);
                    NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(target));
                    require(!captured.isEmpty(), "impact had no animated middle frame for " + entry.school());
                    level.getServer().getWorldData().overworldData().setGameTime(start + 12);
                    NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(target));
                    require(!target.getPersistentData().contains(MARK), "expired impact leaked for " + entry.school());
                    impact.discard();
                } finally { target.discard(); level.getServer().getWorldData().overworldData().setGameTime(time); }
            }
            require(new HashSet<>(signatures.values()).size() == 12, "two schools reused the same observed impact geometry");
            require(types.get("QUENCH").contains("uwudimension:water_bead") && types.get("CLEANSE").contains("uwudimension:rising_grass")
                    && types.get("SHATTER").contains("uwudimension:ice_shard") && types.get("BANISH").contains("uwudimension:wind_petal"),
                    "school-specific botanical/elemental sprites missing");
            // Cancel the actual delayed incoming damage; there must still be no FEINT impact cue.
            Husk denied = target(level, owner);
            java.util.function.Consumer<LivingIncomingDamageEvent> reject = event -> { if (event.getEntity() == denied) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(reject);
            try {
                captured.clear();
                impact(level, owner, denied, schools.get("FEINT"));
                level.getServer().getWorldData().overworldData().setGameTime(time + 14);
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(denied));
                require(!denied.getPersistentData().contains(MARK) && captured.isEmpty(), "rejected delayed FEINT damage emitted a successful impact");
            } finally { NeoForge.EVENT_BUS.unregister(reject); denied.discard(); }
            viewer.discard();
        } finally { level.getServer().getWorldData().overworldData().setGameTime(time); }
        System.out.println("COUNTER ART PASS 12 distinct accepted school packet geometries, animated middle frames, expiry, four original sprite types and deferred/rejected FEINT cues");
    }

    private static Husk target(ServerLevel level, ServerPlayer owner) {
        Husk target = EntityType.HUSK.create(level);
        target.setPos(owner.getX() + 2, owner.getY(), owner.getZ()); target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000); target.setHealth(2000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        require(level.addFreshEntity(target), "cannot add disposable impact target");
        return target;
    }

    private static RelicFlight impact(ServerLevel level, ServerPlayer owner, Husk target, CounterArsenal.Entry entry) throws Exception {
        RelicFlight flight = RangedRelics.FLIGHT.get().create(level);
        CompoundTag data = new CompoundTag(); data.putString("counter", entry.id());
        data.putInt("relic", dev.uwu.dimension.Arsenal.Relic.VESPER_SCYTHE.ordinal());
        flight.readAdditionalSaveData(data); flight.setOwner(owner);
        flight.setItem(new ItemStack(CounterArsenal.ITEMS.get(entry.id()).get()));
        flight.setPos(target.position().add(0, .8, -.5)); flight.setDeltaMovement(new Vec3(0, 0, .5));
        Method hit = RelicFlight.class.getDeclaredMethod("onHitEntity", EntityHitResult.class); hit.setAccessible(true);
        hit.invoke(flight, new EntityHitResult(target));
        return flight;
    }

    private CounterImpactProof() {}
}
