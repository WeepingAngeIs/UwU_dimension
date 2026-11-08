package dev.uwu.test;
import dev.uwu.dimension.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.sweenus.simplyswords.api.AwakeningApi;
import net.sweenus.simplyswords.item.interfaces.RevivalWeapon;
import net.sweenus.simplyswords.world.RevivalCooldownManager;
/** Real lethal damage immediately before/after the mark deadline, with an already-expired cooldown. */
public final class RevivalProof {
    private record Sample(ServerPlayer actor,ItemStack stack,boolean late){}
    private static final List<Sample> samples=new ArrayList<>();
    private static long started;
    private static boolean checkedLate,done;
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void begin(ServerLevel level,ServerPlayer attacker){
        started=level.getGameTime();int i=0;
        for(String id:List.of("waxweaver","wickpiercer"))for(boolean late:new boolean[]{true,false}){
            var p=CounterProof.player(level,new GameProfile(UUID.randomUUID(),"SealGuest"+(i++)));
            p.moveTo(3.5,level.getMaxBuildHeight()-23,12+i*2,0,0);p.setNoGravity(true);level.addNewPlayer(p);
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);
            var stack=AwakeningApi.initializeFullyAwakened(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("simplyswords:"+id))));
            p.setItemInHand(InteractionHand.MAIN_HAND,stack);
            require(((RevivalWeapon)stack.getItem()).canRevive(p,stack,p.damageSources().generic()),"Awakened baseline revival unavailable: "+id);
            CounterEffects.hit(level,attacker,p,CounterArsenal.entry("counter_"+id));
            RevivalCooldownManager.setCooldown(level,p,stack,0);p.getCooldowns().removeCooldown(stack.getItem());
            samples.add(new Sample(p,stack,late));
        }
    }
    static boolean tick(ServerLevel level){
        if(done)return true;
        long age=level.getGameTime()-started;
        if(age==159){
            for(var sample:samples)if(sample.late()){
                var p=sample.actor();require(!RevivalCooldownManager.isCoolingDown(level,p,sample.stack()),"Shortened cooldown did not expire");
                require(!((RevivalWeapon)sample.stack().getItem()).canRevive(p,sample.stack(),p.damageSources().generic()),"Late mark lost revival seal");
                p.invulnerableTime=0;p.hurt(p.damageSources().generic(),2000);
                require(!p.isAlive(),"Sealed lethal hit revived at tick159");
            }
            checkedLate=true;
        }
        if(age>=160){
            require(checkedLate,"Missed late-seal tick");
            for(var sample:samples)if(!sample.late()){
                var p=sample.actor();require(((RevivalWeapon)sample.stack().getItem()).canRevive(p,sample.stack(),p.damageSources().generic()),"Expired seal still blocked revival");
                p.invulnerableTime=0;p.hurt(p.damageSources().generic(),2000);
                require(p.isAlive()&&p.getHealth()>0,"Unsealed ready weapon did not revive after lethal hit");
            }
            samples.forEach(s->s.actor().discard());done=true;
            System.out.println("REVIVAL DEADLINE PASS awakened Waxweaver/Wickpiercer: expired cooldown cannot revive at159; native lethal revival works at160");
        }
        return done;
    }
}
