package dev.uwu.test;
import dev.uwu.dimension.*;
import java.util.*;
import circuitlord.reactivemusic.*;
import circuitlord.reactivemusic.entries.RMRuntimeEntry;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class MusicCamera {
 static String state="menu";static int ticks,scenario;static volatile boolean ready;
 static final String[] REALMS={"uwudimension:sakura_reach","uwudimension:sakura_reach","uwudimension:sakura_reach","minecraft:overworld","minecraft:the_nether","uwudimension:heartwater","uwudimension:blossom_reach"};
 static List<RMRuntimeEntry> entries;
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static RMRuntimeEntry rule(SongpackZip pack,String song,String...conditions){var e=new SongpackEntry();e.events=conditions;e.songs=new String[]{song};return RMRuntimeEntry.create(pack,e);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  if(!"controls".equals(System.getenv("UWU_VISIT")))return;
  var mc=Minecraft.getInstance();ticks++;mc.getToasts().clear();check(ticks<2400,"Music client stalled "+state);
  if(state.equals("menu")&&mc.screen instanceof TitleScreen title){state="create";ticks=0;
   mc.createWorldOpenFlows().createFreshLevel("MusicPolicy",new LevelSettings("Music Policy",GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(152045,true,false),WorldPresets::createNormalWorldDimensions,title);return;}
  if(state.equals("create")&&mc.screen!=null&&mc.player==null)for(var c:List.copyOf(mc.screen.children()))if(c instanceof Button b&&(b.getMessage().getString().equals("Proceed")||b.getMessage().getString().equals("I know what I'm doing!"))){b.onPress();break;}
  if(mc.player==null||mc.getSingleplayerServer()==null)return;
  if(state.equals("create")&&ticks>80){
   var pack=new SongpackZip();pack.config=new SongpackConfig();pack.config.name="Private-pack rule fixture (no audio)";
   entries=List.of(rule(pack,"river_night","NIGHT","DIM=OVERWORLD","BIOME=biomeswevegone:sakura_grove || BIOME=minecraft:cherry_grove || BIOME=natures_spirit:aspen_forest"),rule(pack,"distant_shores","UNDERWATER || BIOME=ocean"),rule(pack,"pinwheel","BIOME=flower_forest || BIOME=cherry_grove || BIOMETAG=FLORAL"));
   state="setup";
  }
  if(state.equals("setup")){
   state="wait";ticks=0;ready=false;var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();final int test=scenario;
   server.execute(()->{
    var key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(REALMS[test]));var level=server.getLevel(key);var p=server.getPlayerList().getPlayer(uuid);
    var at=new BlockPos(0,180,0);level.getChunkAt(at);
    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"execute in "+REALMS[test]+" run fillbiome -16 168 -16 16 196 16 minecraft:cherry_grove");
    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){level.setBlockAndUpdate(new BlockPos(x,179,z),Blocks.WHITE_CONCRETE.defaultBlockState());for(int y=180;y<=184;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),(test==2?Blocks.WATER:Blocks.AIR).defaultBlockState());}
    level.setDayTime(test==1||test==2?6000:18000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
    p.teleportTo(level,.5,180,.5,0,0);p.getAbilities().flying=true;p.onUpdateAbilities();
    if(test==0){var g=People.BLOSSOM_WARDEN.get().create(level);g.moveTo(1.5,180,.5,0,0);level.addFreshEntity(g);}
    ready=true;
   });return;
  }
  if(state.equals("wait")&&ready&&ticks>100&&mc.level.dimension().location().toString().equals(REALMS[scenario])){
   SongPicker.tickEventMap();
   check(SongPicker.currentBiomeName.contains("cherry_grove"),"Biome fixture not synchronized "+SongPicker.currentBiomeName);
   String dim=scenario==4?"minecraft:the_nether":"minecraft:overworld";
   check(SongPicker.currentDimName.equals(dim),"Music context incorrect "+SongPicker.currentDimName);
   check(mc.level.dimension().location().toString().equals(REALMS[scenario]),"Real dimension altered");
   String song=entries.stream().filter(SongPicker::isEntryValid).map(e->e.songs.getFirst()).findFirst().orElse("none");
   String expected=scenario==1||scenario==4?"pinwheel":scenario==2?"distant_shores":"river_night";
   check(song.equals(expected),"Rule selection "+scenario+" expected "+expected+" got "+song);
   if(scenario==0){boolean seen=false;for(var e:mc.level.entitiesForRendering())if(e instanceof BlossomWarden g){seen=true;check(g.getMaxHealth()==6666&&g.getHealth()==6666,"Client guardian health clamped");check(g.getName().getString().equals("UwU Guardian"),"Guardian name missing translation");}check(seen,"Client guardian absent");}
   System.out.println("MUSIC MATCH PASS real="+REALMS[scenario]+" context="+dim+" biome="+SongPicker.currentBiomeName+" scenario="+scenario+" song="+song);
   if(++scenario==REALMS.length){System.out.println("UWU MUSIC CLIENT PASSED (matcher verified; private soundtrack not played)");state="done";mc.getSingleplayerServer().halt(false);mc.stop();return;}
   state="setup";
  }
 }
}
