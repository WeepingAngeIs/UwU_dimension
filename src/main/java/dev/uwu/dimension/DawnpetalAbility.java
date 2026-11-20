package dev.uwu.dimension;

import java.util.List;

/** Stable wire/slot IDs. Only the first four keys are contextual; unused slots never cast. */
public enum DawnpetalAbility {
    SPIN(0, 70), THROW(1, 100);

    public static final List<DawnpetalAbility> POPULATED = List.of(SPIN, THROW);
    public final int index, cooldown;
    DawnpetalAbility(int index, int cooldown) { this.index = index; this.cooldown = cooldown; }

    public static DawnpetalAbility at(int index) {
        return index >= 0 && index < POPULATED.size() ? POPULATED.get(index) : null;
    }
}
