package dev.uwu.dimension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Original blossom weapons share a durable, owner-and-item death-prevention pool. */
public final class RevivalBlossoms {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    public static final java.util.function.Supplier<Item> DIRK = ITEMS.register("returnbloom_dirk", FlowerDirk::new);
    public static final java.util.function.Supplier<Item> AXE = ITEMS.register("dawnpetal_axe", FlowerAxe::new);
    public static final int COOLDOWN_TICKS = 1200;
    private static final String ID = "uwu_revival_id", PHASE = "uwu_revival_phase", LEFT = "uwu_revival_left";

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        // Vanilla totems are checked before die(). Earlier mod rescue cancellations win as well.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RevivalBlossoms::preventDeath);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.COMBAT) {
                event.accept(DIRK.get());
                event.accept(AXE.get());
            }
        });
    }

    public static boolean isWeapon(ItemStack stack) { return stack.is(DIRK.get()) || stack.is(AXE.get()); }
    private static long now(ServerLevel level) { return level.getServer().overworld().getGameTime(); }
    private static Ledger ledger(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Ledger::new, Ledger::load, null), "uwu_blossom_revival");
    }
    private static UUID identity(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(ID)) return tag.getUUID(ID);
        UUID id = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.putUUID(ID, id));
        return id;
    }
    public static long remaining(ServerPlayer player, ItemStack stack) {
        if (!isWeapon(stack)) return 0;
        return ledger(player.serverLevel()).remaining(player.getUUID(), identity(stack), now(player.serverLevel()));
    }
    public static int visualPhase(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(PHASE);
    }
    private static void refresh(ServerPlayer owner, ItemStack stack) {
        long left = remaining(owner, stack);
        int phase = left == 0 ? 0 : 1 + (int)((COOLDOWN_TICKS - left) / 240);
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.getInt(PHASE) == phase && data.getLong(LEFT) == left) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(PHASE, phase);
            tag.putLong(LEFT, left);
        });
    }
    private static void inventory(ItemStack stack, Level level, Entity holder) {
        if (!(holder instanceof ServerPlayer owner)) return;
        identity(stack);
        if (owner.tickCount % 20 == 0) refresh(owner, stack);
        if (owner.tickCount % 12 == 0 && (owner.getMainHandItem() == stack || owner.getOffhandItem() == stack)
                && remaining(owner, stack) == 0) {
            for (var viewer : owner.serverLevel().players()) {
                if (viewer.distanceToSqr(owner) > 24 * 24 || ReachLife.preference(viewer, "sakura_effects", 2) == 0) continue;
                owner.serverLevel().sendParticles(viewer, MagicGlowParticles.FLOWER.get(), false,
                        owner.getX(), owner.getY() + 1.05, owner.getZ(), 1, .25, .12, .25, .003);
            }
        }
    }
    private static void tooltip(ItemStack stack, List<Component> tips) {
        tips.add(Component.literal("A ready sakura restores full health when a fatal blow lands.").withStyle(ChatFormatting.LIGHT_PURPLE));
        long left = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(LEFT);
        tips.add(Component.literal(left > 0 ? "Blossom recharging: " + (left + 19) / 20 + "s"
                : "Blossom ready • shared 60s revival cooldown").withStyle(left > 0 ? ChatFormatting.GRAY : ChatFormatting.GREEN));
    }
    private static void preventDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isRemoved() || player.deathTime != 0
                || player.getHealth() > 0 || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ItemStack chosen = player.getMainHandItem();
        if (!isWeapon(chosen) || remaining(player, chosen) > 0) chosen = player.getOffhandItem();
        if (!isWeapon(chosen)) return;
        long time = now(player.serverLevel());
        if (!ledger(player.serverLevel()).claim(player.getUUID(), identity(chosen), time)) return;
        // State is committed before cancellation or health changes, including reentrant damage listeners.
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        for (ItemStack stack : player.getHandSlots()) if (isWeapon(stack)) refresh(player, stack);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .9F, 1.3F);
        for (int petal = 0; petal < 5; petal++) {
            double angle = petal * Math.PI * 2 / 5;
            HeartCombatGlow.charmHearts(player.serverLevel(), player.getX() + Math.cos(angle) * .45,
                    player.getY() + .9, player.getZ() + Math.sin(angle) * .45, 2, .025, .08, .025, .035);
        }
    }

    private static final class FlowerDirk extends SwordItem {
        FlowerDirk() { super(Tiers.NETHERITE, new Item.Properties().rarity(Rarity.EPIC).attributes(SwordItem.createAttributes(Tiers.NETHERITE, 10, -1.8F))); }
        @Override public boolean shouldCauseReequipAnimation(ItemStack before,ItemStack after,boolean slotChanged) { return slotChanged || before.getItem()!=after.getItem(); }
        @Override public void inventoryTick(ItemStack s, Level l, Entity e, int slot, boolean selected) { super.inventoryTick(s,l,e,slot,selected); inventory(s,l,e); }
        @Override public void appendHoverText(ItemStack s, Item.TooltipContext c, List<Component> tips, TooltipFlag f) { super.appendHoverText(s,c,tips,f); tooltip(s,tips); }
    }
    private static final class FlowerAxe extends AxeItem {
        FlowerAxe() { super(Tiers.NETHERITE, new Item.Properties().rarity(Rarity.EPIC).attributes(AxeItem.createAttributes(Tiers.NETHERITE, 17, -3.05F))); }
        @Override public boolean shouldCauseReequipAnimation(ItemStack before,ItemStack after,boolean slotChanged) { return slotChanged || before.getItem()!=after.getItem(); }
        @Override public void inventoryTick(ItemStack s, Level l, Entity e, int slot, boolean selected) { super.inventoryTick(s,l,e,slot,selected); inventory(s,l,e); }
        @Override public void appendHoverText(ItemStack s, Item.TooltipContext c, List<Component> tips, TooltipFlag f) { super.appendHoverText(s,c,tips,f); tooltip(s,tips); }
    }

    private static final class Ledger extends SavedData {
        private final Map<UUID,Long> owners = new HashMap<>(), stacks = new HashMap<>();
        private long prunedAt = Long.MIN_VALUE;
        private void prune(long time) {
            if (time == prunedAt) return;
            prunedAt = time;
            if (owners.values().removeIf(end -> end <= time) | stacks.values().removeIf(end -> end <= time)) setDirty();
        }
        long remaining(UUID owner, UUID stack, long time) {
            prune(time);
            return Math.max(0, Math.max(owners.getOrDefault(owner,0L), stacks.getOrDefault(stack,0L)) - time);
        }
        boolean claim(UUID owner, UUID stack, long time) {
            if (remaining(owner, stack, time) > 0) return false;
            owners.put(owner, time + COOLDOWN_TICKS);
            stacks.put(stack, time + COOLDOWN_TICKS);
            setDirty();
            return true;
        }
        static Ledger load(CompoundTag nbt, HolderLookup.Provider registries) {
            Ledger result = new Ledger();
            read(nbt.getCompound("Owners"), result.owners);
            read(nbt.getCompound("Stacks"), result.stacks);
            return result;
        }
        private static void read(CompoundTag source, Map<UUID,Long> target) {
            for (String key : source.getAllKeys()) {
                try { target.put(UUID.fromString(key), source.getLong(key)); }
                catch (IllegalArgumentException ignored) { /* A bad entry cannot reset the other valid deadlines. */ }
            }
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag people = new CompoundTag(), items = new CompoundTag();
            owners.forEach((id,end) -> people.putLong(id.toString(), end));
            stacks.forEach((id,end) -> items.putLong(id.toString(), end));
            tag.put("Owners",people);tag.put("Stacks",items);
            return tag;
        }
    }
    private RevivalBlossoms() {}
}
