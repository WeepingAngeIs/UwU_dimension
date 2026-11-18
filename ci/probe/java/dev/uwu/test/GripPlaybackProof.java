package dev.uwu.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/** Reads the active Better Combat animation player after the attacker starts a real swing. */
final class GripPlaybackProof {
    private static Field attackStack;

    private static Field findAttackStack(Player actor) {
        for (Class<?> type = actor.getClass(); type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (field.getType().getName().equals("net.bettercombat.client.animation.AttackAnimationSubStack")) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new AssertionError("Missing Better Combat attack stack on " + actor.getClass());
    }

    private static List<?> armKeyframes(Object data) throws ReflectiveOperationException {
        var result = new ArrayList<>();
        var getPart = data.getClass().getMethod("getPart", String.class);
        for (String arm : List.of("rightArm", "leftArm")) {
            var part = getPart.invoke(data, arm);
            for (String axis : List.of("x", "y", "z", "pitch", "yaw", "roll", "bend", "bendDirection")) {
                var state = part.getClass().getField(axis).get(part);
                result.add(state.getClass().getMethod("getKeyFrames").invoke(state));
            }
        }
        return result;
    }

    static String inspect(Player actor, boolean expectSolo) throws ReflectiveOperationException {
        if (attackStack == null) attackStack = findAttackStack(actor);
        var stack = attackStack.get(actor);
        var base = stack.getClass().getField("base").get(stack);
        var playing = base.getClass().getMethod("getAnimation").invoke(base);
        if (playing == null || !(boolean)playing.getClass().getMethod("isActive").invoke(playing)) return null;
        var data = playing.getClass().getMethod("getData").invoke(playing);
        var registry = Class.forName("dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry");
        var lookup = registry.getMethod("getAnimation", ResourceLocation.class);
        var fingerprint = armKeyframes(data);
        var matches = new ArrayList<String>();
        for (String hands : List.of("one_handed", "two_handed")) {
            for (String direction : List.of("left", "right")) {
                var id = ResourceLocation.fromNamespaceAndPath("bettercombat", hands + "_slash_horizontal_" + direction);
                Object reference = lookup.invoke(null, id);
                if (reference == null) throw new AssertionError("Missing reference attack " + id);
                if (fingerprint.equals(armKeyframes(reference))) matches.add(id.getPath());
            }
        }
        String family = expectSolo ? "one_handed_" : "two_handed_";
        if (matches.size() != 1 || !matches.get(0).startsWith(family))
            throw new AssertionError("Vesper active arm keyframes match " + matches + ", expected " + family);
        var mirror = stack.getClass().getField("mirror").get(stack);
        boolean mirrored = (boolean)mirror.getClass().getMethod("isEnabled").invoke(mirror);
        if (mirrored != (actor.getMainArm() == HumanoidArm.LEFT) || !actor.getOffhandItem().isEmpty())
            throw new AssertionError("Vesper swing handedness " + actor.getMainArm() + ", mirrored=" + mirrored
                    + ", offhand=" + actor.getOffhandItem());
        return "bettercombat:" + matches.get(0) + " mirrored=" + mirrored + " offhand=empty";
    }
}
