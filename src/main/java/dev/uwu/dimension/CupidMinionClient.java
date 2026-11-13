package dev.uwu.dimension;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Drives a charmed local player toward the spot the server chose, around walls and away from ledges.
 * The server decides whom to follow and whom to hit; this only handles the walking.
 */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class CupidMinionClient {
    private static final String CONTROL = "uwudimension.cupid_control";
    private static final double BODY_OFFSET = .4;      // aim at the target's body, not its feet
    private static final double COMBAT_STOP_MAX = 2.5; // a stop closer than this is a foe, which needs a clear line of sight
    private static final double LOOK_AHEAD = 1.0;      // how far ahead the obstacle probe reaches
    private static final double MAX_STEP = 1.05;       // taller than a jump can clear counts as a wall
    private static final double MAX_DROP = 3.0;        // falls up to 3 blocks cost no health
    private static final int DETOUR_TICKS = 12;        // keep a sidestep this long before re-checking the straight path
    private static final int STUCK_TICKS = 16;         // no progress this long flips the detour side
    private static final int OPEN = 0, STEP = 1, BLOCKED = 2;

    // Steering memory. Only the local player is ever steered, so one set of fields is enough.
    private static boolean arrived;
    private static int side = 1;
    private static double detourOffset;
    private static int detourTicks;
    private static int stuckTicks;
    private static double lastX, lastZ;

    @SubscribeEvent
    public static void steer(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) return;
        var data = player.getPersistentData();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        double progress = Math.hypot(x - lastX, z - lastZ);
        lastX = x;
        lastZ = z;
        if (!data.contains(CONTROL)) {
            arrived = false;
            detourTicks = 0;
            stuckTicks = 0;
            return;
        }

        var control = data.getCompound(CONTROL);
        Level level = player.level();
        double tx = control.getDouble("X"), ty = control.getDouble("Y"), tz = control.getDouble("Z");
        double stop = control.getDouble("Stop");
        double dx = tx - x, dy = ty + BODY_OFFSET - y, dz = tz - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Stop at the stop distance, and restart only once clearly out of range so the minion doesn't stutter.
        // A foe also needs a clear shot before the minion counts as arrived.
        boolean sighted = stop >= COMBAT_STOP_MAX || sightLine(level, player, tx, ty, tz);
        if (distance <= stop && sighted) arrived = true;
        else if (distance > stop + .75 || !sighted) arrived = false;
        if (arrived) detourTicks = 0;

        double toTarget = Math.atan2(dz, dx);
        double offset = 0;
        boolean boxedIn = false;
        if (!arrived) {
            if (detourTicks > 0 && probe(level, x, y, z, toTarget + detourOffset) != BLOCKED) {
                offset = detourOffset;
                detourTicks--;
            } else {
                double pick = detour(level, x, y, z, toTarget);
                if (Double.isNaN(pick)) {
                    boxedIn = true;
                    detourTicks = 0;
                } else {
                    offset = pick;
                    detourOffset = pick;
                    detourTicks = pick == 0 ? 0 : DETOUR_TICKS;
                    if (pick != 0) side = pick > 0 ? 1 : -1;
                }
            }
        }

        boolean walk = !arrived && !boxedIn;
        double heading = toTarget + offset;
        boolean jump = walk && player.onGround() && (probe(level, x, y, z, heading) == STEP || dy > .8);

        // Trying to walk without getting anywhere: flip the detour side and hop.
        if (!arrived && progress < .02) {
            if (++stuckTicks > STUCK_TICKS) {
                stuckTicks = 0;
                side = -side;
                detourTicks = 0;
                jump = player.onGround();
            }
        } else {
            stuckTicks = 0;
        }

        if (player.isInWater()) jump = dy > -1;
        if (isLava(level, player.blockPosition())) jump = true;

        float yaw = (float) Math.toDegrees(arrived ? toTarget : heading) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, Math.max(.001, horizontal)) * (180.0 / Math.PI)));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.setXRot(pitch);

        var input = event.getInput();
        input.forwardImpulse = walk ? 1.0F : 0.0F;
        input.leftImpulse = 0.0F;
        input.shiftKeyDown = false;
        input.jumping = jump;
    }

    /** Classifies the path one block ahead at this heading: open, a step it can hop, or blocked (wall, drop, lava). */
    private static int probe(Level level, double x, double y, double z, double angle) {
        double px = x + Math.cos(angle) * LOOK_AHEAD;
        double pz = z + Math.sin(angle) * LOOK_AHEAD;
        BlockPos feet = cell(px, y + .5, pz);
        BlockPos waist = cell(px, y + 1.5, pz);
        BlockPos head = cell(px, y + 2.5, pz);
        if (isLava(level, feet) || isLava(level, waist)) return BLOCKED;
        boolean footSolid = isSolid(level, feet);
        boolean waistSolid = isSolid(level, waist);
        if (!footSolid && !waistSolid) return hasFooting(level, px, y, pz) ? OPEN : BLOCKED;
        if (footSolid && !waistSolid && !isSolid(level, head) && top(level, feet) <= MAX_STEP) return STEP;
        return BLOCKED;
    }

    /** Solid ground or water within MAX_DROP blocks below the probe column. Lava below means no. */
    private static boolean hasFooting(Level level, double x, double y, double z) {
        for (int drop = 0; drop <= MAX_DROP; drop++) {
            BlockPos below = cell(x, y - .5 - drop, z);
            if (isLava(level, below)) return false;
            if (isSolid(level, below) || !level.getFluidState(below).isEmpty()) return true;
        }
        return false;
    }

    /** 0 if the straight path is clear, otherwise the first clear sidestep, or NaN if every direction is blocked. */
    private static double detour(Level level, double x, double y, double z, double toTarget) {
        if (probe(level, x, y, z, toTarget) != BLOCKED) return 0;
        for (double degrees : new double[] {45, 90, 135}) {
            double turn = Math.toRadians(degrees) * side;
            if (probe(level, x, y, z, toTarget + turn) != BLOCKED) return turn;
            if (probe(level, x, y, z, toTarget - turn) != BLOCKED) return -turn;
        }
        return Double.NaN;
    }

    /** True when nothing solid sits between the player's eyes and the target's chest. */
    private static boolean sightLine(Level level, LocalPlayer player, double tx, double ty, double tz) {
        Vec3 aim = new Vec3(tx, ty + 1.2, tz);
        BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), aim,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static BlockPos cell(double x, double y, double z) {
        return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    private static boolean isSolid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Height of the top of a block's collision shape; 0 for air. */
    private static double top(Level level, BlockPos pos) {
        var shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return shape.isEmpty() ? 0 : shape.bounds().maxY;
    }

    private static boolean isLava(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.LAVA);
    }

    @SubscribeEvent
    public static void blockManualActions(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.getPersistentData().contains(CONTROL)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (event.isAttack() && player.getMainHandItem().is(Arsenal.WEAPONS.get(Arsenal.Relic.CUPID_BOW).get())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            PacketDistributor.sendToServer(new CupidMinions.Designate());
        }
    }
}
