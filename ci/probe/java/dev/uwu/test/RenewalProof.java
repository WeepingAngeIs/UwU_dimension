package dev.uwu.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.ReefLife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

final class RenewalProof {
    private static void check(boolean pass, String reason) {
        if (!pass) throw new AssertionError(reason);
    }
    static void run(ServerLevel reach) throws Exception {
        var server = reach.getServer();
        check(server.levelKeys().stream().filter(k -> k.location().getNamespace().equals("uwudimension")).count()==1, "dimension count");
        var site = GroveSites.first(reach);
        var arrival = GroveSites.arrival(reach);
        var checkpoint=Path.of("first-site.txt");
        if (Files.exists(checkpoint)) {
            check(Files.readString(checkpoint).equals(site.toString()),"arrival moved after restart");
            check(reach.getBlockState(arrival.above(5)).is(Blocks.AMETHYST_BLOCK),"saved player block replaced");
            System.out.println("SAKURA DISK RESTART PASS "+site);
            return;
        }
        if (Files.exists(Path.of("baseline-world"))) {
            var marker = new BlockPos(320,185,320);
            for (var world : new ServerLevel[]{reach,server.overworld()}) {
                world.getChunkAt(marker);
                check(world.getBlockState(marker).is(Blocks.LODESTONE),"existing save marker changed in "+world.dimension());
            }
            System.out.println("SAKURA COPIED 1.4.9 SAVE PASS");
        }
        var home=server.overworld();
        BlockPos a=new BlockPos(12,260,12), b=new BlockPos(42,260,12);
        for (BlockPos feet : new BlockPos[]{a,b})
            for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) home.setBlockAndUpdate(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState());
        var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("uwudimension:heartwater_locket"));
        try(var one=new ChestProof.Visitor(home,a,"Linden",UUID.randomUUID());
            var two=new ChestProof.Visitor(home,b,"Mallow",UUID.randomUUID())) {
            var first=one.player;
            var second=two.player;
            first.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
            second.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
            item.use(home, first,InteractionHand.MAIN_HAND);
            item.use(home, second,InteractionHand.MAIN_HAND);
            check(first.serverLevel()==reach && second.serverLevel()==reach, "both visitors arrived");
            var swap=first.getMainHandItem();first.setItemInHand(InteractionHand.MAIN_HAND,second.getMainHandItem());
            second.setItemInHand(InteractionHand.MAIN_HAND,swap);
            home.setBlockAndUpdate(a.offset(0,0,1),Blocks.LAVA.defaultBlockState());
            item.use(reach,first,InteractionHand.MAIN_HAND);item.use(reach,second,InteractionHand.MAIN_HAND);
            check(first.serverLevel()==home && second.serverLevel()==home, "both returned");
            check(first.blockPosition().closerThan(a,6) && second.blockPosition().closerThan(b,6),"traded locket used wrong anchor");
            check(home.getFluidState(first.blockPosition()).isEmpty() && home.noCollision(first.getBoundingBox()),"landing hazard");
        }
        System.out.println("SAKURA TWO VISITOR LOCKET PASS seed="+reach.getSeed()+" arrival="+arrival+" villageSite="+site.usable());
        if(site.usable()) {
            BlockPos chest=site.pos(0,53,103);
            reach.getChunkAt(chest);
            ChestProof.run(reach,chest);
        }
        VillageRenewalProof.run(reach);
        CombatRenewalProof.run(reach);
        reach.setBlockAndUpdate(arrival.above(5),Blocks.AMETHYST_BLOCK.defaultBlockState());
        Files.writeString(checkpoint,site.toString());
        System.out.println("SAKURA RENEWAL SERVER PASS");
    }
}
