package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.*;

public final class ReefLife {
    public static final ResourceKey<Level> REACH = ResourceKey.create(Registries.DIMENSION, UwUDimension.id("sakura_reach"));
    static boolean isSettlementRealm(Level level) { return level.dimension().equals(REACH); }
    static boolean isReach(Level level) { return level.dimension().equals(REACH); }
    static boolean isProduceRealm(Level level) { return level.dimension().equals(REACH); }
    public static final ResourceKey<Biome> REEF = ResourceKey.create(Registries.BIOME, UwUDimension.id("blush_reef"));
    static final ResourceKey<Biome> TRENCH = ResourceKey.create(Registries.BIOME, UwUDimension.id("moonwake_trench"));
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "uwudimension");
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, "uwudimension");
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final DeferredBlock<Block> PEARL = BLOCKS.registerSimpleBlock("nacre_rose",
            BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.AMETHYST).lightLevel(s -> 11));
    public static final DeferredHolder<EntityType<?>,EntityType<Dolphin>> DOLPHIN = TYPES.register("ribbon_dolphin",
            () -> EntityType.Builder.of(Dolphin::new, MobCategory.WATER_CREATURE).sized(.88F,.58F)
                    .clientTrackingRange(10).build("uwudimension:ribbon_dolphin"));

    static void register(IEventBus bus) {
        FEATURES.register("nacre_garden", Garden::new);
        UwUDimension.ITEMS.registerSimpleBlockItem("nacre_rose", PEARL);
        FEATURES.register(bus);
        TYPES.register(bus);
        BLOCKS.register(bus);
        bus.addListener(ReefLife::attributes);
        bus.addListener(ReefLife::placements);
        NeoForge.EVENT_BUS.addListener(ReefLife::waterLife);
    }
    private static void attributes(EntityAttributeCreationEvent e) { e.put(DOLPHIN.get(), Dolphin.createAttributes().build()); }
    private static void placements(RegisterSpawnPlacementsEvent e) {
        e.register(DOLPHIN.get(), SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WaterAnimal::checkSurfaceWaterAnimalSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
    // An additional small, local shoal when vanilla's global water cap is occupied elsewhere.
    // Only loaded, open surface water or water near the swimmer is sampled. PokÃ©mon use Cobblemon itself.
    private static void waterLife(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !isReach(player.level())) return;
        ServerLevel level=player.serverLevel();
        long clock=level.getGameTime()+player.getId();
        if (clock%10==0 && player.isUnderWater() && biomeChunksReady(level, player.blockPosition())
                && seaBiome(level, player.blockPosition())) {
            RandomSource rng=player.getRandom();
            BlockPos glint=player.blockPosition().offset(rng.nextInt(13)-6,rng.nextInt(7)-3,rng.nextInt(13)-6);
            LevelChunk glintChunk = level.getChunkSource().getChunkNow(glint.getX() >> 4, glint.getZ() >> 4);
            if (glintChunk != null && glintChunk.getFluidState(glint).is(FluidTags.WATER))
                level.sendParticles(player,ParticleTypes.END_ROD,false,glint.getX()+.5,glint.getY()+.5,glint.getZ()+.5,2,.35,.22,.35,.004);
        }
        if(clock%160!=0 || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        RandomSource rng=player.getRandom();
        for(int i=0;i<8;i++) {
            double angle=rng.nextDouble()*Math.PI*2;
            int radius=24+rng.nextInt(23);
            BlockPos axis=player.blockPosition().offset((int)(Math.cos(angle)*radius),0,(int)(Math.sin(angle)*radius));
            LevelChunk chunk = level.getChunkSource().getChunkNow(axis.getX() >> 4, axis.getZ() >> 4);
            if (chunk == null) continue;
            // Level.getHeight returns the first free block above the chunk's highest occupied block.
            int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, axis.getX() & 15, axis.getZ() & 15) + 1;
            int y=surface-2-rng.nextInt(3);
            // Also allow closed lakes at other elevations when the observer is actually in one.
            if(Math.abs(y-player.getY())>26 && player.isInWater())y=player.getBlockY()+rng.nextInt(5)-2;
            if(Math.abs(y-player.getY())>28)continue;
            BlockPos at=new BlockPos(axis.getX(),y,axis.getZ());
            if (!clearWater(chunk, at) || !biomeChunksReady(level, at)
                    || level.getNearestPlayer(at.getX()+.5,y,at.getZ()+.5,18,false)!=null) continue;
            AABB area=new AABB(at).inflate(42,16,42);
            boolean sea=seaBiome(level, at);
            if(sea && surface-y<=8 && level.canSeeSkyFromBelowWater(at) && rng.nextBoolean()
                    && level.getEntitiesOfClass(Dolphin.class,area,d->d.getType()==DOLPHIN.get()).size()<2) {
                DOLPHIN.get().spawn(level,at,MobSpawnType.NATURAL);
            } else if(level.getEntitiesOfClass(AbstractFish.class,area).size()<7) {
                var species=sea ? EntityType.TROPICAL_FISH : rng.nextBoolean()?EntityType.SALMON:EntityType.COD;
                AbstractSchoolingFish leader=null;
                int count=3+rng.nextInt(3);
                for(int n=0;n<count;n++) {
                    BlockPos point=at.offset(rng.nextInt(5)-2,0,rng.nextInt(5)-2);
                    LevelChunk fishChunk = level.getChunkSource().getChunkNow(point.getX() >> 4, point.getZ() >> 4);
                    if (fishChunk == null || !clearWater(fishChunk, point) || !biomeChunksReady(level, point)) continue;
                    AbstractSchoolingFish fish=species.spawn(level,point,MobSpawnType.NATURAL);
                    if(fish==null)continue;
                    if(leader==null)leader=fish;else fish.startFollowing(leader);
                }
            }
            return;
        }
    }
    private static boolean clearWater(LevelChunk chunk, BlockPos pos) {
        return chunk.getBlockState(pos).is(Blocks.WATER) && chunk.getBlockState(pos.above()).is(Blocks.WATER)
                && chunk.getBlockState(pos.below()).is(Blocks.WATER);
    }
    private static boolean biomeChunksReady(ServerLevel level, BlockPos pos) {
        // Vanilla's biome lookup chooses between neighboring four-block cells, including during fish spawning.
        int minX = (pos.getX() - 2) >> 4, maxX = (pos.getX() + 2) >> 4;
        int minZ = (pos.getZ() - 2) >> 4, maxZ = (pos.getZ() + 2) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }
    private static boolean seaBiome(LevelReader level, BlockPos at) {
        var biome = level.getBiome(at);
        return biome.is(REEF) || biome.is(TRENCH);
    }

    public static final class Garden extends Feature<NoneFeatureConfiguration> {
        public Garden() { super(NoneFeatureConfiguration.CODEC); }

        @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            var level = context.level();
            if (!isReach(level.getLevel())) return false;
            var random = context.random();
            var origin = context.origin();
            int cx = (origin.getX() & ~15) + 4 + random.nextInt(8);
            int cz = (origin.getZ() & ~15) + 4 + random.nextInt(8);
            boolean deep = level.getBiome(origin).is(TRENCH);
            var bed = deep ? SakuraCraft.ABYSS.get() : SakuraCraft.SHELF.get();
            var plume = deep ? SakuraCraft.KELP.get() : SakuraCraft.PLUME.get();
            boolean made = false;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx*dx + dz*dz > 11 || random.nextInt(4) == 0) continue;
                    var at = new BlockPos(cx + dx, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cx + dx, cz + dz), cz + dz);
                    if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
                            || !level.getBlockState(at).is(Blocks.WATER) || !level.getBlockState(at.above(3)).is(Blocks.WATER)) continue;
                    int height = dx*dx + dz*dz < 4 ? 2 : 1;
                    for (int y = 0; y < height; y++) {
                        level.setBlock(at.above(y), (random.nextInt(18) == 0 ? PEARL.get() : bed).defaultBlockState(), 2);
                    }
                    var crown = random.nextInt(4) == 0 ? SakuraCraft.CLAM.get() : plume;
                    level.setBlock(at.above(height), crown.defaultBlockState(), 2);
                    made = true;
                }
            }
            if (deep && random.nextInt(5) == 0) made |= arch(level, cx, cz, bed);
            return made;
        }

        // Paired ribs grow from the actual seabed. A swimmer can pass through the eye.
        private static boolean arch(WorldGenLevel level, int x, int z, Block bed) {
            int left = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x - 3, z);
            int right = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + 3, z);
            if (Math.abs(left - right) > 2) return false;
            int top = Math.max(left, right) + 4;
            if (!level.getBlockState(new BlockPos(x, top + 1, z)).is(Blocks.WATER)) return false;
            for (int dx = -3; dx <= 3; dx++) {
                int outer = Math.abs(dx);
                int from = outer == 3 ? (dx < 0 ? left : right) : top - (outer == 2 ? 1 : 0);
                int to = top - (outer == 3 ? 1 : 0);
                for (int y = from; y <= to; y++) {
                    var pos = new BlockPos(x + dx, y, z);
                    if (level.getBlockState(pos).is(Blocks.WATER))
                        level.setBlock(pos, (dx == 0 ? PEARL.get() : bed).defaultBlockState(), 2);
                }
            }
            return true;
        }
    }
}
