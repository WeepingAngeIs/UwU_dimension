package dev.uwu.duskcheck;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.CupidShrine;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.People;
import dev.uwu.dimension.PetalShrinePlacement;
import dev.uwu.dimension.RealmResidents;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.SettlementLayout;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Checks actual native placement and actual disk residents in separately named saves. */
final class DuskProbe {
    private static final ResourceLocation VELVET = ResourceLocation.parse("uwudimension:velvet_highlands");
    private static final UUID LIVE = UUID.fromString("45757da3-1ea6-4ccc-8b21-029dd168dc01");
    private static final UUID UNDEAD = UUID.fromString("45757da3-1ea6-4ccc-8b21-029dd168dc02");
    private static final UUID ORIGIN = UUID.fromString("45757da3-1ea6-4ccc-8b21-029dd168dc03");
    private static final UUID OUTSIDE = UUID.fromString("45757da3-1ea6-4ccc-8b21-029dd168dc04");
    private final String stage;
    private final Set<ChunkPos> tickets = new LinkedHashSet<>();
    private final Map<ChunkPos, Integer> loads = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Boolean> fresh = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private ServerLevel level;
    private BlossomVillage.Plan plan;
    private ChunkPos first, second;
    private CompoundTag saved;
    private long entered;
    private int phase;

    DuskProbe(String stage) {
        this.stage = stage;
        NeoForge.EVENT_BUS.addListener(this::load);
    }

    boolean tick(MinecraftServer server) throws Exception {
        if (level == null) {
            level = server.getLevel(ReefLife.REACH);
            require(level != null, "Sakura Reach missing");
            entered = level.getGameTime();
            prepare(server);
            return false;
        }
        require(level.getGameTime() - entered < 1800, "Timed out stage=" + stage + " phase=" + phase);
        if (stage.equals("seed-legacy")) return seedLegacy(server);
        if (stage.equals("legacy") || stage.equals("legacy-restart")) return legacy(server);
        if (stage.equals("fresh-restart")) return freshRestart();
        return fresh(server);
    }

    private void prepare(MinecraftServer server) throws Exception {
        if (stage.equals("legacy") || stage.equals("legacy-restart") || stage.equals("fresh-restart")) {
            saved = NbtIo.readCompressed(packet(server), NbtAccounter.unlimitedHeap());
            var site = GroveSites.site(level, saved.getInt("Gx"), saved.getInt("Gz"));
            boolean dusk = stage.equals("fresh-restart");
            plan = BlossomVillage.plan(level, site, dusk).orElseThrow(() -> new AssertionError("Saved faction lost its plan"));
            require(signature(plan).equals(saved.getString("Plan")), "Saved village plan moved or changed faction");
            require(BlossomVillage.plan(level, site, !dusk).isEmpty(), "Opposite faction appeared on an existing site");
            first = new ChunkPos(saved.getLong("OldChunk"));
            second = new ChunkPos(saved.getLong("NewChunk"));
            request(first);
            if (stage.equals("fresh-restart")) villageChunks().forEach(this::request);
            return;
        }
        require(stage.equals("seed-legacy") || stage.equals("fresh-forward") || stage.equals("fresh-reverse"), "Unknown stage");
        plan = findPlan(stage.equals("seed-legacy"));
        System.out.println("DUSK " + (stage.equals("seed-legacy") ? "LEGACY PLAN " : "FRESH PLAN ") + signature(plan));
        int end = plan.road().length - 1;
        first = new ChunkPos(plan.site().pos(plan.x() + end - plan.road().length / 2,
                plan.road()[end], plan.z() + plan.bends()[end]));
        second = new ChunkPos(plan.plaza());
        require(!first.equals(second), "Fixture needs separated road and plaza chunks");
        if (stage.equals("fresh-reverse")) {
            var swap = first;
            first = second;
            second = swap;
        }
        require(!loaded(first) && !loaded(second), "Pure survey loaded a candidate chunk");
        request(first);
    }

