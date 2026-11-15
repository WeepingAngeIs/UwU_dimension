package dev.uwu.test;

import java.util.Map;
import java.util.UUID;
import dev.uwu.dimension.AcquisitionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Multi-tick tests on a disposable dedicated server, with a connected test visitor. */
final class ToolAreaProof {
    private static ChestProof.Visitor visitor;
    private static ServerPlayer player;
    private static Map<UUID, ServerPlayer> playersById;
    private static BlockPos center;
    private static boolean oldAreaSetting, forced;
    private static net.neoforged.neoforge.common.ModConfigSpec.BooleanValue excavation;
    private static boolean oldExcavation;
    private static int stage, ticks;
    private static Object claims;
    private static java.lang.reflect.Method claimMethod, unclaimMethod;
    private static boolean testClaim;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playerMap(ServerLevel level) throws Exception {
        // The dedicated player list is a subclass and runtime field names can be mapped.
        // Find the unique UUID-to-ServerPlayer map by its declared generic types instead.
        for (var field : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            if (!(field.getGenericType() instanceof java.lang.reflect.ParameterizedType type)
                    || type.getRawType() != Map.class) continue;
            var args = type.getActualTypeArguments();
            if (args.length == 2 && args[0] == UUID.class && args[1] == ServerPlayer.class) {
                field.setAccessible(true);
                return (Map<UUID, ServerPlayer>)field.get(level.getServer().getPlayerList());
            }
        }
        throw new NoSuchFieldException("PlayerList UUID-to-player map");
    }

    private static Item item(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", name));
    }

    private static void fill(ServerLevel level) {
        for (int x=-4; x<=4; x++) for (int z=-4; z<=4; z++)
            level.setBlockAndUpdate(center.offset(x, 0, z), Blocks.STONE.defaultBlockState());
    }

    private static int remaining(ServerLevel level) {
        int count = 0;
        for (int x=-4; x<=4; x++) for (int z=-4; z<=4; z++)
            if (level.getBlockState(center.offset(x, 0, z)).is(Blocks.STONE)) count++;
        return count;
    }

    private static void shootDown(ServerLevel level) {
        Item launcher = item("heart_launcher");
        player.getCooldowns().removeCooldown(launcher);
        player.moveTo(8.5, center.getY()+5, 8.5, 0, 90);
        player.setDeltaMovement(0,0,0);
        launcher.use(level, player, InteractionHand.MAIN_HAND);
    }

    private static void claim(ServerLevel level, UUID owner) throws Exception {
        var dimension = level.dimension().location();
        if (testClaim) unclaimMethod.invoke(claims, dimension, 0, 0);
        testClaim = false;
        Object result = claimMethod.invoke(claims, dimension, owner, 0, 0, 0, false);
        require(result != null, "OPAC did not create the disposable claim");
        testClaim = true;
    }

