package dev.uwu.test;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Real native births, real client tracking and a portrait of both juvenile meshes beside their adult variants. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class GuardianFamilyCamera {
    private record Shape(boolean female,boolean young,float width,float height,float eye){}
    private static volatile Map<UUID,Shape> expected=Map.of();
    private static List<BlossomWarden> sitters=List.of();
    private static final Set<UUID> seen=new HashSet<>();
    private static Method scale;
    private static volatile boolean active;
    private static boolean logged;
    private GuardianFamilyCamera(){}

    static SeaWatch.View frame(ServerPlayer p,String name){
        var l=p.serverLevel();
        // The actual observer has already loaded this area during the immediately preceding Owl portrait.
        var at=GroveSites.arrival(l).offset(5,3,0);
        GuardianFamilyProof.floor(l,at);
        var previous=p.getMainHandItem().copy();GuardianFamilyProof.Family group;
        try{group=GuardianFamilyProof.family(l,at,p,p);}
        finally{p.setItemInHand(InteractionHand.MAIN_HAND,previous);p.inventoryMenu.broadcastChanges();}
        sitters=List.of(group.male(),group.son(),group.daughter(),group.girl());
        var origin=Vec3.atBottomCenterOf(at);
        double[] x={-2,-.56,.58,1.85};
        Map<UUID,Shape> traits=new HashMap<>();
        for(int i=0;i<sitters.size();i++){
            var g=sitters.get(i);
            g.moveTo(origin.x+x[i],origin.y,origin.z+(g.isBaby()?.45:0),0,0);
            g.setNoGravity(true);g.setNoAi(true);g.setInvulnerable(true);g.yBodyRot=0;g.yHeadRot=0;
            traits.put(g.getUUID(),new Shape(g.female(),g.isBaby(),g.getBbWidth(),g.getBbHeight(),g.getEyeHeight()));
        }
        expected=Map.copyOf(traits);seen.clear();logged=false;active=true;
        var aim=origin.add(0,1.31,0);
        return new SeaWatch.View(aim.add(.10,.18,4.85),aim,name,false);
    }

    @SubscribeEvent public static void before(RenderFrameEvent.Pre event){if(active)seen.clear();}
    @SubscribeEvent public static void rendered(RenderLivingEvent.Post<?,?> event){
        if(!active||!(event.getEntity() instanceof BlossomWarden guardian))return;
        var wanted=expected.get(guardian.getUUID());
        if(wanted==null)return;
        if(!(event.getRenderer() instanceof BlossomWardenRenderer renderer))throw new AssertionError("Unexpected guardian renderer");
        if(guardian.female()!=wanted.female()||guardian.isBaby()!=wanted.young())return;
        GuardianFamilyProof.close(guardian.getBbWidth(),wanted.width(),"tracked width");
        GuardianFamilyProof.close(guardian.getBbHeight(),wanted.height(),"tracked height");
        GuardianFamilyProof.close(guardian.getEyeHeight(),wanted.eye(),"tracked eye");
        String path=renderer.getTextureLocation(guardian).getPath();
        if(guardian.female()?!path.contains("female_uwu_guardian"):!path.endsWith("/blossom_warden.png"))
            throw new AssertionError("Unexpected tracked guardian texture "+path);
        try{
            if(scale==null){scale=BlossomWardenRenderer.class.getDeclaredMethod("scale",BlossomWarden.class,PoseStack.class,float.class);scale.setAccessible(true);}
            var pose=new PoseStack();scale.invoke(renderer,guardian,pose,0F);
            double factor=wanted.young()?.5:1;
            GuardianFamilyProof.close(pose.last().pose().m00(),factor,"render scale X");
            GuardianFamilyProof.close(pose.last().pose().m11(),factor,"render scale Y");
            GuardianFamilyProof.close(pose.last().pose().m22(),factor,"render scale Z");
        }catch(ReflectiveOperationException ex){throw new AssertionError("Guardian model scale",ex);}
        seen.add(guardian.getUUID());
    }
    static boolean ready(){
        boolean complete=active&&seen.size()==4&&Minecraft.getInstance().screen==null;
        if(complete&&!logged){logged=true;System.out.println("OWL GUARDIAN FAMILY MODELS PASS native birth, four visible synced renderers, two 0.5-scale juveniles, and matching server eye/width/height");}
        return complete;
    }
    static void release(){active=false;expected=Map.of();sitters.forEach(Entity::discard);sitters=List.of();}
}
