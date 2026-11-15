package dev.uwu.test;

import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.DaggerBackstab;
import dev.uwu.dimension.RelicFlight;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

/** Server-side proof uses real attacks and physical throw hit handling. */
public final class DaggerProof {
    private static final double DISTANCE = 2;
    private static Field cooldown;
    private static LivingEntity observedTarget;
    private static ServerPlayer observedAttacker;
    private static float incoming;
    private static float criticalMultiplier;

    public static final class DamageObserver {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() == observedTarget && event.getSource().getEntity() == observedAttacker)
                incoming = event.getAmount();
        }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void critical(CriticalHitEvent event) {
            if (event.getEntity() == observedAttacker && event.getTarget() == observedTarget)
                criticalMultiplier = event.isCriticalHit() ? event.getDamageMultiplier() : 0;
        }
    }

    private DaggerProof() {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < Math.max(.02F, Math.abs(expected) * .03F);
    }

    private static boolean behind(LivingEntity target, Vec3 attackPosition) throws Exception {
        return DaggerBackstab.isBehind(target, attackPosition);
    }

    private static Vec3 positionAtAngle(LivingEntity target, double angleFromRear, double height) {
        double angle = Math.toRadians(angleFromRear);
        return target.position().add(Math.sin(angle) * DISTANCE, height, -Math.cos(angle) * DISTANCE);
    }

    private static void geometry(LivingEntity target, String label) throws Exception {
        target.setYRot(0);
        target.setYHeadRot(0);
        target.setXRot(0);
        require(behind(target, positionAtAngle(target, 0, 0)), label + ": rear rejected");
        for (double side : new double[]{-1, 1}) {
            require(behind(target, positionAtAngle(target, side * 89.9, 0)),
                    label + ": rear-side 89.9 rejected");
            require(!behind(target, positionAtAngle(target, side * 90, 0)),
                    label + ": exact side 90 accepted");
            require(!behind(target, positionAtAngle(target, side * 90.1, 0)),
                    label + ": front-side 90.1 accepted");
            require(behind(target, positionAtAngle(target, side * 89.9, 80)),
                    label + ": vertical offset altered horizontal bearing");
        }
        require(!behind(target, target.position()), label + ": overlapping hit accepted");
        require(!behind(target, target.position().add(0, 100, 0)), label + ": vertical-only hit accepted");
        require(!behind(target, target.position().add(0.00001, 0, 0)), label + ": near-zero separation accepted");
        require(!behind(target, new Vec3(Double.NaN, 0, 0)), label + ": non-finite position accepted");
        require(!behind(target, new Vec3(Double.POSITIVE_INFINITY, 0, 0)), label + ": infinite position accepted");
        // The head can turn independently of the body, and a vertical look retains yaw.
        target.setYRot(0);
        target.setYHeadRot(180);
        require(!behind(target, positionAtAngle(target, 0, 0)), label + ": ignored turned head");
        require(behind(target, positionAtAngle(target, 180, 0)), label + ": turned rear rejected");
        target.setXRot(-90);
        require(behind(target, positionAtAngle(target, 180, 50)), label + ": vertical look lost head yaw");
        target.setXRot(0);
        target.setYHeadRot(0);
    }

    private static void resetTarget(LivingEntity target) {
        target.setHealth(200);
        target.invulnerableTime = 0;
        target.setDeltaMovement(Vec3.ZERO);
        target.setYRot(0);
        target.setYHeadRot(0);
        target.setXRot(0);
    }

    private static float attack(ServerPlayer player, LivingEntity target, Vec3 spot,
                                int strength, boolean airborne) throws Exception {
        resetTarget(target);
        player.moveTo(spot.x, spot.y, spot.z, 0, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.setSprinting(false);
        player.setOnGround(!airborne);
        player.fallDistance = airborne ? 1F : 0F;
        cooldown.setInt(player, strength);
        observedTarget = target;
        observedAttacker = player;
        incoming = Float.NaN;
        criticalMultiplier = Float.NaN;
        player.attack(target);
        float damage = 200 - target.getHealth();
        require(damage > 0, "Actual attack caused no damage");
        require(Float.isFinite(incoming) && incoming > 0, "No pre-armor damage event observed");
        System.out.println("UWU DAGGER HIT raw=" + incoming + " crit=" + criticalMultiplier
                + " armor=" + target.getArmorValue() + " healthDamage=" + damage + " charge=" + strength);
        return damage;
    }

    private static void melee(ServerPlayer player, LivingEntity target, Item dagger, String label) throws Exception {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dagger));
        // Equipment modifiers are refreshed during ordinary player ticks, not
        // immediately by setItemInHand. Exercise the actual weapon damage here.
        player.setNoGravity(true);
        player.doTick();
        Vec3 front = target.position().add(0, 0, DISTANCE);
        Vec3 rear = target.position().add(0, 0, -DISTANCE);
        Vec3 side = target.position().add(DISTANCE, 0, 0);
        float frontDamage = attack(player, target, front, 100, false);
        float frontIncoming = incoming;
        float sideDamage = attack(player, target, side, 100, false);
        float sideIncoming = incoming;
        float rearDamage = attack(player, target, rear, 100, false);
        float rearIncoming = incoming;
        float partialFront = attack(player, target, front, 2, false);
        float partialFrontIncoming = incoming;
        float partialRear = attack(player, target, rear, 2, false);
        float partialRearIncoming = incoming;
        System.out.println("UWU DAGGER DAMAGE " + label + " front=" + frontDamage + " side=" + sideDamage
                + " rear=" + rearDamage + " partialFront=" + partialFront + " partialRear=" + partialRear);
        require(near(sideDamage, frontDamage), label + ": exact-side melee differs: front="
                + frontDamage + " side=" + sideDamage);
        require(near(sideIncoming, frontIncoming), label + ": side incoming damage differs");
        require(near(rearDamage, frontDamage * 2), label + ": rear melee not 2x: front="
                + frontDamage + " rear=" + rearDamage);
        require(near(rearIncoming, frontIncoming * 2), label + ": rear pre-armor damage not 2x: front="
                + frontIncoming + " rear=" + rearIncoming);
        require(partialFront < frontDamage && near(partialRear, partialFront * 2),
                label + ": partial cooldown bypassed or rear damage incorrect");
        require(near(partialRearIncoming, partialFrontIncoming * 2), label + ": partial pre-armor damage not 2x");

        // The event supplies crit feedback with a neutral multiplier. The primary
        // attack's final pre-armor amount gets the single 2x backstab adjustment.
        resetTarget(target);
        player.moveTo(rear.x, rear.y, rear.z, 0, 0);
        CriticalHitEvent event = new CriticalHitEvent(player, target, 1.5F, true);
        NeoForge.EVENT_BUS.post(event);
        require(event.isCriticalHit() && near(event.getDamageMultiplier(), 1F),
                label + ": rear stacked ordinary crit into another multiplier");
        float rearAir = attack(player, target, rear, 100, true);
        require(near(rearAir, rearDamage), label + ": airborne rear damage stacked criticals");

        // Vanilla main-hand melee does not use an offhand dagger when another item
        // is selected. Better Combat may swap attack hands; that path needs its mod.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(dagger));
        resetTarget(target);
        player.moveTo(rear.x, rear.y, rear.z, 0, 0);
        CriticalHitEvent offhandOnly = new CriticalHitEvent(player, target, 1F, false);
        NeoForge.EVENT_BUS.post(offhandOnly);
        require(!offhandOnly.isCriticalHit(), label + ": vanilla offhand-only melee falsely treated as dagger");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dagger));
    }

    private static void enchantmentAndArmor(ServerLevel level, ServerPlayer player, LivingEntity target) throws Exception {
        var dagger = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.ROSE_DAGGER).get());
        Vec3 front = target.position().add(0, 0, DISTANCE);
        Vec3 rear = target.position().add(0, 0, -DISTANCE);
        player.setItemInHand(InteractionHand.MAIN_HAND, dagger.copy());
        float unenchanted = attack(player, target, front, 100, false);
        dagger.enchant(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, dagger);
        float sharpFront = attack(player, target, front, 100, false);
        float sharpFrontIncoming = incoming;
        float sharpRear = attack(player, target, rear, 100, false);
        float sharpRearIncoming = incoming;
        require(sharpFront > unenchanted, "Sharpness did not contribute to front damage");
        require(near(sharpRear, sharpFront * 2), "Backstab failed to double total Sharpness damage");
        require(near(sharpRearIncoming, sharpFrontIncoming * 2), "Sharpness pre-armor damage was not doubled");
        float sharpRearAir = attack(player, target, rear, 100, true);
        require(near(sharpRearAir, sharpRear), "Airborne Sharpness backstab stacked critical multipliers");
        target.getAttribute(Attributes.ARMOR).setBaseValue(10);
        float armoredFront = attack(player, target, front, 100, false);
        float armoredFrontIncoming = incoming;
        float armoredRear = attack(player, target, rear, 100, false);
        float armoredRearIncoming = incoming;
        require(armoredFront < sharpFront && armoredRear < sharpRear && armoredRear > armoredFront,
                "Armor did not apply normally to front and rear dagger damage");
        require(near(armoredFrontIncoming, sharpFrontIncoming) && near(armoredRearIncoming, sharpRearIncoming),
                "Armor changed the observed pre-armor incoming amount");
        target.getAttribute(Attributes.ARMOR).setBaseValue(0);
    }

    private static float thrown(ServerLevel level, ServerPlayer player, LivingEntity target,
                                InteractionHand hand, boolean rear) throws Exception {
        resetTarget(target);
        Item item = Arsenal.WEAPONS.get(Arsenal.Relic.ROSE_DAGGER).get();
        player.getInventory().clearContent();
        player.getCooldowns().removeCooldown(item);
        // Keep the owner opposite the impact so the test distinguishes the flight
        // hit position from the thrower's location.
        double sign = rear ? -1 : 1;
        player.moveTo(target.getX(), target.getY(), target.getZ() - sign * 8, 0, 0);
        player.setItemInHand(hand, new ItemStack(item));
        item.use(level, player, hand);
        require(player.getItemInHand(hand).isEmpty(), "Thrown dagger did not leave " + hand);
        RelicFlight flight = level.getEntitiesOfClass(RelicFlight.class, player.getBoundingBox().inflate(5),
                e -> e.getOwner() == player && e.getItem().is(item)).stream().findFirst().orElseThrow();
        Vec3 start = target.position().add(0, target.getBbHeight() * .5, sign * 1.2);
        flight.setPos(start);
        flight.setNoGravity(true);
        flight.setDeltaMovement(0, 0, -sign * 2.4);
        try {
            // A real projectile tick sweeps through the target's bounding box,
            // computing the collision position used by the backstab check.
            flight.tick();
            float damage = 200 - target.getHealth();
            require(damage > 0, "Thrown " + hand + " dagger did no damage");
            require(flight.returning(), "Thrown dagger did not enter return state");
            return damage;
        } finally {
            flight.discard();
            player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                    .remove("uwudimension.relic_returns");
            player.getInventory().clearContent();
        }
    }

    private static float legacySnowball(ServerLevel level, ServerPlayer player, LivingEntity target,
                                        boolean rear) {
        resetTarget(target);
        double sign = rear ? -1 : 1;
        player.moveTo(target.getX(), target.getY(), target.getZ() - sign * 8, 0, 0);
        Snowball ball = new Snowball(level, player);
        ball.getPersistentData().putString("uwu.counter", "counter_toxic_longsword");
        ball.setPos(target.getX(), target.getY() + target.getBbHeight() * .5, target.getZ() + sign * 1.2);
        ball.setNoGravity(true);
        ball.setDeltaMovement(0, 0, -sign * 2.4);
        level.addFreshEntity(ball);
        try {
            ball.tick();
            float damage = 200 - target.getHealth();
            require(damage > 0, "Legacy counter projectile missed its target");
            return damage;
        } finally {
            ball.discard();
        }
    }

    private static void independentCrit(ServerPlayer player, LivingEntity target,
                                        net.minecraft.world.entity.ai.attributes.AttributeInstance chance) throws Exception {
        if (chance == null) return;
        var damageKey = ResourceKey.create(Registries.ATTRIBUTE,
                ResourceLocation.fromNamespaceAndPath("apothic_attributes", "crit_damage"));
        var damage = BuiltInRegistries.ATTRIBUTE.getHolder(damageKey).map(player::getAttribute).orElse(null);
        require(damage != null, "Apothic critical chance exists without critical damage");
        double previousDamage = damage.getBaseValue();
        try {
            damage.setBaseValue(1.5);
            chance.setBaseValue(0);
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.ROSE_DAGGER).get()));
            player.setNoGravity(true);
            player.doTick();
            Vec3 front = target.position().add(0, 0, DISTANCE);
            Vec3 rear = target.position().add(0, 0, -DISTANCE);
            float ordinaryFront = attack(player, target, front, 100, false);
            float ordinaryRear = attack(player, target, rear, 100, false);
            chance.setBaseValue(1);
            require(near((float)chance.getValue(), 1F), "Fixture could not force Apothic critical chance");
            float modifiedFront = attack(player, target, front, 100, false);
            float modifiedRear = attack(player, target, rear, 100, false);
            require(near(modifiedFront, ordinaryFront * 1.5F) && near(modifiedRear, ordinaryRear * 1.5F),
                    "Independent Apothic critical modifier was lost or added more than once");
            System.out.println("UWU DAGGER APOTHIC PASS separate critical modifier applied once");
        } finally {
            chance.setBaseValue(0);
            damage.setBaseValue(previousDamage);
        }
    }

    static void run(ServerLevel level, BlockPos base, ServerPlayer player, ServerPlayer other) throws Exception {
        cooldown = LivingEntity.class.getDeclaredField("attackStrengthTicker");
        cooldown.setAccessible(true);
        Mob mob = EntityType.HUSK.create(level);
        require(mob != null, "Missing backstab test mob");
        mob.moveTo(base.getX()+.5, base.getY(), base.getZ()+.5, 0, 0);
        mob.setNoAi(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
        mob.setHealth(200);
        level.addFreshEntity(mob);
        other.moveTo(base.getX()+10.5, base.getY(), base.getZ()+.5, 0, 0);
        other.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        other.setHealth(200);
        // A new ServerPlayer has 60 ticks of spawn protection. Simulate ordinary
        // player ticks so real PvP damage is tested after that protection expires.
        other.setNoGravity(true);
        for (int tick = 0; tick < 70; tick++) other.tick();
        other.moveTo(base.getX()+10.5, base.getY(), base.getZ()+.5, 0, 0);
        other.setHealth(200);
        var chanceKey = ResourceKey.create(Registries.ATTRIBUTE,
                ResourceLocation.fromNamespaceAndPath("apothic_attributes", "crit_chance"));
        var chance = BuiltInRegistries.ATTRIBUTE.getHolder(chanceKey).map(player::getAttribute).orElse(null);
        double previousChance = chance == null ? 0 : chance.getBaseValue();
        if (chance != null) {
            chance.setBaseValue(0);
            require(near((float)chance.getValue(), 0F), "Fixture could not disable independent Apothic critical chance");
        }
        DamageObserver observer = new DamageObserver();
        NeoForge.EVENT_BUS.register(observer);
        try {
            geometry(mob, "mob");
            geometry(other, "player");
            Item named = Arsenal.WEAPONS.get(Arsenal.Relic.ROSE_DAGGER).get();
            Item counter = CounterArsenal.ITEMS.get("counter_toxic_longsword").get();
            require(((CounterArsenal.CounterItem) counter).entry.shape().equals("dagger"),
                    "Retained Plague counter is not a dagger");
            melee(player, mob, named, "named mob");
            melee(player, mob, counter, "counter mob");
            enchantmentAndArmor(level, player, mob);
            require(player.canHarmPlayer(other), "PVP fixture does not permit the player-target test");
            melee(player, other, named, "named player");
            for (InteractionHand hand : InteractionHand.values()) {
                float front = thrown(level, player, mob, hand, false);
                float rear = thrown(level, player, mob, hand, true);
                require(near(rear, front * 2), hand + ": thrown mob rear not 2x: " + front + "/" + rear);
            }
            float frontPlayer = thrown(level, player, other, InteractionHand.MAIN_HAND, false);
            float rearPlayer = thrown(level, player, other, InteractionHand.MAIN_HAND, true);
            require(near(rearPlayer, frontPlayer * 2),
                    "Thrown player rear not 2x: " + frontPlayer + "/" + rearPlayer);
            float legacyFront = legacySnowball(level, player, mob, false);
            float legacyRear = legacySnowball(level, player, mob, true);
            require(near(legacyRear, legacyFront * 2),
                    "Legacy counter projectile rear not 2x: " + legacyFront + "/" + legacyRear);
            independentCrit(player, mob, chance);
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
            if (chance != null) chance.setBaseValue(previousChance);
            observedTarget = null;
            observedAttacker = null;
            mob.discard();
            player.fallDistance = 0;
            player.setOnGround(true);
            player.setNoGravity(false);
            player.getInventory().clearContent();
            other.getInventory().clearContent();
            other.setNoGravity(false);
            other.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
            other.setHealth(20);
        }
        System.out.println("UWU DAGGER ANGLE PASS 90-degree boundaries, turning, cooldown, airborne and thrown impacts");
        System.out.println("UWU DAGGER LIMIT Better Combat offhand interaction requires its mod and a graphical client");
    }
}

