package dev.uwu.test;

import java.util.UUID;
import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.RelicFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Exercises actual item, container and block break paths on the disposable hosted server. */
final class ToolLoreProof {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ServerLevel level) throws Exception {
        BlockPos base = new BlockPos(5, level.getMaxBuildHeight() - 20, 5);
        level.getChunkAt(base);
        try (var first = new ChestProof.Visitor(level, base, "LoreTestA", UUID.randomUUID());
             var second = new ChestProof.Visitor(level, base, "LoreTestB", UUID.randomUUID())) {
            ServerPlayer player = first.player;
            ServerPlayer other = second.player;
            level.addNewPlayer(player);
            level.addNewPlayer(other);
            try {
                dagger(level, base, player);
                DaggerProof.run(level, base, player, other);
                fishing(level, player);
                chakram(level, base, player);
                lore(level, base, player, other);
                OpacToolProof.run(level, base, player, other);
            } finally {
                player.discard();
                other.discard();
            }
        }
        System.out.println("UWU TOOLS AND LORE SERVER PASS dagger, fishing, crops and personal discovery");
    }

    private static void dagger(ServerLevel level, BlockPos base, ServerPlayer player) throws Exception {
        var target = EntityType.HUSK.create(level);
        require(target != null, "No dagger target");
        target.moveTo(base.getX()+.5, base.getY(), base.getZ()+.5, 0, 0);
        target.setYHeadRot(0);
        target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        level.addFreshEntity(target);
        ItemStack dagger = new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.ROSE_DAGGER).get());
        player.setItemInHand(InteractionHand.MAIN_HAND, dagger);
        var cooldown = LivingEntity.class.getDeclaredField("attackStrengthTicker");
        cooldown.setAccessible(true);
        // Apothic Attributes independently rolls criticals, which would skew one
        // side of this deterministic front/rear comparison. Restore it afterwards.
        var chanceKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ATTRIBUTE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("apothic_attributes", "crit_chance"));
        var chance = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(chanceKey)
                .map(player::getAttribute).orElse(null);
        double previousChance = chance == null ? 0 : chance.getBaseValue();
        if (chance != null) chance.setBaseValue(0);
        try {
            player.moveTo(target.getX(), target.getY(), target.getZ()+1.5, 180, 0);
            cooldown.setInt(player, 100);
            player.setOnGround(true);
            player.attack(target);
            float front = 200 - target.getHealth();
            target.setHealth(200);
            target.invulnerableTime = 0;
            target.setYRot(0);
            target.setYHeadRot(0);
            player.moveTo(target.getX(), target.getY(), target.getZ()-1.5, 0, 0);
            cooldown.setInt(player, 100);
            player.setOnGround(true);
            player.attack(target);
            float rear = 200 - target.getHealth();
            require(front > 0 && rear > front * 1.8F && rear < front * 2.2F,
                    "Dagger front/rear damage incorrect: " + front + "/" + rear);
        } finally {
            if (chance != null) chance.setBaseValue(previousChance);
            target.discard();
            player.getInventory().clearContent();
        }
    }

    private static void fishing(ServerLevel level, ServerPlayer player) {
        var entry = CounterArsenal.ROSTER.stream().filter(e -> e.shape().equals("spear")).findFirst().orElseThrow();
        var spear = CounterArsenal.ITEMS.get(entry.id()).get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(spear));
        player.setShiftKeyDown(true);
        spear.use(level, player, InteractionHand.MAIN_HAND);
        require(player.fishing != null && player.getMainHandItem().is(spear),
                "Sneaking with spear failed to cast the fishing hook");
        spear.use(level, player, InteractionHand.MAIN_HAND);
        require(player.fishing == null && player.getMainHandItem().is(spear),
                "Sneaking again failed to retrieve hook and preserve spear");
        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
    }

    private static void chakram(ServerLevel level, BlockPos base, ServerPlayer player) {
        var entry = CounterArsenal.ROSTER.stream().filter(e -> e.shape().equals("chakram")).findFirst().orElseThrow();
        var weapon = CounterArsenal.ITEMS.get(entry.id()).get();
        player.getCooldowns().removeCooldown(weapon);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
        weapon.use(level, player, InteractionHand.MAIN_HAND);
        RelicFlight flight = level.getEntitiesOfClass(RelicFlight.class, player.getBoundingBox().inflate(5),
                item -> item.getOwner() == player && item.getItem().is(weapon)).stream().findFirst().orElseThrow();
        BlockPos mature = base.offset(2, 0, 0);
        BlockPos immature = mature.offset(0, 0, 1);
        var oldMature = level.getBlockState(mature);
        var oldImmature = level.getBlockState(immature);
        var oldFloor = level.getBlockState(mature.below());
        var oldFloor2 = level.getBlockState(immature.below());
        try {
            level.setBlockAndUpdate(mature.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(immature.below(), Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(mature, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            level.setBlockAndUpdate(immature, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));
            flight.setPos(mature.getX()+.5, mature.getY()+.5, mature.getZ()+.5);
            flight.xo = flight.getX(); flight.yo = flight.getY(); flight.zo = flight.getZ();
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(flight));
            require(level.getBlockState(mature).isAir(), "Chakram missed mature wheat");
            require(level.getBlockState(immature).is(Blocks.WHEAT), "Chakram harvested immature wheat");
            var flights = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                    .getCompound("uwudimension.relic_returns");
            var saved = ItemStack.parseOptional(level.registryAccess(),
                    flights.getCompound(flight.getStringUUID()).getCompound("Stack"));
            require(saved.getDamageValue() == 2, "Harvest did not wear reserved weapon");
            level.setBlockAndUpdate(mature, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(flight));
            require(level.getBlockState(mature).is(Blocks.WHEAT), "Same throw harvested one crop twice");
        } finally {
            flight.discard();
            level.setBlockAndUpdate(mature, oldMature);
            level.setBlockAndUpdate(immature, oldImmature);
            level.setBlockAndUpdate(mature.below(), oldFloor);
            level.setBlockAndUpdate(immature.below(), oldFloor2);
            player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove("uwudimension.relic_returns");
            player.getInventory().clearContent();
        }
    }

    private static void openChest(ServerLevel level, ServerPlayer player, BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static void seedWinningRoll(ServerPlayer player) {
        for (long seed = 1; seed < 1000; seed++) {
            player.getRandom().setSeed(seed);
            if (player.getRandom().nextFloat() < .75F) {
                player.getRandom().setSeed(seed);
                return;
            }
        }
        throw new AssertionError("No deterministic winning random seed");
    }

    private static void lore(ServerLevel level, BlockPos base, ServerPlayer first, ServerPlayer second) {
        BlockPos pos = base.offset(4, 0, 0);
        var old = level.getBlockState(pos);
        try {
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            var chest = (RandomizableContainerBlockEntity) level.getBlockEntity(pos);
            chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.withDefaultNamespace("chests/simple_dungeon")));
            first.getInventory().clearContent();
            for (int slot=0; slot<36; slot++) first.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            seedWinningRoll(first);
            openChest(level, first, pos);
            CompoundTag a = first.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            require(a.getLongArray("uwu_lore_chests").length == 1, "First chest did not record an opening");
            require(a.getBoolean("uwu_lore_pending"), "Full inventory did not retain winning discovery");
            first.closeContainer();
            first.getInventory().setItem(0, ItemStack.EMPTY);
            first.tickCount = 40;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(first));
            require(a.getBoolean("uwu_lore_awarded") && !a.getBoolean("uwu_lore_pending"),
                    "Pending book did not deliver after freeing a slot");
            var book = first.getInventory().getItem(0);
            require(book.is(Items.WRITTEN_BOOK) && book.has(DataComponents.WRITTEN_BOOK_CONTENT),
                    "Missing illustrated lore book");
            openChest(level, first, pos);
            require(a.getLongArray("uwu_lore_chests").length == 1, "Reopening counted twice");
            first.closeContainer();
            seedWinningRoll(second);
            openChest(level, second, pos);
            CompoundTag b = second.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            require(b.getLongArray("uwu_lore_chests").length == 1 && b.getBoolean("uwu_lore_awarded"),
                    "Second player did not receive independent discovery");
            second.closeContainer();
            CompoundTag serialized = first.saveWithoutId(new CompoundTag());
            try (var restored = new ChestProof.Visitor(level, base, "LoreRestored", UUID.randomUUID())) {
                restored.player.load(serialized);
                var saved = restored.player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                require(saved.getBoolean("uwu_lore_awarded") && saved.getLongArray("uwu_lore_chests").length == 1,
                        "Player serialization lost discovery state");
            }
        } finally {
            level.setBlockAndUpdate(pos, old);
        }
    }
}
