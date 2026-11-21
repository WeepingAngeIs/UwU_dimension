package dev.uwu.cupidparity;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CupidParityProbe {
    private static final BlockPos START = new BlockPos(259, 181, 259);
    private static final ChunkPos CHUNK = new ChunkPos(START);
    private static final String CHARM_OWNER = "uwudimension.charm_owner";
    private static final String CHARM_END = "uwudimension.charm_end";
    private static final String ATTACK_TARGET = "uwudimension.cupid_target";

    private enum Stage { LOAD, SETUP, DRAW_FIRST, HIT_FIRST, DRAW_SECOND, HIT_SECOND, WISH, DONE }

    private Stage stage = Stage.LOAD;
    private ServerLevel level;
    private ServerPlayer archer;
    private EmbeddedChannel channel;
    private ItemStack bow;
    private Cow first;
    private Cow second;
    private Cow foe;
    private long stageStart;
    private boolean smooth;
    private Method designate;

    public boolean tick(MinecraftServer server) throws ReflectiveOperationException {
        if (level == null) {
            level = server.overworld();
            level.setChunkForced(CHUNK.x, CHUNK.z, true);
            designate = Class.forName("dev.uwu.dimension.CupidMinions").getDeclaredMethod("designate", ServerPlayer.class);
            designate.setAccessible(true);
            stageStart = level.getGameTime();
            return false;
        }
        require(elapsed() < 500, "Cupid fixture timed out at " + stage + ", smooth=" + smooth);
        switch (stage) {
            case LOAD -> {
                if (!level.isPositionEntityTicking(START)) return false;
                for (BlockPos floor : BlockPos.betweenClosed(START.offset(-2, -1, -2), START.offset(8, -1, 8))) {
                    level.setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    for (int dy = 1; dy <= 4; dy++) {
                        level.setBlock(floor.above(dy), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                next(Stage.SETUP);
            }
            case SETUP -> {
                spawnArcher(server);
                first = cow(START.east(5));
                second = cow(START.south(5));
                foe = cow(START.offset(5, 0, 5));
                bow = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", "cupid_bow"))
                        .getDefaultInstance();
                require(bow.getMaxDamage() == 6666 && bow.getHoverName().getString().equals("Cupid Bow"),
                        "Cupid Bow name or durability changed");
                if (smooth) CustomData.update(DataComponents.CUSTOM_DATA, bow,
                        t -> t.putBoolean("uwudimension:smooth_cupid", true));
                archer.setItemInHand(InteractionHand.MAIN_HAND, bow);
                draw(first, Stage.DRAW_FIRST);
            }
            case DRAW_FIRST, DRAW_SECOND -> {
                if (elapsed() < 21) return false;
                boolean opening = stage == Stage.DRAW_FIRST;
                require(archer.isUsingItem(), "Actual bow use was interrupted");
                archer.releaseUsingItem();
                require(!archer.getCooldowns().isOnCooldown(bow.getItem()), "Cupid Bow gained a release cooldown");
                require(bow.getDamageValue() == (opening ? 1 : 2), "Cupid use did not spend one durability");
                next(opening ? Stage.HIT_FIRST : Stage.HIT_SECOND);
            }
            case HIT_FIRST -> {
                if (!charmed(first)) return false;
                draw(second, Stage.DRAW_SECOND);
            }
            case HIT_SECOND -> {
                if (!charmed(second)) return false;
                aim(foe);
                designate.invoke(null, archer);
                for (Cow ally : List.of(first, second)) {
                    require(ally.getTarget() == foe && ally.getPersistentData().hasUUID(ATTACK_TARGET)
                                    && ally.getPersistentData().getUUID(ATTACK_TARGET).equals(foe.getUUID()),
                            "Attack did not designate the same foe for both charmed allies");
                }
                archer.setShiftKeyDown(true);
                for (Cow witness : List.of(first, second)) {
                    aim(witness);
                    designate.invoke(null, archer);
                }
                archer.setShiftKeyDown(false);
                next(Stage.WISH);
            }
            case WISH -> {
                if (elapsed() < 30) return false;
                var babies = level.getEntitiesOfClass(Cow.class, new AABB(START).inflate(9), Cow::isBaby);
                require(babies.size() == 1, "Expected one native Cupid wish result, found " + babies.size());
                long now = level.getGameTime();
                require(first.getPersistentData().getLong("uwudimension.wish_rest_until") > now
                                && second.getPersistentData().getLong("uwudimension.wish_rest_until") > now,
                        "Native Cupid wish did not apply witness rest");
                System.out.println("CUPID BOW GAMEPLAY " + (smooth ? "smooth" : "classic")
                        + " charmed two adults, commanded both, produced one wish and spent two durability without cooldown");
                for (Cow baby : babies) baby.discard();
                for (Cow entity : List.of(first, second, foe)) entity.discard();
                onlinePlayers(server).remove(archer);
                level.removePlayerImmediately(archer, Entity.RemovalReason.DISCARDED);
                channel.finishAndReleaseAll();
                if (!smooth) {
                    smooth = true;
                    next(Stage.SETUP);
                    return false;
                }
                level.setChunkForced(CHUNK.x, CHUNK.z, false);
                next(Stage.DONE);
                System.out.println("CUPID BOW GAMEPLAY PARITY PASS real releases, charm, Attack and crouched wish for both styles");
            }
            case DONE -> { return true; }
        }
        return stage == Stage.DONE;
    }

    private Cow cow(BlockPos feet) {
        Cow cow = EntityType.COW.create(level);
        require(cow != null, "Missing fixture cow");
        cow.moveTo(Vec3.atBottomCenterOf(feet));
        cow.setNoAi(true);
        require(level.addFreshEntity(cow), "Cow creation rejected");
        return cow;
    }

    private void draw(Cow target, Stage next) {
        aim(target);
        require(bow.use(level, archer, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "Cupid did not begin native use");
        next(next);
    }

    private boolean charmed(Cow cow) {
        var data = cow.getPersistentData();
        if (!data.hasUUID(CHARM_OWNER)) {
            require(elapsed() < 30, "A real Cupid arrow missed its fixture target, smooth=" + smooth);
            return false;
        }
        long timeLeft = data.getLong(CHARM_END) - level.getGameTime();
        require(archer.getUUID().equals(data.getUUID(CHARM_OWNER)) && data.getBoolean("uwu.pastel_cupid")
                        && timeLeft >= 790 && timeLeft <= 800 && cow.getHealth() == cow.getMaxHealth(),
                "Cupid changed the 800-tick charm or damaged a successfully charmed target");
        return true;
    }

    private void aim(Cow cow) {
        archer.lookAt(Anchor.EYES, cow.getBoundingBox().getCenter());
    }

    private void spawnArcher(MinecraftServer server) throws ReflectiveOperationException {
        archer = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), smooth ? "SmoothArcher" : "ClassicArcher"),
                ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new ServerGamePacketListenerImpl(server, connection, archer,
                CommonListenerCookie.createInitial(archer.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        channel = new EmbeddedChannel(connection);
        archer.setGameMode(GameType.SURVIVAL);
        archer.setNoGravity(true);
        archer.moveTo(Vec3.atBottomCenterOf(START));
        onlinePlayers(server).add(archer);
        level.addNewPlayer(archer);
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayers(MinecraftServer server) throws ReflectiveOperationException {
        var players = PlayerList.class.getDeclaredField("players");
        players.setAccessible(true);
        return (List<ServerPlayer>) players.get(server.getPlayerList());
    }

    private long elapsed() { return level.getGameTime() - stageStart; }
    private void next(Stage next) { stage = next; stageStart = level.getGameTime(); }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
