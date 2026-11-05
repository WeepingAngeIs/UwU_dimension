package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.MirrorCourt;
import dev.uwu.dimension.VelvetVesper;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Driven once per actual dedicated-server tick. The clock and boss phases run normally. */
public final class BossProof {
    private static final UUID REED=UUID.fromString("2e50db39-8718-4be1-aadd-807982ef81a3");
    private static final UUID VALE=UUID.fromString("341f0330-e1a3-4c17-b014-9a729a30642a");
    private static Guest reed,vale;
    private static VelvetVesper moth;
    private static int ticks;
    private static boolean folding,peerDamage,blockedArrow,nearExpiry,expiry,secondGuard;
    private static long start=-1;
    private static boolean preparingRestart;
    private static Properties checkpoint;
    private static final Path CHECKPOINT=Path.of("mirror-checkpoint.properties");
    private static float healthBeforeBeam;
    private static final List<ChunkPos> forced=new ArrayList<>();

    private static final class Guest {
        final ServerPlayer player;
        final EmbeddedChannel wire;
        int barPackets;
        Guest(ServerLevel level,String name,UUID id,Vec3 at) {
            var profile=new GameProfile(id,name);
            player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
            var conn=new Connection(PacketFlow.SERVERBOUND);
            wire=new EmbeddedChannel(conn);
            new ServerGamePacketListenerImpl(level.getServer(),conn,player,CommonListenerCookie.createInitial(profile,false)) {
                @Override public void send(Packet<?> packet){if(packet instanceof ClientboundBossEventPacket)barPackets++;}
                @Override public void send(Packet<?> packet,PacketSendListener cb){send(packet);}
            };
            player.setPos(at);
            player.setNoGravity(true);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(180);
            player.setHealth(180);
            level.addNewPlayer(player);
        }
        void close(ServerLevel level) {
            level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
            wire.finishAndReleaseAll();
        }
    }