    /** Deliberately find an old Petal checkerboard cell in the actual Velvet biome. */
    private BlossomVillage.Plan findPlan(boolean old) {
        if (old) {
            // Native Playtest 9 site witnessed in Actions run 44937032628. Reuse
            // that regression fixture: discovering it again surveys hundreds of
            // terrain columns in one tick and can trip a busy runner's watchdog.
            require(level.getSeed() == 152045L, "Historical fixture requires seed 152045");
            var site = GroveSites.site(level, 20, 23);
            require(site.usable() && site.x() == 15253 && site.z() == 17787 && site.turns() == 0,
                    "Historical native site drifted");
            var candidate = BlossomVillage.plan(level, site, false)
                    .orElseThrow(() -> new AssertionError("Historical Petal plan missing"));
            require(candidate.plaza().equals(new BlockPos(15149, 95, 17751)), "Historical plaza drifted");
            require(velvet(candidate.plaza().below()), "Historical fixture is not in Velvet Highlands");
            require(BlossomVillage.plan(level, site, true).isEmpty(), "Baseline site admitted Dusk");
            return candidate;
        }
        var origin = GroveSites.first(level);
        int stride = Math.ceilDiv(SettlementLayout.minimum(level) + 600, GroveSites.SPACING);
        for (int ring = 0; ring <= 24; ring++) for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || ((dx + dz) & 1) != 0) continue;
            var site = GroveSites.site(level, origin.gx() + dx * stride, origin.gz() + dz * stride);
            if (!site.usable()) continue;
            var candidate = BlossomVillage.plan(level, site, true);
            if (candidate.isEmpty() || !velvet(candidate.get().plaza().below())) continue;
            require(BlossomVillage.plan(level, site, false).isEmpty(), "A new Velvet cell admitted both factions");
            return candidate.get();
        }
        throw new AssertionError("No habitable Velvet settlement in bounded checkerboard search on seed " + level.getSeed());
    }

    private boolean velvet(BlockPos pos) {
        var source = level.getChunkSource();
        return source.getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()), source.randomState().sampler())
                .is(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, VELVET));
    }

    private boolean fresh(MinecraftServer server) throws Exception {
        if (phase == 0) {
            if (!loaded(first)) return false;
            require(Boolean.TRUE.equals(fresh.get(first)), "First fresh chunk was loaded from disk");
            require(!loads.containsKey(second), "Both controlled chunks loaded before the second request");
            request(second);
            villageChunks().forEach(this::request);
            phase = 1;
            return false;
        }
        if (phase == 1) {
            if (!tickets.stream().allMatch(this::loaded)) return false;
            require(Boolean.TRUE.equals(fresh.get(second)) && loads.get(first) < loads.get(second), "Native load order not witnessed");
            level.getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(plan.plaza()), 2, new ChunkPos(plan.plaza()), true);
            server.overworld().getChunkSource().addRegionTicket(TicketType.FORCED, new ChunkPos(0,0), 2, new ChunkPos(0,0), true);
            phase = 2;
            return false;
        }
        if (phase == 2) {
            if (!level.isPositionEntityTicking(plan.plaza()) || !server.overworld().hasChunk(0,0)) return false;
            // Entity section loading can finish after the chunk reaches FULL.
            if (level.getEntitiesOfClass(Keeper.class, new AABB(plan.plaza()).inflate(100)).isEmpty()) return false;
            verifyDuskVillage();
            populationSetup(server);
            phase = 3;
            return false;
        }
        if (phase == 3) {
            var live = level.getEntity(LIVE);
            var undead = level.getEntity(UNDEAD);
            var known = level.getEntity(ORIGIN);
            if (live == null || undead == null || known == null || live.getType() == EntityType.VILLAGER
                    || undead.getType() == EntityType.ZOMBIE_VILLAGER || known.getType() == EntityType.ZOMBIE_VILLAGER) return false;
            require(live.getType() == People.DUSK_KEEPER.get(), "New vanilla resident became Petal in Velvet");
            require(undead.getType() == People.DUSK_ZOMBIE.get(), "Repeated natural undead spawn became Petal in Velvet");
            require(known.getType() == People.PETAL_ZOMBIE.get(), "Known Petal cure origin was rewritten");
            require(live.getPersistentData().getString("DuskFixtureIdentity").equals("new-live"), "New-resident NBT lost");
            require(((Villager)live).getOffers().size() > 0, "New-resident trades lost");
            require(server.overworld().getEntity(OUTSIDE).getType() == EntityType.VILLAGER, "Outside vanilla resident changed");
            var duskCured = ((ZombieVillager)undead).convertTo(EntityType.VILLAGER, false);
            var petalCured = ((ZombieVillager)known).convertTo(EntityType.VILLAGER, false);
            require(duskCured != null && duskCured.getType() == People.DUSK_KEEPER.get(), "Dusk cure changed faction");
            require(petalCured != null && petalCured.getType() == People.PETAL_KEEPER.get(), "Known Petal cure changed faction");
            var legacyOrigin = People.PETAL_ZOMBIE.get().create(level);
            require(legacyOrigin != null, "No legacy cure-origin fixture");
            setup(legacyOrigin, new UUID(0x45757da31ea64cccL, 0x8b21029dd168dc05L), "legacy-dusk-origin", plan.plaza().above(8));
            var rememberedDusk = new CompoundTag();
            rememberedDusk.putBoolean("WasDusk", true);
            legacyOrigin.getPersistentData().put(RealmResidents.ORIGIN, rememberedDusk);
            require(level.addFreshEntity(legacyOrigin), "Could not add legacy cure-origin fixture");
            var restoredDusk = legacyOrigin.convertTo(EntityType.VILLAGER, false);
            require(restoredDusk != null && restoredDusk.getType() == People.DUSK_KEEPER.get(),
                    "Existing WasDusk provenance lost its original precedence over Petal zombie type");
            CompoundTag packet = geometryPacket();
            IOUtilities.writeNbtCompressed(packet, packet(server));
            System.out.println("DUSK FRESH PASS order=" + (stage.endsWith("reverse") ? "reverse" : "forward")
                    + " native Dusk village, no Petal/shrine, positioned spawn + new undead + birth + cure provenance + Overworld control");
            return true;
        }
        return false;
    }

    private void populationSetup(MinecraftServer server) {
        BlockPos pos = plan.plaza().above(8);
        Villager live = EntityType.VILLAGER.create(level);
        require(live != null && live.getType() == EntityType.VILLAGER, "Position-less factory still rewrites vanilla residents");
        setup(live, LIVE, "new-live", pos);
        trades(live);
        require(level.addFreshEntity(live), "Could not add new resident fixture");
        ZombieVillager undead = EntityType.ZOMBIE_VILLAGER.create(level);
        require(undead != null, "No undead fixture");
        setup(undead, UNDEAD, "new-undead", pos.east(2));
        require(level.addFreshEntity(undead), "Could not add new undead fixture");
        ZombieVillager known = EntityType.ZOMBIE_VILLAGER.create(level);
        require(known != null, "No known-origin fixture");
        setup(known, ORIGIN, "known-petal", pos.west(2));
        var origin = new CompoundTag();
        origin.putBoolean("WasDusk", false);
        known.getPersistentData().put(RealmResidents.ORIGIN, origin);
        require(level.addFreshEntity(known), "Could not add known-origin fixture");
        Villager outside = EntityType.VILLAGER.create(server.overworld());
        require(outside != null, "No Overworld fixture");
        setup(outside, OUTSIDE, "outside", new BlockPos(4, 16, 4));
        require(server.overworld().addFreshEntity(outside), "Could not add Overworld fixture");
        Keeper parent = People.PETAL_KEEPER.get().create(level);
        require(parent != null, "No breeding fixture");
        parent.moveTo(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        Keeper child = parent.getBreedOffspring(level, parent);
        require(child != null && child.getType() == People.DUSK_KEEPER.get()
                && parent.getType() == People.PETAL_KEEPER.get(), "Birth did not preserve parent while selecting a Dusk child");
        Keeper outsideParent = People.PETAL_KEEPER.get().create(server.overworld());
        require(outsideParent != null, "No outside breeding fixture");
        Keeper outsideChild = outsideParent.getBreedOffspring(server.overworld(), outsideParent);
        require(outsideChild != null && outsideChild.getType() == People.PETAL_KEEPER.get(), "Outside breeding faction changed");
    }

    private boolean freshRestart() {
        if (!tickets.stream().allMatch(this::loaded)) return false;
        require(Boolean.FALSE.equals(fresh.get(first)), "Fresh restart regenerated saved terrain");
        verifyGeometry(saved);
        // The explicit Petal cure-provenance actor from the preceding stage is
        // intentional; persistence must leave it alone, not convert it on reload.
        require(PetalShrinePlacement.select(level, plan).isEmpty(), "Dusk gained a shrine after restart");
        require(BlossomVillage.plan(level,plan.site(),false).isEmpty(), "Restart changed the new site's faction");
        System.out.println("DUSK FRESH RESTART PASS stable Dusk plan and generated architecture, no retroactive faction change");
        return true;
    }

    private boolean seedLegacy(MinecraftServer server) throws Exception {
        if (!loaded(first)) return false;
        require(Boolean.TRUE.equals(fresh.get(first)), "Baseline fixture was not newly generated");
        require(!loaded(second), "Baseline accidentally generated the whole settlement");
        CompoundTag packet = geometryPacket();
        var residents = new ListTag();
        BlockPos pos = new BlockPos(first.getMinBlockX()+8, plan.y()+12, first.getMinBlockZ()+8);
        List<Mob> actors = List.of(
                People.PETAL_KEEPER.get().create(level),
                People.DUSK_KEEPER.get().create(level),
                new Villager(EntityType.VILLAGER, level),
                new ZombieVillager(EntityType.ZOMBIE_VILLAGER, level));
        for (int i=0; i<actors.size(); i++) {
            Mob actor = actors.get(i);
            require(actor != null, "Missing baseline actor");
            setup(actor, new UUID(0x45757da31ea64cccL, 0x8b21029dd168dc10L+i), "historical-"+i, pos.offset(i%2,0,i/2));
            if (actor instanceof Villager villager) trades(villager);
            require(level.addFreshEntity(actor), "Could not add historical actor");
            residents.add(residentPacket(actor));
        }
        packet.put("Residents", residents);
        IOUtilities.writeNbtCompressed(packet, packet(server));
        // Halt in this same tick: baseline9's converter would otherwise rewrite
        // the two vanilla control actors before a genuine disk-load test existed.
        System.out.println("DUSK LEGACY SEEDED old Petal road=" + first + " unopened plaza=" + second
                + " saved resident UUID/type/trades/NBT controls=" + residents.size());
        return true;
    }

    private boolean legacy(MinecraftServer server) throws Exception {
        if (phase == 0) {
            if (!loaded(first)) return false;
            require(Boolean.FALSE.equals(fresh.get(first)), "Baseline road did not load from disk");
            if (!residentsPresent()) return false;
            verifyGeometry(saved);
            verifyResidents();
            require(!plan.dusk() && BlossomVillage.plan(level, plan.site(), true).isEmpty(), "Historical Petal site was reassigned");
            request(second);
            phase = 1;
            return false;
        }
        if (!loaded(second) || !residentsPresent()) return false;
        require(Boolean.valueOf(!stage.equals("legacy-restart")).equals(fresh.get(second)),
                "Historical shoulder/plaza did not exercise expected new vs saved terrain");
        verifyGeometry(saved);
        verifyResidents();
        require(!plan.dusk() && BlossomVillage.plan(level, plan.site(), true).isEmpty(), "Partial old site's new chunk changed faction");
        var lace = BuiltInRegistries.BLOCK.getKey(level.getBlockState(plan.plaza().below()).getBlock()).getPath();
        require(lace.equals("petal_quilt"), "Old Petal plaza was replaced: " + lace);
        System.out.println(stage.equals("legacy-restart") ? "DUSK LEGACY RESTART PASS stable frozen upgrade boundary, old resident UUID/type/trades/NBT"
                : "DUSK LEGACY PASS old geometry/residents unchanged, new chunk kept historical Petal assignment");
        return true;
    }

    private CompoundTag geometryPacket() {
        var packet = new CompoundTag();
        packet.putInt("Gx", plan.site().gx());
        packet.putInt("Gz", plan.site().gz());
        packet.putString("Plan", signature(plan));
        packet.putLong("OldChunk", first.toLong());
        packet.putLong("NewChunk", second.toLong());
        var states = new CompoundTag();
        var chunk = level.getChunkSource().getChunkNow(first.x, first.z);
        require(chunk != null, "Snapshot chunk not loaded");
        for (BlockPos pos : BlockPos.betweenClosed(first.getMinBlockX(), Math.max(level.getMinBuildHeight(), plan.y()-12), first.getMinBlockZ(),
                first.getMaxBlockX(), Math.min(level.getMaxBuildHeight()-1,plan.y()+40), first.getMaxBlockZ())) {
            var state = chunk.getBlockState(pos);
            var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id.getNamespace().equals("uwudimension") && (id.getPath().contains("ashlar") || id.getPath().contains("planks")
                    || id.getPath().contains("beam") || id.getPath().contains("roof_tile") || id.getPath().contains("shingle")
                    || id.getPath().contains("frieze") || id.getPath().contains("quilt")))
                states.putString(Long.toString(pos.asLong()), state.toString());
        }
        require(states.size() > 0, "No authored structural blocks in saved baseline road chunk");
        packet.put("States", states);
        return packet;
    }

    private void verifyGeometry(CompoundTag packet) {
        CompoundTag states = packet.getCompound("States");
        for (String position : states.getAllKeys()) {
            BlockPos pos = BlockPos.of(Long.parseLong(position));
            require(level.getBlockState(pos).toString().equals(states.getString(position)), "Historical block changed at " + pos);
        }
    }

    private CompoundTag residentPacket(Mob mob) {
        var row = new CompoundTag();
        row.putUUID("UUID", mob.getUUID());
        row.putString("Type", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        row.putString("Identity", mob.getPersistentData().getString("DuskFixtureIdentity"));
        CompoundTag nbt = mob.saveWithoutId(new CompoundTag());
        for (String key : List.of("CustomName", "Offers", "VillagerData", "Inventory", "NoAI", "Invulnerable"))
            if (nbt.contains(key)) row.put(key, nbt.get(key).copy());
        return row;
    }

    private boolean residentsPresent() {
        for (Tag tag : saved.getList("Residents", Tag.TAG_COMPOUND))
            if (level.getEntity(((CompoundTag)tag).getUUID("UUID")) == null) return false;
        return true;
    }

    private void verifyResidents() {
        for (Tag tag : saved.getList("Residents", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag)tag;
            Entity actor = level.getEntity(row.getUUID("UUID"));
            require(actor instanceof Mob, "Historical actor missing");
            require(row.equals(residentPacket((Mob)actor)), "Historical resident UUID/type/trades/NBT changed: " + row.getString("Identity"));
        }
    }

    private void verifyDuskVillage() {
        require(plan.dusk(), "New plan is not Dusk");
        require(PetalShrinePlacement.select(level, plan).isEmpty(), "Dusk selected Cupid shrine");
        int darkBlocks = 0;
        for (ChunkPos chunk : tickets) {
            var nativeChunk = level.getChunkSource().getChunkNow(chunk.x,chunk.z);
            if (nativeChunk == null) continue;
            for (BlockPos pos : BlockPos.betweenClosed(chunk.getMinBlockX(), plan.y()-10, chunk.getMinBlockZ(),
                    chunk.getMaxBlockX(),plan.y()+45,chunk.getMaxBlockZ())) {
                var block = nativeChunk.getBlockState(pos).getBlock();
                require(block != CupidShrine.BLOCK.get(), "Dusk generated a Cupid shrine at " + pos);
                if (BuiltInRegistries.BLOCK.getKey(block).getPath().equals("velvetwood_planks")) darkBlocks++;
            }
        }
        require(darkBlocks > 20, "Native Dusk houses did not generate");
        var residents = level.getEntitiesOfClass(Keeper.class, new AABB(plan.plaza()).inflate(100));
        require(!residents.isEmpty(), "Native Dusk village has no actual residents");
        require(residents.stream().allMatch(k->k.getType()==People.DUSK_KEEPER.get()), "Native village spawned unintended Petal residents");
    }

    private Set<ChunkPos> villageChunks() {
        var chunks = new LinkedHashSet<ChunkPos>();
        var a = plan.site().pos(plan.x()-60,0,plan.z()-60);
        var b = plan.site().pos(plan.x()+60,0,plan.z()+60);
        for (int x=Math.min(a.getX(),b.getX())>>4; x<=(Math.max(a.getX(),b.getX())>>4); x++)
            for (int z=Math.min(a.getZ(),b.getZ())>>4; z<=(Math.max(a.getZ(),b.getZ())>>4); z++) chunks.add(new ChunkPos(x,z));
        return chunks;
    }

    private void setup(Mob actor, UUID uuid, String identity, BlockPos pos) {
        actor.setUUID(uuid);
        actor.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        actor.setNoAi(true);
        actor.setNoGravity(true);
        actor.setInvulnerable(true);
        actor.setPersistenceRequired();
        actor.setCustomName(Component.literal(identity));
        actor.getPersistentData().putString("DuskFixtureIdentity",identity);
    }

    private void trades(Villager actor) {
        actor.getOffers().clear();
        actor.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD,3),new ItemStack(Items.BREAD,2),7,1,0));
    }

    private void request(ChunkPos chunk) {
        if(tickets.add(chunk))level.getChunkSource().addRegionTicket(TicketType.FORCED,chunk,0,chunk,true);
    }

    private boolean loaded(ChunkPos chunk) { return level.getChunkSource().getChunkNow(chunk.x,chunk.z)!=null; }

    private void load(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel world) || !world.dimension().equals(ReefLife.REACH)) return;
        var chunk=event.getChunk().getPos();
        loads.putIfAbsent(chunk,sequence.incrementAndGet());
        fresh.putIfAbsent(chunk,event.isNewChunk());
    }

    private Path packet(MinecraftServer server) { return server.getWorldPath(LevelResource.ROOT).resolve("uwu-dusk-check.nbt"); }

    private String signature(BlossomVillage.Plan p) {
        return "grid="+p.site().gx()+","+p.site().gz()+" site="+p.site().x()+","+p.site().z()+","+p.site().turns()
                +" plaza="+p.plaza().toShortString()+" dusk="+p.dusk()+" road="+Arrays.toString(p.road())
                +" bends="+Arrays.toString(p.bends())+" houses="+p.houses();
    }

    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
