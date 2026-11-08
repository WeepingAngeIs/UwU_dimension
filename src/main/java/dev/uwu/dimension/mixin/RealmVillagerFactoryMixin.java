package dev.uwu.dimension.mixin;
import dev.uwu.dimension.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityType.class)
public abstract class RealmVillagerFactoryMixin {
    @Inject(method="create(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),cancellable=true)
    private void residentFactory(Level level,CallbackInfoReturnable<Entity> callback){
        if((Object)this==EntityType.VILLAGER&&RealmResidents.applies(level)){
            Keeper keeper=People.PETAL_KEEPER.get().create(level);
            if(keeper!=null){keeper.markAdaptedResident();callback.setReturnValue(keeper);}
        }
    }
}
