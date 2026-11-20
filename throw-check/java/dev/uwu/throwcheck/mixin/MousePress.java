package dev.uwu.throwcheck.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MousePress {
    @Invoker("onPress")
    void throwCheck$press(long window, int button, int action, int modifiers);
}
