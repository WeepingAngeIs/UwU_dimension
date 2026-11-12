package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Emits full-bright pastel sparks and real short-lived block light for combat. */
final class HeartCombatGlow {
    private HeartCombatGlow() {}

    static void trail(ServerLevel level, Vec3 at, int element) {
        var sigil = element == 2 ? MagicGlowParticles.FLOWER.get() : MagicGlowParticles.HEART.get();
        level.sendParticles(sigil, at.x, at.y, at.z, 1, .035, .035, .035, .005);
        level.sendParticles(CupidParticles.PASTEL_HEART.get(), at.x, at.y, at.z, 1, .045, .045, .045, .008);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, .035, .035, .035, .01);
    }

    static void burst(ServerLevel level, Vec3 center, double radius, int element) {
        for (int i = 0; i < 18; i++) {
            double angle = Math.PI * 2.0 * i / 18.0;
            double distance = radius * (.62 + (i % 3) * .12);
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            level.sendParticles((i % 3 == 0 || element == 2) ? MagicGlowParticles.FLOWER.get()
                    : MagicGlowParticles.HEART.get(), x, center.y + .25 + (i % 2) * .22, z,
                    1, .015, .025, .015, .02);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, x, center.y + .25, z, 1, 0, .03, 0, .01);
        }

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
