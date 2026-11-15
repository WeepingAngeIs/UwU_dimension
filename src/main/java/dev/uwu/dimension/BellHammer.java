package dev.uwu.dimension;

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** A deliberate crouch-break queues one 9 by 9 plane, processed at eight blocks per tick. */
@EventBusSubscriber(modid = "uwudimension")
public final class BellHammer {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    static final Supplier<Item> ITEM = ITEMS.register("bell_hammer", HammerItem::new);
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static boolean processing;

    private BellHammer() {}

    static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(ITEM.get());
        });
    }

    static final class HammerItem extends Item {
        HammerItem() {
            super(new Properties().durability(1300).rarity(Rarity.RARE)
                    .component(net.minecraft.core.component.DataComponents.TOOL,
                            Tiers.DIAMOND.createToolProperties(BlockTags.MINEABLE_WITH_PICKAXE)));
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity user) {
            if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0)
                stack.hurtAndBreak(1, user, EquipmentSlot.MAINHAND);
            return true;
        }
        @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                java.util.List<net.minecraft.network.chat.Component> tips, net.minecraft.world.item.TooltipFlag flag) {
            tips.add(net.minecraft.network.chat.Component.literal("Crouch and mine for a 9 × 9 plane when server area mining is enabled."));
        }
    }

    private record Job(ServerLevel level, UUID owner, ItemStack tool, BlockPos center, long startTick, ArrayDeque<BlockPos> positions) {}

    private static boolean hammer(ItemStack stack) {
        return stack.is(ITEM.get()) || (stack.getItem() instanceof CounterArsenal.CounterItem item
                && item.entry.shape().equals("hammer"));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (processing || event.isCanceled() || !AcquisitionPolicy.SPEC.isLoaded() || !AcquisitionPolicy.AREA_MINING.get()
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !player.isShiftKeyDown() || !hammer(player.getMainHandItem())
                || !SakuraAccess.mayInteract(player, event.getPos()) || JOBS.size() >= 32) return;
        if (!event.getState().is(BlockTags.MINEABLE_WITH_PICKAXE) || event.getState().hasBlockEntity()
                || !player.getMainHandItem().isCorrectToolForDrops(event.getState())) return;
        BlockPos center = event.getPos().immutable();
        var look = player.getLookAngle();
        Direction.Axis axis = Math.abs(look.y) >= Math.max(Math.abs(look.x), Math.abs(look.z))
                ? Direction.Axis.Y : Math.abs(look.x) >= Math.abs(look.z) ? Direction.Axis.X : Direction.Axis.Z;
        ArrayDeque<BlockPos> positions = new ArrayDeque<>();
        for (int u = -4; u <= 4; u++) {
            for (int v = -4; v <= 4; v++) {
                if (u == 0 && v == 0) continue;
                positions.add(switch (axis) {
                    case X -> center.offset(0, u, v);
                    case Y -> center.offset(u, 0, v);
                    case Z -> center.offset(u, v, 0);
                });
            }
        }
        JOBS.add(new Job(player.serverLevel(), player.getUUID(), player.getMainHandItem(), center,
                player.serverLevel().getGameTime(), positions));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        int budget = 8;
        while (budget > 0 && !JOBS.isEmpty()) {
            Job job = JOBS.peek();
            if (job.level().getServer() != event.getServer()) { JOBS.remove(); continue; }
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(job.owner());
            if (!AcquisitionPolicy.SPEC.isLoaded() || !AcquisitionPolicy.AREA_MINING.get()
                    || player == null || !player.isAlive() || player.serverLevel() != job.level()
                    || player.getMainHandItem() != job.tool() || !hammer(player.getMainHandItem())) { JOBS.remove(); continue; }
            if (job.level().getGameTime() <= job.startTick()) break;
            if (!job.level().hasChunkAt(job.center()) || !job.level().getBlockState(job.center()).isAir()) {
                JOBS.remove();
                continue;
            }
            BlockPos pos = job.positions().poll();
            if (pos == null) { JOBS.remove(); continue; }
            budget--;
            if (!job.level().hasChunkAt(pos)) continue;
            BlockState state = job.level().getBlockState(pos);
            if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.hasBlockEntity()
                    || !player.getMainHandItem().isCorrectToolForDrops(state) || state.getDestroySpeed(job.level(), pos) < 0
                    || !SakuraAccess.mayInteract(player, pos)) continue;
            processing = true;
            try {
                player.gameMode.destroyBlock(pos);
            } finally {
                processing = false;
            }
        }
    }
}