    static boolean tick(ServerLevel level) throws Exception {
        try {
            if (stage == 0) {
                require(AcquisitionPolicy.SPEC.isLoaded(), "Server policy is not loaded");
                oldAreaSetting = AcquisitionPolicy.AREA_MINING.get();
                center = new BlockPos(8, level.getMaxBuildHeight()-24, 8);
                forced = level.setChunkForced(0, 0, true);
                level.getChunkAt(center);
                visitor = new ChestProof.Visitor(level, center, "AreaToolProof", UUID.randomUUID());
                player = visitor.player;
                level.addNewPlayer(player);
                playersById = playerMap(level);
                playersById.put(player.getUUID(), player);
                player.moveTo(8.5, center.getY()+3, 8.5, 0, 90);
                player.setNoGravity(true);
                player.setShiftKeyDown(true);
                Item hammer = item("bell_hammer");
                require(hammer != Items.AIR, "Bell Hammer item missing");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hammer));
                fill(level);
                AcquisitionPolicy.AREA_MINING.set(false);
                require(player.gameMode.destroyBlock(center), "Disabled-mode center break failed");
                stage = 1;
                ticks = 0;
                return false;
            }
            ticks++;
            if (stage == 1 && ticks >= 3) {
                require(remaining(level) == 80, "Disabled area mining broke neighboring blocks");
                AcquisitionPolicy.AREA_MINING.set(true);
                fill(level);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("bell_hammer")));
                require(player.gameMode.destroyBlock(center), "Enabled-mode center break failed");
                require(remaining(level) == 80, "Area mining did not wait for a later server tick");
                stage = 2;
                ticks = 0;
                return false;
            }
            if (stage == 2 && ticks >= 18) {
                require(remaining(level) == 0, "Hammer failed to mine the full 9 x 9 plane: "+remaining(level)+" remain");
                require(player.getMainHandItem().getDamageValue() == 81, "Hammer durability did not match 81 breaks");
                int drops = level.getEntitiesOfClass(ItemEntity.class,
                        new net.minecraft.world.phys.AABB(center).inflate(32)).stream()
                        .filter(e -> e.getItem().is(Items.COBBLESTONE)).mapToInt(e -> e.getItem().getCount()).sum();
                require(drops >= 81, "Hammer failed to drop cobblestone for all 81 blocks: "+drops);
                var entry = dev.uwu.dimension.CounterArsenal.ROSTER.stream()
                        .filter(e -> e.shape().equals("hammer")).findFirst().orElseThrow();
                Item counterHammer = dev.uwu.dimension.CounterArsenal.ITEMS.get(entry.id()).get();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(counterHammer));
                fill(level);
                require(player.gameMode.destroyBlock(center), "Counter greathammer center break failed");
                stage = 5;
                ticks = 0;
                return false;
            }
            if (stage == 5 && ticks >= 18) {
                require(remaining(level) == 0, "Counter greathammer failed to mine the 9 x 9 plane");
                require(player.getMainHandItem().getDamageValue() == 81,
                        "Counter greathammer durability did not match 81 breaks");
                player.setShiftKeyDown(false);
                Item launcher = item("heart_launcher");
                require(launcher != Items.AIR, "Heart Launcher item missing");
                ItemStack held = new ItemStack(launcher);
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                player.moveTo(8.5, center.getY()+3, 8.5, 0, 0);
                player.setDeltaMovement(0,0,0);
                launcher.use(level, player, InteractionHand.MAIN_HAND);
                require(player.getCooldowns().isOnCooldown(launcher) && held.getDamageValue() == 1,
                        "Launcher did not apply cooldown and shot durability");
                require(player.getDeltaMovement().z < 0, "Launcher did not kick the player backward");
                var projectiles = level.getEntitiesOfClass(ThrowableItemProjectile.class, player.getBoundingBox().inflate(5),
                        shot -> shot.getOwner()==player && BuiltInRegistries.ENTITY_TYPE.getKey(shot.getType())
                                .equals(ResourceLocation.fromNamespaceAndPath("uwudimension", "launcher_heart")));
                require(projectiles.size()==1, "Launcher failed to spawn one heart projectile");
                launcher.use(level, player, InteractionHand.MAIN_HAND);
                require(held.getDamageValue()==1, "Launcher fired again during recoil cooldown");
                var shot = projectiles.getFirst();
                var nbt = shot.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                nbt.putDouble("uwu_distance", 95.5);
                shot.load(nbt);
                shot.setOwner(player);
                shot.setDeltaMovement(.75, 0, 0);
                double before = shot.getX();
                shot.tick();
                require(shot.isRemoved() && shot.getX() - before <= .501,
                        "Heart projectile did not stop at 96 blocks of travel");
                var field = Class.forName("dev.uwu.dimension.HeartLauncher").getDeclaredField("EXCAVATION");
                field.setAccessible(true);
                excavation = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)field.get(null);
                oldExcavation = excavation.get();
                excavation.set(false);
                fill(level);
                shootDown(level);
                stage = 3;
                ticks = 0;
                return false;
            }
            if (stage == 3 && ticks >= 12) {
                require(remaining(level) == 81, "Default-off launcher damaged rock");
                excavation.set(true);
                shootDown(level);
                stage = 4;
                ticks = 0;
                return false;
            }
            if (stage == 4 && ticks >= 18) {
                int removed = 81 - remaining(level);
                require(removed > 0 && removed <= 32, "Enabled launcher failed its bounded excavation: " + removed);
                if (net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")) {
                    var apiType = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                    var api = apiType.getMethod("get", net.minecraft.server.MinecraftServer.class)
                            .invoke(null, level.getServer());
                    claims = apiType.getMethod("getServerClaimsManager").invoke(api);
                    var type = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
                    var get = type.getMethod("get", ResourceLocation.class, int.class, int.class);
                    require(get.invoke(claims, level.dimension().location(), 0, 0) == null,
                            "Disposable area proof chunk already had a claim");
                    claimMethod = type.getMethod("claim", ResourceLocation.class, UUID.class,
                            int.class, int.class, int.class, boolean.class);
                    unclaimMethod = type.getMethod("unclaim", ResourceLocation.class, int.class, int.class);
                    fill(level);
                    claim(level, UUID.randomUUID());
                    player.setShiftKeyDown(true);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("bell_hammer")));
                    player.moveTo(8.5, center.getY()+3, 8.5, 0, 90);
                    require(!player.gameMode.destroyBlock(center) && remaining(level) == 81,
                            "A stranger could break a protected hammer center");
                    stage = 6;
                    ticks = 0;
                    return false;
                }
                System.out.println("UWU AREA TOOLS SERVER PASS 81-block plane, policy, durability, drops, recoil, range and excavation toggle");
                close(level);
                return true;
            }
            if (stage == 6 && ticks >= 3) {
                require(remaining(level) == 81, "Stranger's hammer queued protected breaks");
                claim(level, player.getUUID());
                require(player.gameMode.destroyBlock(center), "Claim owner could not use the hammer");
                stage = 7;
                ticks = 0;
                return false;
            }
            if (stage == 7 && ticks >= 18) {
                require(remaining(level) == 0, "Claim owner's hammer did not finish its 9 x 9 plane");
                fill(level);
                claim(level, UUID.randomUUID());
                player.setShiftKeyDown(false);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("heart_launcher")));
                shootDown(level);
                stage = 8;
                ticks = 0;
                return false;
            }
            if (stage == 8 && ticks >= 18) {
                require(remaining(level) == 81, "Stranger's launcher excavated protected rock");
                claim(level, player.getUUID());
                shootDown(level);
                stage = 9;
                ticks = 0;
                return false;
            }
            if (stage == 9 && ticks >= 18) {
                int removed = 81 - remaining(level);
                require(removed > 0 && removed <= 32, "Claim owner's launcher failed to excavate permitted rock");
                System.out.println("UWU OPAC AREA PASS hammer and launcher denied stranger and allowed owner");
                System.out.println("UWU AREA TOOLS SERVER PASS 81-block plane, policy, durability, drops, recoil, range and excavation toggle");
                close(level);
                return true;
            }
            require(ticks < 100, "Area tool proof timed out at stage " + stage);
            return false;
        } catch (Throwable failure) {
            close(level);
            throw failure;
        }
    }

    private static void close(ServerLevel level) throws Exception {
        if (testClaim) {
            unclaimMethod.invoke(claims, level.dimension().location(), 0, 0);
            testClaim = false;
        }
        if (AcquisitionPolicy.SPEC.isLoaded()) AcquisitionPolicy.AREA_MINING.set(oldAreaSetting);
        if (excavation != null) excavation.set(oldExcavation);
        if (playersById != null && player != null) playersById.remove(player.getUUID());
        if (player != null) player.discard();
        if (visitor != null) visitor.close();
        if (forced) level.setChunkForced(0,0,false);
    }
}
