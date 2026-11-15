package dev.uwu.test;

import java.util.UUID;
import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.GuardianTargeting;
import dev.uwu.dimension.HeartArrow;
import dev.uwu.dimension.People;
import dev.uwu.dimension.RelicFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Disposable server, player inventories, saved entities and the real item-use paths. */
public final class CombatRenewalProof {
    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    public static void run(ServerLevel level) throws Exception {
        BlockPos at = new BlockPos(8, level.getMaxBuildHeight()-16, 8);
        level.getChunkAt(at);
        try (var owner = new ChestProof.Visitor(level, at, "RelicKeeper", UUID.randomUUID())) {
            bows(level, owner.player);
            charm(level, at, owner.player);
            landing(level, owner.player);
            returns(level, owner.player);
            deathDuringThrow(level, at);
            guardian(level, at);
            saveAcrossRestart(level, owner.player);
        }
        System.out.println("UWU COMBAT RENEWAL PASS bow cooldowns, full-slot recovery and guardian stale target");
    }

    private static final java.nio.file.Path PLAYER_SAVE = java.nio.file.Path.of("mid-throw-player.dat");
    private static final java.nio.file.Path PROJECTILE_SAVE = java.nio.file.Path.of("mid-throw-entity.dat");

    private static void saveAcrossRestart(ServerLevel level, ServerPlayer player) throws Exception {
        player.getInventory().clearContent();
        var sword = Arsenal.WEAPONS.get(Arsenal.Relic.BLOSSOM_GREATSWORD).get();
        var item = new ItemStack(sword);
        player.getCooldowns().removeCooldown(sword);
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        item.use(level, player, InteractionHand.MAIN_HAND);
        RelicFlight flight = level.getEntitiesOfClass(RelicFlight.class, player.getBoundingBox().inflate(5),
                f -> f.getOwner()==player).getFirst();
        net.minecraft.nbt.NbtIo.writeCompressed(player.saveWithoutId(new CompoundTag()), PLAYER_SAVE);
        net.minecraft.nbt.NbtIo.writeCompressed(flight.saveWithoutId(new CompoundTag()), PROJECTILE_SAVE);
        flight.discard();
        GuardianTargeting.setCreeperAggro(level, false, level.getServer());
    }

