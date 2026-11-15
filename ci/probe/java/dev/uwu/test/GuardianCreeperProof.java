package dev.uwu.test;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.GuardianTargeting;
import dev.uwu.dimension.People;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Runs against real server entity ticks in an isolated world. The second and third
 * server processes check the saved gamerule, not a synthetic in-memory copy.
 */
final class GuardianCreeperProof {
    private static final Path STAGE=Path.of("guardian-creeper-stage.txt");
    private static final List<BlossomWarden> guardians=new ArrayList<>();
    private static final List<Creeper> creepers=new ArrayList<>();
    private static ServerPlayer witness;
    private static int stage;
    private static long deadline;

    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError("Guardian creeper: "+message);
    }

    private static void toggle(ServerLevel level,boolean enabled)throws Exception {
        var server=level.getServer();
        var source=server.createCommandSourceStack().withLevel(level).withPermission(4);
        int result=server.getCommands().getDispatcher().execute("uwuguardian creeperaggro "+enabled,source);
        check(result==(enabled?1:0)&&GuardianTargeting.creeperAggroOn(level)==enabled,"admin toggle command failed");
    }

    private static String diagnostic(ServerLevel level){
        var out=new StringBuilder();
        for(int i=0;i<creepers.size();i++){
            var c=creepers.get(i);var g=guardians.get(i);
            out.append("\\n case=").append(i).append(" creepAlive=").append(c.isAlive())
                .append(" removed=").append(c.isRemoved()).append(" ticks=").append(c.tickCount)
                .append(" creepPos=").append(c.position()).append(" target=").append(c.getTarget())
                .append(" guardAlive=").append(g.isAlive()).append(" guardRemoved=").append(g.isRemoved())
                .append(" guardPos=").append(g.position()).append(" distance=").append(c.distanceTo(g))
                .append(" lineOfSight=").append(c.getSensing().hasLineOfSight(g))
                .append(" canAttack=").append(c.canAttack(g)).append(" aggro=").append(GuardianTargeting.creeperAggroOn(level))
                .append(" targetGoals=").append(c.targetSelector.getAvailableGoals().stream()
                    .map(goal->goal.getGoal().getClass().getName()+":running="+goal.isRunning()).toList());
        }
        return out.toString();
    }

    static boolean tick(ServerLevel level)throws Exception {
        if(stage==0 && Files.exists(STAGE)){
            String value=Files.readString(STAGE).trim();
            if(value.equals("saved-on")){
                check(GuardianTargeting.creeperAggroOn(level),"explicit enabled preference was not saved");
                toggle(level,false);
                Files.writeString(STAGE,"saved-off");
                System.out.println("GUARDIAN CREEPER RESTART PASS saved true retained; changed to false");
                return true;
            }
            check(value.equals("saved-off"),"unexpected restart marker");
            check(!GuardianTargeting.creeperAggroOn(level),"explicit disabled preference was not saved");
            System.out.println("GUARDIAN CREEPER PERSISTENCE PASS saved true and false across actual server restarts");
            return true;
        }
        if(stage==0){
            check(!GuardianTargeting.creeperAggroOn(level),"new-world default must be off");
            BlockPos origin=new BlockPos(0,level.getMaxBuildHeight()-24,0);
            for(int cx=-1;cx<=3;cx++)for(int cz=-1;cz<=1;cz++)level.setChunkForced(cx,cz,true);
            for(int n=0;n<4;n++){
                BlockPos pos=origin.offset(10*n,0,0);
                for(int x=-3;x<=3;x++)for(int z=-3;z<=7;z++){
                    level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                    for(int y=0;y<5;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
                }
                BlossomWarden guardian=People.BLOSSOM_WARDEN.get().create(level);
                check(guardian!=null,"guardian creation");
                var tag=guardian.saveWithoutId(new CompoundTag());
                tag.putBoolean("FemaleGuardian",(n&1)!=0);
                tag.putBoolean("KeeperSummonedGuardian",true);
                if(n>=2){var family=tag.getCompound("OwlFamily");family.putBoolean("Offspring",true);family.putInt("GrowthTicks",1);tag.put("OwlFamily",family);}
                guardian.load(tag);
                guardian.setNoAi(true);guardian.setNoGravity(true);
                guardian.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
                check(level.addFreshEntity(guardian),"guardian addition");
                check(guardian.female()==((n&1)!=0)&&guardian.isBaby()==(n>=2),"guardian variant creation");
                Creeper creeper=EntityType.CREEPER.create(level);
                check(creeper!=null,"creeper creation");
                var creepTag=creeper.saveWithoutId(new CompoundTag());creepTag.putShort("Fuse",(short)32000);creeper.load(creepTag);
                creeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
                creeper.setNoGravity(true);
                creeper.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+3.25,180,0);
                check(level.addFreshEntity(creeper),"creeper addition");
                guardians.add(guardian);creepers.add(creeper);
            }
            witness=CounterProof.player(level,new GameProfile(UUID.fromString("c719e828-f310-44fa-95d5-41fef4a9da42"),"CreeperWitness"));
            witness.setGameMode(GameType.SPECTATOR);witness.setNoGravity(true);
            witness.moveTo(origin.getX()+.5,origin.getY()+10,origin.getZ()+55.5,0,0);
            level.addNewPlayer(witness);
            deadline=level.getGameTime()+100;stage=1;return false;
        }
        if(stage==1){
            for(var creeper:creepers)check(!(creeper.getTarget() instanceof BlossomWarden),"natural acquisition occurred while off");
            if(level.getGameTime()<deadline)return false;
            toggle(level,true);
            deadline=level.getGameTime()+350;stage=2;return false;
        }
        if(stage==2){
            boolean all=true;
            for(int i=0;i<4;i++)if(creepers.get(i).getTarget()!=guardians.get(i))all=false;
            if(!all){
                if(level.getGameTime()>=deadline)throw new AssertionError("Guardian creeper: enabled natural AI did not acquire each guardian variant"+diagnostic(level));
                return false;
            }
            // Wait for the actual swelling goal to start, then turn protection back on.
            if(creepers.stream().anyMatch(c->c.getSwellDir()<=0)){
                check(level.getGameTime()<deadline,"guardian-targeted creepers never began swelling");return false;
            }
            // Bring an actively swelling fuse to five ticks from detonation.
            // The next normal server ticks must defuse it, not allow an explosion.
            for(var creeper:creepers){
                creeper.causeFallDamage(32000F,0F,level.damageSources().fall());
                check(creeper.getSwelling(1F)>.99F,"could not prepare near-detonation fuse");
            }
            // SwellGoal retains its own cached target when Mob.target is cleared.
            // This tests the edge where that happens just before the rule changes.
            creepers.getFirst().setTarget(null);
            toggle(level,false);
            deadline=level.getGameTime()+12;stage=3;return false;
        }
        if(stage==3){
            if(level.getGameTime()<deadline)return false;
            for(var creeper:creepers){
                check(creeper.isAlive(),"guardian-targeted creeper detonated after disable");
                check(!(creeper.getTarget() instanceof BlossomWarden),"existing guardian target survived disable");
                check(creeper.getSwellDir()<0,"guardian swelling continued after disable");
            }
            for(int i=0;i<4;i++)creepers.get(i).setTarget(guardians.get(i));
            deadline=level.getGameTime()+70;stage=4;return false;
        }
        if(stage==4){
            for(var creeper:creepers)check(!(creeper.getTarget() instanceof BlossomWarden),"guardian reacquired while off");
            if(level.getGameTime()<deadline)return false;
            Creeper creeper=creepers.getFirst();
            witness.setGameMode(GameType.SURVIVAL);
            witness.moveTo(creeper.getX(),creeper.getY(),creeper.getZ()+2,180,0);
            deadline=level.getGameTime()+200;stage=5;return false;
        }
        if(stage==5){
            Creeper creeper=creepers.getFirst();
            if(creeper.getTarget()!=witness){
                check(level.getGameTime()<deadline,"ordinary player was not naturally targeted while guardian protection was enabled");
                return false;
            }
            creeper.setTarget(guardians.getFirst());
            check(creeper.getTarget()==witness,"blocked guardian retarget replaced an existing player target");
            creeper.ignite();
            deadline=level.getGameTime()+5;stage=6;return false;
        }
        if(stage==6){
            if(level.getGameTime()<deadline)return false;
            Creeper creeper=creepers.getFirst();
            check(creeper.isIgnited()&&creeper.getSwellDir()>0,"player-initiated ignition was cancelled");
            witness.discard();
            guardians.forEach(BlossomWarden::discard);
            creepers.forEach(Creeper::discard);
            toggle(level,true);
            Files.writeString(STAGE,"saved-on");
            System.out.println("GUARDIAN CREEPER AI PASS natural off/on, four variants, preexisting swell, reacquisition, player target and manual ignition");
            return true;
        }
        throw new AssertionError("Unknown guardian creeper stage "+stage);
    }
}
