package dev.uwu.test;

import java.util.List;
import java.util.UUID;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class FieldCamera {
    private static int phase, ticks, heartbeat;
    private static long poseTime;
    private static final double[][] VIEWS = {{0.5,81,0.5,0,-12},{0.5,53,55.5,0,-4},{-8.5,53,84.5,-17,3},{38.5,1,110.5,-43,-18}};
    private static final String[] FILES = {"01-flowering-cliffs","02-lantern-water","03-bathhouse","04-hanging-shrine"};

    @SubscribeEvent public static void step(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if (++heartbeat%400==0) {
            System.out.println("FIELD CAMERA phase="+phase+" screen="+(mc.screen==null?"in game":mc.screen.getClass()+" "+mc.screen.getTitle().getString()));
            if(mc.screen!=null) for(var child:mc.screen.children()) if(child instanceof Button b) System.out.println("FIELD BUTTON "+b.getMessage().getString());
        }
        if (phase==0 && mc.screen instanceof TitleScreen title) {
            phase=1;
            var settings=new LevelSettings("Field Visit",GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("FieldVisit",settings,new WorldOptions(152045L,true,false),WorldPresets::createNormalWorldDimensions,title);
            return;
        }
        if (phase==1 && mc.screen!=null && mc.player==null) {
            for (var child:List.copyOf(mc.screen.children())) if(child instanceof Button b) {
                String label=b.getMessage().getString();
                if(label.equals("Proceed") || label.equals("I know what I'm doing!")) { b.onPress(); break; }
            }
        }
        if (phase<1 || mc.player==null || mc.getSingleplayerServer()==null || mc.level==null) return;
        if (phase>=2 && ticks==80) {
            var observed=mc.getSingleplayerServer();
            UUID observerId=mc.player.getUUID();
            observed.execute(()->{
                var player=observed.getPlayerList().getPlayer(observerId);
                if (player==null) return;
                var wild=player.serverLevel().getEntitiesOfClass(PokemonEntity.class,player.getBoundingBox().inflate(80));
                for (var mon:wild) System.out.println("NATURAL CLIENT VISIT | "+mon.getPokemon().getSpecies().getName()+" at "+mon.blockPosition());
            });
        }
        if (ticks++<130) return;
        var server=mc.getSingleplayerServer();
        UUID uuid=mc.player.getUUID();
        if (phase==1) {
            ticks=0;phase++;
            server.execute(()->{
                var user=server.getPlayerList().getPlayer(uuid);
                var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("uwudimension:heartwater_locket")));
                user.setItemInHand(InteractionHand.MAIN_HAND,stack);
                stack.use(user.level(),user,InteractionHand.MAIN_HAND);
            });
            return;
        }
        if (phase>=2 && phase<2+VIEWS.length*2) {
            if (!mc.level.dimension().location().toString().equals("uwudimension:heartwater")) throw new AssertionError("Locket did not enter UwU Dimension");
            int index=(phase-2)/2;
            if (phase%2==1) {
                // Wait for received chunks and meshed camera/sightline sections before recording a frame.
                long elapsed=System.nanoTime()-poseTime;
                var here=mc.player.blockPosition();
                var ahead=here.offset((int)(mc.player.getLookAngle().x*12),0,(int)(mc.player.getLookAngle().z*12));
                if (elapsed < 18_000_000_000L || !mc.levelRenderer.isSectionCompiled(here)
                        || !mc.levelRenderer.isSectionCompiled(ahead) || mc.levelRenderer.countRenderedSections()<3) {
                    if (elapsed>130_000_000_000L) throw new AssertionError("Camera never received terrain "+index);
                    return;
                }
            }
            ticks=0;
            if (phase++%2==0) {
                poseTime=System.nanoTime();
                mc.setScreen(null); mc.options.hideGui=true;
                var v=VIEWS[index];
                server.execute(()->{
                    var p=server.getPlayerList().getPlayer(uuid);
                    p.getAbilities().invulnerable=true;
                    p.serverLevel().setDayTime(1000);
                    p.teleportTo(p.serverLevel(),v[0],v[1],v[2],(float)v[3],(float)v[4]);
                });
            } else Screenshot.grab(mc.gameDirectory,FILES[index]+".png",mc.getMainRenderTarget(),message->System.out.println(message.getString()));
        } else if (phase==2+VIEWS.length*2) {
            phase++;ticks=0;
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(uuid);
                p.getMainHandItem().use(p.level(),p,InteractionHand.MAIN_HAND);
            });
        } else if (mc.level.dimension().equals(Level.OVERWORLD)) {
            System.out.println("HEARTWATER CLIENT ROUND TRIP PASSED");
            mc.stop();
        }
    }
}