    public static boolean tick(ServerLevel level,BlockPos bell) {
        if (++ticks>1500) throw new AssertionError("Mirror Court stalled: beat="+(moth==null?-1:moth.beat())+" charm start="+start);
        var state=level.getBlockState(bell);
        require(state.is(MirrorCourt.BELL.get()),"Missing generated bell at "+bell+": "+state);
        var center=bell.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING),6);
        if(reed==null) {
            auditApproach(level,bell,state.getValue(BlockStateProperties.HORIZONTAL_FACING));
            var chunk=new ChunkPos(center);
            for(int x=chunk.x-1;x<=chunk.x+1;x++)for(int z=chunk.z-1;z<=chunk.z+1;z++)
                if(level.setChunkForced(x,z,true))forced.add(new ChunkPos(x,z));
            var floor=Vec3.atBottomCenterOf(center.below());
            reed=new Guest(level,"MirrorReed",REED,floor.add(0,0,-3.4));
            vale=new Guest(level,"MirrorVale",VALE,floor.add(3.5,0,0));
            healthBeforeBeam=reed.player.getHealth()+vale.player.getHealth();
            ring(level,bell,reed.player);
            ring(level,bell,vale.player);
            var boss=livingMoths(level,center);
            require(boss.size()==1,"Two bell uses must start exactly one encounter");
            moth=boss.getFirst();
            require(moth.getMaxHealth()==500 && moth.getHealth()==500,"Unexpected starting boss health");
            return false;
        }
        if(preparingRestart) {
            if (!moth.guarding()) return false;
            reed.player.attack(moth);
            require(moth.isCharmed(reed.player) && !moth.isCharmed(vale.player),"Restart specimen did not establish a private bond");
            var props=new Properties();
            props.setProperty("bell",Long.toString(bell.asLong()));
            props.setProperty("uuid",moth.getUUID().toString());
            props.setProperty("hp",Float.toString(moth.getHealth()));
            props.setProperty("beat",Integer.toString(moth.beat()));
            props.setProperty("until",Long.toString(level.getGameTime()+200));
            try(var out=Files.newOutputStream(CHECKPOINT)){props.store(out,"Independent expectations for the following Minecraft process");}
            catch(IOException e){throw new UncheckedIOException(e);}
            finish(level);
            return true;
        }
        if(moth.folding())folding=true;
        if(start<0 && moth.guarding()) {
            require(folding,"Guard did not telegraph");
            require(reed.barPackets>0 && vale.barPackets>0 && moth.encounterBar().getPlayers().containsAll(List.of(reed.player,vale.player)),"Co-op boss bar failed to sync");
            require(reed.player.getHealth()+vale.player.getHealth()<healthBeforeBeam,"Telegraphed beam never hit either stationary challenger");
            float hp=moth.getHealth();
            reed.player.attack(moth);
            require(hp==moth.getHealth() && moth.isCharmed(reed.player) && !moth.isCharmed(vale.player),"Guarded melee or individual bond failed");
            require(reed.player.getEffect(MirrorCourt.DEVOTION)!=null && reed.player.getEffect(MirrorCourt.DEVOTION).getDuration()==200,"Wrong devotion duration");
            start=level.getGameTime();
            var save=new CompoundTag();
            moth.saveWithoutId(save);
            var reloaded=MirrorCourt.VESPER.get().create(level);
            require(reloaded!=null,"No boss entity factory");
            reloaded.load(save);
            require(reloaded.getUUID().equals(moth.getUUID()) && reloaded.getHealth()==hp && reloaded.guarding()
                && reloaded.isCharmed(reed.player) && !reloaded.isCharmed(vale.player),"Saved boss lost encounter state");
            return false;
        }
        if(start<0) return false;
        long age=level.getGameTime()-start;
        if(!moth.guarding() && !peerDamage) {
            float hp=moth.getHealth();
            fire(level,reed.player,moth);
            require(hp==moth.getHealth() && moth.isCharmed(reed.player),"Charmed projectile hurt the open boss");
            blockedArrow=true;
            fire(level,vale.player,moth);
            require(moth.getHealth()<hp && !moth.isCharmed(vale.player),"Uncharmed teammate could not damage the open boss");
            require(Math.abs(moth.encounterBar().getProgress()-moth.getHealth()/500)<.0001,"Boss bar did not match actual health");
            peerDamage=true;
        }
        if(age==199) {
            float hp=moth.getHealth();
            reed.player.attack(moth);
            require(moth.isCharmed(reed.player) && moth.getHealth()==hp,"Devotion expired before tick 200");
            nearExpiry=true;
        }
        if(age==200) {
            require(!moth.guarding() && !moth.isCharmed(reed.player),"Devotion still blocked on tick 200");
            float hp=moth.getHealth();
            moth.invulnerableTime=0;
            reed.player.attack(moth);
            require(moth.getHealth()<hp,"Attacker could not hurt open moth at expiry");
            expiry=true;
        }
        if(expiry && !secondGuard && moth.guarding()) {
            float hp=moth.getHealth();
            fire(level,vale.player,moth);
            require(moth.getHealth()==hp && moth.isCharmed(vale.player) && !moth.isCharmed(reed.player),"Guarded projectile did not charm its owner independently");
            secondGuard=true;
        }
        if(secondGuard && !moth.guarding()) {
            require(peerDamage && blockedArrow && nearExpiry,"Missing co-op or timing coverage");
            moth.invulnerableTime=0;
            moth.hurt(level.damageSources().playerAttack(reed.player),800);
            require(!moth.isAlive(),"Defeated moth was still alive");
            ring(level,bell,vale.player);
            var again=livingMoths(level,center);
            require(again.size()==1 && !again.getFirst().getUUID().equals(moth.getUUID()) && again.getFirst().getHealth()==500,"Court did not restart cleanly");
            if ("152045".equals(System.getenv("UWU_FIELD_SEED"))) {
                moth=again.getFirst();
                moth.hurt(level.damageSources().playerAttack(vale.player),27);
                require(moth.getHealth()==473,"Restart specimen did not retain initial damage");
                preparingRestart=true;
                return false;
            }
            again.getFirst().discard();
            finish(level);
            return true;
        }
        return false;
    }
    private static void auditApproach(ServerLevel level,BlockPos bell,Direction forward) {
        Direction right=forward.getCounterClockWise();
        // From the existing archive walk to the bell, test both outside shoulders
        // and the centerline at full survival body height, including the turn.
        for(int lane=-1;lane<=1;lane++) {
            for(int dx=0;dx<=23;dx++)
                walkable(level,bell.relative(right,dx).relative(forward,-14+lane).below());
            for(int dz=-14;dz<=-2;dz++)
                walkable(level,bell.relative(right,lane).relative(forward,dz).below());
        }
    }
    private static void walkable(ServerLevel level,BlockPos feet) {
        var p=Vec3.atBottomCenterOf(feet);
        require(level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP),"Missing court path floor at "+feet);
        require(!level.getBlockCollisions(null,new AABB(p.x-.29,p.y+.001,p.z-.29,p.x+.29,p.y+1.81,p.z+.29)).iterator().hasNext(),
                "Court path obstructed at "+feet);
    }
    private static void finish(ServerLevel level) {
        reed.close(level);vale.close(level);
        for(ChunkPos ch:forced)level.setChunkForced(ch.x,ch.z,false);
        System.out.println("MIRROR COURT PASS | generated bell; distinct survival UUIDs; both boss bar packet streams; 500 HP, telegraphed beam and guard; actual melee and arrow collisions; personal devotion blocked tick 199, allowed tick 200; NBT restore and repeat after defeat");
    }

    /** After the first process has saved and stopped, locate its real entity on disk. */
    public static boolean verifyRestart(ServerLevel level) {
        if(++ticks>150)throw new AssertionError("Saved court did not load in time");
        if(checkpoint==null) {
            checkpoint=new Properties();
            try(var in=Files.newInputStream(CHECKPOINT)){checkpoint.load(in);}
            catch(IOException e){throw new UncheckedIOException(e);}
        }
        BlockPos bell=BlockPos.of(Long.parseLong(checkpoint.getProperty("bell")));
        var state=level.getBlockState(bell);
        require(state.is(MirrorCourt.BELL.get()),"Saved arena bell disappeared");
        BlockPos center=bell.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING),6);
        if(ticks==1) {
            var ch=new ChunkPos(center);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                if(level.setChunkForced(ch.x+dx,ch.z+dz,true))forced.add(new ChunkPos(ch.x+dx,ch.z+dz));
            }
            level.getChunkAt(center);
        }
        var bosses=livingMoths(level,center);
        if(bosses.isEmpty())return false;
        require(bosses.size()==1,"Saved fight duplicated during startup");
        var saved=bosses.getFirst();
        require(saved.getUUID().toString().equals(checkpoint.getProperty("uuid"))
            && saved.getHealth()==Float.parseFloat(checkpoint.getProperty("hp"))
            && saved.beat()==Integer.parseInt(checkpoint.getProperty("beat")) && saved.guarding(),"Restored moth lost HP, identity or guard phase");
        var floor=Vec3.atBottomCenterOf(center.below());
        reed=new Guest(level,"MirrorReed",REED,floor.add(0,0,-3.4));
        vale=new Guest(level,"MirrorVale",VALE,floor.add(3.5,0,0));
        require(saved.isCharmed(reed.player) && !saved.isCharmed(vale.player),"Restart did not restore the correct player's active devotion");
        var tag=saved.saveWithoutId(new CompoundTag());
        var bonds=tag.getList("MirrorBonds",net.minecraft.nbt.Tag.TAG_COMPOUND);
        require(bonds.size()==1 && bonds.getCompound(0).getLong("Until")==Long.parseLong(checkpoint.getProperty("until")),
                "Restart altered absolute expiry");
        ring(level,bell,vale.player);
        require(livingMoths(level,center).size()==1,"Restart lost bell occupancy");
        saved.discard();
        reed.close(level);vale.close(level);
        for(ChunkPos ch:forced)level.setChunkForced(ch.x,ch.z,false);
        System.out.println("MIRROR COURT DISK RESTART PASS | new Minecraft process loaded the same UUID at 473/500 HP in guard, exact per-player expiry and occupied bell");
        return true;
    }

    private static void fire(ServerLevel level,ServerPlayer player,VelvetVesper boss) {
        // Send an ordinary, unenchanted arrow along the line from the archer to the moth.
        var shot=new Arrow(level,player,new ItemStack(Items.ARROW),null);
        var end=boss.getBoundingBox().getCenter();
        var direction=end.subtract(shot.position()).normalize();
        shot.shoot(direction.x,direction.y,direction.z,2.3F,0);
        level.addFreshEntity(shot);
        for(int i=0;i<4 && shot.isAlive();i++)shot.tick();
        if (shot.isAlive())shot.discard();
    }
    private static List<VelvetVesper> livingMoths(ServerLevel level,BlockPos p) {
        return level.getEntitiesOfClass(VelvetVesper.class,new AABB(p).inflate(12),Entity::isAlive);
    }
    private static void ring(ServerLevel level,BlockPos pos,ServerPlayer player) {
        level.getBlockState(pos).useWithoutItem(level,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }
    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
