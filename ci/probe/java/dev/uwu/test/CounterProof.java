package dev.uwu.test;
import dev.uwu.dimension.*;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;

/** Exercises registered survival item entrypoints against real player-held target stacks. */
public final class CounterProof {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ServerPlayer player,foe;
    private static long readyAt=-1;
    public static boolean ready(ServerLevel level){
        if(readyAt>=0)return level.getGameTime()>=readyAt;
        BlockPos base=new BlockPos(0,level.getMaxBuildHeight()-24,0);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=24;z++)level.setBlockAndUpdate(base.offset(x,0,z),Blocks.STONE.defaultBlockState());
        player=player(level,new GameProfile(UUID.fromString("c719e828-f310-44fa-95d5-41fef4a9da11"),"UwUArcher"));
        foe=player(level,new GameProfile(UUID.fromString("c719e828-f310-44fa-95d5-41fef4a9da12"),"SwordDuelist"));
        player.moveTo(.5,base.getY()+1,.5,0,0);foe.moveTo(.5,base.getY()+1,8.5,180,0);
        level.addNewPlayer(player);level.addNewPlayer(foe);
        require(player.canHarmPlayer(foe),"Arena PvP disabled");
        require(foe.isPickable(),"Simulated duelist is not collidable");
        require(level.getEntities(player,player.getBoundingBox().inflate(12)).contains(foe),"Duelist absent from loaded entity index");
        foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);foe.setHealth(1000);
        player.setNoGravity(true);foe.setNoGravity(true);
        readyAt=level.getGameTime()+70;
        return false;
    }
    public static void run(ServerLevel level) throws Exception {
        BlockPos base=new BlockPos(0,level.getMaxBuildHeight()-24,0);
        try{
            require(CounterArsenal.ROSTER.size()==133,"Expected audited 133 weapon/relic identities");
            Set<String> targets=new HashSet<>();
            for(var entry:CounterArsenal.ROSTER){
                require(targets.add(entry.target()),"Duplicate counter "+entry.target());
                Item opponent=BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.target()));
                require(opponent!=Items.AIR,"Missing installed target "+entry.target());
                foe.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(opponent));
                foe.teleportTo(.5,base.getY()+1,8.5);foe.setHealth(1000);foe.invulnerableTime=0;
                var item=CounterArsenal.ITEMS.get(entry.id()).get();var stack=new ItemStack(item);
                player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.getCooldowns().removeCooldown(item);
                item.use(level,player,InteractionHand.MAIN_HAND);
                if(entry.shape().equals("scythe")){player.getPersistentData().remove("uwu.scythe_flight");item.onUseTick(level,player,stack,71988);}
                for(var projectile:level.getEntitiesOfClass(Snowball.class,player.getBoundingBox().inflate(3)))for(int t=0;t<9&&projectile.isAlive();t++){projectile.tickCount++;projectile.tick();}
                for(var f:level.getEntitiesOfClass(RelicFlight.class,player.getBoundingBox().inflate(3)))for(int t=0;t<9&&f.isAlive();t++){f.tickCount++;f.tick();}
                for(var lash:level.getEntitiesOfClass(RibbonLash.class,player.getBoundingBox().inflate(3)))for(int t=0;t<6&&lash.isAlive();t++){lash.tickCount++;lash.tick();}
                require(CounterEffects.active(player)!=null&&CounterEffects.active(player).target().equals(entry.target()),"Ward failed "+entry.id());
                require(CounterEffects.marked(foe)!=null&&CounterEffects.marked(foe).target().equals(entry.target()),"Normal cast missed matching player "+entry.id()+" projectiles="+level.getEntitiesOfClass(Snowball.class,player.getBoundingBox().inflate(24)).stream().map(b->b.position()+" alive="+b.isAlive()).toList());
                require(stack.getDamageValue()==1,"Cast durability "+entry.id());
                clearProjectiles(level,player);
            }
            System.out.println("COUNTER ROSTER PASS 133 normal casts against installed player-held weapon forms");
            var ion=CounterArsenal.ROSTER.stream().filter(e->e.target().endsWith(":ionbound_stormscale")).findFirst().orElseThrow();
            var ionStack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(ion.target())));
            ionStack.set(net.sweenus.simplyswords.registry.ComponentTypeRegistry.ION_CUBES.get(),new net.sweenus.simplyswords.item.component.IonCubeComponent(3,0));
            foe.setItemInHand(InteractionHand.MAIN_HAND,ionStack);CounterEffects.hit(level,player,foe,ion);
            require(ionStack.get(net.sweenus.simplyswords.registry.ComponentTypeRegistry.ION_CUBES.get()).cubes()==0,"Ion cubes survived matching counter");
            for(String target:List.of("waxweaver","wickpiercer")){
                var e=CounterArsenal.entry("counter_"+target);var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.target())));foe.setItemInHand(InteractionHand.MAIN_HAND,stack);
                CounterEffects.hit(level,player,foe,e);
                require(net.sweenus.simplyswords.world.RevivalCooldownManager.isCoolingDown(level,foe,stack),"Revival seal failed "+target);
            }
            System.out.println("COUNTER MANAGER PASS cube depletion and both revival seals");
            foe.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);foe.setHealth(1000);foe.invulnerableTime=0;foe.teleportTo(.5,base.getY()+1,7.5);
            var twin=Arsenal.WEAPONS.get(Arsenal.Relic.AXOLOTL_WHIP).get();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(twin));twin.use(level,player,InteractionHand.MAIN_HAND);
            var lashes=level.getEntitiesOfClass(RibbonLash.class,player.getBoundingBox().inflate(3));require(lashes.size()==1,"Visible lash entity missing");
            for(int t=0;t<6;t++){lashes.getFirst().tickCount++;lashes.getFirst().tick();}
            require(level.getEntitiesOfClass(Axolotl.class,player.getBoundingBox().inflate(3),a->a.getTags().contains("uwu.twin")).size()==2,"Twinfin must summon exactly two");clearProjectiles(level,player);
            var frost=Arsenal.WEAPONS.get(Arsenal.Relic.FROST_WHIP).get();
            foe.invulnerableTime=0;player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(frost));frost.use(level,player,InteractionHand.MAIN_HAND);
            for(var lash:level.getEntitiesOfClass(RibbonLash.class,player.getBoundingBox().inflate(3)))for(int t=0;t<6;t++){lash.tickCount++;lash.tick();}
            require(foe.getTicksFrozen()==160&&foe.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Frost lash did not freeze its struck target");clearProjectiles(level,player);
            var rift=Arsenal.WEAPONS.get(Arsenal.Relic.RIFT_WHIP).get();
            foe.invulnerableTime=0;player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(rift));rift.use(level,player,InteractionHand.MAIN_HAND);
            for(var lash:level.getEntitiesOfClass(RibbonLash.class,player.getBoundingBox().inflate(3)))for(int t=0;t<6;t++){lash.tickCount++;lash.tick();}
            require(player.getPersistentData().hasUUID("uwu.rift_target"),"Rift lash did not mark target");
            player.getCooldowns().removeCooldown(rift);player.setShiftKeyDown(true);rift.use(level,player,InteractionHand.MAIN_HAND);player.setShiftKeyDown(false);
            require(player.distanceToSqr(foe)<9&&!player.getPersistentData().hasUUID("uwu.rift_target"),"Sneak-use did not safely teleport the wielder beside marked foe");
            require(level.noCollision(player),"Rift landing inside a solid block");player.teleportTo(.5,base.getY()+1,.5);clearProjectiles(level,player);
            System.out.println("WHIP EFFECTS PASS frost freeze and marked-target safe teleport");
            var scythe=Arsenal.WEAPONS.get(Arsenal.Relic.VESPER_SCYTHE).get();var scytheStack=new ItemStack(scythe);player.setItemInHand(InteractionHand.MAIN_HAND,scytheStack);player.getCooldowns().removeCooldown(scythe);player.getPersistentData().remove("uwu.scythe_flight");
            scythe.use(level,player,InteractionHand.MAIN_HAND);scythe.onUseTick(level,player,scytheStack,71988);
            var flights=level.getEntitiesOfClass(RelicFlight.class,player.getBoundingBox().inflate(3));require(flights.size()==1&&!flights.getFirst().wave(),"Held throw must fire without release");
            scythe.onUseTick(level,player,scytheStack,71987);require(level.getEntitiesOfClass(RelicFlight.class,player.getBoundingBox().inflate(3)).size()==1,"Held throw duplicated");
            require(player.getMainHandItem()==scytheStack&&scytheStack.getCount()==1,"Scythe ownership changed");
            for(int t=0;t<60&&flights.getFirst().isAlive();t++){flights.getFirst().tickCount++;flights.getFirst().tick();}require(!flights.getFirst().isAlive(),"Thrown scythe failed to return");
            System.out.println("RANGED RELICS PASS textured lash, exactly two axolotls, held scythe return");
            var glitch=(GlitchBow)UwUDimension.GLITCH.get();var bow=new ItemStack(glitch);player.setItemInHand(InteractionHand.MAIN_HAND,bow);
            int last=-1;
            for(int i=0;i<180;i++){
                player.getCooldowns().removeCooldown(glitch);glitch.use(level,player,InteractionHand.MAIN_HAND);glitch.releaseUsing(bow,level,player,71980);
                int next=bow.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getInt("uwu_form");
                require(next!=last,"Glitch repeated form");last=next;require(player.getMainHandItem()==bow&&bow.getCount()==1,"Glitch manufactured inventory weapon");clearProjectiles(level,player);
            }
            require(bow.getDamageValue()==180,"Glitch did not preserve cumulative durability");
            System.out.println("GLITCH PASS 180 distinct-consecutive firing transitions on one durable stack");
            loot(level);
            var site=GroveSites.first(level);level.getChunkAt(site.pos(0,53,103));
            dev.uwu.test.ChestProof.run(level,site.pos(0,53,103));
            require(level.getBlockState(site.pos(-7,-17,146)).is(MirrorCourt.BELL.get()),"Boss bell not on normal dungeon path");
            level.getChunkAt(site.pos(-7,-18,159));
            require(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(site.pos(-7,-18,159)).getBlock()).getNamespace().equals("lootr"),"Boss court reward missing");
        }finally{clearProjectiles(level,player);player.discard();foe.discard();}
    }
    private static ServerPlayer player(ServerLevel level,GameProfile profile){
        var player=new ServerPlayer(level.getServer(),level,profile,net.minecraft.server.level.ClientInformation.createDefault());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),connection,player,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}
        };
        return player;
    }
    private static void loot(ServerLevel level){
        for(String name:List.of("rose_bathhouse","inverted_shrine","tide_archive","mirror_court")){
            var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,ResourceLocation.parse("uwudimension:chests/"+name));
            var table=level.getServer().reloadableRegistries().getLootTable(key);
            int found=0;Set<String> ids=new HashSet<>();var seeds=new java.util.Random(928174L);
            for(int i=1;i<=6000;i++){
                long seed=seeds.nextLong();
                var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,new net.minecraft.world.phys.Vec3(0,300,0)).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
                for(var item:table.getRandomItems(params,seed))if(isWeapon(item)){found++;ids.add(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());}
                var outside=new net.minecraft.world.level.storage.loot.LootParams.Builder(level.getServer().overworld()).withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,new net.minecraft.world.phys.Vec3(0,100,0)).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
                require(table.getRandomItems(outside,seed).stream().noneMatch(CounterProof::isWeapon),"Weapon generated outside dimension: "+name);
            }
            double expected=name.equals("rose_bathhouse")?.05:name.equals("inverted_shrine")?.08:.12;
            require(Math.abs(found/6000.0-expected)<.025,"Rare probability mismatch "+name+" "+found);
            System.out.println("RARE LOOT PASS "+name+" rolls=6000 weapons="+found+" distinct="+ids.size()+" outside=0");
        }
    }
    private static boolean isWeapon(ItemStack s){return s.getItem() instanceof Arsenal.RelicItem||s.getItem() instanceof CounterArsenal.CounterItem||s.getItem() instanceof GlitchBow;}
    private static void clearProjectiles(ServerLevel level,Player p){
        p.stopUsingItem();
        for(Entity e:level.getEntities(p,p.getBoundingBox().inflate(40),e->e instanceof net.minecraft.world.entity.projectile.Projectile||e instanceof RibbonLash))e.discard();
    }
}
