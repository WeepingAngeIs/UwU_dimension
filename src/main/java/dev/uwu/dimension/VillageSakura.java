package dev.uwu.dimension;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A solid landmark reserved on untouched land near each suitable settlement. */
final class VillageSakura {
    private static final int RADIUS = 17;
    private record Key(int gx, int gz, boolean dusk) {}
    private record Plot(int x, int y, int z, int trunk) {}
    private static final Map<ServerLevel, Map<Key, Optional<Plot>>> PLANS = new WeakHashMap<>();

    private static synchronized Map<Key, Optional<Plot>> cache(ServerLevel level) {
        return PLANS.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>());
    }

    static void place(HeartwaterFeature.Valley valley, BlossomVillage.Plan village) {
        ServerLevel level = valley.level.getLevel();
        var cache = cache(level);
        if (cache.size() > 4096) cache.clear();
        Key key = new Key(village.site().gx(), village.site().gz(), village.dusk());
        cache.computeIfAbsent(key, ignored -> survey(level, village)).ifPresent(plot -> build(valley, plot));
    }

    private static Optional<Plot> survey(ServerLevel level, BlossomVillage.Plan village) {
        var site = village.site();
        var house = MoonveilTreehouse.plan(level, village);
        for (int distance : new int[] {62, 75, 88}) {
            for (int lane : new int[] {0, 18, -18, 34, -34}) {
                for (int direction = 0; direction < 4; direction++) {
                    int x = village.x() + (direction < 2 ? lane : direction == 2 ? distance : -distance);
                    int z = village.z() + (direction < 2 ? (direction == 0 ? -distance : distance) : lane);
                    if (Math.abs(x) > 185 || z < -180 || z > 10) continue;
                    if (house.isPresent() && Math.abs(x - house.get().x()) <= RADIUS + MoonveilTreehouse.FOOTPRINT
                            && Math.abs(z - house.get().z()) <= RADIUS + MoonveilTreehouse.FOOTPRINT) continue;
                    boolean occupied = Math.abs(x - village.x()) <= RADIUS + 35
                            && Math.abs(z - village.z()) <= RADIUS + 8;
                    for (var h : village.houses()) {
                        if (Math.abs(x - h.x()) <= RADIUS + h.width() + 3
                                && Math.abs(z - h.z()) <= RADIUS + h.depth() + 3) occupied = true;
                    }
                    if (occupied) continue;
                    int wx = site.wx(x,z), wz = site.wz(x,z);
                    int y = GroveSites.height(level,wx,wz);
                    if (y <= 64 || y + 40 >= level.getMaxBuildHeight()) continue;
                    if (!level.getWorldBorder().isWithinBounds(new BlockPos(wx-RADIUS,y,wz-RADIUS))
                            || !level.getWorldBorder().isWithinBounds(new BlockPos(wx+RADIUS,y,wz+RADIUS))
                            || !SettlementLayout.untouchedForVillageSakura(level,wx-RADIUS,wz-RADIUS,wx+RADIUS,wz+RADIUS)) continue;
                    boolean suitable = true;
                    for (int dx = -6; dx <= 6; dx += 3) for (int dz = -6; dz <= 6; dz += 3) {
                        int h = GroveSites.height(level,wx+dx,wz+dz);
                        if (h <= 63 || Math.abs(h-y) > 2 || !GroveSites.cherry(level,wx+dx,h+1,wz+dz)) suitable = false;
                    }
                    if (suitable) {
                        int height = 20 + Math.floorMod(HeartwaterFeature.Valley.mix(wx,wz,level.getSeed()),6);
                        return Optional.of(new Plot(x,y,z,height));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static void build(HeartwaterFeature.Valley v, Plot p) {
        if (!v.overlaps(p.x-RADIUS,p.z-RADIUS,p.x+RADIUS,p.z+RADIUS)) return;
        BlockState log = Blocks.CHERRY_LOG.defaultBlockState();
        BlockState leaf = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
        for (int y=1; y<=p.trunk; y++) {
            int radius = y<=4 ? 2 : 1;
            for (int x=-radius; x<=radius; x++) for (int z=-radius; z<=radius; z++) {
                if (Math.abs(x)+Math.abs(z) <= radius+1) put(v,p,x,y,z,log);
            }
        }
        // Follow small surveyed dips without grading or replacing the surrounding ground.
        for (int x=-2; x<=2; x++) for (int z=-2; z<=2; z++) {
            if (Math.abs(x)+Math.abs(z)>3) continue;
            int ground=GroveSites.height(v.level.getLevel(),v.wx(p.x+x,p.z+z),v.wz(p.x+x,p.z+z));
            for (int y=ground-p.y+1; y<1; y++) put(v,p,x,y,z,log);
        }
        int[][] boughs = {{-10,3,-3},{8,5,-8},{11,2,4},{-6,6,10},{2,8,2}};
        for (int[] branch : boughs) {
            int steps=Math.max(Math.abs(branch[0]),Math.abs(branch[2]));
            for (int i=0; i<=steps; i++) {
                int x=Math.round(branch[0]*i/(float)steps);
                int y=p.trunk+Math.round(branch[1]*i/(float)steps);
                int z=Math.round(branch[2]*i/(float)steps);
                Direction.Axis axis=Math.abs(branch[0])>=Math.abs(branch[2])?Direction.Axis.X:Direction.Axis.Z;
                put(v,p,x,y,z,log.setValue(RotatedPillarBlock.AXIS,v.face(axis==Direction.Axis.X?Direction.EAST:Direction.SOUTH).getAxis()));
            }
            int bx=branch[0], by=p.trunk+branch[1]+2, bz=branch[2];
            for (int x=-5;x<=5;x++) for (int z=-5;z<=5;z++) for (int y=-2;y<=3;y++) {
                if (x*x+z*z+3*y*y>29) continue;
                if (by+y<=p.trunk) continue;
                if ((HeartwaterFeature.Valley.mix(p.x+bx+x,p.z+bz+z,v.seed+y)&15)==0) continue;
                put(v,p,bx+x,by+y,bz+z,leaf);
            }
        }
    }

    private static void put(HeartwaterFeature.Valley v, Plot p, int dx, int dy, int dz, BlockState state) {
        int x=p.x+dx,z=p.z+dz;
        if (!v.inside(x,z)) return;
        BlockPos pos=new BlockPos(v.wx(x,z),p.y+dy,v.wz(x,z));
        BlockState existing=v.chunk.getBlockState(pos);
        if (existing.hasBlockEntity() || !existing.getFluidState().isEmpty()) return;
        if (existing.isAir() || existing.is(BlockTags.LEAVES) || existing.is(Blocks.SHORT_GRASS)
                || existing.is(Blocks.TALL_GRASS) || existing.is(Blocks.PINK_PETALS)) v.put(x,p.y+dy,z,state);
    }
}

