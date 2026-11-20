package dev.uwu.dimension;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One brief, purely cosmetic impact composition per recipient. It never schedules damage. */
final class CounterImpactArt {
    private static final String MARK = "uwu.counter_impact_art";
    private static final DustParticleOptions ROSE = color(1, .55F, .78F);
    private static final DustParticleOptions LILAC = color(.7F, .45F, 1);
    private static final DustParticleOptions MINT = color(.42F, .94F, .67F);
    private static final DustParticleOptions AQUA = color(.45F, .86F, 1);
    private static final DustParticleOptions GOLD = color(1, .82F, .4F);
    private static final DustParticleOptions IVORY = color(1, .96F, .84F);
    private static final DustParticleOptions EMBER = color(.9F, .35F, .23F);
    private static final DustParticleOptions SHADE = color(.32F, .24F, .44F);

    private static DustParticleOptions color(float red, float green, float blue) {
        return new DustParticleOptions(new org.joml.Vector3f(red, green, blue), .65F);
    }

    static void begin(ServerLevel level, LivingEntity target, CounterArsenal.Entry entry, Vec3 incoming) {
        CompoundTag mark = new CompoundTag();
        Vec3 forward = incoming.lengthSqr() > 1.0E-6 ? incoming.normalize() : target.getLookAngle().scale(-1);
        mark.putDouble("x", forward.x); mark.putDouble("y", forward.y); mark.putDouble("z", forward.z);
        mark.putString("school", entry.school()); mark.putString("theme", entry.theme());
        mark.putInt("seed", entry.id().hashCode());
        mark.putString("world", level.dimension().location().toString());
        mark.putLong("start", level.getGameTime());
        target.getPersistentData().put(MARK, mark);
        tick(level, target);
    }

    static void tick(ServerLevel level, LivingEntity target) {
        CompoundTag mark = target.getPersistentData().getCompound(MARK);
        if (mark.isEmpty()) return;
        long age = level.getGameTime() - mark.getLong("start");
        if (age < 0 || age >= 12 || !level.dimension().location().toString().equals(mark.getString("world"))) {
            target.getPersistentData().remove(MARK); return;
        }
        if ((age & 1) != 0 || mark.contains("drawn") && mark.getLong("drawn") == level.getGameTime()) return;
        mark.putLong("drawn", level.getGameTime());
        new Frame(level, target, mark, age / 12.0).draw(mark.getString("school"));
    }

    private static final class Frame {
        final ServerLevel level;
        final Vec3 origin, feet, forward, side, up;
        final double progress, scale, mirror;
        final ParticleOptions accent;
        int ordinal;

        Frame(ServerLevel level, LivingEntity target, CompoundTag mark, double progress) {
            this.level = level; this.progress = progress;
            forward = new Vec3(mark.getDouble("x"), mark.getDouble("y"), mark.getDouble("z"));
            Vec3 horizontal = forward.cross(new Vec3(0, 1, 0));
            if (horizontal.lengthSqr() < 1.0E-4) horizontal = new Vec3(1, 0, 0);
            Vec3 vertical = horizontal.normalize().cross(forward).normalize();
            double roll = ((mark.getInt("seed") >>> 3) % 17 - 8) * Math.PI / 180;
            side = horizontal.normalize().scale(Math.cos(roll)).add(vertical.scale(Math.sin(roll)));
            up = side.cross(forward).normalize();
            scale = Math.min(1.3, .7 + target.getBbWidth() * .3) * (.94 + (mark.getInt("seed") & 3) * .04);
            mirror = (mark.getInt("seed") & 4) == 0 ? 1 : -1;
            origin = target.position().add(0, Math.min(1.35, target.getBbHeight() * .5), 0);
            feet = target.position().add(0, .08, 0);
            accent = mark.getString("theme").equals("dusk") ? LILAC : ROSE;
        }

        Vec3 at(double horizontal, double vertical, double depth) {
            return origin.add(side.scale(horizontal * scale * mirror)).add(up.scale(vertical * scale)).add(forward.scale(depth * scale));
        }

        void point(ParticleOptions effect, Vec3 at) { BlossomArts.point(level, effect, at, ordinal++); }

        void stroke(ParticleOptions effect, Vec3 start, Vec3 end, int count) {
            for (int step = 0; step <= count; step++) point(effect, start.lerp(end, (double) step / count));
        }

        void draw(String school) {
            switch (school) {
                case "QUENCH" -> quench();
                case "CLEANSE" -> cleanse();
                case "ANCHOR" -> anchor();
                case "SEVER" -> sever();
                case "SHATTER" -> shatter();
                case "SILENCE" -> silence();
                case "REAP" -> reap();
                case "BANISH" -> banish();
                case "FEINT" -> feint();
                case "GUARD" -> guard();
                case "STAGGER" -> stagger();
                case "SPACE" -> space();
                default -> { }
            }
        }

        // Orange cinder streaks shorten and turn into falling aqua beads.
        void quench() {
            for (int spoke = -1; spoke <= 1; spoke++) {
                double x = spoke * .31, fall = progress * .7;
                stroke(progress < .34 ? EMBER : AQUA, at(x, .45 - fall, 0), at(x + .12, .7 - fall - progress * .2, 0), 3);
                point(ArtParticles.WATER.get(), at(x, .4 - fall, 0));
            }
        }

        // A pale leaf peels up and out from a small violet impurity.
        void cleanse() {
            point(SHADE, at(0, 0, 0));
            double shift = .5 * progress;
            for (int step = 0; step < 12; step++) {
                double t = step * Math.PI * 2 / 12;
                point(MINT, at(shift + .24 * Math.sin(t), shift + .42 * Math.cos(t), .1));
            }
            point(ArtParticles.GRASS.get(), at(shift, shift + .42, .1));
            point(MagicGlowParticles.FLOWER.get(), at(shift + .1, shift + .5, .1));
        }

