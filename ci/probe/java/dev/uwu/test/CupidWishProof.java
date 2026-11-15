package dev.uwu.test;

import dev.uwu.dimension.*;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;

final class CupidWishProof {
    private static final String OWNER="uwudimension.charm_owner", REST="uwudimension.wish_rest_until";
    private final ServerLevel level;
    private final ServerPlayer archer,visitor;
    private final BlockPos origin;
    private final Method select,advance;
    private CupidWishProof(ServerLevel l,BlockPos o,ServerPlayer a,ServerPlayer b) throws Exception {
        level=l;origin=o;archer=a;visitor=b;
        var logic=Class.forName("dev.uwu.dimension.CupidWishes");
        select=logic.getDeclaredMethod("select",ServerPlayer.class,LivingEntity.class);
        advance=logic.getDeclaredMethod("advance",ServerPlayer.class,long.class);
        select.setAccessible(true);advance.setAccessible(true);
    }
    static void run(ServerLevel l) throws Exception {
        BlockPos o=new BlockPos(85,l.getMaxBuildHeight()-9,85);
        for(int x=-2;x<=5;x++)for(int z=-2;z<=5;z++)
            l.setBlockAndUpdate(o.offset(x,-1,z),Blocks.STONE.defaultBlockState());
        try(var one=new ChestProof.Visitor(l,o,"Clover",UUID.randomUUID());var two=new ChestProof.Visitor(l,o,"Briar",UUID.randomUUID())){
            l.addNewPlayer(one.player);l.addNewPlayer(two.player);
            try {new CupidWishProof(l,o,one.player,two.player).exercise();}
            finally{one.player.discard();two.player.discard();}
        }
        System.out.println("CUPID WISH PASS same/mixed species, fallback, no factory, both witness orders, claims, cooldown, cancellation, race, expiry and disappearing targets");
    }
    private <T extends Mob> T mob(EntityType<T> kind,int x) {
        var m=kind.create(level);
        if(m==null)throw new AssertionError(kind);
        m.moveTo(origin.getX()+.5+x,origin.getY(),origin.getZ()+.5,0,0);
        m.setNoAi(true); m.setNoGravity(true);m.setPersistenceRequired();level.addFreshEntity(m);charm(m);return m;
    }
    private void charm(LivingEntity e) {
        var d=e.getPersistentData();d.putBoolean("uwu.pastel_cupid",true);
        d.putUUID(OWNER,archer.getUUID());d.putLong("uwudimension.charm_end",level.getGameTime()+800);e.addTag(OWNER);
    }
    private void click(ServerPlayer p,LivingEntity e)throws Exception{select.invoke(null,p,e);}
    private void settle()throws Exception{advance.invoke(null,archer,level.getGameTime()+20);}
    private void pair(LivingEntity a,LivingEntity b)throws Exception{click(archer,a);click(archer,b);}
    private long babies(){return level.getEntitiesOfClass(AgeableMob.class,new AABB(origin).inflate(7),AgeableMob::isBaby).size();}
    private void check(boolean pass,String what){if(!pass)throw new AssertionError("Cupid wish "+what);}
    private void fresh(){archer.getPersistentData().remove("uwudimension.wish_archer_until");}
    private void exercise()throws Exception{
        archer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get()));
        var cow=mob(EntityType.COW,1);var pig=mob(EntityType.PIG,2);
        pair(cow,pig);click(visitor,pig);advance.invoke(null,archer,level.getGameTime()+2);
        check(babies()==0,"appeared before hearts");
        settle();check(babies()==1 && cow.getAge()==6000 && pig.getAge()==6000,"mixed species / parental rest");
        pair(cow,pig);settle();check(babies()==1,"duplicate while resting");fresh();
        var cow2=mob(EntityType.COW,2);var cow3=mob(EntityType.COW,3);
        pair(cow2,cow3);settle();check(babies()==2,"same species");fresh();
        var golem=mob(EntityType.IRON_GOLEM,0);var sheep=mob(EntityType.SHEEP,1);
        pair(golem,sheep);settle();check(babies()==3 && sheep.getAge()==6000,"only one factory");fresh();
        var modded=mob(People.BLOSSOM_WARDEN.get(),2);var iron=mob(EntityType.IRON_GOLEM,1);
        pair(modded,iron);settle();check(babies()==3 && !modded.getPersistentData().contains(REST),"no valid factory");
        var young=mob(EntityType.PIG,1);young.setBaby(true);
        var mature=mob(EntityType.COW,2);pair(young,mature);settle();click(archer,null);
        check(babies()==4 && mature.getAge()==0,"juvenile selected");
        var wolf=mob(EntityType.WOLF,1);wolf.tame(visitor);pair(wolf,mature);settle();click(archer,null);
        check(babies()==4 && wolf.isOwnedBy(visitor),"unowned pet");
        Consumer<CupidPairEvent> deny=e->e.setCanceled(true);NeoForge.EVENT_BUS.addListener(deny);
        try {pair(iron,mature);settle();click(archer,null);check(babies()==4,"claim veto");}
        finally {NeoForge.EVENT_BUS.unregister(deny);}
        if(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")) {
            Class<?> pac=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object api=pac.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,level.getServer());
            Method getter=pac.getMethod("getServerClaimsManager");
            Object claims=getter.invoke(api);Class<?> methods=getter.getReturnType();
            var loc=level.dimension().location();int cx=origin.getX()>>4,cz=origin.getZ()>>4;
            methods.getMethod("claim",loc.getClass(),UUID.class,int.class,int.class,int.class,boolean.class)
                .invoke(claims,loc,visitor.getUUID(),-1,cx,cz,false);
            try { pair(iron,mature);settle();click(archer,null);check(babies()==4,"real OPAC protected chunk"); }
            finally { methods.getMethod("unclaim",loc.getClass(),int.class,int.class).invoke(claims,loc,cx,cz); }
            System.out.println("CUPID OPAC WISH PASS another player's claimed chunk");
        }
        pair(iron,mature);click(archer,null);settle();check(babies()==4,"cancel after second");
        click(archer,iron);advance.invoke(null,archer,level.getGameTime()+201);click(archer,mature);settle();
        check(babies()==4,"expired first selection");click(archer,null);
        var lost=mob(EntityType.SHEEP,0);pair(lost,mature);lost.discard();settle();check(babies()==4,"despawn during cue");
        var rival=mob(EntityType.SHEEP,0);pair(rival,mature);rival.getPersistentData().putUUID(OWNER,visitor.getUUID());
        settle();check(babies()==4,"charm ownership changed during cue");
        charm(visitor);pair(visitor,mature);settle();click(archer,null);check(babies()==4,"witness opted out");
        var prefs=new CompoundTag();prefs.putInt("cupid_witness",1);visitor.getPersistentData().put(Player.PERSISTED_NBT_TAG,prefs);
        pair(visitor,mature);settle();check(babies()==5 && mature.getAge()==6000,"player then creature");fresh();
        visitor.getPersistentData().remove(REST);
        var last=mob(EntityType.SHEEP,0);pair(last,visitor);settle();check(babies()==6 && last.getAge()==6000,"creature then player");
        for(var e:level.getEntitiesOfClass(Mob.class,new AABB(origin).inflate(7))) e.discard();
    }
}

