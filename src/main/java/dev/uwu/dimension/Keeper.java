package dev.uwu.dimension;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

public final class Keeper extends Villager {
    private static final String ALLEGIANCE = "uwudimension_fellowship";
    private final Map<UUID, Long> peacefulUntil = new HashMap<>();
    private UUID companion;
    private boolean settled;
    private BlockPos home;
    private long nextGift;
    private BlockPos village;
    private long nextCourtship;
    private boolean legacyHome;
    private boolean checkedVillageSites;
    private int offeredJobLevel;
    private boolean adaptedResident;
    public void markAdaptedResident(){adaptedResident=true;}
    private static final int FACTION_OFFERS = 4;

    @Override public float getAgeScale() { return isBaby() ? .5625F : 1F; }

    public Keeper(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    boolean dusk() { return getType() == People.DUSK_KEEPER.get(); }
    private String side() { return dusk() ? "dusk" : "petal"; }
    private boolean owns(Player player) { return player.getUUID().equals(companion); }
    private boolean showsMoney(Player player) { return player.isHolding(Items.EMERALD); }

    private boolean allied(Player player) {
        return side().equals(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getString(ALLEGIANCE));
    }

    private boolean attacks(Player player) {
        return dusk() && !owns(player) && !allied(player) && !showsMoney(player)
                && peacefulUntil.getOrDefault(player.getUUID(), 0L) <= level().getGameTime()
                && player != getTradingPlayer() && !player.isCreative() && !player.isSpectator();
    }

    @Override
    protected void registerGoals() {
        // The vanilla Villager brain owns movement, doors, shelter, sleep and work.
        // Only faction combat and an explicit follower order may override its walk target.
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.02, true));
        goalSelector.addGoal(2, new FollowFriend());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Keeper.class, true,
                mob -> mob instanceof Keeper keeper && keeper.dusk() != dusk()));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                mob -> mob instanceof Player player && attacks(player)));
    }

    private final class FollowFriend extends Goal {
        private Player friend;
        private int nextPath;

        FollowFriend() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            if (companion == null || settled || isTrading() || getTarget() != null) return false;
            friend = level().getPlayerByUUID(companion);
            return friend != null && friend.isAlive() && !friend.isSpectator() && distanceToSqr(friend) > 16;
        }

        @Override public boolean canContinueToUse() {
            return !settled && !isTrading() && getTarget() == null && friend != null && friend.isAlive()
                    && !friend.isSpectator() && distanceToSqr(friend) > 4 && friend.level() == level();
        }

        @Override public void tick() {
            getLookControl().setLookAt(friend, 20, 30);
            if (--nextPath <= 0) {
                nextPath = 8;
                getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(friend, 1.13F, 2));
            }
        }

        @Override public void stop() { friend = null; }
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        Player previous = getTradingPlayer();
        super.setTradingPlayer(player);
        if (player == null && previous != null)
            peacefulUntil.put(previous.getUUID(), level().getGameTime() + 400);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && (isTrading() || target instanceof Player player && !attacks(player))) return;
        super.setTarget(target);
    }

    @Override
    public boolean doHurtTarget(Entity victim) {
        if (victim instanceof Player player && !attacks(player)) {
            setTarget(null);
            return false;
        }
        return super.doHurtTarget(victim);
    }

    @Override
    public void aiStep() {
        if (level() instanceof ServerLevel server) {
            long now = server.getGameTime();
            if (village != null && !checkedVillageSites) {
                checkedVillageSites = true;
                indexFurnishings(server, blockPosition(), 3);
                if (server.hasChunkAt(village)) indexFurnishings(server, village, 2);
            }
            if (dusk() && tickCount % 5 == 0) {
                peacefulUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
                for (Player nearby : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(24),
                        p -> showsMoney(p) && hasLineOfSight(p))) {
                    peacefulUntil.put(nearby.getUUID(), now + 400);
                }
            }
            if (getTarget() instanceof Player player && !attacks(player)) {
                setTarget(null);
                getNavigation().stop();
            }
            if (tickCount % 20 == 0) {
                if ((companion == null || settled) && home == null) assignHome(blockPosition());
                if (!isTrading() && companion != null && getTarget() == null) defendCompanion();
                if (!isTrading() && !isSleeping() && getTarget() == null && now % 100 < 20)
                    liveWithNeighbors(server, now);
                if (legacyHome && tickCount >= 60 && !isTrading() && companion == null) {
                    // Old hamlets already remember each keeper's home post. Neighbors adopt
                    // one common post as their court without changing a single legacy block.
                    var neighbors = server.getEntitiesOfClass(Keeper.class,getBoundingBox().inflate(34),
                            k -> k.dusk()==dusk() && k.companion==null && k.home!=null);
                    if (neighbors.size() >= 2) {
                        BlockPos shared = neighbors.stream().filter(k -> k.village!=null)
                                .map(k->k.village).min(BlockPos::compareTo).orElseGet(() ->
                                        neighbors.stream().map(k->k.home).min(BlockPos::compareTo).orElse(home));
                        bindVillage(shared);
                        legacyHome=false;
                    }
                }
                if (village != null && tickCount % 200 == 0 && blockPosition().closerThan(village, 40))
                    BlossomWarden.ensureVillageGuard(server, village);
                if (!dusk() && now >= nextGift && !isTrading() && getTarget() == null) shareGift(server, now);
            }
        }
        super.aiStep();
    }

    // Worldgen writes the house into a protochunk before the live POI manager is available.
    // Reconcile actual furnishings once on load; the vanilla brain claims its own tickets.
    private static void indexFurnishings(ServerLevel level, BlockPos center, int radius) {
        var pois = level.getPoiManager();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-radius,-1,-radius), center.offset(radius,1,radius))) {
            if (!level.hasChunkAt(at)) continue;
            var type = PoiTypes.forState(level.getBlockState(at));
            if (type.isPresent() && pois.getType(at).isEmpty()) pois.add(at.immutable(), type.get());
        }
    }

    private void assignHome(BlockPos pos) {
        home = pos.immutable();
        restrictTo(home, 32);
    }

    public void bindVillage(BlockPos court) { village = court.immutable(); }
    @Nullable public BlockPos villageCourt() { return village; }

    @Override public boolean hurt(DamageSource source, float amount) {
        float health = getHealth() + getAbsorptionAmount();
        boolean accepted = super.hurt(source, amount);
        if (accepted && getHealth() + getAbsorptionAmount() < health
                && level() instanceof ServerLevel server && source.getEntity() instanceof LivingEntity offender)
            BlossomWarden.alert(server, this, offender);
        return accepted;
    }

    // Vanilla TradeWithVillager and VillagerMakeLove check the exact vanilla entity type.
    // Supply the matching-faction interaction while reusing vanilla food points, inventories,
    // bed POI tickets, navigation and breeding cooldowns.
    private void liveWithNeighbors(ServerLevel server, long now) {
        if (companion != null && !settled || isBaby()) return;
        var brain = getBrain();
        if (!brain.isActive(Activity.MEET) && !brain.isActive(Activity.IDLE)) return;
        for (Keeper neighbor : server.getEntitiesOfClass(Keeper.class, getBoundingBox().inflate(4),
                k -> k != this && k.dusk() == dusk() && k.isAlive() && !k.isSleeping() && !k.isTrading())) {
            if (!hasLineOfSight(neighbor)) continue;
            gossip(server, neighbor, now);
            if (hasExcessFood() && neighbor.wantsMoreFood()) {
                for (int slot = 0; slot < getInventory().getContainerSize(); slot++) {
                    ItemStack food = getInventory().getItem(slot);
                    if (FOOD_POINTS.containsKey(food.getItem()) && food.getCount() >= 6) {
                        BehaviorUtils.throwItem(this, getInventory().removeItem(slot, food.getCount() / 2), neighbor.position());
                        break;
                    }
                }
            }
            if (now >= nextCourtship && canBreed() && neighbor.canBreed() && getId() < neighbor.getId()) {
                nextCourtship = now + 600;
                var bed = server.getPoiManager().take(t -> t.is(PoiTypes.HOME), (t, pos) -> {
                    var path = getNavigation().createPath(pos, 1);
                    return path != null && path.canReach();
                }, blockPosition(), 40);
                if (bed.isPresent()) {
                    Keeper child = getBreedOffspring(server, neighbor);
                    if (child != null) {
                        child.setAge(-24000);
                        child.moveTo(getX(), getY(), getZ(), getYRot(), 0);
                        child.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(server.dimension(), bed.get()));
                        if (server.addFreshEntity(child)) {
                            eatAndDigestFood(); neighbor.eatAndDigestFood(); setAge(6000); neighbor.setAge(6000);
                            server.broadcastEntityEvent(child, (byte)18);
                        } else server.getPoiManager().release(bed.get());
                    } else server.getPoiManager().release(bed.get());
                }
            }
            break;
        }
    }

    private void defendCompanion() {
        Player player = level().getPlayerByUUID(companion);
        if (player == null || distanceToSqr(player) > 256) return;
        LivingEntity threat = player.getLastHurtByMob();
        if (player.tickCount - player.getLastHurtByMobTimestamp() > 100) return;
        if (threat instanceof Mob mob && mob.isAlive() && mob.level() == level()
                && distanceToSqr(mob) < 256 && !(mob instanceof Keeper k && dusk() == k.dusk())) setTarget(mob);
    }

    private void shareGift(ServerLevel server, long now) {
        nextGift = now + 1200 + random.nextInt(1600);
        Player recipient = server.getNearestPlayer(this, 7);
        if (recipient == null || !hasLineOfSight(recipient)) return;
        if (recipient.getHealth() < recipient.getMaxHealth()) {
            recipient.heal(4);
            server.sendParticles(ParticleTypes.HEART, recipient.getX(), recipient.getEyeY(), recipient.getZ(),
                    3, 0.25, 0.20, 0.25, 0.01);
        } else if (recipient.getFoodData().needsFood() || random.nextFloat() < 0.3F) {
            ItemStack food = new ItemStack(Items.PUMPKIN_PIE);
            if (!recipient.addItem(food)) recipient.drop(food, false);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (isTrading() && getTradingPlayer() != player) return InteractionResult.CONSUME;
        ItemStack held = player.getItemInHand(hand);
        if (attacks(player)) {
            player.displayClientMessage(Component.literal("The keeper watches your hands. Show an emerald."), true);
            return InteractionResult.CONSUME;
        }
        if (getTarget() == player) setTarget(null);
        var food = held.get(DataComponents.FOOD);
        if (food != null && getHealth() < getMaxHealth()) {
            heal(Math.max(1, food.nutrition()));
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
                food.usingConvertsTo().ifPresent(container -> {
                    var remainder = container.copy();
                    if (held.isEmpty()) player.setItemInHand(hand, remainder);
                    else if (!player.addItem(remainder)) player.drop(remainder, false);
                });
            }
            playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 0.8F, 1.2F);
            ((ServerLevel)level()).sendParticles(ParticleTypes.HEART,getX(),getEyeY(),getZ(),4,.2,.1,.2,0);
            return InteractionResult.CONSUME;
        }
        if (!player.isShiftKeyDown() && held.getItem() instanceof BlockItem blockItem) {
            var type = PoiTypes.forState(blockItem.getBlock().defaultBlockState());
            if (type.isPresent()) {
                var job = BuiltInRegistries.VILLAGER_PROFESSION.stream()
                        .filter(p -> p != VillagerProfession.NONE && p.heldJobSite().test(type.get())).findFirst();
                if (job.isPresent()) {
                    teachProfession((ServerLevel)level(), player, job.get());
                    return InteractionResult.CONSUME;
                }
            }
        }
        if (isBaby() || isSleeping()) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (owns(player) && held.isEmpty()) {
                settled = !settled;
                if (settled) assignHome(blockPosition());
                else { clearRestriction(); home = null; releaseDailyPois(); }
                player.displayClientMessage(Component.literal(settled ? "Your keeper will live here." : "Your keeper will follow you."), true);
            } else if (held.is(Items.EMERALD)) {
                recruit(player, held);
            } else {
                player.displayClientMessage(Component.literal("Recruit with four emeralds in hand. Guide a recruit with an empty hand."), true);
            }
            return InteractionResult.CONSUME;
        }
        getNavigation().stop();
        setTarget(null);
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), getVillagerData().getLevel());
        return InteractionResult.CONSUME;
    }

    private void teachProfession(ServerLevel level, Player player, VillagerProfession job) {
        if (isBaby() || isSleeping()) {
            player.displayClientMessage(Component.literal(isBaby() ? "This keeper can learn a job after growing up." : "Wait until this keeper wakes up to teach a job."), true);
            return;
        }
        if (getVillagerData().getProfession() != VillagerProfession.NONE || getVillagerXp() > 0) {
            player.displayClientMessage(Component.literal("Only an unemployed keeper who has never traded can learn a new job."), true);
            return;
        }
        var pois = level.getPoiManager();
        var pending = getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        var station = pending.filter(p -> p.dimension().equals(level.dimension()) && p.pos().closerThan(blockPosition(), 12.01)
                        && pois.getType(p.pos()).filter(job.heldJobSite()).isPresent()
                        && canReachStation(p.pos()))
                .map(GlobalPos::pos);
        if (station.isEmpty()) {
            station = pois.take(job.heldJobSite(), (type, pos) -> canReachStation(pos), blockPosition(), 12);
            if (station.isEmpty()) {
                player.displayClientMessage(Component.literal("Place an unclaimed matching workstation within 12 blocks with a clear path to it."), true);
                return;
            }
            if (pending.isPresent()) releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        }
        getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), station.get()));
        setVillagerData(getVillagerData().setProfession(job));
        refreshBrain(level);
        level.broadcastEntityEvent(this, (byte)14);
        player.displayClientMessage(Component.literal("Your keeper learned a job: ")
                .append(Component.translatable("entity.minecraft.villager." + BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).getPath())), true);
    }

    private boolean canReachStation(BlockPos station) {
        // Reaching a directly adjacent workstation does not require a walk, including
        // the tick an entity lands or enters the world before navigation is initialized.
        if (blockPosition().distManhattan(station) <= 1) return true;
        var path = getNavigation().createPath(station, 1);
        return path != null && path.canReach();
    }

    private void recruit(Player player, ItemStack held) {
        CompoundTag root = player.getPersistentData();
        CompoundTag saved = root.getCompound(Player.PERSISTED_NBT_TAG);
        String allegiance = saved.getString(ALLEGIANCE);
        if (companion != null) {
            player.displayClientMessage(Component.literal(owns(player) ? "Already your companion. Crouch with an empty hand to settle or follow." : "This keeper already has a companion."), true);
        } else if (!allegiance.isEmpty() && !allegiance.equals(side())) {
            player.displayClientMessage(Component.literal("Your oath belongs to the other settlement."), true);
        } else if (held.getCount() < 4 && !player.isCreative()) {
            player.displayClientMessage(Component.literal("Hold four emeralds together to join this keeper's settlement."), true);
        } else {
            saved.putString(ALLEGIANCE, side());
            root.put(Player.PERSISTED_NBT_TAG, saved);
            if (!player.isCreative()) held.shrink(4);
            companion = player.getUUID();
            home = null;
            settled = false;
            clearRestriction();
            releaseDailyPois();
            playSound(getNotifyTradeSound(), 0.8F, 1.12F);
            player.displayClientMessage(Component.literal("Oath accepted. Crouch and interact with an empty hand to settle your keeper."), true);
        }
    }

    private void releaseDailyPois() {
        if (!(level() instanceof ServerLevel)) return;
        for (var memory : java.util.List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE,
                MemoryModuleType.POTENTIAL_JOB_SITE, MemoryModuleType.MEETING_POINT)) {
            releasePoi(memory);
            getBrain().eraseMemory(memory);
        }
    }

    @Override public void setVillagerData(VillagerData updated) {
        MerchantOffers previous = offers;
        boolean changed = getVillagerData().getProfession() != updated.getProfession();
        super.setVillagerData(updated);
        if (changed) {
            offeredJobLevel = 0;
            if (previous != null) {
                while (previous.size() > FACTION_OFFERS) previous.removeLast();
                offers = previous;
            }
        }
    }

    @Override public MerchantOffers getOffers() {
        MerchantOffers current = super.getOffers();
        if (!level().isClientSide && offeredJobLevel < getVillagerData().getLevel()
                && getVillagerData().getProfession() != VillagerProfession.NONE) updateTrades();
        return current;
    }

    @Override protected void updateTrades() {
        if (offers == null) {
            getOffers();
            return;
        }
        if (offers.isEmpty()) addFactionOffers();
        int level = getVillagerData().getLevel();
        if (getVillagerData().getProfession() != VillagerProfession.NONE && offeredJobLevel < level) {
            offeredJobLevel = level;
            super.updateTrades();
        }
    }

    private void addFactionOffers() {
        if (dusk()) {
            sell(1, Items.BLACK_CANDLE, 3);
            sell(2, Items.SOUL_LANTERN, 2);
            sell(5, Items.ENDER_PEARL, 2);
            buy(Items.AMETHYST_SHARD, 6);
        } else {
            sell(1, Items.PUMPKIN_PIE, 3);
            sell(2, Items.PINK_PETALS, 12);
            sell(3, Items.CHERRY_SAPLING, 2);
            buy(Items.HONEYCOMB, 4);
        }
    }

    private void sell(int price, Item item, int quantity) {
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), new ItemStack(item, quantity), 12, 1, 0.0F));
    }

    private void buy(Item item, int quantity) {
        offers.add(new MerchantOffer(new ItemCost(item, quantity), new ItemStack(Items.EMERALD), 8, 1, 0.0F));
    }

    @Override @Nullable public Keeper getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Keeper child = (dusk() ? People.DUSK_KEEPER : People.PETAL_KEEPER).get().create(level);
        if (child != null && village != null) child.bindVillage(village);
        return child;
    }
    @Override public boolean canBreed() { return super.canBreed() && (companion == null || settled); }
    @Override public void spawnGolemIfNeeded(ServerLevel world, long time, int minimumVillagers) {
        // This settlement's bell is guarded by its flower guardian.
        if (village != null) BlossomWarden.ensureVillageGuard(world, village);
    }
    @Override public SoundEvent getNotifyTradeSound() { return dusk() ? People.DUSK_VOICE.get() : People.PETAL_VOICE.get(); }
    @Override protected SoundEvent getTradeUpdatedSound(boolean valid) { return getNotifyTradeSound(); }
    @Override protected SoundEvent getAmbientSound() { return getNotifyTradeSound(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return getNotifyTradeSound(); }
    @Override public int getAmbientSoundInterval() { return 260; }

    @Override public void addAdditionalSaveData(CompoundTag data) {
        super.addAdditionalSaveData(data);
        if(adaptedResident)data.putBoolean("AdaptedResident",true);
        if (companion != null) data.putUUID("Companion", companion);
        if (home != null) data.putLong("Hearth", home.asLong());
        data.putBoolean("Settled", settled);
        data.putLong("NextGift", nextGift);
        data.putInt("OfferedJobLevel", offeredJobLevel);
        if (village != null) data.putLong("VillageCourt", village.asLong());
        if (legacyHome) data.putBoolean("SeekLegacyCourt", true);
    }

    @Override public void readAdditionalSaveData(CompoundTag data) {
        super.readAdditionalSaveData(data);
        adaptedResident=adaptedResident||data.getBoolean("AdaptedResident");
        offeredJobLevel = adaptedResident&&!data.contains("OfferedJobLevel")&&data.contains("Offers")?getVillagerData().getLevel():data.getInt("OfferedJobLevel");
        companion = data.hasUUID("Companion") ? data.getUUID("Companion") : null;
        settled = data.getBoolean("Settled");
        if (data.contains("Hearth")) assignHome(BlockPos.of(data.getLong("Hearth")));
        nextGift = data.getLong("NextGift");
        if (data.contains("VillageCourt")) village = BlockPos.of(data.getLong("VillageCourt"));
        legacyHome = village==null && (data.contains("RestockDay") || data.getBoolean("SeekLegacyCourt"));
    }
}