        // A seed clasp closes at ankle level, with a single bright center.
        void anchor() {
            double gap = .42 * (1 - Math.min(1, progress * 2));
            for (int half : new int[]{-1, 1}) for (int step = 0; step <= 7; step++) {
                double angle = -.5 * Math.PI + step * Math.PI / 7;
                Vec3 at = feet.add(side.scale(half * (gap + .35 * Math.cos(angle)) * mirror))
                        .add(forward.scale(.35 * Math.sin(angle)));
                point(MINT, at);
            }
            point(ParticleTypes.END_ROD, feet.add(0, .16, 0));
        }

        // One luminous thread splits, and its two ends retreat from a growing gap.
        void sever() {
            double gap = .08 + progress * .65;
            for (int half : new int[]{-1, 1}) {
                stroke(accent, at(half * gap, 0, 0), at(half * .9, .03 * half, 0), 5);
                point(ParticleTypes.END_ROD, at(half * gap, 0, 0));
            }
            point(ParticleTypes.CHERRY_LEAVES, at(0, -.45 * progress, 0));
        }

        // The corners of a diamond prism fracture into four moving crystalline shards.
        void shatter() {
            for (int corner = 0; corner < 4; corner++) {
                double angle = corner * Math.PI / 2;
                double x = Math.cos(angle), y = Math.sin(angle);
                Vec3 end = at(x * (.32 + progress * .6), y * (.45 + progress * .45), 0);
                stroke(IVORY, at(x * .14, y * .2, 0), end, 3);
                point(ArtParticles.ICE.get(), end);
            }
        }

        // A quiet octagonal halo contracts to an empty center.
        void silence() {
            double radius = .85 * (1 - progress) + .05;
            for (int spoke = 0; spoke < 8; spoke++) {
                double angle = spoke * Math.PI / 4;
                double next = angle + Math.PI / 4;
                stroke(spoke % 2 == 0 ? SHADE : accent, at(Math.cos(angle) * radius, Math.sin(angle) * radius, 0),
                        at(Math.cos(next) * radius, Math.sin(next) * radius, 0), 1);
            }
            point(ArtParticles.WIND.get(), at(0, radius, 0));
        }

        // Three fading petals spiral towards the open notch of a broken ring.
        void reap() {
            for (int step = 2; step <= 13; step++) {
                double angle = step * Math.PI * 2 / 16;
                point(SHADE, at(Math.cos(angle) * .46, Math.sin(angle) * .46, 0));
            }
            for (int petal = 0; petal < 3; petal++) {
                double angle = petal * Math.PI * 2 / 3 + progress * 2;
                double radius = .95 * (1 - progress);
                point(MagicGlowParticles.FLOWER.get(), at(Math.cos(angle) * radius, Math.sin(angle) * radius, 0));
            }
            point(LILAC, at(.4, 0, 0));
        }

        // Four wisps are expelled away from the impact, with short inward-pointing tails.
        void banish() {
            double radius = .2 + progress * 1.1;
            for (int spoke = 0; spoke < 4; spoke++) {
                double angle = spoke * Math.PI / 2 + .25;
                double x = Math.cos(angle), y = Math.sin(angle);
                stroke(AQUA, at(x * (radius - .16), y * (radius - .16), .15), at(x * radius, y * radius, .15), 2);
                point(ArtParticles.WIND.get(), at(x * radius, y * radius, .15));
            }
        }

        // A thin second slash lags behind the accepted delayed strike, with no second damage event.
        void feint() {
            double sweep = -.6 + progress * 1.2;
            stroke(accent, at(sweep - .22, -.45, 0), at(sweep + .22, .45, 0), 6);
            stroke(LILAC, at(sweep - .43, -.32, -.1), at(sweep -.11, .32, -.1), 4);
        }

        // Two upright leaf plates close in front of the chest.
        void guard() {
            double gap = .6 * (1 - Math.min(1, progress * 1.7));
            for (int half : new int[]{-1, 1}) for (int step = 0; step < 10; step++) {
                double angle = step * Math.PI * 2 / 10;
                point(MINT, at(half * (gap + .2 * Math.sin(angle)), .52 * Math.cos(angle), -.12));
            }
            point(ArtParticles.GRASS.get(), at(-gap, 0, -.12));
            point(ArtParticles.GRASS.get(), at(gap, 0, -.12));
            if (progress > .5) point(IVORY, at(0, 0, -.15));
        }

        // Two heavy bars descend with staggered timing and strike the floor.
        void stagger() {
            for (int half : new int[]{-1, 1}) {
                double fall = Math.max(0, Math.min(1, progress * 2.4 - (half > 0 ? .35 : 0)));
                Vec3 bottom = feet.add(side.scale(half * .32)).add(0, .85 * (1 - fall), 0);
                stroke(GOLD, bottom, bottom.add(0, .55, 0), 6);
                if (fall == 1) point(ParticleTypes.CRIT, bottom);
            }
        }

        // A paired leading/trailing crescent opens in the actual displacement direction.
        void space() {
            for (int arc = 0; arc < 2; arc++) for (int step = -5; step <= 5; step++) {
                double angle = step * Math.PI / 12;
                double radius = .55 + arc * .2;
                double depth = .08 + progress * .7 + arc * .22;
                point(arc == 0 ? accent : IVORY, at(Math.sin(angle) * radius, Math.cos(angle) * radius - .35, depth));
            }
            point(ArtParticles.WIND.get(), at(0, .2, .35 + progress * .7));
        }
    }

    private CounterImpactArt() {}
}
