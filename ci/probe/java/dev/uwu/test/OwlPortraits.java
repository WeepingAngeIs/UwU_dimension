package dev.uwu.test;

import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.BlossomWardenRenderer;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.KeeperRenderer;
import dev.uwu.dimension.KeeperZombieRenderer;
import dev.uwu.dimension.OwlExpression;
import dev.uwu.dimension.People;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Portrait each resident using its actual renderer; time eyelid closeups to a natural live blink. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class OwlPortraits {
    private static final List<Mob> sitters=new ArrayList<>();
    private static final Map<BlockPos,BlockState> roof=new HashMap<>();
    private static volatile SeaWatch.View view;
    private static volatile Set<UUID> ids=Set.of();
    private static final Set<UUID> seen=new java.util.HashSet<>();
    private static int eyelid=-1,photos;
    private static final String[] EYES={"open","half","closed"};

    static SeaWatch.View frame(ServerPlayer p,String name) {
        var l=p.serverLevel();
        Vec3 origin=Vec3.atBottomCenterOf(GroveSites.arrival(l)).add(0,.06,0);
        List<Mob> models=new ArrayList<>();
        if(name.equals("owl-keepers")) {
            for(var type:List.of(People.PETAL_KEEPER.get(),People.PETAL_ZOMBIE.get(),People.DUSK_KEEPER.get(),People.DUSK_ZOMBIE.get())) {
                models.add(type.create(l));
            }
        } else {
            for(boolean girl:name.equals("owl-expression")?new boolean[]{true}:new boolean[]{true,false}) {
                var guard=People.BLOSSOM_WARDEN.get().create(l);
                var data=guard.saveWithoutId(new CompoundTag());data.putBoolean("FemaleGuardian",girl);
                data.putBoolean("KeeperSummonedGuardian",true);guard.load(data);models.add(guard);
            }
        }
        double gap=name.equals("owl-keepers")?1.25:1.62;
        for(int i=0;i<models.size();i++) {
            var mob=models.get(i);
            Vec3 at=origin.add((i-(models.size()-1)/2.0)*gap,0,0);
            if(mob instanceof dev.uwu.dimension.KeeperZombieVillager) {
                BlockPos center=BlockPos.containing(at).above(4);
                for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                    var pos=center.offset(dx,0,dz);
                    roof.putIfAbsent(pos,l.getBlockState(pos));
                    l.setBlockAndUpdate(pos,Blocks.MOSS_BLOCK.defaultBlockState());
                }
            }
            mob.moveTo(at.x,at.y,at.z,0,0);
            mob.yBodyRot=0;mob.yHeadRot=0;mob.setNoAi(true);mob.setNoGravity(true);mob.setInvulnerable(true);
            if(!l.addFreshEntity(mob)) throw new AssertionError("Missing Owl model "+i);
            sitters.add(mob);
        }
        Vec3 look=origin.add(0,name.equals("owl-expression")?1.47:name.equals("owl-keepers")?1.05:1.31,0);
        var shot=new SeaWatch.View(look.add(name.equals("owl-expression")?.09:.19,.05,name.equals("owl-expression")?1.26:name.equals("owl-keepers")?4.35:3.75),look,name,false);
        ids=sitters.stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toUnmodifiableSet());
        view=shot;
        return shot;
    }

    @SubscribeEvent public static void before(RenderFrameEvent.Pre event) {
        if(view==null)return;
        seen.clear();eyelid=-1;
    }

    @SubscribeEvent public static void drawn(RenderLivingEvent.Post<?,?> event) {
        if(view==null || !ids.contains(event.getEntity().getUUID()))return;
        var entity=event.getEntity();
        Object renderer=event.getRenderer();
        if(renderer instanceof BlossomWardenRenderer original && entity instanceof BlossomWarden guard) {
            if(view.file().equals("owl-expression")) {
                eyelid=OwlExpression.frame(guard.getUUID(),guard.tickCount);
                String name=original.getTextureLocation(guard).getPath();
                String required=new String[]{"female_uwu_guardian.png","female_uwu_guardian_half.png","female_uwu_guardian_blink.png"}[eyelid];
                if(!name.endsWith(required))throw new AssertionError("Incorrect animated skin "+name);
            }
        } else if(!(renderer instanceof KeeperRenderer || renderer instanceof KeeperZombieRenderer))
            throw new AssertionError("Missing keeper or Guardian renderer "+renderer);
        seen.add(entity.getUUID());
    }

    @SubscribeEvent public static void after(RenderFrameEvent.Post event) {
        var scene=view;
        if(scene==null || !scene.file().equals("owl-expression") || eyelid<0 || (photos & 1<<eyelid)!=0)return;
        var mc=Minecraft.getInstance();
        if(mc.screen!=null || mc.player==null || mc.player.getEyePosition().distanceToSqr(scene.eye())>.09 || mc.levelRenderer.countRenderedSections()<2) return;
        Screenshot.grab(mc.gameDirectory,"owl-eye-"+EYES[eyelid]+".png",mc.getMainRenderTarget(),m->System.out.println(m.getString()));
        photos|=1<<eyelid;
    }

    static boolean ready(String name) {
        return seen.containsAll(ids) && (!name.equals("owl-expression") || photos==7);
    }

    static void release(ServerPlayer p) {
        view=null;
        ids=Set.of();
        sitters.forEach(Entity::discard);sitters.clear();
        roof.forEach((pos,state)->p.serverLevel().setBlockAndUpdate(pos,state));roof.clear();
    }
}
