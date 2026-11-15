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
        NeoForge.EVENT_BUS.addListener(MirrorCourt::approach);
        NeoForge.EVENT_BUS.addListener(MirrorCourt::treasure);
        NeoForge.EVENT_BUS.addListener(MirrorCourt::commands);
    }

    private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        var command = net.minecraft.commands.Commands.literal("uwucourt").requires(source -> source.hasPermission(2));
        for (String action : java.util.List.of("status", "reconcile")) {
            command.then(net.minecraft.commands.Commands.literal(action)
                    .then(net.minecraft.commands.Commands.argument("bell", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                    .executes(context -> {
                        var source = context.getSource();
                        ServerLevel level = source.getLevel();
                        var bell = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(context, "bell");
                        String result = inspect(level, bell, action.equals("reconcile"));
                        source.sendSuccess(() -> Component.literal(result), false);
                        return 1;
                    })));
        }
        event.getDispatcher().register(command);
    }

    private static String inspect(ServerLevel level, BlockPos bell, boolean reconcile) {
        if (!ReefLife.isReach(level)) return "Run this command in Sakura Reach.";
        long key = bell.asLong();
        Ledger ledger = ledger(level);
        String victory = ledger.defeated.contains(key) ? "open" : "sealed";
        String summary = "Court " + bell.toShortString() + " | treasure " + victory + " | "
                + ledger.seals.getOrDefault(key, java.util.Set.of()).size() + " personal seals | ";
        UUID id = ledger.active.get(key);
        if (id == null) {
            if (level.hasChunkAt(bell) && !level.getBlockState(bell).is(BELL.get())) return "No court bell at " + bell.toShortString();
            return summary + "no active encounter";
        }
        if (level.getEntity(id) instanceof VelvetVesper moth && moth.isAlive()) {
            return summary + "guardian " + id + " at " + moth.blockPosition().toShortString() + ", " + (int)moth.getHealth() + " HP";
        }
        if (!ledger.lastSeen.containsKey(key) && !level.hasChunkAt(bell)) return summary + "old encounter; bell chunk unloaded";
        BlockPos last = ledger.lastSeen.containsKey(key) ? BlockPos.of(ledger.lastSeen.get(key)) : bell;
        if (!ledger.lastSeen.containsKey(key) && level.getBlockState(bell).is(BELL.get())) {
            last = bell.relative(level.getBlockState(bell).getValue(BlockStateProperties.HORIZONTAL_FACING), 6);
        }
        if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(last))) {
            ledger.missingSince.remove(key);
            return summary + "guardian unloaded; last seen " + last.toShortString();
        }
        if (!reconcile) return summary + "guardian absent from its loaded chamber; /uwucourt reconcile can clear this record";
        long since = ledger.missingSince.computeIfAbsent(key, ignored -> level.getGameTime());
        long left = 100 - (level.getGameTime()-since);
        if (left > 0) return summary + "checking absent guardian; repeat in " + ((left+19)/20) + " seconds";
        release(level, bell, id);
        return summary + "stale encounter cleared; players can ready at the bell";
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
        v.shell(-17,-20,139,3,-6,163,ReachChambers.Room.COURT);
        v.doorway(-7,-19,139,ReachArchitecture.TRACERY.get());
        v.box(-15,-19,141,1,-19,161,ReachArchitecture.PEARL.get());
        // A magenta-and-gold mosaic circles the dais. The entire walkable floor stays flush.
        for (int x=-15;x<=1;x++) for (int z=141;z<=161;z++) {
            int dx=Math.abs(x+7), dz=Math.abs(z-152);
            if (dx+dz==6) v.block(x,-19,z,HeartwaterFeature.ROSETTE.get());
            else if ((dx==5 || dz==5) && dx<=5 && dz<=5) v.block(x,-19,z,HeartwaterFeature.LACE.get());
        }
        for (int side : new int[]{-15,1}) for (int z : new int[]{143,150,158}) {
            v.box(side,-18,z,side,-9,z,ReachArchitecture.RIB.get());
            v.box(side,-11,z-1,side,-10,z+1,HeartwaterFeature.QUILT.get());
            v.block(side,-10,z,ReachArchitecture.PILOT.get());
        }
        // Tall back-wall stained-glass butterfly, with a luminous backing inside the obsidian shell.
        for (int x=-13;x<=-1;x++) for (int y=-17;y<=-9;y++) {
            int dx=Math.abs(x+7), dy=y+14;
            if ((dy>=0 && dx>=1 && dx<=6 && Math.abs(dy-dx/2)<=2)
                    || (dy<0 && dx>0 && dx<=4 && Math.abs(dy+dx/2)<=1)) {
                v.block(x,y,162,ReachArchitecture.PILOT.get());
                v.block(x,y,161,dy<0?ReachArchitecture.window(true,1):dx%3==0?ReachArchitecture.window(false,1):ReachArchitecture.window(false,-1));
            }
        }
        v.box(-7,-16,161,-7,-9,161,SakuraMaterials.SILVER_BLOCK.get());
        v.block(-7,-18,146,ReachArchitecture.TRACERY.get());
        v.put(-7,-17,146,BELL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,v.face(Direction.SOUTH)));
        v.block(-7,-19,152,ReachArchitecture.PILOT.get());
        v.chest(-7,-18,159,"mirror_court");
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
        private boolean readyAt(Player player, long bell, long now) {
            var data = player.getPersistentData();
            return data.getLong("uwu.court_ready_until") > now && data.getLong("uwu.court_ready_at") == bell;
        }
        private void ring(ServerLevel level, BlockPos pos, BlockState state, Player player) {
            if (!ReefLife.isReach(level)) return;
            Ledger ledger = ledger(level);
            long key = pos.asLong();
            BlockPos center = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING), 6);
            if (ledger.active.containsKey(key)) {
                var existing = level.getEntity(ledger.active.get(key));
                if (existing instanceof VelvetVesper moth && moth.isAlive()) {
                    moth.join(player);
                    player.displayClientMessage(Component.literal("You joined this encounter. Help near the dais to earn a personal victory seal."), false);
                    return;
                }
                BlockPos last = BlockPos.of(ledger.lastSeen.getOrDefault(key, center.asLong()));
                if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(last))) {
                    ledger.missingSince.remove(key);
                    player.displayClientMessage(Component.literal("The guardian's last chamber is unloaded. Its encounter is saved."), false);
                    return;
                }
                long first = ledger.missingSince.computeIfAbsent(key, ignored -> level.getGameTime());
                if (level.getGameTime() - first < 100) {
                    player.displayClientMessage(Component.literal("The court is listening. Ring again in five seconds."), false);
                    return;
                }
                release(level, pos, ledger.active.get(key));
            }
            if (!player.isShiftKeyDown() || !readyAt(player, key, level.getGameTime())) {
                player.getPersistentData().putLong("uwu.court_ready_at", key);
                player.getPersistentData().putLong("uwu.court_ready_until", level.getGameTime() + 600);
                player.displayClientMessage(Component.literal("Ready for 30 seconds. Allies may touch the bell to join. "
                        + "Crouch and ring to begin with nearby ready players. Solo is welcome."), false);
                return;
            }
            if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(center))) {
                player.displayClientMessage(Component.literal("The chamber is still waking. Ring again in a moment."), false);
                return;
            }
            VelvetVesper moth=VESPER.get().create(level);
            if (moth==null) return;
            moth.bindBell(pos);
            for (var guest : level.players()) {
                if (guest.isAlive() && !guest.isSpectator() && guest.blockPosition().closerThan(pos, 24)
                        && readyAt(guest, key, level.getGameTime())) moth.join(guest);
            }
            moth.join(player);
            moth.moveTo(center.getX()+.5,center.getY()+.05,center.getZ()+.5,
                    state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().toYRot(),0);
            if (!level.noCollision(moth)) {
                player.displayClientMessage(Component.literal("The guardian needs more space above its dais."), false);
                return;
            }
            if (!level.addFreshEntity(moth)) {
                player.displayClientMessage(Component.literal("The guardian could not enter the chamber. Ring again."), false);
                return;
            }
            ledger.active.put(key,moth.getUUID());
            ledger.lastSeen.put(key, center.asLong());
            for (var guest : level.players()) {
                if (readyAt(guest, key, level.getGameTime())) guest.getPersistentData().remove("uwu.court_ready_until");
            }
            player.getPersistentData().remove("uwu.court_ready_until");
            ledger.setDirty();
            level.playSound(null,pos,SoundEvents.BELL_RESONATE,SoundSource.HOSTILE,1,.72F);
            level.sendParticles(ParticleTypes.CHERRY_LEAVES,moth.getX(),moth.getY()+1,moth.getZ(),38,1,1,1,.05);
        }
    }

    private static void approach(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)||p.isSpectator()||p.tickCount%20!=0||p.getY()>0||!ReefLife.isReach(p.serverLevel()))return;
        ServerLevel level=p.serverLevel();var site=GroveSites.site(level,Math.floorDiv(p.getBlockX()+GroveSites.SPACING/2,GroveSites.SPACING),Math.floorDiv(p.getBlockZ()+GroveSites.SPACING/2,GroveSites.SPACING));
        if(!site.usable())return;
        BlockPos bell=site.pos(-7,-17,146);if(!level.hasChunkAt(bell)||!level.getBlockState(bell).is(BELL.get()))return;
        double distance=p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(bell));
        if(distance<42*42 && p.getPersistentData().getLong("uwu.court_hint")!=bell.asLong()){
            p.getPersistentData().putLong("uwu.court_hint",bell.asLong());
            p.sendSystemMessage(Component.literal("Beyond the archive, the Mirror Court waits. Follow the lit path to the glowing bell at "+bell.getX()+", "+bell.getY()+", "+bell.getZ()+". Its guardian watches over a sealed treasure chest."));
        }

    }
    private static void treasure(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel level)||!ReefLife.isReach(level))return;
        BlockPos chest=event.getPos();
        if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(chest).getBlock()).getNamespace().equals("lootr"))return;
        for(Direction facing:Direction.Plane.HORIZONTAL){
            BlockPos bell=chest.above().relative(facing,-13);
            if(!level.hasChunkAt(bell)||!level.getBlockState(bell).is(BELL.get()))continue;
            if(ledger(level).defeated.contains(bell.asLong()))return;
            
            event.getEntity().displayClientMessage(Component.literal("Ring the court bell, then crouch and ring again to challenge Velvet Vesper. A victory opens personal treasure for everyone."),true);event.setCanceled(true);return;
        }
    }
    static boolean track(ServerLevel level, BlockPos bell, VelvetVesper moth) {
        if (bell == null) return true;
        Ledger data = ledger(level);
        if (!moth.getUUID().equals(data.active.get(bell.asLong()))) return false;
        long here = moth.blockPosition().asLong();
        Long before = data.lastSeen.put(bell.asLong(), here);
        if (before == null || before.longValue() != here) data.setDirty();
        data.missingSince.remove(bell.asLong());
        return true;
    }

    static void defeated(ServerLevel level, BlockPos bell, UUID boss, java.util.Set<UUID> helpers) {
        if (bell == null) return;
        Ledger data = ledger(level);
        long key = bell.asLong();
        if (!boss.equals(data.active.get(key))) return;
        data.defeated.add(key);
        data.seals.computeIfAbsent(key, ignored -> new java.util.HashSet<>()).addAll(helpers);
        data.setDirty();
        for (var player : level.players()) {
            if (helpers.contains(player.getUUID())) player.sendSystemMessage(Component.literal(
                    "Mirror Court victory seal earned. This village's personal treasure is open to everyone."));
        }
    }

    public static boolean hasSeal(ServerLevel level, BlockPos bell, UUID player) {
        return ledger(level).seals.getOrDefault(bell.asLong(), java.util.Set.of()).contains(player);
    }

    static void release(ServerLevel level, BlockPos pos, UUID id) {
        if (pos == null) return;
        Ledger ledger = ledger(level);
        long key = pos.asLong();
        if (ledger.active.remove(key, id)) {
            ledger.lastSeen.remove(key);
            ledger.missingSince.remove(key);
            ledger.setDirty();
        }
    }

    private static Ledger ledger(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(Ledger.FACTORY,"uwudimension_mirror_courts");
    }
    private static final class Ledger extends SavedData {
        static final Factory<Ledger> FACTORY=new Factory<>(Ledger::new,Ledger::load,null);
        final Map<Long,UUID> active=new HashMap<>();
        final Map<Long,Long> lastSeen = new HashMap<>();
        final Map<Long,Long> missingSince = new HashMap<>();
        final Map<Long,java.util.Set<UUID>> seals = new HashMap<>();
        final java.util.Set<Long> defeated=new java.util.HashSet<>();
        static Ledger load(CompoundTag root, HolderLookup.Provider registry) {
            var state=new Ledger();
            for(long bell:root.getLongArray("Defeated"))state.defeated.add(bell);
            for (Tag element : root.getList("Courts",Tag.TAG_COMPOUND)) {
                var row=(CompoundTag)element;
                if (row.hasUUID("Moth")) state.active.put(row.getLong("Bell"),row.getUUID("Moth"));
                if (row.contains("Last")) state.lastSeen.put(row.getLong("Bell"),row.getLong("Last"));
            }
            for (Tag element : root.getList("Seals", Tag.TAG_COMPOUND)) {
                var row = (CompoundTag)element;
                if (row.hasUUID("Player")) state.seals.computeIfAbsent(row.getLong("Bell"),
                        ignored -> new java.util.HashSet<>()).add(row.getUUID("Player"));
            }
            return state;
        }
        @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registry) {
            var rows=new ListTag();
            active.forEach((pos,id)->{var row=new CompoundTag();row.putLong("Bell",pos);row.putUUID("Moth",id); if(lastSeen.containsKey(pos)) row.putLong("Last",lastSeen.get(pos)); rows.add(row);});
            root.put("Courts",rows);
            var sealsTag = new ListTag();
            seals.forEach((bell, players) -> players.forEach(player -> {
                var row = new CompoundTag();
                row.putLong("Bell", bell);
                row.putUUID("Player", player);
                sealsTag.add(row);
            }));
            root.put("Seals", sealsTag);
            root.putLongArray("Defeated",defeated.stream().mapToLong(Long::longValue).toArray());
            return root;
        }
    }
}

