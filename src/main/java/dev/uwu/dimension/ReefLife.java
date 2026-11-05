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
    public static final ResourceKey<Level> PREVIEW = ResourceKey.create(Registries.DIMENSION, UwUDimension.id("blossom_reach"));
    static boolean isReach(Level level) { return level.dimension().equals(REACH)||level.dimension().equals(PREVIEW); }
    public static final ResourceKey<Biome> REEF = ResourceKey.create(Registries.BIOME, UwUDimension.id("blush_reef"));
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
    // Only loaded, open surface water or water near the swimmer is sampled. Pokémon use Cobblemon itself.
    private static void waterLife(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !isReach(player.level())) return;
        ServerLevel level=player.serverLevel();
        long clock=level.getGameTime()+player.getId();
        if (clock%10==0 && player.isUnderWater() && level.getBiome(player.blockPosition()).is(REEF)) {
            RandomSource rng=player.getRandom();
            BlockPos glint=player.blockPosition().offset(rng.nextInt(13)-6,rng.nextInt(7)-3,rng.nextInt(13)-6);
            if (level.getFluidState(glint).is(FluidTags.WATER))
                level.sendParticles(player,ParticleTypes.END_ROD,false,glint.getX()+.5,glint.getY()+.5,glint.getZ()+.5,2,.35,.22,.35,.004);
        }
        if(clock%160!=0 || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        RandomSource rng=player.getRandom();
        for(int i=0;i<8;i++) {
            double angle=rng.nextDouble()*Math.PI*2;
            int radius=24+rng.nextInt(23);
            BlockPos axis=player.blockPosition().offset((int)(Math.cos(angle)*radius),0,(int)(Math.sin(angle)*radius));
            if(!level.hasChunkAt(axis))continue;
            int surface=level.getHeight(Heightmap.Types.WORLD_SURFACE,axis.getX(),axis.getZ());
            int y=surface-2-rng.nextInt(3);
            // Also allow closed lakes at other elevations when the observer is actually in one.
            if(Math.abs(y-player.getY())>26 && player.isInWater())y=player.getBlockY()+rng.nextInt(5)-2;
            if(Math.abs(y-player.getY())>28)continue;
            BlockPos at=new BlockPos(axis.getX(),y,axis.getZ());
            if(!clearWater(level,at)||level.getNearestPlayer(at.getX()+.5,y,at.getZ()+.5,18,false)!=null)continue;
            AABB area=new AABB(at).inflate(42,16,42);
            boolean sea=level.getBiome(at).is(REEF);
            if(sea && surface-y<=8 && level.canSeeSkyFromBelowWater(at) && rng.nextInt(3)==0
                    && level.getEntitiesOfClass(Dolphin.class,area).size()<2) {
                DOLPHIN.get().spawn(level,at,MobSpawnType.NATURAL);
            } else if(level.getEntitiesOfClass(AbstractFish.class,area).size()<7) {
                var species=sea ? EntityType.TROPICAL_FISH : rng.nextBoolean()?EntityType.SALMON:EntityType.COD;
                AbstractSchoolingFish leader=null;
                int count=3+rng.nextInt(3);
                for(int n=0;n<count;n++) {
                    BlockPos point=at.offset(rng.nextInt(5)-2,0,rng.nextInt(5)-2);
                    if(!clearWater(level,point))continue;
                    AbstractSchoolingFish fish=species.spawn(level,point,MobSpawnType.NATURAL);
                    if(fish==null)continue;
                    if(leader==null)leader=fish;else fish.startFollowing(leader);
                }
            }
            return;
        }
    }
    private static boolean clearWater(LevelReader l,BlockPos p) {
        return l.getBlockState(p).is(Blocks.WATER) && l.getBlockState(p.above()).is(Blocks.WATER)
                && l.getBlockState(p.below()).is(Blocks.WATER);
    }
    public static final class Garden extends Feature<NoneFeatureConfiguration> {
        public Garden(){super(NoneFeatureConfiguration.CODEC);}
        @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            var l=ctx.level();var r=ctx.random();BlockPos center=ctx.origin();
            if(!l.getLevel().dimension().equals(REACH))return false;
            Block[] colony={Blocks.BRAIN_CORAL_BLOCK,Blocks.FIRE_CORAL_BLOCK,Blocks.BUBBLE_CORAL_BLOCK,Blocks.TUBE_CORAL_BLOCK,Blocks.HORN_CORAL_BLOCK};
            Block[] crowns={Blocks.BRAIN_CORAL_FAN,Blocks.FIRE_CORAL,Blocks.BUBBLE_CORAL_FAN,Blocks.TUBE_CORAL,Blocks.HORN_CORAL_FAN};
            int kind=r.nextInt(colony.length),size=2+r.nextInt(3);
            for(int dx=-size;dx<=size;dx++)for(int dz=-size;dz<=size;dz++) {
                if(dx*dx+dz*dz>size*size || r.nextInt(4)==0)continue;
                int x=center.getX()+dx,z=center.getZ()+dz;
                BlockPos p=new BlockPos(x,l.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z),z);
                if(!l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP) || !l.getBlockState(p).is(Blocks.WATER)
                    || !l.getBlockState(p.above(3)).is(Blocks.WATER))continue;
                int height=1+(dx*dx+dz*dz<=2?r.nextInt(2):0);
                for(int y=0;y<height;y++)l.setBlock(p.above(y),(r.nextInt(11)==0?PEARL.get():colony[kind]).defaultBlockState(),2);
                l.setBlock(p.above(height),crowns[kind].defaultBlockState(),2);
            }
            return true;
        }
    }
}
