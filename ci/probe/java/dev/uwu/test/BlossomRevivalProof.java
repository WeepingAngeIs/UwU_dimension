package dev.uwu.test;

import dev.uwu.dimension.Moonbells;
import dev.uwu.dimension.RevivalBlossoms;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Actual lethal damage, durable item/owner custody, exact cooldown boundary and native flower placement. */
public final class BlossomRevivalProof {
    private static final UUID PERSISTENT = UUID.fromString("64ee0e6d-999c-4161-8f5b-a835ac69bf15");
    private static final Path RECORD = Path.of("blossom-revival-fixture.nbt");
    private static final UUID RESCUED = UUID.fromString("64ee0e6d-999c-4161-8f5b-a835ac69bf16");
    private static void check(boolean ok,String why) { if(!ok)throw new AssertionError("BLOSSOM "+why); }
    private static void arm(ServerPlayer p, ItemStack main, ItemStack off) {
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
        p.setHealth(40);p.invulnerableTime=0;
        p.setItemInHand(InteractionHand.MAIN_HAND,main);p.setItemInHand(InteractionHand.OFF_HAND,off);
        try { var field=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");field.setAccessible(true);field.setInt(p,0); }
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void lethal(ServerPlayer p) { p.invulnerableTime=0;check(p.hurt(p.damageSources().generic(),500),"lethal rejected before native rescue"); }
    public static void run(ServerLevel level) throws Exception {
        BlockPos pos=new BlockPos(5,level.getMaxBuildHeight()-30,5);
        try(var visitor=new ChestProof.Visitor(level,pos,"BlossomOwner",PERSISTENT);
            var borrower=new ChestProof.Visitor(level,pos.east(2),"BlossomBorrower",UUID.randomUUID());
            var plain=new ChestProof.Visitor(level,pos.east(4),"BlossomInventory",UUID.randomUUID());
            var totem=new ChestProof.Visitor(level,pos.east(6),"BlossomTotem",UUID.randomUUID());
            var rescued=new ChestProof.Visitor(level,pos.east(8),"BlossomOtherRescue",RESCUED);
            var bypass=new ChestProof.Visitor(level,pos.east(10),"BlossomBypass",UUID.randomUUID())) {
            ItemStack dirk=new ItemStack(RevivalBlossoms.DIRK.get()),axe=new ItemStack(RevivalBlossoms.AXE.get());
            arm(visitor.player,dirk,axe);
            check(RevivalBlossoms.remaining(visitor.player,dirk)==0,"initial readiness");
            ItemStack duplicate=dirk.copy();
            lethal(visitor.player);
            check(visitor.player.isAlive()&&visitor.player.getHealth()==40,"full current maximum health rescue");
            check(RevivalBlossoms.remaining(visitor.player,dirk)==1200,"exact owner/item deadline");
            check(RevivalBlossoms.visualPhase(dirk)==1,"immediate spent bud sync");
            check(RevivalBlossoms.remaining(visitor.player,axe)==1200,"dual hand shared pool");
            check(RevivalBlossoms.remaining(visitor.player,new ItemStack(RevivalBlossoms.DIRK.get()))==1200,"fresh swap bypass");
            check(RevivalBlossoms.remaining(borrower.player,duplicate)==1200,"stale pre-trigger copied UUID bypass");
            check(RevivalBlossoms.remaining(borrower.player,new ItemStack(RevivalBlossoms.AXE.get()))==0,"unrelated owner/item incorrectly spent");
            CompoundTag fixture=new CompoundTag();fixture.put("Stack",dirk.save(level.registryAccess()));
            fixture.putLong("Deadline",level.getServer().overworld().getGameTime()+1200);
            NbtIo.writeCompressed(fixture,RECORD);

            ItemStack untouched=new ItemStack(RevivalBlossoms.AXE.get());
            arm(totem.player,untouched,new ItemStack(Items.TOTEM_OF_UNDYING));lethal(totem.player);
            check(totem.player.isAlive()&&totem.player.getOffhandItem().isEmpty(),"native totem precedence");
            check(RevivalBlossoms.remaining(totem.player,untouched)==0,"totem spent blossom cooldown");
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, false,
                    net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, event -> {
                        if(event.getEntity().getUUID().equals(RESCUED)) {event.getEntity().setHealth(17);event.setCanceled(true);}
                    });
            ItemStack otherRescue=new ItemStack(RevivalBlossoms.DIRK.get());
            arm(rescued.player,otherRescue,ItemStack.EMPTY);lethal(rescued.player);
            check(rescued.player.getHealth()==17&&RevivalBlossoms.remaining(rescued.player,otherRescue)==0,"earlier rescue precedence");
            ItemStack bypassStack=new ItemStack(RevivalBlossoms.DIRK.get());arm(bypass.player,bypassStack,ItemStack.EMPTY);
            check(bypass.player.hurt(bypass.player.damageSources().genericKill(),500)&&!bypass.player.isAlive(),"absolute bypass resurrected");
            check(RevivalBlossoms.remaining(bypass.player,bypassStack)==0,"absolute bypass spent charge");
            arm(plain.player,ItemStack.EMPTY,ItemStack.EMPTY);plain.player.getInventory().add(new ItemStack(RevivalBlossoms.DIRK.get()));
            lethal(plain.player);check(!plain.player.isAlive(),"inventory-only revival");
            ItemStack fresh=new ItemStack(RevivalBlossoms.AXE.get());arm(borrower.player,fresh,ItemStack.EMPTY);
            lethal(borrower.player);check(borrower.player.isAlive()&&borrower.player.getHealth()==40,"axe entry point full rescue");
            lethal(borrower.player);check(!borrower.player.isAlive(),"second lethal hit revived during cooldown");
            nativePlant(level,visitor.player,pos.south(5),Moonbells.PETAL.get(),Moonbells.PETAL_ITEM.get());
            nativePlant(level,visitor.player,pos.south(8),Moonbells.DUSK.get(),Moonbells.DUSK_ITEM.get());
        }
        System.out.println("BLOSSOM PASS full-health dirk/axe, held-only, shared owner, stale copied ID, totem precedence, repeat lethal, two rooted light plants; durable fixture saved");
    }
    private static void nativePlant(ServerLevel level,ServerPlayer p,BlockPos pos,net.minecraft.world.level.block.Block plant,net.minecraft.world.item.Item item) {
        level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
        var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
        check(p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)).consumesAction(),"native two-high placement");
        check(level.getBlockState(pos).is(plant)&&level.getBlockState(pos).getValue(DoublePlantBlock.HALF)==DoubleBlockHalf.LOWER,"lower half");
        check(level.getBlockState(pos.above()).is(plant)&&level.getBlockState(pos.above()).getLightEmission(level,pos.above())==12,"upper emits real light 12");
        check(level.getBlockState(pos).getLightEmission(level,pos)==0,"stem emits extra light");
        level.destroyBlock(pos.above(),true);check(level.getBlockState(pos).isAir(),"upper break leaves lower plant");
        level.setBlock(pos,Blocks.WATER.defaultBlockState(),3);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
        p.getMainHandItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit));
        check(level.getBlockState(pos).is(Blocks.WATER),"plant replaced water");
    }
    public static void restart(ServerLevel level) throws Exception {
        check(Files.isRegularFile(RECORD),"missing disk fixture");
        CompoundTag fixture=NbtIo.readCompressed(RECORD,NbtAccounter.unlimitedHeap());
        ItemStack stack=ItemStack.parseOptional(level.registryAccess(),fixture.getCompound("Stack"));
        long oldTime=level.getServer().overworld().getGameTime();
        try(var visitor=new ChestProof.Visitor(level,new BlockPos(4,level.getMaxBuildHeight()-30,4),"BlossomReload",PERSISTENT)) {
            arm(visitor.player,stack,ItemStack.EMPTY);
            long deadline=fixture.getLong("Deadline");
            check(RevivalBlossoms.remaining(visitor.player,stack)==Math.max(0,deadline-oldTime),"restart deadline not restored");
            check(deadline>oldTime,"fixture expired before restart boundary proof");
            level.getServer().getWorldData().overworldData().setGameTime(deadline-1);
            check(RevivalBlossoms.remaining(visitor.player,stack)==1,"last blocked tick");
            stack.getItem().inventoryTick(stack,level,visitor.player,0,true);
            check(RevivalBlossoms.visualPhase(stack)==5,"last recharge petal state");
            level.getServer().getWorldData().overworldData().setGameTime(deadline);
            check(RevivalBlossoms.remaining(visitor.player,stack)==0,"exact ready tick");
            stack.getItem().inventoryTick(stack,level,visitor.player,0,true);
            check(RevivalBlossoms.visualPhase(stack)==0,"ready sprite at deadline");
            lethal(visitor.player);check(visitor.player.isAlive()&&visitor.player.getHealth()==40,"deadline native lethal rescue");
            check(RevivalBlossoms.remaining(visitor.player,stack)==1200,"second durable deadline");
        } finally {level.getServer().getWorldData().overworldData().setGameTime(oldTime);}
        System.out.println("BLOSSOM RESTART PASS restored owner/item deadlines, blocked at1199 and real rescue at1200");
    }
    private BlossomRevivalProof() {}
}
