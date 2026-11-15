package dev.uwu.test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import dev.uwu.dimension.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Photographs real held models and one moving, server-fired Cupid arrow through the production renderer. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class RelicPortraits {
    private static final String[] NAMES={"rest","early","middle","full"};
    private static final BakedModel[] MODELS=new BakedModel[4];
    private static final Set<UUID> previousShots=new HashSet<>();
    private static ItemStack oldMain,oldOff;
    private static UUID sitter;
    private static int side,stage,handTicks;
    private static boolean recording,flight,finished;
    private static Entity camera;
    private static HeartArrow tracked;
    private static boolean filmArrow;
    private static boolean filmedArrow,drawCue;
    private static Vec3 impactCue;
    private static int impactFrames;
    private static UUID targetId;

    public static void received(ClientboundLevelParticlesPacket packet) {
        if(!recording || finished)return;
        if(packet.getParticle().getType()==CupidParticles.DRAW_HEART.get()) drawCue=true;
        if(flight && packet.getParticle().getType()==CupidParticles.IMPACT_OUTLINE.get())
            impactCue=new Vec3(packet.getX(),packet.getY(),packet.getZ());
    }

    static SeaWatch.View frame(ServerPlayer p,String name) {
        var level=p.serverLevel();
        var feet=Vec3.atBottomCenterOf(GroveSites.arrival(level));
        if(name.equals("female-guardian")) {
            var guardian=People.BLOSSOM_WARDEN.get().create(level);
            if(guardian==null) throw new AssertionError("Missing guardian");
            CompoundTag tag=guardian.saveWithoutId(new CompoundTag());
            tag.putBoolean("FemaleGuardian",true);
            guardian.load(tag);
            guardian.moveTo(feet.x,feet.y,feet.z,-15F,0);
            guardian.yBodyRot=-15F;
            guardian.yHeadRot=-15F;
            guardian.setNoAi(true);
            guardian.setInvulnerable(true);
            if(!level.addFreshEntity(guardian)||!guardian.female()) throw new AssertionError("Guardian portrait setup failed");
            sitter=guardian.getUUID();
            var aim=feet.add(0,guardian.getBbHeight()*.5,0);
            return new SeaWatch.View(aim.add(.9,.17,3.9),aim,name,false);
        }
        oldMain=p.getMainHandItem().copy();
        oldOff=p.getOffhandItem().copy();
        putBow(p,InteractionHand.MAIN_HAND);
        // Give the real hand renderer multiple frames at each draw stage under software rendering.
        p.server.tickRateManager().setTickRate(8F);
        var eye=feet.add(.2,8,3);
        return new SeaWatch.View(eye,eye.add(0,2,-12),name,false);
    }

    private static void putBow(ServerPlayer player,InteractionHand hand) {
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        player.setItemInHand(hand,new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get()));
        player.containerMenu.broadcastChanges();
        if(hand==InteractionHand.OFF_HAND) {
            var level=player.serverLevel();
            var bank=BlockPos.containing(player.getEyePosition().add(0,1.45,-17));
            for(int x=-2;x<=2;x++)for(int z=-1;z<=1;z++)
                level.setBlockAndUpdate(bank.offset(x,-1,z),net.minecraft.world.level.block.Blocks.MOSS_BLOCK.defaultBlockState());
            var cow=EntityType.COW.create(level);
            if(cow==null)throw new AssertionError("Missing archery subject");
            cow.moveTo(player.getX(),bank.getY(),player.getZ()-17,90,0);
            cow.setNoAi(true);cow.setPersistenceRequired();
            if(!level.addFreshEntity(cow))throw new AssertionError("Archery subject did not join");
            targetId=cow.getUUID();
        }
    }

    static boolean guardian(Minecraft mc) {
        for(var entity:mc.level.entitiesForRendering())
            if(entity instanceof BlossomWarden g && entity.getUUID().equals(sitter) && g.female())return true;
        return false;
    }

    private static InteractionHand hand(){return side==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;}

    static boolean gallery(Minecraft mc) {
        if(finished) return true;
        recording=true;
        mc.options.hideGui=flight;
        if(flight) return false;
        if(stage==4) {
            mc.options.keyUse.setDown(false);
            if(side==1) {
                for(var entity:mc.level.entitiesForRendering()) if(entity instanceof HeartArrow) previousShots.add(entity.getUUID());
                flight=true;
                mc.options.hideGui=true;
            }
            mc.gameMode.releaseUsingItem(mc.player);
            if(!flight) {
                side=1;stage=0;handTicks=0;drawCue=false;
                var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
                server.execute(()->putBow(server.getPlayerList().getPlayer(id),InteractionHand.OFF_HAND));
            }
            return false;
        }
        var held=mc.player.getItemInHand(hand());
        if(!held.is(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get())) return false;
        handTicks++;
        if(stage>0) {
            mc.options.keyUse.setDown(true);
            if(!mc.player.isUsingItem()) mc.gameMode.useItem(mc.player,hand());
        }
        return false;
    }

    @SubscribeEvent public static void beforeFrame(RenderFrameEvent.Pre event) {
        if(!recording||!flight||finished)return;
        var mc=Minecraft.getInstance();
        if(tracked==null) for(var entity:mc.level.entitiesForRendering())
            if(entity instanceof HeartArrow arrow && arrow.getOwner()==mc.player && !previousShots.contains(arrow.getUUID())
                    && arrow.getDeltaMovement().lengthSqr()>.1) {
                tracked=arrow;break;
            }
        if(tracked==null || (!filmedArrow && tracked.getDeltaMovement().lengthSqr()<=.1))return;
        if(camera==null) {
            camera=EntityType.ARMOR_STAND.create(mc.level);
            if(camera==null)throw new AssertionError("Missing photo camera");
            mc.setCameraEntity(camera);
        }
        Vec3 focus,eye;
        if(!filmedArrow) {
            focus=tracked.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            var lateral=tracked.getDeltaMovement().normalize().cross(new Vec3(0,1,0)).normalize();
            eye=focus.add(lateral.scale(1.5)).add(0,.25,0);
        } else {
            Entity subject=null;
            for(var entity:mc.level.entitiesForRendering()) if(entity.getUUID().equals(targetId)){subject=entity;break;}
            if(subject==null)return;
            focus=subject.position().add(0,subject.getBbHeight()+.38,0);
            eye=focus.add(1,.1,2.9);
            if(impactCue!=null) {
                if(impactCue.distanceToSqr(subject.position().add(0,subject.getBbHeight()+.82,0))>1)
                    throw new AssertionError("Cupid outline did not arrive at the photographed target");
                impactFrames++;
            }
        }
        var aim=focus.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z)));
        // Camera eye height is carried over from the same-sized player, then interpolates to the stand.
        var pos=eye.subtract(0,camera.getEyeHeight(),0);
        camera.moveTo(pos.x,pos.y,pos.z,yaw,pitch);
        camera.setOldPosAndRot();
        filmArrow=true;
    }

    @SubscribeEvent public static void afterFrame(RenderFrameEvent.Post event) {
        if(!recording||finished)return;
        var mc=Minecraft.getInstance();
        if(flight) {
            if(!filmArrow)return;
            if(!filmedArrow) {
                var renderer=mc.getEntityRenderDispatcher().getRenderer(tracked);
                String texture=renderer.getTextureLocation(tracked).toString();
                if(!texture.equals("uwudimension:textures/entity/cupid_heart_arrow.png"))throw new AssertionError("Cupid missile rendered "+texture);
                photo(mc,"cupid-arrow-flight");
                filmedArrow=true;
                return;
            }
            if(impactFrames<2)return;
            photo(mc,"cupid-impact-outline");
            System.out.println("CUPID HELD MODEL PASS four distinct models in both hands, moving heart-tipped arrow, full-draw and real impact cues");
            mc.setCameraEntity(mc.player);finished=true;return;
        }
        if(stage>=4 || handTicks<8)return;  // Allow the hand-transition animation to settle before the idle photo.
        var stack=mc.player.getItemInHand(hand());
        if(!stack.is(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get()))return;
        int t=mc.player.getTicksUsingItem();
        if(stage==0&&mc.player.isUsingItem() || stage==1&&(t<3||t>=10) || stage==2&&(t<11||t>=18) || stage==3&&(t<20||!drawCue)) return;
        var rendered=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        var sprite=rendered.getParticleIcon().contents().name().toString();
        String expected="uwudimension:item/cupid_bow"+(stage==0?"":"_draw"+(stage-1));
        if(!sprite.equals(expected))throw new AssertionError("Draw stage "+stage+" at "+t+" ticks selected "+sprite+" expected "+expected);
        if(side==0) {
            for(int i=0;i<stage;i++) if(MODELS[i]==rendered)throw new AssertionError("Duplicate Cupid bow model "+stage);
            MODELS[stage]=rendered;
        } else if(MODELS[stage]!=rendered)throw new AssertionError("Offhand selected wrong model "+stage);
        photo(mc,"cupid-"+(side==0?"right":"left")+"-"+NAMES[stage]);
        stage++;
    }
    private static void photo(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),msg->System.out.println(msg.getString()));
    }
    static void restore(ServerPlayer p) {
        p.stopUsingItem();
        p.setItemInHand(InteractionHand.MAIN_HAND,oldMain);
        p.setItemInHand(InteractionHand.OFF_HAND,oldOff);
        p.server.tickRateManager().setTickRate(20F);
        p.containerMenu.broadcastChanges();
    }
}
