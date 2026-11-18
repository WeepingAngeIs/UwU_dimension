package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.Fallsong;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Disposable dedicated-server probe. Damage enters through ServerPlayer.hurt. */
final class HeartShieldProof {
    private static final String OWNER = "uwudimension.charm_owner";
    private static final String END = "uwudimension.charm_end";
    private static final InteractionHand HAND = InteractionHand.OFF_HAND;
    private static final BlockPos FLOOR = new BlockPos(8, 286, 8);
    private static ServerPlayer defender;
    private static Husk first, second, control;
    private static long started;
    private static boolean finished;
    private static final boolean FALLSONG = "fallsong".equals(System.getenv("UWU_SHIELD_CASE"));
    private static final List<ClientboundLevelParticlesPacket> PARTICLES = new ArrayList<>();
    private static final List<ClientboundSetEntityMotionPacket> MOTION = new ArrayList<>();

    static void tick(MinecraftServer server) {
        if (finished) return;
        try {
            run(server.overworld());
        } catch (Throwable error) {
            finished = true;
            System.err.println("HEART SHIELD PROOF FAILED");
            error.printStackTrace();
            server.halt(false);
        }
    }

    private static void run(ServerLevel level) {
        if (defender == null) {
            level.setChunkForced(0, 0, true);
            level.getChunkAt(FLOOR);
            if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0, 0))) return;
            for (int x = -4; x <= 4; x++)
                for (int z = -5; z <= 5; z++)
                    level.setBlockAndUpdate(FLOOR.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            var profile = new GameProfile(UUID.randomUUID(), "ShieldKeeper");
            defender = FALLSONG ? packetObserver(level, profile) : CounterProof.player(level, profile);
            defender.moveTo(8.5, 287, 8.5, 0, 0);
            defender.setNoGravity(!FALLSONG);
            level.addNewPlayer(defender);
            first = attacker(level);
            second = attacker(level);
            control = attacker(level);
            started = level.getGameTime();
            return;
        }
        // The network connection normally runs doTick. This isolated visitor is
        // in the level, but does not have an ordinary ticking packet listener.
        defender.doTick();
        int age = Math.toIntExact(level.getGameTime() - started);
        if (FALLSONG) {
            fallsong(level, age);
            return;
        }
        switch (age) {
            case 75 -> {
                equip(shield("heartbriar_shield"));
                melee(level, control, -3, false);
                raise(level);
                require(!defender.isBlocking(), "Shield skipped its raise delay");
                melee(level, control, 3, false);
            }
            case 82 -> {
                require(defender.isBlocking(), "Heartbriar did not raise");
                melee(level, control, 3, true);
                melee(level, control, -3, true);
                var arrow = new Arrow(EntityType.ARROW, level);
                var state = arrow.saveWithoutId(new CompoundTag());
                state.putByte("PierceLevel", (byte) 1);
                arrow.load(state);
                arrow.setPos(defender.position().add(0, 0, 3));
                require(arrow.getPierceLevel() == 1, "Piercing fixture");
                hit(level.damageSources().arrow(arrow, control), false);
                var fall = level.damageSources().fall();
                require(fall.is(DamageTypeTags.BYPASSES_SHIELD), "Fall bypass fixture");
                hit(new DamageSource(fall.typeHolder(), control), false);
                equip(Items.SHIELD);
                raise(level);
            }
            case 89 -> {
                melee(level, control, 3, true);
                melee(level, control, -3, false);
                equip(shield("petalfold_shield"));
                melee(level, control, 3, false);
                raise(level);
            }
            case 96 -> {
                melee(level, control, -3, false);
                melee(level, first, 3, true);
                first.setPos(4.5, 287, 4.5);
            }
            case 97 -> {
                // Consecutive genuine blocks on distinct eligible attackers.
                // An already charmed attacker is prevented from hurting its owner.
                melee(level, second, 3, true);
                second.setPos(12.5, 287, 12.5);
            }
            case 99 -> {
                active(first, 96);
                active(second, 97);
                require(!control.getPersistentData().hasUUID(OWNER), "Idle or rear hit triggered Petalfold");
                require(!defender.getCooldowns().isOnCooldown(defender.getItemInHand(HAND).getItem()), "Petalfold cooldown");
            }
            case 215 -> active(first, 96);
            case 216 -> {
                expired(first);
                active(second, 97);
                melee(level, first, 3, true);
                first.setPos(4.5, 287, 4.5);
            }
            case 218 -> {
                active(first, 216);
                expired(second);
                require(!control.getPersistentData().hasUUID(OWNER), "Control attacker charmed");
                System.out.println("HEART SHIELDS SERVER PASS front rear idle warmup durability piercing bypass vanilla-control consecutive-petalfold 120-tick-expiration recharm");
                finished = true;
                level.getServer().halt(false);
            }
        }
    }

    private static ServerPlayer packetObserver(ServerLevel level, GameProfile profile) {
        var server = level.getServer();
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundLevelParticlesPacket particle) PARTICLES.add(particle);
                if (packet instanceof ClientboundSetEntityMotionPacket velocity) MOTION.add(velocity);
            }
            @Override public void send(Packet<?> packet, PacketSendListener callback) { send(packet); }
        };
        return player;
    }

    private static void fallsong(ServerLevel level, int age) {
        switch (age) {
            case 75 -> {
                equip(shield("heartwater_window_shield"));
                melee(level, control, 3, false);
                raise(level);
            }
            case 82 -> melee(level, control, -3, false);
            case 84 -> {
                require(!Fallsong.isCharged(defender.getItemInHand(HAND)), "Idle or failed block stored a drop");
                melee(level, control, 3, true);
            }
            case 86 -> {
                require(Fallsong.isCharged(defender.getItemInHand(HAND)), "Successful block failed to store a drop");
                melee(level, first, 3, true);
            }
            case 88 -> {
                defender.stopUsingItem();
                var held = defender.getItemInHand(HAND);
                var saved = ItemStack.parseOptional(level.registryAccess(), (CompoundTag)held.save(level.registryAccess()));
                require(Fallsong.isCharged(saved), "Shield lost its drop during serialization");
                equip(held.getItem());
                double ordinary = jump(true);
                require(ordinary > .3 && ordinary < .6 && Fallsong.isCharged(held), "Charge leaked to another stack");
                defender.setItemInHand(HAND, saved);
                PARTICLES.clear();
                MOTION.clear();
                require(Math.abs(jump(false) - ordinary) < .001 && Fallsong.isCharged(saved), "Airborne jump consumed the drop");
                double boosted = jump(true);
                require(boosted >= .65 && boosted <= .71 && !Fallsong.isCharged(saved), "Charged jump or consumption failed: " + boosted);
                require(!defender.getAbilities().flying && !defender.getAbilities().mayfly && !defender.isNoGravity(),
                    "A single Fallsong jump changed flight or gravity");
                require(MOTION.stream().anyMatch(p -> p.getId() == defender.getId() && Math.abs(p.getYa() - boosted) < .001
                    && Math.abs(p.getXa() - .08) < .001 && Math.abs(p.getZa() + .04) < .001), "Boost velocity never reached owning connection");
                require(PARTICLES.stream().anyMatch(p -> particleId(p).equals("uwudimension:fallsong_cascade")
                    && p.getCount() == 0 && (int)p.getXDist() == defender.getId()), "Missing Fallsong cascade for jumping player");
                require(Math.abs(jump(true) - ordinary) < .001, "A second stored drop or permanent jump boost remained");
                System.out.println("FALLSONG JUMP PASS serverY=" + boosted + " ordinaryY=" + ordinary
                    + " retainedPerStack=true airborneGuard=true singleUse=true");
                equip(shield("petalfold_shield"));
                raise(level);
            }
            case 96 -> {
                PARTICLES.clear();
                melee(level, second, 3, true);
            }
            case 98 -> {
                inspectSpray();
                System.out.println("FALLSONG AND PETAL SPRAY SERVER PASS");
                finished = true;
                level.getServer().halt(false);
            }
        }
    }

    private static double jump(boolean grounded) {
        defender.setOnGround(grounded);
        defender.setSprinting(false);
        defender.setDeltaMovement(.08, 0, -.04);
        defender.jumpFromGround();
        Vec3 motion = defender.getDeltaMovement();
        require(Math.abs(motion.x - .08) < .0001 && Math.abs(motion.z + .04) < .0001, "Fallsong changed horizontal movement");
        return motion.y;
    }

    private static String particleId(ClientboundLevelParticlesPacket p) {
        return BuiltInRegistries.PARTICLE_TYPE.getKey(p.getParticle().getType()).toString();
    }

    private static void inspectSpray() {
        // The real attack is due south (+Z), so both outward rays have the
        // lateral sign of their X offset. Read the recipient's actual packets.
        int[] flowers = {0, 0}, hearts = {0, 0};
        double minLateral = Double.POSITIVE_INFINITY;
        for (var p : PARTICLES) {
            String id = particleId(p);
            boolean flower = id.equals("uwudimension:neon_flower");
            if (!flower && !id.equals("uwudimension:neon_heart")) continue;
            if (p.getCount() != 0) continue;
            double side = p.getX() - 8.5;
            double lateral = Math.signum(side) * p.getXDist() * p.getMaxSpeed();
            double forward = p.getZDist() * p.getMaxSpeed();
            require(Math.abs(side) >= .72 && p.getZ() > 8.75 && p.getY() < defender.getEyeY(), "Bloom crossed the sight line");
            require(lateral >= (flower ? .35 : .44) && forward > .10 && p.getYDist() > 0, "Spray did not travel outwards");
            minLateral = Math.min(minLateral, lateral);
            (flower ? flowers : hearts)[side < 0 ? 0 : 1]++;
        }
        for (int side = 0; side < 2; side++) require(flowers[side] >= 1 && hearts[side] >= 1, "Both rays need blooms and hearts");
        System.out.println("PETAL SPRAY PASS flowersL/R=" + flowers[0] + "/" + flowers[1]
            + " heartsL/R=" + hearts[0] + "/" + hearts[1] + " minOutwardVelocity=" + minLateral);
    }

    private static Husk attacker(ServerLevel level) {
        var mob = EntityType.HUSK.create(level);
        require(mob != null, "Husk fixture");
        mob.moveTo(8.5, 287, 12.5, 180, 0);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        require(level.addFreshEntity(mob), "Failed to add husk");
        return mob;
    }

    private static Item shield(String path) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", path));
        require(item instanceof ShieldItem, "Missing shield " + path);
        return item;
    }

    private static void equip(Item item) {
        defender.stopUsingItem();
        defender.setItemInHand(HAND, new ItemStack(item));
    }

    private static void raise(ServerLevel level) {
        var stack = defender.getItemInHand(HAND);
        stack.use(level, defender, HAND);
        require(defender.isUsingItem() && defender.getUseItem() == stack, "Shield was not used from offhand");
    }

    private static void melee(ServerLevel level, Husk source, double dz, boolean blocked) {
        defender.moveTo(8.5, 287, 8.5, 0, 0);
        defender.setYHeadRot(0);
        defender.setDeltaMovement(Vec3.ZERO);
        source.setPos(8.5, 287, 8.5 + dz);
        hit(level.damageSources().mobAttack(source), blocked);
    }

    private static void hit(DamageSource source, boolean blocked) {
        defender.invulnerableTime = 0;
        defender.setHealth(defender.getMaxHealth());
        var stack = defender.getItemInHand(HAND);
        int wear = stack.getDamageValue();
        boolean hurt = defender.hurt(source, 5);
        require(hurt != blocked, "Unexpected hurt result for " + source + ", expected blocked=" + blocked);
        require((defender.getHealth() == defender.getMaxHealth()) == blocked, "Health outcome for " + source);
        int lost = stack.getDamageValue() - wear;
        require(blocked ? lost >= 4 : lost == 0, "Shield wear " + lost + " for " + source);
    }

    private static void active(Husk mob, int attackTick) {
        var data = mob.getPersistentData();
        require(mob.getTags().contains(OWNER) && data.hasUUID(OWNER) && defender.getUUID().equals(data.getUUID(OWNER)),
            "Missing charm or incorrect owner for block at " + attackTick);
        require(data.getLong(END) == started + attackTick + 120, "Incorrect charm deadline for block at " + attackTick);
    }

    private static void expired(Husk mob) {
        require(!mob.getTags().contains(OWNER) && !mob.getPersistentData().hasUUID(OWNER), "Charm failed to expire");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