    /** Called on the second dedicated server process in the disposable save. */
    public static void resume(ServerLevel level) throws Exception {
        require(!GuardianTargeting.creeperAggroOn(level), "Creeper rule lost on disk restart");
        CompoundTag ownerState = net.minecraft.nbt.NbtIo.readCompressed(PLAYER_SAVE,
                net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        CompoundTag flightState = net.minecraft.nbt.NbtIo.readCompressed(PROJECTILE_SAVE,
                net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var other = level.getServer().overworld();
        try (var visitor = new ChestProof.Visitor(other, BlockPos.ZERO, "RelicKeeper", ownerState.getUUID("UUID"))) {
            var player = visitor.player;
            player.load(ownerState);
            player.setPos(0, other.getMaxBuildHeight()-18, 0);
            player.tickCount = 20;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            var sword = Arsenal.WEAPONS.get(Arsenal.Relic.BLOSSOM_GREATSWORD).get();
            require(player.getInventory().countItem(sword)==1, "Offline owner failed to recover after restart and dimension change");
            // Simulate a late load of the original entity chunk. Even with a valid owner
            // the persisted projectile is visual only once the claim has been redeemed.
            var oldFlight = dev.uwu.dimension.RangedRelics.FLIGHT.get().create(level);
            oldFlight.load(flightState);
            oldFlight.setOwner(player);
            oldFlight.tick();
            require(oldFlight.isRemoved() && player.getInventory().countItem(sword)==1, "Stale chunk duplicated the delivery");
        }
        GuardianTargeting.setCreeperAggro(level, true, level.getServer());
        System.out.println("UWU RELIC RESTART PASS persisted owner, changed dimension, stale projectile and creeper rule");
    }

    private static void bows(ServerLevel level, ServerPlayer player) throws ReflectiveOperationException {
        for (var kind : new Arsenal.Relic[]{Arsenal.Relic.CUPID_BOW, Arsenal.Relic.RIPPLE_BOW}) {
            ItemStack bow = new ItemStack(Arsenal.WEAPONS.get(kind).get());
            player.setItemInHand(InteractionHand.MAIN_HAND, bow);
            bow.use(level, player, InteractionHand.MAIN_HAND);
            bow.releaseUsing(level, player, bow.getUseDuration(player) - 20);
            player.stopUsingItem();
            require(player.getCooldowns().isOnCooldown(bow.getItem()) == (kind == Arsenal.Relic.RIPPLE_BOW),
                    "Wrong firing cooldown for " + kind);
            require(bow.getDamageValue() == 1, "Draw and release did not fire");
            require(bow.getRarity() == (kind == Arsenal.Relic.CUPID_BOW ? Rarity.EPIC : Rarity.RARE), "Rarity leaked");
            if (kind == Arsenal.Relic.CUPID_BOW) rarity(level, bow);
        }
        for (var arrow : level.getEntitiesOfClass(HeartArrow.class, player.getBoundingBox().inflate(4),
                arrow -> arrow.getOwner() == player)) arrow.discard();
    }

    private static void rarity(ServerLevel level, ItemStack bow) throws ReflectiveOperationException {
        boolean expected = "1".equals(System.getenv("UWU_TEST_APOTHEOSIS"));
        require(net.neoforged.fml.ModList.get().isLoaded("apotheosis") == expected,
                "Optional Apotheosis fixture mismatch");
        var loaded = ItemStack.parseOptional(level.registryAccess(), (CompoundTag)bow.save(level.registryAccess()));
        for (var stack : new ItemStack[]{bow, loaded}) {
            var displayed = stack.getHoverName();
            int expectedNameColor = expected ? 0xED7014 : 0xFFAA00;
            var displayedColor = displayed.getStyle().getColor();
            require(stack.getRarity() == Rarity.EPIC && displayedColor != null
                    && displayedColor.getValue() == expectedNameColor,
                    "Wrong Cupid name/tier: "+displayed.getString()+" "+displayedColor+" base="+stack.getRarity());
            System.out.println("CUPID STACK name="+displayed.getString()+" base="+stack.getRarity()
                    +" nameColor="+Integer.toHexString(displayedColor.getValue()));
            var text = new java.util.ArrayList<net.minecraft.network.chat.Component>();
            stack.getItem().appendHoverText(stack, net.minecraft.world.item.Item.TooltipContext.EMPTY,
                    text, net.minecraft.world.item.TooltipFlag.NORMAL);
            require(text.stream().anyMatch(c -> c.getString().equals("Mythic")) == expected, "Mythic tooltip mismatch");
            if (!expected) continue;
            // Call the installed mod's public API without adding a dependency to either jar.
            var api = Class.forName("dev.shadowsoffire.apotheosis.affix.AffixHelper");
            Object holder = api.getMethod("getRarity", ItemStack.class).invoke(null, stack);
            require(holder != null && holder.getClass().getMethod("getId").invoke(holder).toString()
                    .equals("apotheosis:mythic"), "Apotheosis did not recognize the bow as Mythic");
            Object rarity = ((java.util.function.Supplier<?>)holder).get();
            Object render = rarity.getClass().getMethod("renderData").invoke(rarity);
            var color = (net.minecraft.network.chat.TextColor)rarity.getClass().getMethod("color").invoke(rarity);
            float height = (float)render.getClass().getMethod("beamHeight").invoke(render);
            require(color.getValue()==0xED7014 && height>0, "Mythic beam render data did not resolve");
            System.out.println("CUPID APOTHEOSIS tier=apotheosis:mythic label=Mythic beamColor="
                    +Integer.toHexString(color.getValue())+" beamHeight="+height);
        }
        System.out.println("SAKURA CUPID RARITY PASS "+(expected?"Apotheosis Mythic":"standalone Epic"));
    }

    private static void charm(ServerLevel level, BlockPos at, ServerPlayer owner) {
        try (var visitor = new ChestProof.Visitor(level, at, "RoseRecipient", UUID.randomUUID());
             var observer = new ChestProof.Visitor(level, at, "PetalObserver", UUID.randomUUID())) {
            ServerPlayer target = visitor.player;
            target.moveTo(owner.getX(), owner.getY(), owner.getZ() + 4, 180, 0);
            target.setNoGravity(true);
            level.addNewPlayer(target);
            var watcher = observer.player;
            watcher.moveTo(owner.getX()+3, owner.getY(), owner.getZ());
            watcher.setNoGravity(true);
            level.addNewPlayer(watcher);
            try {
                var bow = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get());
                owner.setItemInHand(InteractionHand.MAIN_HAND, bow);
                owner.setYRot(0);
                owner.setXRot(0);
                int duration = bow.getUseDuration(owner);
                for (int draw = 0; draw < 2; draw++) {
                    bow.use(level,owner,InteractionHand.MAIN_HAND);
                    bow.getItem().onUseTick(level,owner,bow,duration-19);
                    require(visitor.drawCues==draw && observer.drawCues==draw, "Partial draw emitted a cue");
                    bow.getItem().onUseTick(level,owner,bow,duration-20);
                    bow.getItem().onUseTick(level,owner,bow,duration-20);
                    bow.getItem().onUseTick(level,owner,bow,duration-43);
                    require(visitor.drawCues==draw+1 && observer.drawCues==draw+1, "Held full draw repeated its cue");
                    owner.stopUsingItem(); // Cancel each held draw without an arrow.
                }
                bow.use(level,owner,InteractionHand.MAIN_HAND);
                bow.getItem().onUseTick(level,owner,bow,duration-4);
                owner.stopUsingItem();
                require(visitor.drawCues==2 && observer.drawCues==2 && visitor.impactCues==0, "Canceled short draw flashed");
                shootAt(level, owner, bow);
                require(visitor.drawCues==3 && observer.drawCues==3 && visitor.impactCues==1 && observer.impactCues==1
                        && visitor.impactQuality==2 && observer.impactQuality==2 && visitor.impactY>target.getEyeY()+.65,
                        "Rapid release/draw, shared heart outline, or first-person clearance failed");
                var watcherPrefs = watcher.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                watcherPrefs.putInt("sakura_distance",2);
                watcher.getPersistentData().put(Player.PERSISTED_NBT_TAG,watcherPrefs);
                String key = "uwudimension.charm_owner";
                require(target.getPersistentData().hasUUID(key)
                        && target.getPersistentData().getUUID(key).equals(owner.getUUID()), "Real heart arrow failed to charm player");
                require(target.getPersistentData().getLong("uwudimension.charm_end") == level.getGameTime()+800,
                        "Wrong player charm duration");
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(target));
                require(!target.getPersistentData().hasUUID(key), "Player charm persisted after logout");
                int fullHearts = visitor.receivedCupidHearts;
                var preferences = target.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                preferences.putInt("sakura_effects", 0);
                target.getPersistentData().put(Player.PERSISTED_NBT_TAG, preferences);
                shootAt(level, owner, bow);
                int quietHearts = visitor.receivedCupidHearts - fullHearts;
                require(fullHearts>0 && quietHearts<=fullHearts && visitor.impactCues==2 && visitor.impactQuality==0
                        && observer.impactCues==1 && observer.drawCues==3 && visitor.drawCues==4,
                        "Quiet impact outline, rapid redraw, or distant viewer filter failed");
                System.out.println("SAKURA CUPID HEARTS PASS fullTrail="+fullHearts+" quietTrail="+quietHearts
                        +" impacts="+visitor.impactCues+" fullDrawCues="+visitor.drawCues+" distant="+(observer.impactCues-1));
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(
                        target, level.dimension(), net.minecraft.world.level.Level.OVERWORLD));
                require(!target.getPersistentData().hasUUID(key), "Player charm persisted after dimension change");
                watcherPrefs.putInt("sakura_distance",12);
                shootAt(level, owner, bow);
                require(observer.impactCues==2 && observer.drawCues==4, "Viewer's expanded distance did not restore cues");
                watcher.moveTo(owner.getX()+3, owner.getY()+37, owner.getZ());
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(
                        target, level.damageSources().generic()));
                require(!target.getPersistentData().hasUUID(key), "Player charm persisted after death notification");
                for (var mode : new net.minecraft.world.level.GameType[]{net.minecraft.world.level.GameType.CREATIVE,
                        net.minecraft.world.level.GameType.SPECTATOR}) {
                    target.setGameMode(mode);
                    shootAt(level, owner, bow);
                    require(!target.getPersistentData().hasUUID(key), "Cupid charmed " + mode);
                }
                target.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                var board = level.getScoreboard();
                var team = board.addPlayerTeam("renewalProof");
                try {
                    team.setAllowFriendlyFire(false);
                    board.addPlayerToTeam(target.getScoreboardName(), team);
                    board.addPlayerToTeam(owner.getScoreboardName(), team);
                    shootAt(level, owner, bow);
                    require(!target.getPersistentData().hasUUID(key), "Cupid bypassed friendly fire setting");
                } finally {
                    board.removePlayerTeam(team);
                }
            } finally {
                watcher.discard();
                target.discard();
            }
        }
    }

    private static void shootAt(ServerLevel level, ServerPlayer owner, ItemStack bow) {
        bow.use(level, owner, InteractionHand.MAIN_HAND);
        bow.releaseUsing(level, owner, bow.getUseDuration(owner)-20);
        owner.stopUsingItem();
        for (var arrow : level.getEntitiesOfClass(HeartArrow.class, owner.getBoundingBox().inflate(3),
                a -> a.getOwner()==owner)) {
            for (int t=0; t<5 && arrow.isAlive(); t++) arrow.tick();
            arrow.discard();
        }
    }

    private static void landing(ServerLevel level, ServerPlayer player) {
        var sword = Arsenal.WEAPONS.get(Arsenal.Relic.BLOSSOM_GREATSWORD).get();
        var held = new ItemStack(sword);
        player.setItemInHand(InteractionHand.OFF_HAND, held);
        player.setYRot(0);
        player.setXRot(0);
        BlockPos wall = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()+4);
        var original = level.getBlockState(wall);
        level.setBlockAndUpdate(wall, net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState());
        try {
            held.use(level, player, InteractionHand.OFF_HAND);
            RelicFlight flight = level.getEntitiesOfClass(RelicFlight.class, player.getBoundingBox().inflate(4),
                    f -> f.getOwner()==player).getFirst();
            for (int i=0; i<6 && !flight.landed(); i++) flight.tick();
            require(flight.landed(), "Sword did not embed on block impact");
            var stuckAt = flight.position();
            for (int t=1; t<16; t++) {
                flight.tick();
                require(flight.landed() && flight.position().distanceToSqr(stuckAt)<1e-5, "Sword moved during landing pause");
            }
            for (int t=0; t<15 && flight.isAlive(); t++) flight.tick();
            require(!flight.isAlive() && player.getOffhandItem().is(sword), "Embedded sword did not return");
            player.getCooldowns().removeCooldown(sword);
        } finally {
            level.setBlockAndUpdate(wall, original);
        }
    }

    private static void returns(ServerLevel level, ServerPlayer player) {
        var sword = Arsenal.WEAPONS.get(Arsenal.Relic.BLOSSOM_GREATSWORD).get();
        var held = new ItemStack(sword);
        held.set(DataComponents.CUSTOM_NAME, Component.literal("Proof of ownership"));
        player.setItemInHand(InteractionHand.OFF_HAND, held);
        held.use(level, player, InteractionHand.OFF_HAND);
        require(player.getOffhandItem().isEmpty(), "Throw never left the hand");
        var flights = level.getEntitiesOfClass(RelicFlight.class, player.getBoundingBox().inflate(5),
                flight -> flight.getOwner() == player);
        require(flights.size() == 1, "Throw must create one projectile");
        var escrow = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("uwudimension.relic_returns");
        require(escrow.contains(flights.getFirst().getStringUUID()), "Missing player's durable flight claim");
        flights.getFirst().discard();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            player.getInventory().setItem(i, new ItemStack(Items.GLASS));
        player.tickCount = 20;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        require(!escrow.isEmpty(), "Full inventory lost the claim");
        CompoundTag snapshot = player.saveWithoutId(new CompoundTag());
        require(snapshot.getCompound("NeoForgeData").getCompound(Player.PERSISTED_NBT_TAG)
                .getCompound("uwudimension.relic_returns").size() == 1, "Player save omitted weapon");
        player.getInventory().setItem(3, ItemStack.EMPTY);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        ItemStack returned = player.getInventory().getItem(3);
        require(returned.is(sword) && returned.getDamageValue() == 1
                && returned.getHoverName().getString().equals("Proof of ownership"), "Returned stack lost components");
        require(player.getInventory().countItem(sword) == 1 && escrow.isEmpty(), "Return duplicated a sword");
    }

    private static void deathDuringThrow(ServerLevel level, BlockPos at) {
        UUID id = UUID.randomUUID();
        var sword = Arsenal.WEAPONS.get(Arsenal.Relic.BLOSSOM_GREATSWORD).get();
        try (var original = new ChestProof.Visitor(level, at, "PetalKnight", id);
             var replacement = new ChestProof.Visitor(level, at, "PetalKnight", id)) {
            var before = original.player;
            var after = replacement.player;
            var held = new ItemStack(sword);
            held.set(DataComponents.CUSTOM_NAME, Component.literal("Petal Knight's Keepsake"));
            before.setItemInHand(InteractionHand.MAIN_HAND, held);
            held.use(level, before, InteractionHand.MAIN_HAND);
            var flight = level.getEntitiesOfClass(RelicFlight.class, before.getBoundingBox().inflate(4),
                    f -> f.getOwner()==before).getFirst();
            before.setHealth(0);
            before.die(before.damageSources().genericKill());
            flight.tick();
            require(!flight.isAlive() && before.getInventory().countItem(sword)==0, "Dead owner received a relic");
            after.restoreFrom(before, false);
            after.tickCount = 20;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(after));
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(after));
            var caught = after.getMainHandItem();
            require(caught.is(sword) && caught.getDamageValue()==1
                    && caught.getHoverName().getString().equals("Petal Knight's Keepsake")
                    && after.getInventory().countItem(sword)==1, "Player respawn lost or duplicated the thrown stack");
        }
    }

    private static void blockedCrowd(ServerLevel level, BlockPos at, BlossomWarden guardian,
            CompoundTag save, net.minecraft.world.entity.LivingEntity reachable) {
        BlockPos prison = at.offset(4, 0, 4);
        var trapped = EntityType.HUSK.create(level);
        trapped.moveTo(prison.getX()+.5, prison.getY(), prison.getZ()+.5);
        level.addFreshEntity(trapped);
        var removed = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        try {
            // An enclosed target offers no collision-free adjacent intercept position.
            for (int x=-2; x<=2; x++) for (int z=-2; z<=2; z++) {
                if (x==0 && z==0) continue;
                var pos = prison.offset(x, 1, z);
                removed.put(pos, level.getBlockState(pos));
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), 2);
            }
            var queue = new net.minecraft.nbt.ListTag();
            for (UUID id : new UUID[]{trapped.getUUID(), reachable.getUUID()}) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Offender", id);
                queue.add(entry);
            }
            save.put("Offenders", queue);
            save.putInt("PursueTicks", 599);
            guardian.load(save);
            reachable.moveTo(at.getX()+1, at.getY(), at.getZ());
            guardian.aiStep();
            require(reachable.getUUID().equals(guardian.offender()), "A blocked enemy starved the reachable queue");
            guardian.aiStep();
            require(guardian.gripping(), "Guardian ignored the next reachable enemy");
        } finally {
            removed.forEach((pos, original)->level.setBlock(pos, original, 2));
            trapped.discard();
        }
    }

    private static void guardian(ServerLevel level, BlockPos at) {
        // Keep combatants at the same elevation when advancing only the guardian's AI.
        // In midair its real gravity advances while the stationary probe targets do not.
        var floor = new java.util.HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (int x=-5; x<=7; x++) for (int z=-5; z<=7; z++) {
            BlockPos pos = at.offset(x, -1, z);
            floor.put(pos, level.getBlockState(pos));
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        }
        BlossomWarden guardian = People.BLOSSOM_WARDEN.get().create(level);
        var threat = EntityType.HUSK.create(level);
        guardian.moveTo(at.getX(), at.getY(), at.getZ());
        guardian.bindVillage(at);
        var dead = EntityType.HUSK.create(level);
        dead.moveTo(at.getX()+6, at.getY(), at.getZ());
        level.addFreshEntity(dead);
        dead.setHealth(0);
        threat.moveTo(at.getX()+2, at.getY(), at.getZ());
        level.addFreshEntity(threat);
        try {
            UUID missing = UUID.randomUUID();
            CompoundTag save = guardian.saveWithoutId(new CompoundTag());
            var queue = new net.minecraft.nbt.ListTag();
            for (UUID id : new UUID[]{missing, dead.getUUID(), threat.getUUID()}) {
                var entry = new CompoundTag();
                entry.putUUID("Offender", id);
                queue.add(entry);
            }
            save.put("Offenders", queue);
            save.putByte("GripPhase", (byte)1);
            guardian.load(save);
            for (int i = 0; i < 10; i++) guardian.aiStep();
            guardian.aiStep(); // Immediately discard the queued dead mob.
            require(threat.getUUID().equals(guardian.offender()), "Vanished mob still blocks the queue");
            guardian.aiStep();
            require(guardian.gripping(), "Guardian did not resume combat: guardian="+guardian.position()
                    +" threat="+threat.position()+" queue="+guardian.offender()+" pose="+(guardian.throwing()?"throw":"chase"));
            require(guardian.getMaxHealth() == 6666 && guardian.getEffect(MobEffects.REGENERATION).getAmplifier()==3
                    && guardian.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier()==3, "Guardian buff regression");
            require(threat.getUUID().equals(guardian.offender()), "Crowded queue lost active threat");
            // Directly advance the native AI against a stranded target. Reset only the
            // impossible crossing on each tick so no pathfinder movement masks the timer.
            save.putByte("GripPhase", (byte) 1);
            var stranded = new net.minecraft.nbt.ListTag();
            var head = new CompoundTag();
            head.putUUID("Offender", threat.getUUID());
            stranded.add(head);
            save.put("Offenders", stranded);
            guardian.load(save);
            threat.moveTo(at.getX()+6, at.getY(), at.getZ());
            for (int i=0; i<64 && !guardian.gripping(); i++) {
                guardian.moveTo(at.getX()-2, at.getY(), at.getZ());
                guardian.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                guardian.setNoGravity(true);
                guardian.aiStep();
            }
            require(guardian.gripping(), "Stalled pursuit failed to intercept");
            blockedCrowd(level, at, guardian, save, threat);
            var handmade = EntityType.IRON_GOLEM.create(level);
            handmade.moveTo(at.getX(), at.getY()+4, at.getZ());
            handmade.setPlayerCreated(true);
            require(level.addFreshEntity(handmade), "Pumpkin-built golem replaced");
            handmade.discard();
            var natural = EntityType.IRON_GOLEM.create(level);
            natural.moveTo(at.getX(), at.getY(), at.getZ());
            require(!level.addFreshEntity(natural), "Natural golem escaped replacement");
            var restored = People.BLOSSOM_WARDEN.get().create(level);
            var guardianSave = guardian.saveWithoutId(new CompoundTag());
            for (boolean female : new boolean[]{false, true}) {
                guardianSave.putBoolean("FemaleGuardian", female);
                restored.load(guardianSave);
                require(restored.female() == female && restored.getMaxHealth()==6666, "Guardian variant changed on load");
            }
            guardianSave.remove("FemaleGuardian");
            restored.load(guardianSave);
            require(!restored.female(), "Legacy saved guardian unexpectedly changed appearance");
            boolean before = GuardianTargeting.creeperAggroOn(level);
            GuardianTargeting.setCreeperAggro(level, !before, level.getServer());
            require(GuardianTargeting.creeperAggroOn(level) != before, "Creeper setting did not change");
            GuardianTargeting.setCreeperAggro(level, before, level.getServer());
        } finally {
            dead.discard();
            threat.discard();
            guardian.discard();
            floor.forEach((pos, original)->level.setBlock(pos, original, 2));
        }
    }
}
