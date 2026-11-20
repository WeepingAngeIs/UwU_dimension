package dev.uwu.dimension;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

final class HeartwaterAmbience {
    private static final Map<ServerLevel,Long> SENT = new WeakHashMap<>();
    static long packets(ServerLevel level) { return SENT.getOrDefault(level,0L); }

    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !ReefLife.isReach(player.level())) return;
        ServerLevel level=player.serverLevel();
        long tick=level.getGameTime()+player.getId();
        int stage=ReachJournal.chapter(player);
        if (stage>0 && stage<3 && tick%40==0 && player.getY()<60) {
            var site=GroveSites.forChunk(level,new net.minecraft.world.level.ChunkPos(player.blockPosition()));
            if (site.usable() && GroveSites.adventure(level,site)) {
                if (player.blockPosition().closerThan(site.pos(0,54,67),18)) ReachJournal.discover(player,2);
                if (player.blockPosition().closerThan(site.pos(-7,-17,146),25)) ReachJournal.discover(player,3);
            }
        }
        int mode=ReachLife.preference(player,"sakura_effects",2);
        if (mode==0 || tick%(mode==1 ? 30 : 10)!=0) return;
        double range=ReachLife.preference(player,"sakura_distance",12);
        var at=player.blockPosition();
        if (tick%30 == 0 && ReachLife.preference(player,"sakura_trail",0) == 1 && player.onGround()
                && Math.abs(player.getX()-player.xOld)+Math.abs(player.getZ()-player.zOld) > .012) {
            emit(player,ParticleTypes.CHERRY_LEAVES,player.getX(),player.getY()+.35,player.getZ(),range,2,.18,.06,.18,.012);
        }
        if (tick%60==0 && VillageCommons.nearFestival(level,at)) {
            emit(player,ParticleTypes.CHERRY_LEAVES,player.getX(),player.getY()+2,player.getZ(),range,3,.9,.12,.9,.01);
        }
        var random=player.getRandom();
        double angle=random.nextDouble()*Math.PI*2;
        double radius=Math.min(range*.65,6);
        double x=player.getX()+Math.cos(angle)*radius, z=player.getZ()+Math.sin(angle)*radius;
        if (player.isUnderWater() && player.getY()<34) {
            // Watch the direction the light draws toward. An occasional ink shadow marks a real pursuer.
            if (tick%30 == 0) {
                var foes = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        player.getBoundingBox().inflate(Math.min(12,range)), mob -> mob.isAlive()
                                && mob.getTarget() == player && mob.isInWater() && mob.hasLineOfSight(player));
                var pursuer = foes.stream().min(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
                if (pursuer.isPresent()) {
                    var ray = pursuer.get().getEyePosition().subtract(player.getEyePosition());
                    var shadow = player.getEyePosition().add(ray.scale(.65));
                    emit(player,ParticleTypes.SQUID_INK,shadow.x,shadow.y,shadow.z,range,2,.35,.10,.12,.01);
                    emit(player,ParticleTypes.GLOW,shadow.x,shadow.y+.16,shadow.z,range,2,.12,.04,.08,.005);
                    return;
                }
            }
            double heading=Math.floorDiv(tick,70)*2.39996;
            double reach=Math.min(range*.7, 1+(70-tick%70)*.09);
            emit(player,ParticleTypes.GLOW,player.getX()+Math.cos(heading)*reach,player.getEyeY(),
                    player.getZ()+Math.sin(heading)*reach,range,2,.12,.05,.12,.012);
        } else if (player.isInWater() || level.getFluidState(at.below()).is(FluidTags.WATER)) {
            if (Math.abs(player.getX()-player.xOld)+Math.abs(player.getZ()-player.zOld)>.012)
                emit(player,ParticleTypes.GLOW,player.getX(),player.getY()+.2,player.getZ(),range,4,.6,.04,.6,.015);
        } else if (level.isNight() && level.getBiome(at).is(ReefLife.REEF)) {
            emit(player, ParticleTypes.GLOW,x,player.getY()+.5,z,range,2,.4,.1,.4,.01);
        } else if (level.canSeeSky(at.above()) && tick%30==0) {
            emit(player,ParticleTypes.CHERRY_LEAVES,x,player.getY()+2,z,range,1,.1,.1,.1,.01);
        }
    }

    private static void emit(ServerPlayer p, ParticleOptions kind,double x,double y,double z,double max,
                             int count,double dx,double dy,double dz,double speed) {
        if (p.distanceToSqr(x,y,z)>max*max) return;
        ServerLevel level=p.serverLevel();
        if (level.sendParticles(p,kind,false,x,y,z,count,dx,dy,dz,speed)) SENT.merge(level,1L,Long::sum);
    }
}
