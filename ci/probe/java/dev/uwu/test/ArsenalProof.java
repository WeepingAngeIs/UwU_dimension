package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.HeartArrow;
import dev.uwu.dimension.Bellwake;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-side checks using the registered survival items, real projectiles, entities and events. */
public final class ArsenalProof {
    public static void run(ServerLevel level) {
        for (Arsenal.Relic kind : Arsenal.Relic.values()) {
            ItemStack stack = new ItemStack(Arsenal.WEAPONS.get(kind).get());
            double[] damage = {1};
            stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, modifier) -> {
                if (attr.equals(Attributes.ATTACK_DAMAGE)) damage[0] += modifier.amount();
            });
            require(damage[0] == kind.damage && damage[0] >= 14 && damage[0] <= 24, "Damage for " + kind + " = " + damage[0]);
        }

        BlockPos platform = new BlockPos(6, level.getMaxBuildHeight() - 18, 6);
        level.getChunkAt(platform);
        var marker = platform.offset(0, 0, 4);
        var priorBlock = level.getBlockState(marker);
        var keeper = new TestArcher(level);
        keeper.moveTo(platform.getX()+0.5, platform.getY()+1, platform.getZ()+0.5, 0, 0);
        level.addNewPlayer(keeper);
        keeper.setNoGravity(true);
        measureDamage(level, keeper);
        keeper.moveTo(platform.getX()+0.5, platform.getY()+1, platform.getZ()+0.5, 0, 0);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) throw new AssertionError("Zombie factory");
        zombie.moveTo(keeper.getX(), keeper.getY(), keeper.getZ()+4.5, 180, 0);
        level.addFreshEntity(zombie);
        try {
            zombie.setTarget(keeper);
            var bow = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get());
            keeper.setItemInHand(InteractionHand.MAIN_HAND, bow);
            bow.use(level, keeper, InteractionHand.MAIN_HAND);
            bow.releaseUsing(level, keeper, bow.getUseDuration(keeper) - 20);
            var arrows = level.getEntitiesOfClass(HeartArrow.class, keeper.getBoundingBox().inflate(2), a -> a.getOwner() == keeper);
            require(arrows.size() == 1, "Cupid Bow must fire exactly one heart projectile");
            for (int i = 0; i < 4 && arrows.getFirst().isAlive(); i++) arrows.getFirst().tick();
            require(zombie.getHealth() == zombie.getMaxHealth(), "Successful charm must preserve health");
            require(zombie.getPersistentData().hasUUID("uwudimension.charm_owner"), "Heart arrow missed its ordinary mob");
            zombie.setTarget(keeper);
            require(zombie.getTarget() == null, "Charmed zombie targeted its keeper");
            var intruder = EntityType.HUSK.create(level);
            if (intruder == null) throw new AssertionError("Husk factory");
            intruder.moveTo(keeper.getX()+3,keeper.getY(),keeper.getZ());
            level.addFreshEntity(intruder);
            try {
                keeper.setLastHurtByMob(intruder);
                zombie.tickCount = 5;
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(zombie));
                require(zombie.getTarget() == intruder, "Charmed guardian did not defend keeper");
            } finally { intruder.discard(); }
            // Advance the charm's expiry, retaining all native mob state and events.
            zombie.getPersistentData().putLong("uwudimension.charm_end", level.getGameTime());
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(zombie));
            require(!zombie.getPersistentData().hasUUID("uwudimension.charm_owner") && zombie.getTarget() == keeper, "Zombie did not regain prior target");
            keeper.stopUsingItem();
            zombie.moveTo(keeper.getX()+2, keeper.getY(), keeper.getZ()+4.5);
            level.setBlockAndUpdate(marker, Blocks.STONE.defaultBlockState());
            double drop = keeper.getEyeY() - (marker.getY()+1);
            keeper.setXRot((float)Math.toDegrees(Math.atan2(drop,4.0)));
            var wand = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.RIBBONHARE_SCEPTER).get());
            keeper.setItemInHand(InteractionHand.MAIN_HAND,wand);
            wand.use(level,keeper,InteractionHand.MAIN_HAND);
            var rabbits = level.getEntitiesOfClass(Rabbit.class, keeper.getBoundingBox().inflate(8), r -> r.getTags().contains("uwudimension.hare_caller"));
            require(rabbits.size() == 1 && zombie.getTarget() == rabbits.getFirst(), "Ribbonhare spawn and thump must draw zombie aggro");
            Rabbit lure = rabbits.getFirst();
            level.getChunkAt(new BlockPos(42, 1, 115));
            keeper.moveTo(42.5, 2, 118.5);
            lure.moveTo(42.5, 1, 115.5);
            // The hare is in its first thump tick. Run it at the deep lake, then at its
            // next thump interval; both pass through the ordinary summon event handler.
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(lure));
            var lake = lure.getBoundingBox().inflate(24);
            var wakes = level.getEntitiesOfClass(Bellwake.class, lake);
            require(wakes.size() == 1, "Lake failed to answer the summoned hare");
            lure.getPersistentData().putLong("uwudimension.hare_end", level.getGameTime() + 140);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(lure));
            require(level.getEntitiesOfClass(Bellwake.class, lake).size() == 1, "Second thump duplicated the Bellwake");
            lure.discard();
            System.out.println("HEARTWATER ARSENAL PASS | 11 registered melee stats (14Ã¢â‚¬â€œ24); drawn heart arrow, guard, owner targeting protection and AI expiry; ribbonhare ledge cast and hostile lure; single Bellwake encounter after successive lake thumps.");
        } finally {
            level.setBlockAndUpdate(marker, priorBlock);
            zombie.discard();
            keeper.discard();
        }
    }
    // A fake player does not receive normal controller ticks. Use the real equipment
    // update and advance only the attack cooldown, then invoke the usual attack.
    private static final class TestArcher extends FakePlayer {
        TestArcher(ServerLevel level) {
            super(level, new GameProfile(UUID.fromString("5979a10b-bb3a-4a60-98e7-d07094183c1f"), "HeartArcher"));
        }
        void equipAndCharge(ItemStack weapon) {
            setItemInHand(InteractionHand.MAIN_HAND, weapon);
            doTick();
            attackStrengthTicker = 100;
            setOnGround(true);
            fallDistance = 0;
        }
    }

    private static void measureDamage(ServerLevel level, TestArcher archer) {
        for (Arsenal.Relic kind : Arsenal.Relic.values()) {
            // Wardens are charm-immune and have no innate armor. Golems provide a
            // second high-health, unarmored target for every other item.
            Mob foe = kind == Arsenal.Relic.CUPID_BOW ? EntityType.WARDEN.create(level) : EntityType.IRON_GOLEM.create(level);
            require(foe != null && foe.getArmorValue() == 0, "Expected an unarmored target for " + kind);
            ItemStack stack = new ItemStack(Arsenal.WEAPONS.get(kind).get());
            archer.equipAndCharge(stack);
            foe.moveTo(archer.getX(), archer.getY(), archer.getZ()+3, 180, 0);
            level.addFreshEntity(foe);
            try {
                float before = foe.getHealth();
                archer.attack(foe);
                float melee = before - foe.getHealth();
                require(Math.abs(melee - kind.damage) < 0.01, kind + " unarmored melee " + melee + " expected " + kind.damage);
                foe.invulnerableTime = 0; // Separate clean hit, outside vanilla hurt immunity.
                before = foe.getHealth();
                if (kind == Arsenal.Relic.BLOSSOM_GREATSWORD) {
                    var petals = archer.getPersistentData().getCompound("uwudimension.petals");
                    require(!petals.isEmpty(), "Charged greatsword hit must create petals");
                    petals.putLong("time", level.getGameTime() - 12);
                }
                if (kind == Arsenal.Relic.RIBBONHARE_SCEPTER) {
                    System.out.println("HEARTWATER DAMAGE " + kind + " melee=" + melee + "; non-damaging lure tested separately");
                    continue;
                }
                stack.use(level, archer, InteractionHand.MAIN_HAND);
                boolean bow = kind == Arsenal.Relic.CUPID_BOW || kind == Arsenal.Relic.RIPPLE_BOW;
                if (bow) stack.releaseUsing(level, archer, stack.getUseDuration(archer) - 20);
                if (bow || kind == Arsenal.Relic.LANTERN_SPEAR || kind == Arsenal.Relic.TIDELACE_TRIDENT) {
                    var shots = level.getEntitiesOfClass(Projectile.class, archer.getBoundingBox().inflate(2), e -> e.getOwner() == archer);
                    require(shots.size() == 1, "Could not identify the launched projectile for " + kind);
                    Projectile shot = shots.getFirst();
                    for (int tick=0; tick<6 && shot.isAlive(); tick++) shot.tick();
                    require(!shot.isAlive(), "Projectile missed its unarmored target: " + kind);
                }
                archer.stopUsingItem();
                float expected = switch (kind) {
                    case BLOSSOM_GREATSWORD -> 16;
                    case BELLHEART_AXE -> 0;
                    default -> kind.damage;
                };
                float cast = before - foe.getHealth();
                require(Math.abs(cast-expected)<0.01, kind + " unarmored cast " + cast + " expected " + expected);
                System.out.println("HEARTWATER DAMAGE " + kind + " melee=" + melee + " cast=" + cast);
            } finally { foe.discard(); }
        }
    }

    private static void require(boolean okay, String message) {
        if (!okay) throw new AssertionError(message);
    }
}


