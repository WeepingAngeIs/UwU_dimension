package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.uwu.dimension.MirrorCourt;
import dev.uwu.dimension.People;
import dev.uwu.dimension.VelvetVesper;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class VillageRenewalProof {
    public static void run(ServerLevel level) {
        if (!level.dimension().location().equals(ResourceLocation.parse("uwudimension:sakura_reach"))) {
            throw new IllegalArgumentException("Run the village renewal probe in Sakura Reach");
        }
        var pos = new BlockPos(8, level.getMaxBuildHeight()-12, 8);
        var visitor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "RenewalVisitor"));
        visitor.moveTo(pos.getX()+.5, pos.getY()+1, pos.getZ()+.5, 0, 90);
        var original = level.getBlockState(pos);
        var above = level.getBlockState(pos.above());
        require(original.isAir() && above.isAir(), "Probe expects empty temporary sky space");
        try {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            var pad = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("uwudimension:drift_blossom"));
            visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(pad));
            pad.asItem().use(level, visitor, InteractionHand.MAIN_HAND);
            require(level.getBlockState(pos.above()).is(pad), "Drift blossom did not land above the water");
            require(level.getFluidState(pos).isSource(), "Pad displaced the source water");
            require(!level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty(), "Pad has no footing");
            level.removeBlock(pos.above(), false);
            require(level.getFluidState(pos).isSource(), "Removing the pad removed water");

            level.setBlockAndUpdate(pos, MirrorCourt.BELL.get().defaultBlockState());
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            var bell = level.getBlockState(pos);
            var area = new AABB(pos).inflate(12);
            bell.useWithoutItem(level,visitor,hit);
            require(level.getEntitiesOfClass(VelvetVesper.class, area).isEmpty(), "Readiness spawned a moth");
            visitor.setShiftKeyDown(true);
            bell.useWithoutItem(level,visitor,hit);
            var moths = level.getEntitiesOfClass(VelvetVesper.class,area);
            require(moths.size()==1, "Confirmed solo readiness did not spawn exactly one moth");
            bell.useWithoutItem(level,visitor,hit);
            require(level.getEntitiesOfClass(VelvetVesper.class,area).size()==1, "Joining spawned a duplicate");
            moths.getFirst().discard();

            var dusk = People.DUSK_KEEPER.get().create(level);
            var petal = People.PETAL_KEEPER.get().create(level);
            require(dusk!=null && petal!=null, "Keeper factories failed");
            dusk.moveTo(visitor.getX(),visitor.getY(),visitor.getZ(),0,0);
            petal.moveTo(visitor.getX()+2,visitor.getY(),visitor.getZ(),0,0);
            dusk.setVillagerData(dusk.getVillagerData().setProfession(VillagerProfession.LEATHERWORKER));
            require(!dusk.getName().getString().contains("dusk_keeper.leatherworker"), "Keeper title leaked an untranslated key");
            visitor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.EMERALD,8));
            dusk.mobInteract(visitor,InteractionHand.MAIN_HAND);
            require(visitor.getMainHandItem().getCount()==8, "Recruit preview charged emeralds");
            dusk.mobInteract(visitor,InteractionHand.MAIN_HAND);
            require(visitor.getMainHandItem().getCount()==4 && dusk.hasCustomName(), "Confirmation failed to name and recruit");
            require(command(level,visitor,"keepers roster")==1, "The first recruit was missing from its owner's roster");
            petal.mobInteract(visitor,InteractionHand.MAIN_HAND);
            petal.mobInteract(visitor,InteractionHand.MAIN_HAND);
            require(visitor.getMainHandItem().getCount()==4, "Opposite oath bypassed");
            command(level,visitor,"keepers truce confirm");
            petal.mobInteract(visitor,InteractionHand.MAIN_HAND);
            petal.mobInteract(visitor,InteractionHand.MAIN_HAND);
            require(visitor.getMainHandItem().isEmpty() && command(level,visitor,"keepers roster")==2, "Reconciliation lost former recruit");
            dusk.setTarget(petal);
            require(dusk.getTarget()==null, "Opposing companions fought at a shared base");
            var zombie = dusk.convertTo(net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER, false);
            require(zombie instanceof ZombieVillager && zombie.getType()==People.DUSK_ZOMBIE.get(),
                    "Converting Dusk keeper selected a vanilla zombie villager");
            zombie.discard();
            petal.discard();
            System.out.println("VILLAGE RENEWAL PASS | source-water pad collision, deliberate solo readiness, no duplicate boss, recruit preview, truce, two-companion roster, keeper title, Dusk zombie identity");
        } finally {
            level.setBlockAndUpdate(pos,original);
            level.setBlockAndUpdate(pos.above(),above);
        }
    }

    private static int command(ServerLevel level, FakePlayer player, String command) {
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
        } catch (CommandSyntaxException e) {
            throw new AssertionError(command, e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}