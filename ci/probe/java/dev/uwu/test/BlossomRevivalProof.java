package dev.uwu.test;

import dev.uwu.dimension.Moonbells;
import dev.uwu.dimension.AcquisitionPolicy;
import dev.uwu.dimension.RevivalBlossoms;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
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
    private static BlockPos lightPlot;
    private static long lightPlaced, lightBroken;
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
            craftRecipes(level,visitor.player,pos.south(13));
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
            arm(plain.player,ItemStack.EMPTY,ItemStack.EMPTY);plain.player.getInventory().setItem(9,new ItemStack(RevivalBlossoms.DIRK.get()));
            check(plain.player.getMainHandItem().isEmpty()&&plain.player.getOffhandItem().isEmpty(),"inventory-only fixture accidentally equipped weapon");
            lethal(plain.player);check(!plain.player.isAlive(),"inventory-only revival");
            ItemStack fresh=new ItemStack(RevivalBlossoms.AXE.get());arm(borrower.player,fresh,ItemStack.EMPTY);
            lethal(borrower.player);check(borrower.player.isAlive()&&borrower.player.getHealth()==40,"axe entry point full rescue");
            lethal(borrower.player);check(!borrower.player.isAlive(),"second lethal hit revived during cooldown");
            nativePlant(level,visitor.player,pos.south(5),Moonbells.PETAL.get(),Moonbells.PETAL_ITEM.get());
            nativePlant(level,visitor.player,pos.south(8),Moonbells.DUSK.get(),Moonbells.DUSK_ITEM.get());
            // A separate loaded plot retains the actual flower long enough for asynchronous light propagation.
            lightPlot=new BlockPos(45,level.getMaxBuildHeight()-30,45);
            level.getChunkAt(lightPlot);
            for(BlockPos air:BlockPos.betweenClosed(lightPlot.offset(-2,0,-2),lightPlot.offset(2,3,2)))level.setBlock(air,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(lightPlot.below(),Blocks.DIRT.defaultBlockState(),3);
            DoublePlantBlock.placeAt(level,Moonbells.PETAL.get().defaultBlockState(),lightPlot,3);
            lightPlaced=level.getGameTime();lightBroken=0;
        }
        System.out.println("BLOSSOM PASS full-health dirk/axe, held-only, shared owner, stale copied ID, totem precedence, repeat lethal, two rooted light plants; durable fixture saved");
    }
    private static void craftRecipes(ServerLevel level,ServerPlayer player,BlockPos table) {
        check(AcquisitionPolicy.SPEC.isLoaded()&&AcquisitionPolicy.weaponAcquisitionEnabled(),"default enabled acquisition fixture");
        level.setBlock(table,Blocks.CRAFTING_TABLE.defaultBlockState(),3);
        var prism=BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("uwudimension:roseglass_prism")).orElseThrow();
        for(boolean axe:new boolean[]{false,true}) {
            String key=axe?"dawnpetal_axe":"returnbloom_dirk";
            String[] pattern=axe?new String[]{"NN ","NSP"," ST"}:new String[]{" N "," PT"," S "};
            var materials=new java.util.ArrayList<ItemStack>();
            for(String row:pattern)for(char token:row.toCharArray()) materials.add(token==' '?ItemStack.EMPTY:new ItemStack(switch(token){
                case 'N'->Items.NETHERITE_INGOT;case 'P'->prism;case 'T'->Items.TOTEM_OF_UNDYING;default->Items.STICK;
            }));
            long netherite=materials.stream().filter(stack->stack.is(Items.NETHERITE_INGOT)).count();
            check(netherite==(axe?3:1)&&materials.stream().filter(stack->stack.is(Items.TOTEM_OF_UNDYING)).count()==1,"rare recipe cost "+key);
            CraftingInput input=CraftingInput.of(3,3,materials);
            var holder=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow(()->new AssertionError("missing revival recipe "+key));
            check(holder.id().equals(ResourceLocation.fromNamespaceAndPath("uwudimension",key)),"wrong recipe selected "+key);
            var expected=axe?RevivalBlossoms.AXE.get():RevivalBlossoms.DIRK.get();
            check(holder.value().assemble(input,level.registryAccess()).is(expected),"recipe assembles wrong item "+key);
            var oldMenu=player.containerMenu;
            var menu=new CraftingMenu(71,player.getInventory(),ContainerLevelAccess.create(level,table));
            player.containerMenu=menu;
            try {
                for(int i=0;i<9;i++)menu.getSlot(i+1).set(materials.get(i).copy());
                check(menu.getSlot(0).getItem().is(expected)&&menu.getSlot(0).getItem().getCount()==1,"native crafting result "+key);
                menu.clicked(0,0,ClickType.PICKUP,player);
                check(menu.getCarried().is(expected)&&menu.getCarried().getCount()==1,"actual result pickup "+key);
                for(int i=1;i<=9;i++)check(menu.getSlot(i).getItem().isEmpty(),"recipe failed to consume ingredient slot "+i+" "+key);
                check(menu.getSlot(0).getItem().isEmpty(),"recipe left duplicate result "+key);
                menu.setCarried(ItemStack.EMPTY);
                AcquisitionPolicy.WEAPON_ACQUISITION.set(false);
                check(AcquisitionPolicy.weaponAcquisitionEnabled(),"world-restart policy changed within a running world");
                // This policy intentionally takes effect only after world restart. Simulate its
                // restarted cache in this disposable fixture, without changing production semantics.
                AcquisitionPolicy.WEAPON_ACQUISITION.clearCache();
                check(!AcquisitionPolicy.weaponAcquisitionEnabled(),"simulated restart did not load disabled acquisition");
                check(!AcquisitionPolicy.allowsCraft(new ItemStack(expected)),"disabled policy allows new weapon "+key);
                check(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).isEmpty(),"disabled policy recipe lookup bypass "+key);
                for(int i=0;i<9;i++)menu.getSlot(i+1).set(materials.get(i).copy());
                check(menu.getSlot(0).getItem().isEmpty(),"disabled policy native crafting bypass "+key);
            } finally {
                AcquisitionPolicy.WEAPON_ACQUISITION.set(true);
                AcquisitionPolicy.WEAPON_ACQUISITION.clearCache();
                player.containerMenu=oldMenu;
            }
        }
        System.out.println("BLOSSOM CRAFTING PASS both native result pickups consume totem/netherite/prism/sticks once; world-restart cache respected, simulated-restart disabled lookup and menu reject both");
    }
    /** Call on server ticks until true. Checks both propagation and removal through the real light engine. */
    public static boolean verifyLighting(ServerLevel level) {
        check(lightPlot!=null,"retained lighting fixture missing");
        long now=level.getGameTime();
        if(lightBroken==0) {
            if(now-lightPlaced<12)return false;
            BlockPos upper=lightPlot.above(),adjacent=upper.east();
            check(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,upper)==12,"real source block light not12");
            check(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,adjacent)>=11,"adjacent server block light did not propagate");
            level.destroyBlock(upper,false);lightBroken=now;
            return false;
        }
        if(now-lightBroken<12)return false;
        check(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,lightPlot.above())==0,"removed source left stale block light");
        check(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,lightPlot.above().east())==0,"removed source left stale adjacent light");
        System.out.println("MOONBELL LIGHT ENGINE PASS source12 adjacent11, removed flower clears propagated server block light");
        return true;
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
