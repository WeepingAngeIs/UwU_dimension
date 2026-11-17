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
                    // Support the entire 5x5 authored trunk base on the same surface;
                    // do not build independent pillars down a sloping hillside.
                    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
                        if(GroveSites.height(level,site.wx(x+dx,z+dz),site.wz(x+dx,z+dz))!=y)suitable=false;
                    if (suitable) {
                        int height = 38;
                        return Optional.of(new Plot(x,y,z,height));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static void build(HeartwaterFeature.Valley v, Plot p) {
        if (!v.overlaps(p.x-RADIUS,p.z-RADIUS,p.x+RADIUS,p.z+RADIUS)) return;
        if(SakuraForest.target(v.level.getLevel(),v.wx(p.x,p.z),v.wz(p.x,p.z)))return;
        var plan=GiantSakuraTree.plan(p.trunk,v.seed ^ p.x*31L ^ p.z);
        for(var entry:plan.entrySet()){
            var at=entry.getKey();var state=entry.getValue();
            if(state.is(Blocks.CHERRY_LOG)||state.is(Blocks.CHERRY_WOOD)){
                var axis=state.getValue(RotatedPillarBlock.AXIS);
                if(axis!=Direction.Axis.Y)state=state.setValue(RotatedPillarBlock.AXIS,
                        v.face(axis==Direction.Axis.X?Direction.EAST:Direction.SOUTH).getAxis());
            }
            put(v,p,at.getX(),at.getY()+1,at.getZ(),state);
        }
    }

    private static void put(HeartwaterFeature.Valley v, Plot p, int dx, int dy, int dz, BlockState state) {
        int x=p.x+dx,z=p.z+dz;
        if(v.planning()){v.put(x,p.y+dy,z,state);return;}
        if (!v.inside(x,z)) return;
        BlockPos pos=new BlockPos(v.wx(x,z),p.y+dy,v.wz(x,z));
        BlockState existing=v.chunk.getBlockState(pos);
        if (existing.hasBlockEntity() || !existing.getFluidState().isEmpty()) return;
        if (existing.isAir() || existing.is(BlockTags.LEAVES) || existing.is(Blocks.SHORT_GRASS)
                || existing.is(Blocks.TALL_GRASS) || existing.is(Blocks.PINK_PETALS)) v.put(x,p.y+dy,z,state);
    }
}

