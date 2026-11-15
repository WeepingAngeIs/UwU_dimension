package dev.uwu.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Finds camera angles on naturally exposed geology without placing or excavating scenery. */
final class StrataCamera {
    static final TagKey<Biome> SURFACE = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("uwudimension","sakura_music_land"));
    private static final int[][] ANGLES = {{-4,-3},{4,-3},{-3,4},{3,4},{0,-4},{0,4},{-4,0},{4,0}};

    static SeaWatch.View frame(ServerPlayer player, BlockPos origin, String name) {
        var level = player.serverLevel();
        boolean submerged = name.equals("moonwake-trench");
        var target = block(submerged ? "moonwake_shellstone" : "roseglass_ore");
        if (submerged) origin = trench(level, origin);
        int centerX = origin.getX() >> 4;
        int centerZ = origin.getZ() >> 4;
        for (int radius = 0; radius <= 7; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                var chunk = level.getChunk(centerX + dx, centerZ + dz);
                var view = search(player, chunk, target, submerged, name);
                if (view != null) return view;
            }
        }
        throw new AssertionError("No exposed " + target + " panorama near " + origin);
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("uwudimension", path));
    }

    static SeaWatch.View night(ServerPlayer p, BlockPos origin) {
        var level = p.serverLevel();
        var gen = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        for(int ring=0;ring<=72;ring++) for(int dx=-ring;dx<=ring;dx++) for(int dz=-ring;dz<=ring;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz)) != ring) continue;
            int x=origin.getX()+dx*64, z=origin.getZ()+dz*64;
            if(!gen.getBiomeSource().getNoiseBiome(x>>2,30,z>>2,random.sampler()).is(SURFACE)) continue;
            if(gen.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random) < gen.getSeaLevel()+4) continue;
            level.getChunk(x>>4,z>>4);
            int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            BlockPos eye=new BlockPos(x,top+4,z);
            if(!level.getBiome(eye).is(SURFACE) || !level.getBiome(eye.below(2)).is(SURFACE)) continue;
            for(int[] dir : ANGLES) {
                int vx=x+dir[0]*5, vz=z+dir[1]*5;
                level.getChunk(vx>>4,vz>>4);
                int vy=level.getHeight(Heightmap.Types.WORLD_SURFACE,vx,vz);
                if(vy>eye.getY()+2) continue;
                return new SeaWatch.View(Vec3.atCenterOf(eye),Vec3.atCenterOf(new BlockPos(vx,vy+1,vz)),"sakura-night",false);
            }
        }
        throw new AssertionError("No native Sakura land biome in night photo survey");
    }

    private static SeaWatch.View search(ServerPlayer p, LevelChunk chunk, Block target, boolean wet, String name) {
        var level = p.serverLevel();
        var cursor = new BlockPos.MutableBlockPos();
        for (int sy = 0; sy < chunk.getSections().length; sy++) {
            var section = chunk.getSection(sy);
            if (!section.maybeHas(s -> s.is(target))) continue;
            int low = chunk.getMinBuildHeight() + sy * 16;
            for (int y = 15; y >= 0; y--) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (!section.getBlockState(x,y,z).is(target)) continue;
                cursor.set(chunk.getPos().getMinBlockX() + x, low + y, chunk.getPos().getMinBlockZ() + z);
                if(wet && (cursor.getY() < 25 || level.getHeight(Heightmap.Types.OCEAN_FLOOR,cursor.getX(),cursor.getZ()) > cursor.getY()+4))
                    continue;
                if(!wet && (cursor.getY() < 10 || level.canSeeSky(cursor))) continue;
                Vec3 focus = Vec3.atCenterOf(cursor);
                for(int[] offset : ANGLES) for(int h : new int[]{2,1,-1}) {
                    BlockPos eye = cursor.offset(offset[0],h,offset[1]);
                    if (wet ? !level.getFluidState(eye).is(FluidTags.WATER) || !level.getFluidState(eye.below()).is(FluidTags.WATER)
                            : !level.isEmptyBlock(eye) || !level.isEmptyBlock(eye.below())) continue;
                    Vec3 camera = Vec3.atCenterOf(eye);
                    var hit = level.clip(new ClipContext(camera,focus,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                    if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(cursor)) continue;
                    return new SeaWatch.View(camera,focus,name,false);
                }
            }
        }
        return null;
    }

    private static BlockPos trench(ServerLevel level, BlockPos origin) {
        var gen = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var biome = ResourceLocation.fromNamespaceAndPath("uwudimension","moonwake_trench");
        int sea = gen.getSeaLevel();
        for(int ring=0;ring<=100;ring++) for(int dx=-ring;dx<=ring;dx++) for(int dz=-ring;dz<=ring;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz)) != ring) continue;
            int x=origin.getX()+dx*96, z=origin.getZ()+dz*96;
            if(!gen.getBiomeSource().getNoiseBiome(x>>2,(sea-6)>>2,z>>2,random.sampler()).is(biome)) continue;
            if(gen.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random) > sea-8) continue;
            return new BlockPos(x,sea-6,z);
        }
        throw new AssertionError("No natural Moonwake Trench sample within survey range");
    }
}
