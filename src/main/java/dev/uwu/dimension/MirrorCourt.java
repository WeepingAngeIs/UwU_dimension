package dev.uwu.dimension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The single bell and its court survive chunk unloads independently of the challenger. */
public final class MirrorCourt {
    private static final String MOD = "uwudimension";
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MOD);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD);
    public static final DeferredHolder<EntityType<?>, EntityType<VelvetVesper>> VESPER = TYPES.register("velvet_vesper",
        () -> EntityType.Builder.<VelvetVesper>of(VelvetVesper::new, MobCategory.CREATURE).sized(2.25F, 2.85F)
                .clientTrackingRange(10).build(MOD + ":velvet_vesper"));
    public static final DeferredHolder<MobEffect, MobEffect> DEVOTION = EFFECTS.register("mirror_devotion",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0xe995c1, ParticleTypes.HEART) {});
    public static final DeferredBlock<CourtBell> BELL = BLOCKS.register("mirror_court_bell", CourtBell::new);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        EFFECTS.register(bus);
        BLOCKS.register(bus);
        bus.addListener(MirrorCourt::attributes);
        NeoForge.EVENT_BUS.addListener(MirrorCourt::melee);
        NeoForge.EVENT_BUS.addListener(MirrorCourt::target);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(VESPER.get(), VelvetVesper.createMobAttributes().add(Attributes.MAX_HEALTH, 500)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.FOLLOW_RANGE, 24).build());
    }
    private static void melee(AttackEntityEvent event) {
        if (event.getTarget() instanceof VelvetVesper moth && moth.refusesAttack(event.getEntity()))
            event.setCanceled(true);
    }
    private static void target(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof VelvetVesper moth && moth.isCharmed(event.getEntity()))
            event.setNewAboutToBeSetTarget(null);
    }

    static void build(HeartwaterFeature.Valley v) {
        if (!v.overlaps(-18,130,17,164)) return;
        // A three-wide level path leaves the existing archive walk, crosses the lake
        // margin, then turns into a lit cave pocket facing the court's only opening.
        v.passage(-8,-19,131,16,-14,133);
        v.passage(-8,-19,131,-6,-14,138);
        v.passage(-11,-19,134,-3,-12,137);
        for (int x=-5;x<=15;x+=5) v.block(x,-19,132,HeartwaterFeature.GLASS.get());
        for (int x:new int[]{-11,-3}) v.block(x,-18,136,HeartwaterFeature.GLASS.get());
        v.shell(-17,-20,139,3,-6,163,Blocks.POLISHED_BLACKSTONE_BRICKS);
        v.doorway(-7,-19,139,Blocks.CHISELED_QUARTZ_BLOCK);
        v.box(-15,-19,141,1,-19,161,Blocks.SMOOTH_QUARTZ);
        // A magenta-and-gold mosaic circles the dais. The entire walkable floor stays flush.
        for (int x=-15;x<=1;x++) for (int z=141;z<=161;z++) {
            int dx=Math.abs(x+7), dz=Math.abs(z-152);
            if (dx+dz==6 || ((dx==5 || dz==5) && dx<=5 && dz<=5)) v.block(x,-19,z,Blocks.PURPUR_BLOCK);
        }
        for (int side : new int[]{-15,1}) for (int z : new int[]{143,150,158}) {
            v.box(side,-18,z,side,-9,z,Blocks.QUARTZ_PILLAR);
            v.box(side,-11,z-1,side,-10,z+1,Blocks.PURPUR_PILLAR);
            v.block(side,-10,z,Blocks.PEARLESCENT_FROGLIGHT);
        }
        // Tall back-wall stained-glass butterfly, with a luminous backing inside the obsidian shell.
        for (int x=-13;x<=-1;x++) for (int y=-17;y<=-9;y++) {
            int dx=Math.abs(x+7), dy=y+14;
            if ((dy>=0 && dx>=1 && dx<=6 && Math.abs(dy-dx/2)<=2)
                    || (dy<0 && dx>0 && dx<=4 && Math.abs(dy+dx/2)<=1)) {
                v.block(x,y,162,Blocks.PEARLESCENT_FROGLIGHT);
                v.block(x,y,161,dy<0?Blocks.PURPLE_STAINED_GLASS:dx%3==0?Blocks.CYAN_STAINED_GLASS:Blocks.PINK_STAINED_GLASS);
            }
        }
        v.box(-7,-16,161,-7,-9,161,Blocks.CHISELED_COPPER);
        v.block(-7,-18,146,Blocks.CHISELED_QUARTZ_BLOCK);
        v.put(-7,-17,146,BELL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,v.face(Direction.SOUTH)));
        v.block(-7,-19,152,Blocks.PEARLESCENT_FROGLIGHT);
    }

    public static final class CourtBell extends Block {
        private static final VoxelShape SHAPE = Block.box(1,0,1,15,16,15);
        private CourtBell() {
            super(BlockBehaviour.Properties.of().strength(5,1200).noOcclusion().lightLevel(s -> 9).sound(SoundType.AMETHYST));
            registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
            builder.add(BlockStateProperties.HORIZONTAL_FACING);
        }
        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
        @Override protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
            if (world instanceof ServerLevel level && !player.isSpectator()) ring(level,pos,state,player);
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        private void ring(ServerLevel level, BlockPos pos, BlockState state, Player player) {
            if (!level.dimension().location().getNamespace().equals(MOD)) return;
            Ledger ledger = ledger(level);
            if (ledger.active.containsKey(pos.asLong())) {
                player.displayClientMessage(Component.translatable("message.uwudimension.court_busy"), true);
                return;
            }
            BlockPos center=pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING),6);
            VelvetVesper moth=VESPER.get().create(level);
            if (moth==null) return;
            moth.bindBell(pos);
            moth.moveTo(center.getX()+.5,center.getY()+.05,center.getZ()+.5,
                    state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().toYRot(),0);
            if (!level.noCollision(moth) || !level.addFreshEntity(moth)) return;
            ledger.active.put(pos.asLong(),moth.getUUID());
            ledger.setDirty();
            level.playSound(null,pos,SoundEvents.BELL_RESONATE,SoundSource.HOSTILE,1,.72F);
            level.sendParticles(ParticleTypes.CHERRY_LEAVES,moth.getX(),moth.getY()+1,moth.getZ(),38,1,1,1,.05);
        }
    }

    static void release(ServerLevel level, BlockPos pos, UUID id) {
        if (pos==null) return;
        Ledger ledger=ledger(level);
        if (ledger.active.remove(pos.asLong(),id)) ledger.setDirty();
    }
    private static Ledger ledger(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(Ledger.FACTORY,"uwudimension_mirror_courts");
    }
    private static final class Ledger extends SavedData {
        static final Factory<Ledger> FACTORY=new Factory<>(Ledger::new,Ledger::load,null);
        final Map<Long,UUID> active=new HashMap<>();
        static Ledger load(CompoundTag root, HolderLookup.Provider registry) {
            var state=new Ledger();
            for (Tag element : root.getList("Courts",Tag.TAG_COMPOUND)) {
                var row=(CompoundTag)element;
                if (row.hasUUID("Moth")) state.active.put(row.getLong("Bell"),row.getUUID("Moth"));
            }
            return state;
        }
        @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registry) {
            var rows=new ListTag();
            active.forEach((pos,id)->{var row=new CompoundTag();row.putLong("Bell",pos);row.putUUID("Moth",id);rows.add(row);});
            root.put("Courts",rows);
            return root;
        }
    }
}

