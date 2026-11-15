package dev.uwu.test;

import java.util.List;
import java.util.UUID;
import dev.uwu.dimension.ReefCharts;
import dev.uwu.dimension.ReefLife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;

/** Exercises material progression in a disposable server world through registered items and real chunk generation. */
public final class RenewalArmorProof {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }
    private static void check(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }
    public static void run(ServerLevel level) {
        for (String recipe : List.of("roseglass_prism_smelting", "vesper_opal_blasting", "confluence_lamp"))
            check(level.getRecipeManager().byKey(id(recipe)).isPresent(), "Missing recipe " + recipe);
        for (String material : List.of("petalweave", "nightbriar")) armor(level, material);
        Block rose = BuiltInRegistries.BLOCK.get(id("roseglass_ore"));
        Block opal = BuiltInRegistries.BLOCK.get(id("vesper_opal_ore"));
        boolean sawRose = false, sawOpal = false;
        for (int x = 0; x < 5 && !(sawRose && sawOpal); x++) for (int z = 0; z < 5 && !(sawRose && sawOpal); z++) {
            // Adjacent untouched chunks, away from the disposable visitor fixtures.
            var chunk = level.getChunk(18 + x, 18 + z);
            sawRose |= contains(chunk, rose);
            sawOpal |= contains(chunk, opal);
        }
        check(sawRose && sawOpal, "Generated Sakura chunks missing roseglass=" + !sawRose + " or opal=" + !sawOpal);
        var water = ReefCharts.nearest(level, BlockPos.ZERO, 0);
        check(water != null && level.getBiome(water).is(ReefLife.REEF) && level.getFluidState(water).is(FluidTags.WATER),
                "Missing navigable natural Blush Reef water");
        var ocean = TagKey.create(Registries.BIOME, ResourceLocation.parse("cobblemon:is_ocean"));
        check(level.getBiome(water).is(ocean), "Missing Cobblemon ocean habitat membership");
        boolean reef = false;
        for (int x = -2; x <= 2 && !reef; x++) for (int z = -2; z <= 2 && !reef; z++)
            reef = contains(level.getChunk((water.getX() >> 4) + x, (water.getZ() >> 4) + z),
                    BuiltInRegistries.BLOCK.get(id("blush_shellstone")));
        check(reef, "Natural reef sample has no shellstone garden");
        System.out.println("SAKURA ARMOR AND HABITAT PASS seed=" + level.getSeed() + " reef=" + water);
    }
    private static boolean contains(LevelChunk chunk, Block block) {
        for (var section : chunk.getSections())
            if (section.maybeHas(s -> s.is(block)))
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                    if (section.getBlockState(x, y, z).is(block)) return true;
        return false;
    }
    private static void armor(ServerLevel level, String material) {
        try (var visitor = new ChestProof.Visitor(level, new BlockPos(8, 170, 8), "ThreadKeeper", UUID.randomUUID())) {
            var player = visitor.player;
            for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
                var key = id(material + "_" + type.getName());
                var item = BuiltInRegistries.ITEM.get(key);
                check(item instanceof ArmorItem armor && armor.getType() == type, "Missing armor piece " + key);
                check(level.getRecipeManager().byKey(key).isPresent(), "Missing armor recipe " + key);
                player.setItemSlot(type.getSlot(), new ItemStack(item));
            }
            var zombie = EntityType.ZOMBIE.create(level);
            check(zombie != null, "Missing attacker");
            for (int i = 0; i < 65; i++) player.tick(); // Advance a new server profile past spawn immunity.
            player.setHealth(8);
            check(player.hurt(level.damageSources().mobAttack(zombie), 1F), "Armor fixture damage failed");
            if (material.equals("petalweave")) check(player.getHealth() > 8F, "Petalweave failed to heal");
            else check(player.hasEffect(MobEffects.MOVEMENT_SPEED) && player.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                    "Nightbriar escape ward failed");
            check(player.getPersistentData().getLong("uwudimension.ward_ready") > level.getServer().overworld().getGameTime(),
                    "Missing shared ward recovery");
        }
    }
}
