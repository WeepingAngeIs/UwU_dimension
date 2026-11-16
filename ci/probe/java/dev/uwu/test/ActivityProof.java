package dev.uwu.test;

import com.google.gson.*;
import dev.uwu.dimension.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.advancements.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Native criteria truth tables plus actual use/break/feed hooks in disposable server worlds. */
final class ActivityProof {
    static final UUID ALPHA=UUID.fromString("c85f9018-bd56-4d65-a0ab-777700000001");
    private static final UUID BETA=UUID.fromString("c85f9018-bd56-4d65-a0ab-777700000002"),OBSERVER=UUID.fromString("c85f9018-bd56-4d65-a0ab-777700000003");
    private static ResourceLocation id(String value){return ResourceLocation.parse(value.contains(":")?value:"uwudimension:"+value);}
    private static ItemStack item(String name){return new ItemStack(BuiltInRegistries.ITEM.getOptional(id(name)).orElseThrow());}
    static void check(boolean ok,String what){if(!ok)throw new AssertionError("ACTIVITIES "+what);}
    static AdvancementHolder holder(ServerPlayer p,String path){
        var a=p.server.getAdvancements().get(id(path.startsWith("activities/")||path.equals("a_rather_pretty_place")||path.startsWith("recipes/")?path:"activities/"+path));
        check(a!=null,"missing advancement "+path);return a;
    }
    static boolean done(ServerPlayer p,String path){return p.getAdvancements().getOrStartProgress(holder(p,path)).isDone();}
    static void expect(ServerPlayer p,String path,boolean value,String reason){check(done(p,path)==value,reason+" "+path+" expected="+value);}
    private static void reset(ServerPlayer p,JsonArray catalog){
        for(var row:catalog){var a=p.server.getAdvancements().get(id(row.getAsJsonObject().get("id").getAsString()));
            check(a!=null&&a.value().display().isPresent()&&!a.value().display().get().getIcon().isEmpty(),"catalog/icon loads "+row);
            for(var c:a.value().criteria().keySet())p.getAdvancements().revoke(a,c);
        }
    }
    private static void inventory(ServerPlayer p,String... names){
        p.getInventory().clearContent();
        for(var name:names){var stack=item(name);p.getInventory().add(stack.copy());CriteriaTriggers.INVENTORY_CHANGED.trigger(p,p.getInventory(),stack);}
    }
    private static void eat(ServerPlayer p,String name){
        var stack=item(name);check(stack.getFoodProperties(p)!=null,"edible "+name);
        p.getFoodData().setFoodLevel(5);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        p.setItemInHand(InteractionHand.MAIN_HAND,stack.finishUsingItem(p.level(),p));
    }
    private static void biome(ServerLevel level,String name){
        var key=ResourceKey.create(Registries.BIOME,id(name));var biome=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
        level.getChunk(0,0).fillBiomesFromNoise((x,y,z,sampler)->biome,level.getChunkSource().randomState().sampler());
    }
    private static void legacy(ServerPlayer p)throws Exception{
        Path old=Path.of("activity-old-progress.json");if(!Files.exists(old))return;
        p.getAdvancements().save();
        var before=JsonParser.parseString(Files.readString(old)).getAsJsonObject();
        var file=p.server.getWorldPath(LevelResource.ROOT).resolve("advancements").resolve(ALPHA+".json");
        var after=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for(var name:List.of("uwudimension:a_rather_pretty_place","uwudimension:recipes/sakura_craft/petalweave_helmet"))
            check(before.has(name)&&before.get(name).equals(after.get(name)),"legacy dates/criteria changed "+name);
        System.out.println("ACTIVITY LEGACY UPGRADE PASS root and recipe dates/criteria preserved");
    }
    static void first(ServerLevel level)throws Exception {
        var catalog=JsonParser.parseString(Files.readString(Path.of("../../docs/ACTIVITY-ADVANCEMENTS.json"))).getAsJsonArray();
        check(catalog.size()==50,"catalog count");
        BlockPos at=new BlockPos(8,level.getMaxBuildHeight()-80,8);GuardianFamilyProof.floor(level,at);
        try(var va=new ChestProof.Visitor(level,at,"ActivityA",ALPHA);var vb=new ChestProof.Visitor(level,at,"ActivityB",BETA);var vc=new ChestProof.Visitor(level,at,"ActivityObserver",OBSERVER)){
            var a=va.player;var b=vb.player;var c=vc.player;level.addNewPlayer(a);level.addNewPlayer(b);level.addNewPlayer(c);
            try{
                for(var p:List.of(a,b,c))reset(p,catalog);
                legacy(a);
                CriteriaTriggers.CHANGED_DIMENSION.trigger(a,Level.OVERWORLD,ReefLife.REACH);
                CriteriaTriggers.CHANGED_DIMENSION.trigger(b,Level.OVERWORLD,Level.NETHER);
                expect(a,"a_rather_pretty_place",true,"root entry");expect(b,"a_rather_pretty_place",false,"unrelated dimension");
                for(var row:catalog){
                    var definition=row.getAsJsonObject();var criteria=definition.getAsJsonObject("criteria");
                    if(!definition.get("id").getAsString().contains("/journey/"))continue;
                    for(var entry:criteria.entrySet()){
                        String visit=entry.getValue().getAsJsonObject().getAsJsonObject("conditions").getAsJsonObject("player").getAsJsonObject("location").get("biomes").getAsString();
                        biome(level,visit);CriteriaTriggers.LOCATION.trigger(a);
                    }
                    check(a.getAdvancements().getOrStartProgress(a.server.getAdvancements().get(id(definition.get("id").getAsString()))).isDone(),"biome criterion "+definition.get("id"));
                }
                try(var outside=new ChestProof.Visitor(level.getServer().overworld(),new BlockPos(8,100,8),"Outside",UUID.randomUUID())){
                    biome(level.getServer().overworld(),"minecraft:cherry_grove");CriteriaTriggers.LOCATION.trigger(outside.player);
                    expect(outside.player,"journey/canopy_club",false,"same biome in Overworld excluded");
                }
                inventory(a,"heartberry_burger","cozy_udon_hotpot","sakura_mochi_ice_cream","sakura_rice_seeds");
                expect(a,"pantry/burger",false,"acquisition is not consumption");expect(a,"pantry/seeds",false,"seed acquisition is not eating");
                expect(a,"garden/seeds",true,"seed acquisition");
                eat(a,"heartberry_burger");eat(a,"cozy_udon_hotpot");eat(a,"maple_ice_cream_sundae");eat(a,"cherry_picnic_bento");eat(a,"heartberry");eat(a,"sakura_rice_seeds");
                for(String path:List.of("burger","hotpot","dessert","picnic","snack","seeds"))expect(a,"pantry/"+path,true,"actual item consumption");
                expect(a,"pantry/three_course",false,"two of three foods remains partial");
                for(String set:List.of("petalweave","nightbriar")){
                    inventory(a,set+"_helmet",set+"_chestplate",set+"_leggings",set+"_boots");
                    expect(a,"wardrobe/"+set+"_crafted",false,"owning armor is not crafting");
                    for(String part:List.of("helmet","chestplate","leggings"))CriteriaTriggers.RECIPE_CRAFTED.trigger(a,id(set+"_"+part),List.of());
                    expect(a,"wardrobe/"+set+"_crafted",false,"three crafting criteria incomplete");
                    CriteriaTriggers.CRAFTER_RECIPE_CRAFTED.trigger(a,id(set+"_boots"),List.of());
                    expect(a,"wardrobe/"+set+"_crafted",false,"automatic crafter is not personal crafting");
                    CriteriaTriggers.RECIPE_CRAFTED.trigger(a,id(set+"_boots"),List.of());
                    expect(a,"wardrobe/"+set+"_crafted",true,"four actual native recipe events");
                    CriteriaTriggers.LOCATION.trigger(a);expect(a,"wardrobe/"+set+"_worn",false,"inventory is not worn armor");
                    a.setItemSlot(EquipmentSlot.HEAD,item(set+"_helmet"));a.setItemSlot(EquipmentSlot.CHEST,item(set+"_chestplate"));a.setItemSlot(EquipmentSlot.LEGS,item(set+"_leggings"));
                    CriteriaTriggers.LOCATION.trigger(a);expect(a,"wardrobe/"+set+"_worn",false,"partial worn set");
                    a.setItemSlot(EquipmentSlot.FEET,item(set+"_boots"));CriteriaTriggers.LOCATION.trigger(a);expect(a,"wardrobe/"+set+"_worn",true,"complete matching worn set");
                    for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))a.setItemSlot(slot,ItemStack.EMPTY);
                }
                inventory(a,"roseglass_prism","vesper_opal","giant_sakura_sapling","cupid_bow");
                for(String path:List.of("wardrobe/petalweave_gem","wardrobe/nightbriar_gem","garden/sapling","cupid/bow"))expect(a,path,true,"native inventory acquisition");
                expect(a,"garden/giant",false,"sapling acquisition is not growth");expect(a,"cupid/charm",false,"bow acquisition is not charm");
                for(var faction:List.of("petal","dusk")){
                    var merchant=(AbstractVillager)BuiltInRegistries.ENTITY_TYPE.get(id(faction+"_keeper")).create(level);
                    CriteriaTriggers.TRADE.trigger(a,merchant,item("heartberry"));expect(a,"community/"+faction+"_trade",true,"native faction trade event");merchant.discard();
                }
                expect(a,"community/both_trades",true,"both faction trade criteria");expect(b,"community/both_trades",false,"no trade credit to bystander");
                crops(a,at);commons(a,b);court(a,b,at);
                c.setPos(at.getX()+5.5,at.getY(),at.getZ()+5.5);
                GuardianFamilyProof.floor(level,at);GuardianFamilyProof.conditions(level,at,a,b);
                expect(a,"guardians/feed",true,"valid feeding");expect(a,"guardians/family",false,"vetoed/blocked family did not award");expect(b,"guardians/together",false,"no child yet");
                var family=GuardianFamilyProof.family(level,at,a,b);
                for(var p:List.of(a,b)){expect(p,"guardians/family",true,"actual guardian birth");expect(p,"guardians/together",true,"both distinct feeders");}
                expect(c,"guardians/family",false,"nearby observer excluded");expect(c,"guardians/together",false,"observer cooperation excluded");family.all().forEach(Entity::discard);
                CombatRenewalProof.returns(level,a);expect(a,"utility/return",true,"actual inventory delivery");expect(b,"utility/return",false,"return owner attribution");
                OpacToolProof.run(level,at,a,b);expect(a,"garden/chakram",true,"owner crop harvest");expect(b,"garden/chakram",false,"denied stranger harvest");
                a.getAdvancements().save();legacy(a);
                int count=0;for(var criterion:a.getAdvancements().getOrStartProgress(holder(a,"pantry/three_course")).getCompletedCriteria())count++;
                check(count==2,"exact partial progress before restart");
                System.out.println("ACTIVITY SERVER PASS 50 loaded, native truth tables, crop success/cancellation, claims, family cooperation, village/court attribution and relic return");
            }finally{a.discard();b.discard();c.discard();}
        }
    }
    private static void crops(ServerPlayer p,BlockPos at){
        var level=p.serverLevel();BlockPos pos=at.offset(1,0,0);level.setBlockAndUpdate(pos.below(),Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        p.setPos(at.getX()+.5,at.getY(),at.getZ()+1.5);p.setItemInHand(InteractionHand.MAIN_HAND,item("sakura_rice_seeds"));
        var hit=new BlockHitResult(Vec3.atCenterOf(pos.below()),Direction.UP,pos.below(),false);
        check(p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction(),"native crop placement");
        expect(p,"garden/plant",true,"real BlockItem placement");check(p.gameMode.destroyBlock(pos),"immature break");expect(p,"garden/harvest",false,"immature excluded");
        var mature=BuiltInRegistries.BLOCK.get(id("sakura_rice_crop")).defaultBlockState().setValue(CropBlock.AGE,7);level.setBlockAndUpdate(pos,mature);
        Consumer<BlockEvent.BreakEvent> deny=e->{if(e.getPos().equals(pos))e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class,deny);
        try{check(!p.gameMode.destroyBlock(pos),"protected break denied");expect(p,"garden/harvest",false,"denied break excluded");}finally{NeoForge.EVENT_BUS.unregister(deny);}
        check(p.gameMode.destroyBlock(pos),"mature break");expect(p,"garden/harvest",true,"mature native crop actual break");
        var leaves=BuiltInRegistries.BLOCK.stream().filter(block->block.getClass().getSimpleName().equals("FruitingLeavesBlock")).findFirst().orElseThrow();
        var state=leaves.defaultBlockState();var fruit=(net.minecraft.world.level.block.state.properties.BooleanProperty)state.getBlock().getStateDefinition().getProperty("fruiting");
        level.setBlockAndUpdate(pos,state.setValue(fruit,false));p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        p.gameMode.useItemOn(p,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));expect(p,"garden/fruit",false,"empty leaves excluded");
        level.setBlockAndUpdate(pos,state.setValue(fruit,true));p.gameMode.useItemOn(p,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));expect(p,"garden/fruit",true,"actual fruit picking");
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        p.setItemInHand(InteractionHand.MAIN_HAND,item("heart_neon_lantern"));p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,hit);expect(p,"utility/lantern_placed",true,"actual lantern placement");
        expect(p,"utility/lantern_crafted",false,"placing is not crafting");CriteriaTriggers.RECIPE_CRAFTED.trigger(p,id("heart_neon_lantern_from_silver"),List.of());expect(p,"utility/lantern_crafted",true,"alternative silver recipe");
        expect(p,"utility/dusk_lantern",false,"pink lantern is not dusk lantern");
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());p.setItemInHand(InteractionHand.MAIN_HAND,item("dusk_heart_lantern"));
        check(p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction(),"native dusk lantern placement");expect(p,"utility/dusk_lantern",true,"actual dusk lantern placement");
    }
    private static void commons(ServerPlayer a,ServerPlayer b){
        var v=BlossomVillage.first(a.serverLevel(),false);var bell=v.site().pos(v.x()+2,v.y()+1,v.z()-2);var level=a.serverLevel();
        level.setBlockAndUpdate(bell.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(bell,Blocks.BELL.defaultBlockState());a.setShiftKeyDown(true);b.setShiftKeyDown(true);
        a.setPos(bell.getX()+.5,bell.getY(),bell.getZ()+1.5);a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item("sakura_rice_seeds").getItem(),8));
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(a,InteractionHand.MAIN_HAND,bell,new BlockHitResult(Vec3.atCenterOf(bell),Direction.UP,bell,false)));
        expect(a,"community/contribute",true,"accepted supplies");expect(b,"community/contribute",false,"other player not a contributor");
        b.setPos(bell.getX()+1.5,bell.getY(),bell.getZ()+1.5);b.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(b,InteractionHand.MAIN_HAND,bell,new BlockHitResult(Vec3.atCenterOf(bell),Direction.UP,bell,false)));
        expect(b,"community/keepsake",false,"unearned keepsake excluded");
        a.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(a,InteractionHand.MAIN_HAND,bell,new BlockHitResult(Vec3.atCenterOf(bell),Direction.UP,bell,false)));
        expect(a,"community/keepsake",true,"earned keepsake claim");a.setShiftKeyDown(false);b.setShiftKeyDown(false);
    }
    @SuppressWarnings("unchecked") private static void court(ServerPlayer a,ServerPlayer b,BlockPos at)throws Exception{
        var type=Class.forName("dev.uwu.dimension.MirrorCourt");var getter=type.getDeclaredMethod("ledger",ServerLevel.class);getter.setAccessible(true);var ledger=getter.invoke(null,a.serverLevel());
        var field=ledger.getClass().getDeclaredField("active");field.setAccessible(true);var active=(Map<Long,UUID>)field.get(ledger);var bell=at.offset(0,0,9);var boss=UUID.randomUUID();active.put(bell.asLong(),boss);
        var victory=type.getDeclaredMethod("defeated",ServerLevel.class,BlockPos.class,UUID.class,Set.class);victory.setAccessible(true);
        victory.invoke(null,a.serverLevel(),bell,UUID.randomUUID(),Set.of(a.getUUID()));expect(a,"community/court",false,"wrong encounter UUID excluded");
        victory.invoke(null,a.serverLevel(),bell,boss,Set.of(a.getUUID()));expect(a,"community/court",true,"valid victory participant");expect(b,"community/court",false,"court observer excluded");active.remove(bell.asLong());
    }
    static void restart(ServerLevel level)throws Exception{
        try(var visitor=new ChestProof.Visitor(level,new BlockPos(8,level.getMaxBuildHeight()-80,8),"ActivityA",ALPHA)){
            var p=visitor.player;expect(p,"a_rather_pretty_place",true,"root survives restart");expect(p,"garden/harvest",true,"manual progress survives restart");
            expect(p,"guardians/together",true,"cooperation survives restart");expect(p,"pantry/three_course",false,"partial progress survives restart");
            legacy(p);eat(p,"sakura_mochi_ice_cream");expect(p,"pantry/three_course",true,"last criterion completes saved partial goal");
            var entry=holder(p,"pantry/three_course");p.getAdvancements().save();var file=p.server.getWorldPath(LevelResource.ROOT).resolve("advancements").resolve(ALPHA+".json");
            var first=JsonParser.parseString(Files.readString(file)).getAsJsonObject().get(entry.id().toString());
            eat(p,"sakura_mochi_ice_cream");p.getAdvancements().save();var second=JsonParser.parseString(Files.readString(file)).getAsJsonObject().get(entry.id().toString());check(first.equals(second),"repeat award changed completion dates");
            System.out.println("ACTIVITY RESTART PASS earned/partial progress and root preserved, no repeated reward/date reset");
        }
    }
}
