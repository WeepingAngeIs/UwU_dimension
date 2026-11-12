package dev.uwu.dimension.mixin;
import dev.uwu.dimension.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Mob.class)
public abstract class RealmCureMixin {
    @ModifyVariable(method="convertTo",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private EntityType<?> keeperCure(EntityType<?> destination){
        if((Object)this instanceof ZombieVillager zombie&&destination==EntityType.VILLAGER&&RealmResidents.applies(zombie.level()))
            return zombie.getType()==People.DUSK_ZOMBIE.get()
                    ||zombie.getPersistentData().getCompound(RealmResidents.ORIGIN).getBoolean("WasDusk")
                    ?People.DUSK_KEEPER.get():People.PETAL_KEEPER.get();
        return destination;
    }
}
