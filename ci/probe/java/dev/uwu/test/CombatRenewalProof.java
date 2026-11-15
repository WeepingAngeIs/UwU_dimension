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

    public static void run(ServerLevel level) {
        BlockPos at = new BlockPos(8, level.getMaxBuildHeight()-16, 8);
        level.getChunkAt(at);
        try (var owner = new ChestProof.Visitor(level, at, "RelicKeeper", UUID.randomUUID())) {
            bows(level, owner.player);
            returns(level, owner.player);
            guardian(level, at);
        }
        System.out.println("UWU COMBAT RENEWAL PASS bow cooldowns, full-slot recovery and guardian stale target");
    }

    private static void bows(ServerLevel level, ServerPlayer player) {
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
        }
        for (var arrow : level.getEntitiesOfClass(HeartArrow.class, player.getBoundingBox().inflate(4),
                arrow -> arrow.getOwner() == player)) arrow.discard();
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

    private static void guardian(ServerLevel level, BlockPos at) {
        BlossomWarden guardian = People.BLOSSOM_WARDEN.get().create(level);
        var threat = EntityType.HUSK.create(level);
        guardian.moveTo(at.getX(), at.getY(), at.getZ());
        guardian.bindVillage(at);
        threat.moveTo(at.getX()+2, at.getY(), at.getZ());
        level.addFreshEntity(threat);
        try {
            UUID missing = UUID.randomUUID();
            CompoundTag save = guardian.saveWithoutId(new CompoundTag());
            var queue = new net.minecraft.nbt.ListTag();
            for (UUID id : new UUID[]{missing, threat.getUUID()}) {
                var entry = new CompoundTag();
                entry.putUUID("Offender", id);
                queue.add(entry);
            }
            save.put("Offenders", queue);
            save.putByte("GripPhase", (byte)1);
            guardian.load(save);
            for (int i = 0; i < 10; i++) guardian.aiStep();
            require(threat.getUUID().equals(guardian.offender()), "Vanished mob still blocks the queue");
            guardian.aiStep();
            require(guardian.gripping(), "Guardian did not resume combat");
            require(guardian.getMaxHealth() == 6666 && guardian.getEffect(MobEffects.REGENERATION).getAmplifier()==3
                    && guardian.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier()==3, "Guardian buff regression");
            boolean female = guardian.female();
            var restored = People.BLOSSOM_WARDEN.get().create(level);
            restored.load(guardian.saveWithoutId(new CompoundTag()));
            require(restored.female() == female, "Guardian model changed on load");
            boolean before = GuardianTargeting.creeperAggroOn(level);
            GuardianTargeting.setCreeperAggro(level, !before, level.getServer());
            require(GuardianTargeting.creeperAggroOn(level) != before, "Creeper setting did not change");
            GuardianTargeting.setCreeperAggro(level, before, level.getServer());
        } finally {
            threat.discard();
            guardian.discard();
        }
    }
}
