package dev.uwu.test;

import dev.uwu.dimension.Keeper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

final class UpgradeProof {
    static boolean ready(MinecraftServer server) {
        if(!"152045".equals(System.getenv("UWU_FIELD_SEED")))return true;
        ServerLevel old=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("uwudimension:heartwater")));
        BlockPos marker=new BlockPos(2,82,2);
        if(old==null||!old.getBlockState(marker).is(Blocks.LODESTONE))throw new AssertionError("1.0.0 player marker missing");
        var keepers=old.getEntitiesOfClass(Keeper.class,new AABB(marker).inflate(32),k->k.getTags().contains("uwu_upgrade_fixture"));
        if(keepers.isEmpty() && server.getTickCount()<250)return false;
        if(keepers.size()!=1)throw new AssertionError("Original keeper missing or duplicated");
        var tag=keepers.getFirst().saveWithoutId(new CompoundTag());
        if(!tag.getBoolean("Settled")||!tag.hasUUID("Companion")||!tag.getUUID("Companion").equals(new java.util.UUID(0x100000002L,0x300000004L)))throw new AssertionError("Legacy recruit lost ownership");
        var reward=new BlockPos(0,53,103);
        if(old.getBlockEntity(reward)==null||!net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(old.getBlockState(reward).getBlock()).getNamespace().equals("lootr"))
            throw new AssertionError("Legacy Lootr reward missing");
        System.out.println("UPGRADE PASS | 1.0.0 saved marker, keeper owner and generated chest survived replacement");
        return true;
    }
}
