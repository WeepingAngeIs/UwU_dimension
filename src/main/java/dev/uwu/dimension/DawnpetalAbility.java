package dev.uwu.dimension;

import java.util.List;
import net.minecraft.world.entity.player.Player;

/** Stable wire/slot IDs. Only the first four keys are contextual; unused slots never cast. */
public enum DawnpetalAbility {
    SPIN(0, 70), THROW(1, 100), HEART_SPIN(2, 120);

    public static final List<DawnpetalAbility> POPULATED = List.of(SPIN, THROW, HEART_SPIN);
    public final int index, cooldown;
    DawnpetalAbility(int index, int cooldown) { this.index = index; this.cooldown = cooldown; }

    public static DawnpetalAbility at(int index) {
        return index >= 0 && index < POPULATED.size() ? POPULATED.get(index) : null;
    }

    public static DawnpetalAbility at(Player player, int index) {
        var held = player.getMainHandItem();
        if (held.is(RevivalBlossoms.AXE.get())) return at(index);
        if (!held.is(RevivalBlossoms.DIRK.get())) return null;
        return index == THROW.index ? THROW : null;
    }
}
