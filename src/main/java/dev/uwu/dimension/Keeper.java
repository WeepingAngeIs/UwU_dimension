package dev.uwu.dimension;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

public final class Keeper extends AbstractVillager {
    private static final String ALLEGIANCE = "uwudimension_fellowship";
    private final Map<UUID, Long> peacefulUntil = new HashMap<>();
    private UUID companion;
    private boolean settled;
    private BlockPos home;
    private long nextGift;
    private long restockDay = -1;

    public Keeper(EntityType<? extends AbstractVillager> type, Level level) {
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
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.02, true));
        goalSelector.addGoal(3, new FollowFriend());
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.9));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 7));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
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
                getNavigation().moveTo(friend, 1.13);
            }
        }

        @Override public void stop() { getNavigation().stop(); friend = null; }
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
                long day = server.getDayTime() / 24000L;
                if (restockDay != day && !isTrading()) {
                    if (offers != null) offers.forEach(MerchantOffer::resetUses);
                    restockDay = day;
                }
                if (!dusk() && now >= nextGift && !isTrading() && getTarget() == null) shareGift(server, now);
            }
        }
        super.aiStep();
    }

    private void assignHome(BlockPos pos) {
        home = pos.immutable();
        restrictTo(home, 7);
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
        if (!isAlive() || isBaby()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (isTrading() && getTradingPlayer() != player) return InteractionResult.CONSUME;
        ItemStack held = player.getItemInHand(hand);
        if (attacks(player)) {
            player.displayClientMessage(Component.literal("The keeper watches your hands. Show an emerald."), true);
            return InteractionResult.CONSUME;
        }
        if (getTarget() == player) setTarget(null);
        if (player.isShiftKeyDown()) {
            if (owns(player) && held.isEmpty()) {
                settled = !settled;
                if (settled) assignHome(blockPosition());
                else { clearRestriction(); home = null; }
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
        openTradingScreen(player, getDisplayName(), 1);
        return InteractionResult.CONSUME;
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
            playSound(getNotifyTradeSound(), 0.8F, 1.12F);
            player.displayClientMessage(Component.literal("Oath accepted. Crouch and interact with an empty hand to settle your keeper."), true);
        }
    }

    @Override protected void updateTrades() {
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
        getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, price), new ItemStack(item, quantity), 12, 1, 0.0F));
    }

    private void buy(Item item, int quantity) {
        getOffers().add(new MerchantOffer(new ItemCost(item, quantity), new ItemStack(Items.EMERALD), 8, 1, 0.0F));
    }

    @Override protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp() && level() instanceof ServerLevel server)
            ExperienceOrb.award(server, position(), 2 + random.nextInt(3));
    }

    @Override @Nullable public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) { return null; }
    @Override public SoundEvent getNotifyTradeSound() { return dusk() ? People.DUSK_VOICE.get() : People.PETAL_VOICE.get(); }
    @Override protected SoundEvent getTradeUpdatedSound(boolean valid) { return getNotifyTradeSound(); }
    @Override protected SoundEvent getAmbientSound() { return getNotifyTradeSound(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return getNotifyTradeSound(); }
    @Override public int getAmbientSoundInterval() { return 260; }

    @Override public void addAdditionalSaveData(CompoundTag data) {
        super.addAdditionalSaveData(data);
        if (companion != null) data.putUUID("Companion", companion);
        if (home != null) data.putLong("Hearth", home.asLong());
        data.putBoolean("Settled", settled);
        data.putLong("NextGift", nextGift);
        data.putLong("RestockDay", restockDay);
    }

    @Override public void readAdditionalSaveData(CompoundTag data) {
        super.readAdditionalSaveData(data);
        companion = data.hasUUID("Companion") ? data.getUUID("Companion") : null;
        settled = data.getBoolean("Settled");
        if (data.contains("Hearth")) assignHome(BlockPos.of(data.getLong("Hearth")));
        nextGift = data.getLong("NextGift");
        restockDay = data.getLong("RestockDay");
    }
}
