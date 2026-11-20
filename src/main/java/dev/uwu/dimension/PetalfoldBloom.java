package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** One contact-directed scatter per completed block. Only visual cues live here. */
@EventBusSubscriber(modid = "uwudimension")
public final class PetalfoldBloom {
    private PetalfoldBloom() {}

    record Fleck(int minimumQuality, boolean heart, int delay, Vec3 position, Vec3 velocity,
                 Vec3 viewPosition, Vec3 viewVelocity) {}

    static List<Fleck> sample(long seed, PetalfoldImpact hit) {
        var random = new Random(seed);
        double contactRadius = Math.hypot(hit.hitX(), hit.hitY());
        double angle = Math.atan2(hit.hitY(), hit.hitX());
        // A centered impact has no preferred exit edge. Outer hits keep their measured side.
        // Near the rim, rotating the entire fan could place the next exit on top of the
        // initial spark. Keep its angular sector reserved as the impact approaches the lip.
        double interior = Math.clamp((.73 - contactRadius) / .15, 0, 1);
        double spread = contactRadius < .30 ? Math.PI : .025 + (1 - contactRadius) * .78 * interior;
        double root = angle + random.nextDouble(-spread, spread);
        double positiveStep = Math.toRadians(random.nextDouble(21, 29));
        double negativeStep = Math.toRadians(random.nextDouble(21, 29));
        int winding = random.nextBoolean() ? 1 : -1;
        double curl = Math.toRadians(random.nextDouble(-19, 19));
        // The first piece leaves the point of contact. The rest peel off distinct sections
        // of the curved lip. The first four slots already span the sweep at low quality.
        int[] stations = {0, -3, 2, -1, 4, -5, 1, -2, 3, -4};
        if (random.nextBoolean()) { stations[1] = 2; stations[2] = -3; }
        if (random.nextBoolean()) { stations[4] = -5; stations[5] = 4; }
        int[] phase = {0, 2, 4, 6, 1, 5, 1, 3, 4, 5};
        if (random.nextBoolean()) { phase[1] = 1; phase[6] = 2; }
        shuffle(phase, 1, 4, random);
        shuffle(phase, 4, 6, random);
        shuffle(phase, 6, 10, random);
        boolean firstHeart = random.nextBoolean();
        boolean backOnEven = random.nextBoolean();
        var result = new ArrayList<Fleck>(10);
        for (int i = 0; i < stations.length; i++) {
            int station = winding * stations[i];
            double theta = root + station * (station > 0 ? positiveStep : negativeStep);
            theta += Math.toRadians(random.nextDouble(-1.8, 1.8));
            double radius = random.nextDouble(.93, 1.01);
            double x = i == 0 ? hit.hitX() : Math.cos(theta) * radius;
            double y = i == 0 ? hit.hitY() : Math.sin(theta) * radius;
            boolean backLip = i != 0 && ((station % 2 == 0) == backOnEven);
            // The pink boss projects farther forward than the thin outer rim.
            double faceDepth = .034 + .097 * (1 - contactRadius * contactRadius);
            double depth = i == 0 ? faceDepth : backLip ? random.nextDouble(-.068, -.039) : random.nextDouble(.018, .045);
            double heading = theta + curl + Math.toRadians(random.nextDouble(-14, 14));
            double speed = random.nextDouble(.083, .152);
            double out = backLip ? -random.nextDouble(.025, .065) * (.65 + hit.grazing())
                                 : random.nextDouble(.030, .080);
            double dx = Math.cos(heading) * speed, dy = Math.sin(heading) * speed;
            boolean heart = i == 0 ? firstHeart : i == 1 ? !firstHeart : random.nextBoolean();
            result.add(new Fleck(i < 4 ? 0 : i < 6 ? 1 : 2, heart, phase[i],
                    hit.point(x, y, depth, false), hit.toss(dx, dy, out, false),
                    hit.point(x, y, depth, true), hit.toss(dx, dy, out, true)));
        }
        return List.copyOf(result);
    }

    private static void shuffle(int[] slots, int from, int to, Random random) {
        for (int i = to - 1; i > from; i--) {
            int j = random.nextInt(from, i + 1), swap = slots[i];
            slots[i] = slots[j];
            slots[j] = swap;
        }
    }

    private record Audience(ServerPlayer viewer, int quality, boolean wearer) {}
    private record Burst(LivingEntity blocker, Vec3 center, List<Fleck> flecks, List<Audience> audience) {
        void emit(ServerLevel level, int phase) {
            if (!blocker.isAlive() || blocker.level() != level) return;
            if (blocker instanceof ServerPlayer player
                    && level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
            for (var recipient : audience) {
                var viewer = recipient.viewer;
                if (viewer.level() != level || !viewer.isAlive()
                        || level.getServer().getPlayerList().getPlayer(viewer.getUUID()) != viewer
                        || !CupidEffects.inRange(viewer, viewer.distanceToSqr(center))) continue;
                for (var fleck : flecks) {
                    if (fleck.delay != phase || fleck.minimumQuality > recipient.quality) continue;
                    var at = fleck.position;
                    var v = fleck.velocity;
                    var type = PetalfoldParticleOptions.create(fleck.heart,
                            recipient.wearer ? fleck.viewPosition.subtract(at) : null,
                            recipient.wearer ? fleck.viewVelocity.subtract(v) : null);
                    level.sendParticles(viewer, type, false, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1);
                }
            }
        }
    }

    private record Wave(Burst burst, int phase) {}
    private static final Map<ServerLevel, NavigableMap<Long, List<Wave>>> PENDING = new HashMap<>();

    static void play(ServerLevel level, LivingEntity blocker, PetalfoldImpact impact) {
        var audience = new ArrayList<Audience>();
        for (var viewer : level.players()) {
            if (CupidEffects.inRange(viewer, viewer.distanceToSqr(impact.center()))) {
                int quality = Mth.clamp(ReachLife.preference(viewer, "sakura_effects", 2), 0, 2);
                audience.add(new Audience(viewer, quality, viewer == blocker));
            }
        }
        if (audience.isEmpty()) return;
        var burst = new Burst(blocker, impact.center(), sample(level.getRandom().nextLong(), impact), List.copyOf(audience));
        burst.emit(level, 0);
        // A bucket contains every block at that tick, including several by the same holder.
        var queue = PENDING.computeIfAbsent(level, ignored -> new TreeMap<>());
        for (int phase = 1; phase <= 6; phase++) {
            queue.computeIfAbsent(level.getGameTime() + phase, ignored -> new ArrayList<>()).add(new Wave(burst, phase));
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var queue = PENDING.get(level);
        if (queue == null) return;
        while (!queue.isEmpty() && queue.firstKey() <= level.getGameTime()) {
            for (var wave : queue.pollFirstEntry().getValue()) wave.burst.emit(level, wave.phase);
        }
        if (queue.isEmpty()) PENDING.remove(level);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }
}
