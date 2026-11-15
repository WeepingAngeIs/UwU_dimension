package dev.uwu.test;

import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.HeartArrow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

@EventBusSubscriber(modid="heartwater_probe")
public final class NetworkCupidServer {
    static final String SESSION="network-cupid";
    static final int FLOOR=112;
    private static boolean ready,entityHit,wallHit;

    static void tick(MinecraftServer server) {
        if (ready || server.getPlayerList().getPlayers().isEmpty()) return;
        var p=server.getPlayerList().getPlayers().getFirst();
        var level=server.overworld();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doMobSpawning false");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule doDaylightCycle false");
        level.setDayTime(6000);
        for (int x=-9;x<=9;x++) for(int z=-4;z<=16;z++) {
            level.setBlockAndUpdate(new BlockPos(x,FLOOR,z),Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            if(z==15) for(int y=1;y<=8;y++)
                level.setBlockAndUpdate(new BlockPos(x,FLOOR+y,z),Blocks.GRAY_CONCRETE.defaultBlockState());
        }
        var cow=EntityType.COW.create(level);
        cow.setNoAi(true);
        cow.setInvulnerable(true);
        cow.setCustomName(Component.literal("Cupid witness"));
        cow.moveTo(.5,FLOOR+1,10.5,180,0);
        if(!level.addFreshEntity(cow)) throw new AssertionError("Missing real arrow target");
        // Four ordinary tracked entities on a separate sightline. Spawn-packet evidence complements the actual feed/birth tests in Sakura.
        for(int i=0;i<4;i++) {
            var resident=dev.uwu.dimension.People.BLOSSOM_WARDEN.get().create(level);
            if(resident==null)throw new AssertionError("Missing Guardian type");
            var data=new net.minecraft.nbt.CompoundTag();
            resident.saveWithoutId(data);
            data.putBoolean("FemaleGuardian",(i & 1)!=0);
            if(i>=2){
                var family=new net.minecraft.nbt.CompoundTag();
                family.putBoolean("Offspring",true);
                family.putInt("GrowthTicks",24000);
                data.put("OwlFamily",family);
            }
            resident.load(data);
            resident.setNoAi(true);
            resident.setPersistenceRequired();
            resident.moveTo(-7.5,FLOOR+1,-2.4+1.85*i,-90,0);
            if(resident.isBaby()!=(i>=2) || !level.addFreshEntity(resident))throw new AssertionError("Guardian tracking setup "+i);
        }
        p.teleportTo(level,.5,FLOOR+1,.5,0,3);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get()));
        p.containerMenu.broadcastChanges();
        // Stagger the three or four in-flight flight cues on a software-rendered headless client.
        server.tickRateManager().setTickRate(10F);
        ready=true;
        System.out.println("NETWORK CUPID SERVER READY | connected "+p.getUUID());
    }

    @SubscribeEvent public static void impact(ProjectileImpactEvent event) {
        if (!SESSION.equals(System.getenv("UWU_VISIT")) || !(event.getProjectile() instanceof HeartArrow arrow)
                || !(arrow.getOwner() instanceof ServerPlayer)) return;
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity().getType()==EntityType.COW) {
            entityHit=true; System.out.println("NETWORK CUPID SERVER ENTITY HIT");
        }
        if (event.getRayTraceResult() instanceof BlockHitResult hit && arrow.level().getBlockState(hit.getBlockPos()).is(Blocks.GRAY_CONCRETE)) {
            wallHit=true; System.out.println("NETWORK CUPID SERVER WALL HIT");
        }
        if(entityHit && wallHit) System.out.println("NETWORK CUPID BOTH SERVER HITS PASS");
    }
}
