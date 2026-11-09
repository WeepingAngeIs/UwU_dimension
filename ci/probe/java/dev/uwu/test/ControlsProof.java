package dev.uwu.test;
import dev.uwu.dimension.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import noobanidus.mods.lootr.common.block.entity.LootrChestBlockEntity;
public final class ControlsProof {
 static int phase,ticks;static BlossomWarden guard;static float savedHealth;static net.minecraft.world.entity.monster.Zombie threat;static double startX;
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static ResourceLocation id(String s){return ResourceLocation.parse(s);}
 static boolean tick(ServerLevel level)throws Exception{
  if(phase==0){
   if(Boolean.getBoolean("uwu.controlsReload")){
    check(!AcquisitionPolicy.heartLocketCraftingEnabled()&&!AcquisitionPolicy.weaponAcquisitionEnabled(),"Saved server policy not reloaded");
    var n=net.minecraft.nbt.NbtIo.readCompressed(java.nio.file.Path.of("guardian-vitality.dat"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
    var restored=People.BLOSSOM_WARDEN.get().create(level);restored.load(n);
    check(restored.getHealth()==2345&&restored.getMaxHealth()==6666,"Disk reload changed damaged guardian health");
    System.out.println("UWU CONTROLS RESTART PASSED persisted disabled policy and guardian2345/6666");return true;
   }
   for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setChunkForced(x,z,true);
   phase=1;return false;
  }
  if(phase==1){
   var weapons=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(AcquisitionPolicy::isWeapon).toList();
   check(weapons.size()==149,"Registered arsenal changed: "+weapons.size());
   var locket=BuiltInRegistries.ITEM.get(id("uwudimension:heartwater_locket"));
   var pos=new BlockPos(0,180,0);
   for(int x=-3;x<=18;x++)for(int z=-3;z<=5;z++){level.setBlockAndUpdate(new BlockPos(x,180,z),Blocks.WHITE_CONCRETE.defaultBlockState());for(int y=181;y<=185;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
   level.setBlockAndUpdate(pos,Blocks.CRAFTING_TABLE.defaultBlockState());
   try(var visitor=new ChestProof.Visitor(level,pos.above(),"Policy",UUID.randomUUID())){
    var player=visitor.player;
    var allOriginalRecipes=java.util.List.copyOf(level.getRecipeManager().getRecipes());
    for(boolean craft:List.of(true,false))for(boolean loot:List.of(true,false)){
     AcquisitionPolicy.HEART_LOCKET_CRAFTING.set(craft);AcquisitionPolicy.WEAPON_ACQUISITION.set(loot);
     for(var click:List.of(ClickType.PICKUP,ClickType.QUICK_MOVE)){
      player.getInventory().clearContent();
      var menu=new CraftingMenu(19,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=menu;
      menu.getSlot(1).set(new ItemStack(Items.COMPASS));menu.getSlot(2).set(new ItemStack(Items.POPPY));menu.getSlot(3).set(new ItemStack(Items.AMETHYST_SHARD));
      menu.slotsChanged(menu.getSlot(1).container);
      check(menu.getSlot(0).getItem().is(locket)==craft,"Locket result mismatch "+craft+"/"+loot);
      menu.clicked(0,0,click,player);
      boolean obtained=menu.getCarried().is(locket)||player.getInventory().contains(new ItemStack(locket));
      check(obtained==craft,"Native "+click+" crafting bypass");
      if(!craft)check(menu.getSlot(1).getItem().is(Items.COMPASS),"Blocked recipe consumed ingredients");
     }
     boolean visible=level.getRecipeManager().getRecipes().stream().anyMatch(r->r.id().equals(id("uwudimension:heartwater_locket")));
     check(visible==craft,"Recipe sync visibility mismatch");
     var table=LootTable.lootTable();
     for(var w:weapons)table.withPool(LootPool.lootPool().add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(w.getItem())));
     var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST);
     var generated=table.build().getRandomItems(params);
     var originals=allOriginalRecipes;
     try{
      for(var w:weapons){
       var ingredients=NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.DIRT));
       var recipe=new RecipeHolder<CraftingRecipe>(id("heartwater_probe:weapon"),new ShapelessRecipe("",CraftingBookCategory.MISC,w.copy(),ingredients));
       level.getRecipeManager().replaceRecipes(List.of(recipe));
       var input=CraftingInput.of(1,1,List.of(new ItemStack(Items.DIRT)));
       check(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level,recipe).isPresent()==loot,"Weapon recipe bypass "+w);
      }
     }finally{level.getRecipeManager().replaceRecipes(originals);}

     check(generated.size()==(loot?149:0),"All-weapon loot gate failed "+generated.size());
     for(var w:weapons){
      var keeper=People.PETAL_KEEPER.get().create(level);keeper.setTradingPlayer(player);
      var offers=new MerchantOffers();offers.add(new MerchantOffer(new ItemCost(Items.EMERALD),w.copy(),999,1,0));keeper.setOffers(offers);
      var trade=new MerchantMenu(20,player.getInventory(),keeper);trade.getSlot(0).set(new ItemStack(Items.EMERALD));trade.slotsChanged(trade.getSlot(0).container);
      check(!trade.getSlot(2).getItem().isEmpty()==loot,"Trade gate "+w);
     }
     System.out.println("CONFIG COMBINATION PASS locket="+craft+" weapons="+loot+" native manual/shift crafting, sync,149 loot+trade outputs");
    }
    AcquisitionPolicy.HEART_LOCKET_CRAFTING.set(true);AcquisitionPolicy.WEAPON_ACQUISITION.set(true);
    var chestPos=pos.offset(3,0,0);var block=BuiltInRegistries.BLOCK.get(id("lootr:lootr_chest"));level.setBlockAndUpdate(chestPos,block.defaultBlockState());
    var chest=(LootrChestBlockEntity)level.getBlockEntity(chestPos);
    chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,id("heartwater_probe:all_weapons")),1234);
    var opened=ChestProof.open(level,chestPos,player);check(AcquisitionPolicy.isWeapon(opened.getItem(0)),"Enabled Lootr fixture generated no weapons");opened.setItem(0,weapons.getFirst().copy());opened.setChanged();player.closeContainer();
    AcquisitionPolicy.WEAPON_ACQUISITION.set(false);
    check(AcquisitionPolicy.isWeapon(ChestProof.open(level,chestPos,player).getItem(0)),"Existing Lootr player content deleted");player.closeContainer();
    try(var other=new ChestProof.Visitor(level,pos.above(),"NewPolicy",UUID.randomUUID())){
     var fresh=ChestProof.open(level,chestPos,other.player);
     for(int i=0;i<fresh.getContainerSize();i++)check(!AcquisitionPolicy.isWeapon(fresh.getItem(i)),"New Lootr player bypassed disabled acquisition");
    }
    System.out.println("LOOTR PREEXISTING CHEST PASS existing player retains weapon; new player rolls disabled policy");
   }
   AcquisitionPolicy.WEAPON_ACQUISITION.set(true);
   guard=People.BLOSSOM_WARDEN.get().create(level);guard.moveTo(.5,181,.5,0,0);guard.setNoAi(true);level.addFreshEntity(guard);
   check(guard.getMaxHealth()==6666&&guard.getHealth()==6666,"Guardian health clamped");
   var zombie=EntityType.ZOMBIE.create(level);check(zombie.getMaxHealth()==20,"Global attribute health changed");
   guard.setHealth(3333);var n=new CompoundTag();guard.saveWithoutId(n);var restored=People.BLOSSOM_WARDEN.get().create(level);restored.load(n);check(restored.getHealth()==3333&&restored.getMaxHealth()==6666,"Reload healed damaged guardian");
   n.remove("GuardianVitalityVersion");n.putFloat("Health",160);var attrs=n.getList("attributes",10);for(int i=0;i<attrs.size();i++)if(attrs.getCompound(i).getString("id").equals("minecraft:generic.max_health"))attrs.getCompound(i).putDouble("base",320);
   restored.load(n);check(restored.getHealth()==3333,"Old guardian proportional migration failed "+restored.getHealth());
   guard.setNoAi(false);phase=2;ticks=0;return false;
  }
  ticks++;
  if(ticks==3){check(guard.hasEffect(MobEffects.REGENERATION)&&guard.getEffect(MobEffects.REGENERATION).getAmplifier()==3,"Missing Regen IV");guard.removeAllEffects();}
  if(ticks==6){check(guard.hasEffect(MobEffects.REGENERATION),"Milk-style removal not restored");check(!guard.hasEffect(MobEffects.MOVEMENT_SPEED),"Idle guardian has combat speed");guard.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,200,5));}
  if(ticks==9){check(guard.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier()==5,"External stronger speed removed");guard.removeEffect(MobEffects.MOVEMENT_SPEED);
   var keeper=People.PETAL_KEEPER.get().create(level);keeper.moveTo(12.5,181,.5,0,0);level.addFreshEntity(keeper);
   threat=EntityType.ZOMBIE.create(level);threat.moveTo(16.5,181,.5,0,0);threat.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));level.addFreshEntity(threat);startX=guard.getX();}
  if(ticks==35){check(guard.offender()!=null,"Guardian did not acquire approaching zombie");check(guard.hasEffect(MobEffects.MOVEMENT_SPEED)&&guard.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier()==3,"Aggro Speed IV missing");check(guard.getX()>startX+.1,"Guardian failed to pursue normally");threat.discard();}
  if(ticks==60){check(guard.offender()==null&&!guard.hasEffect(MobEffects.MOVEMENT_SPEED),"Combat speed persisted idle");
   guard.setHealth(2345);var disk=new CompoundTag();guard.saveWithoutId(disk);net.minecraft.nbt.NbtIo.writeCompressed(disk,java.nio.file.Path.of("guardian-vitality.dat"));
   AcquisitionPolicy.HEART_LOCKET_CRAFTING.set(false);AcquisitionPolicy.WEAPON_ACQUISITION.set(false);AcquisitionPolicy.SPEC.save();
   System.out.println("UWU CONTROLS SERVER PASSED guardian6666 migration/reload/regen, ordinary max unchanged, acquisition four combinations");return true;}
  return false;
 }
}
