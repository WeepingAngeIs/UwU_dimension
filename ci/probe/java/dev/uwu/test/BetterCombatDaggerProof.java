package dev.uwu.test;

import java.lang.reflect.Method;
import java.util.UUID;
import dev.uwu.dimension.Arsenal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Calls the installed Better Combat server packet handler with a real offhand attack. */
@EventBusSubscriber(modid = "heartwater_probe")
public final class BetterCombatDaggerProof {
    private static boolean ran;

    private BetterCombatDaggerProof() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (ran || !server.isDedicatedServer() || server.getTickCount() < 80
                || !"1".equals(System.getenv("UWU_TEST_BETTERCOMBAT"))) return;
        ran = true;
        try {
            run(server.overworld());
        } catch (Throwable failure) {
            System.err.println("UWU BETTER COMBAT OFFHAND FAIL");
            failure.printStackTrace();
            server.halt(false);
        }
    }

    private static void run(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("bettercombat"))
            throw new AssertionError("Better Combat is required for this proof");
        BlockPos base = new BlockPos(5, level.getMaxBuildHeight() - 20, 5);
        level.getChunkAt(base);
        try (var visitor = new ChestProof.Visitor(level, base, "OffhandTest", UUID.randomUUID())) {
            ServerPlayer player = visitor.player;
            var target = EntityType.HUSK.create(level);
            if (target == null) throw new AssertionError("Could not create target");
            level.addNewPlayer(player);
            target.moveTo(base.getX() + .5, base.getY(), base.getZ() + .5, 0, 0);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            target.getAttribute(Attributes.ARMOR).setBaseValue(0);
            target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
            target.setHealth(200);
            level.addFreshEntity(target);
            player.setNoGravity(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            ItemStack dagger = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.ROSE_DAGGER).get());
            player.setItemInHand(InteractionHand.OFF_HAND, dagger);

            Class<?> helper = Class.forName("net.bettercombat.logic.PlayerAttackHelper");
            Method current = helper.getMethod("getCurrentAttack", Player.class, int.class);
            int offhandCombo = -1;
            for (int combo = 0; combo < 32; combo++) {
                Object attack = current.invoke(null, player, combo);
                if (attack != null && (boolean)attack.getClass().getMethod("isOffHand").invoke(attack)
                        && ((ItemStack)attack.getClass().getMethod("itemStack").invoke(attack)).is(dagger.getItem())) {
                    offhandCombo = combo;
                    break;
                }
            }
            if (offhandCombo < 0) throw new AssertionError("Better Combat did not select the offhand dagger");

            Class<?> request = Class.forName("net.bettercombat.network.Packets$C2S_AttackRequest");
            var constructor = request.getConstructor(int.class, boolean.class, int.class, int.class, int[].class);
            Object packet = constructor.newInstance(offhandCombo, false, player.getInventory().selected,
                    target.getId(), new int[]{target.getId()});
            Class<?> network = Class.forName("net.bettercombat.network.ServerNetwork");
            Method dispatch = network.getMethod("handleAttackRequest", request,
                    net.minecraft.server.MinecraftServer.class, ServerPlayer.class,
                    ServerGamePacketListenerImpl.class);
            var ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            ticker.setAccessible(true);

            float front = strike(level, player, target, dispatch, packet, ticker, false);
            float rear = strike(level, player, target, dispatch, packet, ticker, true);
            if (front <= 0 || Math.abs(rear - front * 2) > .1F)
                throw new AssertionError("Better Combat offhand front/rear damage " + front + "/" + rear);
            if (!player.getOffhandItem().is(dagger.getItem()) || !player.getMainHandItem().is(Items.IRON_SWORD))
                throw new AssertionError("Better Combat did not restore both hands");
            target.discard();
            player.discard();
            System.out.println("UWU BETTER COMBAT OFFHAND PASS actual packet handler front/rear and hand restoration");
        }
    }

    private static float strike(ServerLevel level, ServerPlayer player, LivingEntity target,
            Method dispatch, Object packet, java.lang.reflect.Field ticker, boolean behind) throws Exception {
        target.setHealth(200);
        target.invulnerableTime = 0;
        target.setYRot(0);
        target.setYHeadRot(0);
        player.moveTo(target.getX(), target.getY(), target.getZ() + (behind ? -1.5 : 1.5), behind ? 0 : 180, 0);
        player.setOnGround(true);
        player.setDeltaMovement(0, 0, 0);
        ticker.setInt(player, 100);
        dispatch.invoke(null, packet, level.getServer(), player, player.connection);
        return 200 - target.getHealth();
    }
}

