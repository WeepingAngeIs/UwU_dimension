package dev.uwu.hotbarcheck;

import dev.uwu.dimension.RelicFlight;
import dev.uwu.dimension.RevivalBlossoms;
import net.bettercombat.network.Packets;
import net.bettercombat.network.ServerNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Runs the original Better Combat server request path in the disposable hotbar world. */
final class ReturnbloomProbe {
    private enum Step { OTHER, MISS, REJECTED, THROW, CATCH, GROUP, EARLY, READY, DONE }
    private static final String KEY = "uwu.returnbloom_turn_back_until";
    private static final float SIDEWAYS = 95;
    private final ServerPlayer player;
    private final Vec3 aim;
    private final Cow left, right, thrownAt;
    private Step step = Step.OTHER;
    private long next, triggered;
    private double beforeThrow;

    ReturnbloomProbe(ServerPlayer player) {
        this.player = player;
        var level = player.serverLevel();
        BlockPos floor = player.blockPosition().above(4);
        for (int x = -4; x <= 4; x++) {
            for (int z = -3; z <= 12; z++) {
                var block = floor.offset(x, 0, z);
                level.setBlockAndUpdate(block, Blocks.DEEPSLATE.defaultBlockState());
                level.setBlockAndUpdate(block.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(block.above(2), Blocks.AIR.defaultBlockState());
            }
        }
        aim = Vec3.atBottomCenterOf(floor.above());
        player.teleportTo(aim.x, aim.y, aim.z);
        player.setYRot(0);
        player.setXRot(3);
        left = makeCow(-.7, 2.3);
        right = makeCow(.7, 2.3);
        thrownAt = makeCow(0, 5.75);
        equip(new ItemStack(Items.IRON_SWORD));
        next = now() + 20;
    }

    boolean tick() throws ReflectiveOperationException {
        long time = now();
        if (time < next) return false;
        switch (step) {
            case OTHER -> {
                place(left, -.7, 2.3);
                float health = left.getHealth();
                player.attack(left);
                require(left.getHealth() < health && !turned(left) && cooldown() == 0, "Ordinary sword triggered Turn Back");
                equip(new ItemStack(RevivalBlossoms.DIRK.get()));
                advance(Step.MISS, 20);
            }
            case MISS -> {
                request();
                require(cooldown() == 0 && !turned(left) && !turned(right), "Empty Better Combat swing spent Turn Back");
                advance(Step.REJECTED, 20);
            }
            case REJECTED -> {
                place(left, -.7, 2.3);
                left.setInvulnerable(true);
                float health = left.getHealth();
                request(left);
                require(left.getHealth() == health && cooldown() == 0 && !turned(left),
                        "Rejected melee hit spent Turn Back");
                left.setInvulnerable(false);
                advance(Step.THROW, 20);
            }
            case THROW -> {
                place(left, -3, -2);
                place(right, 3, -2);
                place(thrownAt, 0, 5.75);
                beforeThrow = thrownAt.getHealth();
                player.setYRot(0);
                player.setXRot(3);
                var cast = RelicFlight.class.getDeclaredMethod("castReturnbloom", net.minecraft.server.level.ServerLevel.class,
                        Player.class, InteractionHand.class);
                cast.setAccessible(true);
                require((boolean) cast.invoke(null, player.serverLevel(), player, InteractionHand.MAIN_HAND),
                        "Actual Dirk throw failed to launch");
                require(player.getMainHandItem().isEmpty(), "Dirk throw left its stock in the held slot");
                advance(Step.CATCH, 2);
            }
            case CATCH -> {
                if (!player.getMainHandItem().is(RevivalBlossoms.DIRK.get())) {
                    require(time <= next + 140, "Thrown Dirk did not return before the control expired");
                    return false;
                }
                require(thrownAt.getHealth() < beforeThrow && !turned(thrownAt) && cooldown() == 0,
                        "Dirk throw missed its witness or incorrectly fired Turn Back");
                advance(Step.GROUP, 20);
            }
            case GROUP -> {
                require(group(true), "Two real hits in the same Better Combat request did not both turn");
                triggered = time;
                require(cooldown() == triggered + 120, "Turn Back did not spend exactly 120 ticks once per group");
                next = triggered + 96;
                step = Step.EARLY;
            }
            case EARLY -> {
                require(group(false), "A real swing during the six-second cooldown triggered Turn Back");
                require(cooldown() == triggered + 120, "Gated hits extended the original cooldown");
                next = triggered + 120;
                step = Step.READY;
            }
            case READY -> {
                require(time == triggered + 120 && group(true) && cooldown() == time + 120,
                        "Turn Back was not available exactly six seconds later");
                left.discard();
                right.discard();
                thrownAt.discard();
                System.out.println("RETURNBLOOM TURN BACK PASS two victims per Better Combat swing, 120 ticks,"
                        + " miss/rejected hit/projectile/other weapon controls");
                step = Step.DONE;
                return true;
            }
            case DONE -> { return true; }
        }
        return false;
    }

    private boolean group(boolean expectedTurn) {
        place(left, -.7, 2.3);
        place(right, .7, 2.3);
        float first = left.getHealth(), second = right.getHealth();
        request(left, right);
        return left.getHealth() < first && right.getHealth() < second
                && turned(left) == expectedTurn && turned(right) == expectedTurn;
    }

    private void request(Cow... targets) {
        int[] ids = java.util.Arrays.stream(targets).mapToInt(Cow::getId).toArray();
        int cursor = ids.length > 0 ? ids[0] : -1;
        var packet = new Packets.C2S_AttackRequest(0, false, player.getInventory().selected, cursor, ids);
        ServerNetwork.handleAttackRequest(packet, player.serverLevel().getServer(), player, player.connection);
    }

    private Cow makeCow(double x, double z) {
        var cow = EntityType.COW.create(player.serverLevel());
        require(cow != null, "Could not create Turn Back witness");
        cow.setNoAi(true);
        cow.setPersistenceRequired();
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        cow.setHealth(200);
        place(cow, x, z);
        require(player.serverLevel().addFreshEntity(cow), "Could not add Turn Back witness");
        return cow;
    }

    private void place(Cow cow, double x, double z) {
        cow.moveTo(aim.x + x, aim.y, aim.z + z, SIDEWAYS, 0);
        cow.setYHeadRot(SIDEWAYS);
        cow.setYBodyRot(SIDEWAYS);
        cow.setDeltaMovement(Vec3.ZERO);
        cow.invulnerableTime = 0;
    }

    private void equip(ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.containerMenu.broadcastChanges();
    }

    private boolean turned(Cow cow) {
        return Math.abs(Mth.wrapDegrees(cow.getYRot() - player.getYRot())) < 1
                && Math.abs(Mth.wrapDegrees(cow.yBodyRot - player.getYRot())) < 1;
    }

    private long now() { return player.serverLevel().getServer().overworld().getGameTime(); }
    private long cooldown() { return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong(KEY); }
    private void advance(Step step, int ticks) { this.step = step; next = now() + ticks; }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
