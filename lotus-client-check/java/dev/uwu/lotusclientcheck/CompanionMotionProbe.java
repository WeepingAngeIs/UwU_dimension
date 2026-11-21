package dev.uwu.lotusclientcheck;

import net.minecraft.world.phys.Vec3;

/** Exercises the same move samples and interpolated coordinates used by all companion effects. */
final class CompanionMotionProbe {
    static void verify() throws ReflectiveOperationException {
        Class<?> type = Class.forName("dev.uwu.dimension.RevivalBlossomMotion");
        var constructor = type.getDeclaredConstructor(Vec3.class, int.class);
        var follow = type.getDeclaredMethod("follow", Vec3.class, int.class, float.class);
        var sample = type.getDeclaredMethod("sample", float.class);
        var reset = type.getDeclaredMethod("reset", Vec3.class, int.class, int.class);
        constructor.setAccessible(true);
        follow.setAccessible(true);
        sample.setAccessible(true);
        reset.setAccessible(true);

        var motion = constructor.newInstance(Vec3.ZERO, 0);
        follow.invoke(motion, new Vec3(.20, 0, 0), 1, 1F);
        double first = ((Vec3) sample.invoke(motion, 1F)).x;
        require(first > 0 && first < .20, "first step must leave a trailing distance");
        follow.invoke(motion, new Vec3(.40, 0, 0), 2, 1F);
        Vec3 second = (Vec3) sample.invoke(motion, 1F);
        require(second.x - first > first && second.x < .40, "companion must accelerate toward a moving owner");
        require(near((Vec3) sample.invoke(motion, .5F), new Vec3(first, 0, 0).lerp(second, .5)), "render interpolation");
        follow.invoke(motion, new Vec3(.40, 0, 0), 2, 1F);
        require(near((Vec3) sample.invoke(motion, 1F), second), "multiple consumers advanced the same tick");

        for (int tick = 3; tick <= 10; tick++) {
            follow.invoke(motion, new Vec3(tick * .20, 0, 0), tick, 1F);
        }
        double atStop = ((Vec3) sample.invoke(motion, 1F)).x;
        Vec3 rest = new Vec3(2, 0, 0);
        follow.invoke(motion, rest, 11, 1F);
        double coasting = ((Vec3) sample.invoke(motion, 1F)).x;
        require(atStop < coasting && coasting < rest.x, "companion should coast when the player stops");
        for (int tick = 12; tick <= 64; tick++) follow.invoke(motion, rest, tick, 1F);
        require(near((Vec3) sample.invoke(motion, 1F), rest), "companion did not settle at its resting offset");

        for (int tick = 65; tick <= 75; tick++) {
            Vec3 owner = rest.add((tick - 64) * 1.10, (tick - 64) * .55, 0);
            follow.invoke(motion, owner, tick, 1F);
            Vec3 lag = ((Vec3) sample.invoke(motion, 1F)).subtract(owner);
            require(lag.horizontalDistance() <= .850001 && Math.abs(lag.y) <= .160001, "follow lag exceeded limits");
        }
        Vec3 destination = new Vec3(400, 90, -220);
        follow.invoke(motion, destination, 76, 1F);
        require(near((Vec3) sample.invoke(motion, 0F), destination)
                && near((Vec3) sample.invoke(motion, 1F), destination), "discontinuous movement left old render history");
        Vec3 shortTeleport = destination.add(.25, 0, 0);
        reset.invoke(motion, shortTeleport, 76, 0);
        require(near((Vec3) sample.invoke(motion, .25F), shortTeleport), "explicit position correction did not clear interpolation");

        reset.invoke(motion, shortTeleport, 76, 3);
        for (int tick = 77; tick <= 79; tick++) {
            Vec3 owner = shortTeleport.add((tick - 76) * 2, 0, 0);
            follow.invoke(motion, owner, tick, 1F);
            require(near((Vec3) sample.invoke(motion, 1F), owner), "remote relocation was damped a second time");
        }
        follow.invoke(motion, shortTeleport.add(6.2, 0, 0), 80, 1F);
        require(((Vec3) sample.invoke(motion, 1F)).x < shortTeleport.x + 6.2, "momentum did not resume after relocation");
        System.out.println("LOTUS COMPANION MOMENTUM PASS trailing, acceleration, coast, settle, interpolation and relocation");
    }

    private static boolean near(Vec3 actual, Vec3 expected) { return actual.distanceToSqr(expected) < 1e-10; }

    private static void require(boolean valid, String problem) {
        if (!valid) throw new AssertionError(problem);
    }

    private CompanionMotionProbe() {}
}
