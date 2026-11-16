package dev.uwu.test.mixin;

import dev.uwu.test.RelicPortraits;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Camera-only flight photo: render from beside the real flying projectile at the frame's interpolation. */
@Mixin(Camera.class)
public abstract class CupidCameraMixin {
    @Invoker("setPosition") protected abstract void uwu$position(Vec3 point);
    @Invoker("setRotation") protected abstract void uwu$rotation(float yaw,float pitch);
    @Inject(method="setup",at=@At("RETURN"))
    private void photo(BlockGetter level,Entity viewer,boolean third,boolean reversed,float partial,CallbackInfo ci) {
        var lens=dev.uwu.test.LauncherCamera.lens(partial);
        if(lens==null)lens=RelicPortraits.flightLens(partial);
        if(lens==null)return;
        var d=lens.aim().subtract(lens.eye());
        uwu$position(lens.eye());
        uwu$rotation((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
    }
}
