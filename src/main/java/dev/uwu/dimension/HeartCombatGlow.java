package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Emits full-bright pastel sparks and real short-lived block light for combat. */
final class HeartCombatGlow {
    private HeartCombatGlow() {}

    static void trail(ServerLevel level, Vec3 at, int element) {
        trail(level, at, element, true);
    }

    static void trail(ServerLevel level, Vec3 at, int element, boolean pastelHeart) {
        var sigil = element == 2 ? MagicGlowParticles.FLOWER.get() : MagicGlowParticles.HEART.get();
        for (var viewer : level.players()) {
            if (viewer.distanceToSqr(at) > 32 * 32) continue;
            int mode = ReachLife.preference(viewer, "sakura_effects", 2);
            if (mode == 0 && level.getGameTime() % 4 != 0) continue;
            level.sendParticles(viewer, sigil, false, at.x, at.y, at.z, 1, .035, .035, .035, .005);
            if (pastelHeart)
                level.sendParticles(viewer, CupidParticles.PASTEL_HEART.get(), false,
                        at.x, at.y, at.z, 1, .045, .045, .045, .008);
            if (mode == 2) level.sendParticles(viewer, ParticleTypes.END_ROD, false,
                    at.x, at.y, at.z, 1, .035, .035, .035, .01);
        }
    }

    static void charmHearts(ServerLevel level, double x, double y, double z, int fullCount,
            double spreadX, double spreadY, double spreadZ, double speed) {
        for (var viewer : level.players()) {
            if (!CupidEffects.inRange(viewer, viewer.getEyePosition().distanceToSqr(x, y, z))) continue;
            int mode = ReachLife.preference(viewer, "sakura_effects", 2);
            int count = mode == 0 ? 1 : mode == 1 ? (fullCount + 1) / 2 : fullCount;
            level.sendParticles(viewer, CupidParticles.PASTEL_HEART.get(), false, x, y, z,
                    count, spreadX, spreadY, spreadZ, speed);
        }
    }

    static void burst(ServerLevel level, Vec3 center, double radius, int element) {
        for (var viewer : level.players()) {
            if (viewer.distanceToSqr(center) > 32 * 32) continue;
            int mode = ReachLife.preference(viewer, "sakura_effects", 2);
            int points = mode == 0 ? 3 : mode == 1 ? 8 : 18;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points;
                double distance = radius * (.62 + (i % 3) * .12);
                double x = center.x + Math.cos(angle) * distance;
                double z = center.z + Math.sin(angle) * distance;
                level.sendParticles(viewer, (i % 3 == 0 || element == 2) ? MagicGlowParticles.FLOWER.get()
                        : MagicGlowParticles.HEART.get(), false, x, center.y + .25 + (i % 2) * .22, z,
                        1, .015, .025, .015, .02);
                if (mode == 2 && i % 3 == 0) level.sendParticles(viewer, ParticleTypes.END_ROD,
                        false, x, center.y + .25, z, 1, 0, .03, 0, .01);
            }
        }

        illuminate(level,center,radius);
    }

    static void illuminate(ServerLevel level, Vec3 center, double radius) {
        placeLight(level, BlockPos.containing(center));
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            double angle = Math.PI * 2.0 * i / steps;
            placeLight(level, BlockPos.containing(center.x + Math.cos(angle) * radius * .58,
                    center.y + .35, center.z + Math.sin(angle) * radius * .58));
        }
    }

    private static void placeLight(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        var light = HeartLanterns.FLASH.get();
        var current = level.getBlockState(pos);
        if (current.is(light)) {
            level.setBlock(pos, light.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            level.scheduleTick(pos, light, 1);
        } else if (level.isEmptyBlock(pos)) {
            if (level.setBlock(pos, light.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL))
                level.scheduleTick(pos, light, 1);
        }
    }
}
