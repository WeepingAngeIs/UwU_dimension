package dev.uwu.test;

import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.People;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.UUID;

/** Strike a generated resident through the multiplayer controller and observe the client position packets. */
public final class GuardianCameraProof {
    private static int ticks;
    private static volatile int victimId=-1;
    private static volatile double takeoff, startX, startZ;
    private static volatile boolean clicked, returned, wounded;
    private static boolean gripSeen;
    private static long started;
    private static BlockPos plaza;
    private static UUID resident, guardian;
    private static volatile String lastObservation="awaiting village";
    private static long nextObservation;
    private static float previousHealth;

    public static boolean tick(Minecraft mc) {
        if (returned) return true;
        if (mc.player==null || mc.level==null || mc.gameMode==null || mc.getSingleplayerServer()==null) return false;
        if (++ticks==1) started=System.nanoTime();
        if ((System.nanoTime()-started)>240_000_000_000L)
            throw new AssertionError("Guardian client timed out: clicked="+clicked+" wounded="+wounded+" grip="+gripSeen+" y="+mc.player.getY()+" start="+takeoff+" server="+lastObservation);
        var server=mc.getSingleplayerServer(); UUID id=mc.player.getUUID();
        if (ticks%10==1 && !clicked) server.execute(()->{
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if(player==null) return;
            ServerLevel level=player.serverLevel();
            if(plaza==null) {
                plaza=GroveSites.village(level,false);
                player.teleportTo(level,plaza.getX()+.5,plaza.getY(),plaza.getZ()+.5,0,0);
                return;
            }
            var guards=level.getEntitiesOfClass(BlossomWarden.class,new AABB(plaza).inflate(64),v->v.isAlive() && v.offender()==null);
            if (guards.isEmpty()) { lastObservation="settlement has no available guardian at "+plaza; return; }
            var guard=guards.getFirst();
            guardian=guard.getUUID();
            Keeper k=resident!=null && level.getEntity(resident) instanceof Keeper existing?existing:
                    level.getEntitiesOfClass(Keeper.class,new AABB(plaza).inflate(45),
                    v->v.isAlive()&&!v.isBaby()&&v.getType()==People.PETAL_KEEPER.get()).stream()
                    .min(Comparator.comparingDouble(v->v.distanceToSqr(guard))).orElse(null);
            if(k==null) return;
            if(resident==null) {
                resident=k.getUUID();
                previousHealth=k.getHealth();
                // Keep the generated volunteer by its house long enough for chunk synchronization.
                // The guardian retains normal AI for the whole experiment.
                k.setNoAi(true);
                if(k.isSleeping()) k.stopSleeping();
                k.getNavigation().stop();
            }
            if(player.distanceToSqr(k)>5 || victimId<0) {
                for (Direction direction:Direction.Plane.HORIZONTAL) {
                    Vec3 spot=k.position().add(direction.getStepX()*1.15,0,direction.getStepZ()*1.15);
                    if(!level.noCollision(player,player.getBoundingBox().move(spot.subtract(player.position()))))continue;
                    player.teleportTo(level,spot.x,spot.y,spot.z,player.getYRot(),0);break;
                }
            }
            takeoff=player.getY();startX=player.getX();startZ=player.getZ();victimId=k.getId();
        });
        if(!clicked && victimId>=0 && mc.level.getEntity(victimId) instanceof Keeper k &&
                mc.player.distanceToSqr(k)<7 && Math.abs(mc.player.getY()-takeoff)<2) {
            mc.gameMode.attack(mc.player,k);mc.player.swing(InteractionHand.MAIN_HAND);clicked=true;
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);
                if(p!=null && p.serverLevel().getEntity(resident) instanceof Keeper v) v.setNoAi(false);
            });
        }
        if(clicked && ticks%5==0) {
            if(!gripSeen) gripSeen=!mc.level.getEntitiesOfClass(BlossomWarden.class,mc.player.getBoundingBox().inflate(10),BlossomWarden::gripping).isEmpty();
            server.execute(()->{
                var p=server.getPlayerList().getPlayer(id);
                if (p==null || !(p.serverLevel().getEntity(resident) instanceof Keeper v)) return;
                var level=p.serverLevel();
                if(v.getLastHurtByMob()==p && v.getHealth()<previousHealth) wounded=true;
                if (level.getGameTime()>=nextObservation) {
                    nextObservation=level.getGameTime()+100;
                    var g=level.getEntity(guardian);
                    String pursuit="unloaded";
                    if(g instanceof BlossomWarden w) {
                        var state=new net.minecraft.nbt.CompoundTag();
                        w.addAdditionalSaveData(state);
                        pursuit=w.blockPosition()+" active="+w.tickCount+" order="+state.get("Offenders")
                                +" phase="+state.getByte("GripPhase")+" chaseTicks="+state.getInt("PursueTicks");
                    }
                    lastObservation="serverTick="+level.getGameTime()+" player="+p.getUUID()+" at "+p.blockPosition()
                            +" villager="+v.getUUID()+" health="+v.getHealth()+"/"+previousHealth+" court="+v.villageCourt()
                            +" guard="+guardian+" "+pursuit;
                    System.out.println("GUARDIAN CLIENT OBSERVATION "+lastObservation);
                }
            });
        }
        if(clicked && wounded && mc.player.getY()-takeoff>7960) {
            double rise=mc.player.getY()-takeoff;
            double lateral=Math.hypot(mc.player.getX()-startX,mc.player.getZ()-startZ);
            if(rise>8030 || lateral<70 || !gripSeen)
                throw new AssertionError("Unexpected client throw rise="+rise+" lateral="+lateral+" grip="+gripSeen);
            System.out.println("GUARDIAN CLIENT PASS | actual normal attack of generated Keeper; visible synced grip; client rise="+rise+" away="+lateral);
            server.execute(()->{
                ServerPlayer p=server.getPlayerList().getPlayer(id);
                if(p==null) return;
                p.teleportTo(p.serverLevel(),plaza.getX()+.5,plaza.getY(),plaza.getZ()+.5,0,0);
                p.setDeltaMovement(Vec3.ZERO);p.resetFallDistance();returned=true;
            });
        }
        return false;
    }
}
