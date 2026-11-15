package dev.uwu.test;

import java.util.UUID;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.RelicFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Uses the real optional Open Parties and Claims API on a disposable server world. */
public final class OpacToolProof {
    private OpacToolProof() {}

    public static final class BreakObserver {
        final BlockPos position;
        int events;
        BreakObserver(BlockPos position) { this.position = position; }
        @net.neoforged.bus.api.SubscribeEvent
        public void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
            if (event.getPos().equals(position)) events++;
        }
    }

    private static int cropDrops(ServerLevel level, BlockPos position) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(position).inflate(3)).stream()
                .filter(entity -> entity.getItem().is(net.minecraft.world.item.Items.WHEAT)
                        || entity.getItem().is(net.minecraft.world.item.Items.WHEAT_SEEDS))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    static void run(ServerLevel level, BlockPos base, ServerPlayer owner, ServerPlayer stranger) throws Exception {
        if (!ModList.get().isLoaded("openpartiesandclaims")) return;
        Class<?> apiType = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
        Object api = apiType.getMethod("get", MinecraftServer.class).invoke(null, level.getServer());
        Object claims = apiType.getMethod("getServerClaimsManager").invoke(api);
        Class<?> claimsType = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
        Object protection = apiType.getMethod("getChunkProtection").invoke(api);
        Class<?> protectionType = Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
        var access = protectionType.getMethod("hasChunkAccess", UUID.class, ResourceLocation.class, int.class, int.class);
        var claim = claimsType.getMethod("claim", ResourceLocation.class, UUID.class, int.class, int.class, int.class, boolean.class);
        var unclaim = claimsType.getMethod("unclaim", ResourceLocation.class, int.class, int.class);
        var get = claimsType.getMethod("get", ResourceLocation.class, int.class, int.class);
        ResourceLocation dimension = level.dimension().location();
        BlockPos crop = base.offset(3, 0, 0);
        int x = crop.getX() >> 4, z = crop.getZ() >> 4;
        if (get.invoke(claims, dimension, x, z) != null)
            throw new AssertionError("Disposable claim fixture is already claimed");
        var oldCrop = level.getBlockState(crop);
        var oldFloor = level.getBlockState(crop.below());
        var observer = new BreakObserver(crop);
        NeoForge.EVENT_BUS.register(observer);
        try {
            level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            if (claim.invoke(claims, dimension, owner.getUUID(), 0, x, z, false) == null)
                throw new AssertionError("Open Parties and Claims refused the test claim");
            if (!Boolean.TRUE.equals(access.invoke(protection, owner.getUUID(), dimension, x, z))
                    || !Boolean.FALSE.equals(access.invoke(protection, stranger.getUUID(), dimension, x, z)))
                throw new AssertionError("Claim owner and stranger access differ from expected roles");
            int dropsBefore = cropDrops(level, crop);
            harvest(level, stranger, crop);
            if (!level.getBlockState(crop).is(Blocks.WHEAT) || observer.events != 0
                    || cropDrops(level, crop) != dropsBefore)
                throw new AssertionError("A stranger's chakram harvested inside the claim");
            harvest(level, owner, crop);
            if (!level.getBlockState(crop).isAir() || observer.events != 1
                    || cropDrops(level, crop) <= dropsBefore)
                throw new AssertionError("The owner's chakram did not produce a break event and crop drops");
            System.out.println("UWU OPAC CLAIM PASS chakram denied stranger, owner broke crop with event and drops");
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
            unclaim.invoke(claims, dimension, x, z);
            level.setBlockAndUpdate(crop, oldCrop);
            level.setBlockAndUpdate(crop.below(), oldFloor);
        }
    }

    private static void harvest(ServerLevel level, ServerPlayer player, BlockPos crop) {
        player.getInventory().clearContent();
        var entry = CounterArsenal.ROSTER.stream().filter(e -> e.shape().equals("chakram")).findFirst().orElseThrow();
        var item = CounterArsenal.ITEMS.get(entry.id()).get();
        player.getCooldowns().removeCooldown(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        item.use(level, player, InteractionHand.MAIN_HAND);
        RelicFlight flight = level.getEntitiesOfClass(RelicFlight.class, player.getBoundingBox().inflate(5),
                entity -> entity.getOwner() == player && entity.getItem().is(item)).stream().findFirst().orElseThrow();
        try {
            flight.setPos(crop.getX()+.5, crop.getY()+.5, crop.getZ()+.5);
            flight.xo = flight.getX(); flight.yo = flight.getY(); flight.zo = flight.getZ();
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(flight));
        } finally {
            flight.discard();
            player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove("uwudimension.relic_returns");
            player.getInventory().clearContent();
        }
    }
}
