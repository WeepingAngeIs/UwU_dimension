package dev.uwu.lotusclientcheck;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.client.RevivalHandPose;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Measures the production item renderer while the integrated server owns a real 60-second deadline. */
public final class DirkCountdownProbe {
    private enum Step { BASELINE, COUNTDOWN, AWAY, RETURNING, EXPIRED }

    private static DirkCountdownProbe active;
    private final Consumer<Consumer<ServerPlayer>> onServer;
    private final Set<BakedModel> models = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Long> packets = new HashSet<>();
    private final Set<Long> drawnSeconds = new HashSet<>();
    private final Set<Integer> phases = new HashSet<>();
    private Step step = Step.BASELINE;
    private volatile long started = -1;
    private volatile long serverRemainingAtExpiry = -1;
    private volatile UUID itemId;
    private int entered = -1;
    private final int sessionStart;
    private ClientboundContainerSetSlotPacket deferred;
    private long handSerial;
    private long deferredSerial;
    private long replaySerial = -1;
    private Matrix4f previousRestDraw;
    private Matrix4f preReplayDraw;
    private float replayPoseDelta = Float.NaN;
    private Boolean restingBefore;
    private Boolean restingBetween;
    private Boolean restingAfter;
    private boolean lethalRequested;
    private boolean slotChanged;
    private boolean expiryPacket;
    private float baselineHeight = Float.NaN;
    private float height;
    private float previousHeight;
    private ItemStack nativeHeld = ItemStack.EMPTY;
    private Matrix4f baselinePose;
    private float smallestIdleHeight = 1F;
    private float observedSlotDrop;
    private float poseDelta;
    private float transformDelta;
    private boolean activeReequip;
    private int idleDraws;
    private int expiryDraws;
    private Vector3f baseRotation;
    private Vector3f baseTranslation;
    private Vector3f baseScale;

    DirkCountdownProbe(Minecraft client, Consumer<Consumer<ServerPlayer>> onServer) {
        this.onServer = onServer;
        active = this;
        sessionStart = client.player.tickCount;
        select(client, 0);
        client.options.bobView().set(false);
        onServer.accept(actor -> {
            actor.getInventory().setItem(0, new ItemStack(RevivalBlossoms.DIRK.get()));
            actor.getInventory().setItem(1, new ItemStack(Items.STICK));
        });
    }

    boolean tick(Minecraft client) {
        int now = client.player.tickCount;
        require(now - sessionStart < RevivalBlossoms.COOLDOWN_TICKS + 240, "Dirk fixture timed out in " + step);
        if (step == Step.BASELINE) {
            var stack = client.player.getMainHandItem();
            if (!isDirk(stack) || identity(stack) == null || height < .995F || baselinePose == null) return false;
            if (entered < 0) entered = now;
            if (now - entered < 20 || lethalRequested) return false;
            lethalRequested = true;
            itemId = identity(stack);
            baselineHeight = height;
            onServer.accept(actor -> {
                ItemStack serverDirk = actor.getMainHandItem();
                require(isDirk(serverDirk) && itemId.equals(identity(serverDirk)), "Server held a different Dirk");
                require(RevivalBlossoms.remaining(actor, serverDirk) == 0, "Baseline Dirk already on cooldown");
                actor.setHealth(1);
                actor.invulnerableTime = 0;
                actor.hurt(actor.damageSources().generic(), 8);
                require(actor.isAlive() && actor.getHealth() == actor.getMaxHealth(), "Real lethal hit did not revive");
                require(RevivalBlossoms.remaining(actor, serverDirk) == RevivalBlossoms.COOLDOWN_TICKS,
                        "Revival did not create the real server deadline");
                started = actor.serverLevel().getServer().overworld().getGameTime();
            });
            step = Step.COUNTDOWN;
            return false;
        }
        if (started < 0) return false;
        long age = client.level.getGameTime() - started;
        switch (step) {
            case COUNTDOWN -> {
                if (!slotChanged && age >= 480) {
                    slotChanged = true;
                    step = Step.AWAY;
                    entered = now;
                    select(client, 1);
                } else if (age >= RevivalBlossoms.COOLDOWN_TICKS && remaining(client.player.getMainHandItem()) == 0) {
                    require(expiryPacket, "No actual zero-time inventory packet at expiry");
                    step = Step.EXPIRED;
                    entered = now;
                    onServer.accept(actor -> {
                        require(itemId.equals(identity(actor.getMainHandItem())), "Slot control replaced the Dirk");
                        serverRemainingAtExpiry = RevivalBlossoms.remaining(actor, actor.getMainHandItem());
                    });
                }
            }
            case AWAY -> {
                if (now - entered < 12) return false;
                require(client.player.getMainHandItem().is(Items.STICK), "Carried-slot control did not select the stick");
                require(observedSlotDrop >= .25F, "Observer missed the intentional native re-equip animation");
                step = Step.RETURNING;
                entered = now;
                select(client, 0);
            }
            case RETURNING -> {
                if (now - entered < 16) return false;
                require(itemId.equals(identity(client.player.getMainHandItem())) && height >= .995F,
                        "Returning to the original slot did not restore the same item and idle pose");
                step = Step.COUNTDOWN;
            }
            case EXPIRED -> {
                if (now - entered < 28) return false;
                require(serverRemainingAtExpiry == 0 && expiryDraws >= 10 && idleDraws > 200
                                && packets.size() >= 56 && drawnSeconds.size() >= 54
                                && phases.containsAll(Set.of(0, 1, 2, 3, 4, 5))
                                && Boolean.TRUE.equals(restingBefore) && Boolean.TRUE.equals(restingAfter)
                                && restingBetween != null && Float.isFinite(replayPoseDelta),
                        "Incomplete witness coverage. packets=" + packets.size() + " drawn=" + drawnSeconds.size()
                                + " phases=" + phases + " idleDraws=" + idleDraws + " expiryDraws=" + expiryDraws
                                + " serverRemaining=" + serverRemainingAtExpiry
                                + " replayGrip=" + restingBefore + " -> " + restingBetween + " -> " + restingAfter
                                + " replayPoseDelta=" + replayPoseDelta);
                System.out.printf(Locale.ROOT, "DIRK NATIVE PACKET REPLAY grip=%s->%s->%s sameIdentity=true"
                                + " nativeEquipUnchanged=true renderedPoseDelta=%.6f%n",
                        restingBefore, restingBetween, restingAfter, replayPoseDelta);
                require(restingBetween || replayPoseDelta >= .10F,
                        "Resting predicate changed during the replay without a witnessed render offset");
                System.out.printf(Locale.ROOT, "DIRK COUNTDOWN OBSERVED outcome=%s syncs=%d drawnOffsets=%d phases=%s models=%d"
                                + " baselineHeight=%.4f minIdleHeight=%.4f intentionalSlotDrop=%.4f unexpectedEquip=%s"
                                + " maxIdlePoseDelta=%.6f maxDeclaredTransformDelta=%.6f idleDraws=%d expiryDraws=%d age=%d%n",
                        restingBetween ? "NO_SNAP_OBSERVED" : "SNAP_REPRODUCED", packets.size(), drawnSeconds.size(), phases,
                        models.size(), baselineHeight, smallestIdleHeight, observedSlotDrop, activeReequip, poseDelta,
                        transformDelta, idleDraws, expiryDraws, age);
                active = null;
                return true;
            }
            case BASELINE -> throw new IllegalStateException();
        }
        return false;
    }

    public static void handTick(ItemStack rendered, float height, float previous) {
        var probe = active;
        if (probe == null) return;
        probe.handSerial++;
        probe.nativeHeld = rendered;
        probe.height = height;
        probe.previousHeight = previous;
        if (probe.replaySerial >= 0 && probe.handSerial > probe.replaySerial && probe.restingAfter == null) {
            var client = Minecraft.getInstance();
            var current = client.player.getMainHandItem();
            if (ItemStack.isSameItemSameComponents(rendered, current)) {
                probe.restingAfter = RevivalHandPose.usesRestingGrip(client.player, InteractionHand.MAIN_HAND, rendered, .5F, 0F);
            }
        }
        if (probe.step == Step.AWAY || probe.step == Step.RETURNING) {
            probe.observedSlotDrop = Math.max(probe.observedSlotDrop, probe.baselineHeight - height);
        } else if (probe.canMeasure() && (!probe.itemId.equals(identity(rendered)) || height < .975F)) {
            probe.activeReequip = true;
        }
    }

    public static boolean deferPacket(ClientboundContainerSetSlotPacket packet) {
        var probe = active;
        var client = Minecraft.getInstance();
        if (probe == null || !client.isSameThread() || !probe.canMeasure() || probe.replaySerial >= 0
                || probe.deferred != null || probe.previousRestDraw == null || packet.getContainerId() != 0
                || packet.getSlot() != 36 || client.level.getGameTime() - probe.started < 130) return false;
        ItemStack before = client.player.getMainHandItem();
        ItemStack after = packet.getItem();
        if (!probe.itemId.equals(identity(after)) || remaining(before) <= remaining(after) || remaining(after) <= 0) return false;
        probe.deferred = packet;
        probe.deferredSerial = probe.handSerial;
        return true;
    }

    public static void replayBeforeFrame(RenderFrameEvent.Pre event) {
        var probe = active;
        if (probe == null || probe.deferred == null || probe.handSerial <= probe.deferredSerial) return;
        var client = Minecraft.getInstance();
        var held = client.player.getMainHandItem();
        var incoming = probe.deferred.getItem();
        require(ItemStack.isSameItemSameComponents(held, probe.nativeHeld)
                        && ItemStack.isSameItemSameComponents(withoutCountdown(held), withoutCountdown(incoming))
                        && probe.height >= .995F && probe.previousHeight >= .995F,
                "Actual packet changed more than the countdown or native hand was not settled");
        probe.preReplayDraw = new Matrix4f(probe.previousRestDraw);
        probe.restingBefore = RevivalHandPose.usesRestingGrip(client.player, InteractionHand.MAIN_HAND, probe.nativeHeld, .5F, 0F);
        var packet = probe.deferred;
        probe.deferred = null;
        probe.replaySerial = probe.handSerial;
        float nativeHeight = probe.height;
        client.getConnection().handleContainerSetSlot(packet);
        require(ItemStack.isSameItemSameComponents(incoming, client.player.getMainHandItem())
                        && probe.height == nativeHeight && probe.handSerial == probe.replaySerial,
                "Replay did not install the new native stack between hand-renderer ticks");
        probe.restingBetween = RevivalHandPose.usesRestingGrip(client.player, InteractionHand.MAIN_HAND, probe.nativeHeld, .5F, 0F);
    }

    private static ItemStack withoutCountdown(ItemStack stack) {
        ItemStack comparison = stack.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, comparison, tag -> {
            tag.remove("uwu_revival_phase");
            tag.remove("uwu_revival_left");
        });
        return comparison;
    }

    public static void slotPacket(ItemStack stack) {
        var probe = active;
        if (probe == null || probe.itemId == null || !isDirk(stack) || !probe.itemId.equals(identity(stack))) return;
        long left = remaining(stack);
        if (left > 0) probe.packets.add(left);
        else if (!probe.packets.isEmpty()) probe.expiryPacket = true;
    }

    public static void model(ItemStack stack, ItemDisplayContext context, BakedModel model) {
        var probe = active;
        if (probe == null || !context.firstPerson() || !isDirk(stack)) return;
        probe.models.add(model);
        var transform = model.getTransforms().getTransform(context);
        if (probe.step == Step.BASELINE) {
            probe.baseRotation = new Vector3f(transform.rotation);
            probe.baseTranslation = new Vector3f(transform.translation);
            probe.baseScale = new Vector3f(transform.scale);
        } else if (probe.canMeasure()) {
            probe.transformDelta = Math.max(probe.transformDelta, Math.max(
                    probe.baseRotation.distance(transform.rotation), Math.max(
                            probe.baseTranslation.distance(transform.translation), probe.baseScale.distance(transform.scale))));
        }
    }

    public static void draw(ItemStack stack, ItemDisplayContext context, PoseStack poses) {
        var probe = active;
        if (probe == null || !context.firstPerson() || !isDirk(stack)) return;
        if (probe.step == Step.BASELINE && probe.height >= .995F) {
            probe.baselinePose = new Matrix4f(poses.last().pose());
        } else if (probe.itemId != null && probe.itemId.equals(identity(stack))) {
            long left = remaining(stack);
            if (left > 0) probe.drawnSeconds.add(left);
            probe.phases.add(RevivalBlossoms.visualPhase(stack));
            if (probe.canMeasure()) {
                if (probe.replaySerial == probe.handSerial && Float.isNaN(probe.replayPoseDelta)) {
                    probe.replayPoseDelta = delta(probe.preReplayDraw, poses.last().pose());
                }
                if (probe.previousRestDraw == null) probe.previousRestDraw = new Matrix4f(poses.last().pose());
                else probe.previousRestDraw.set(poses.last().pose());
                probe.idleDraws++;
                if (probe.step == Step.EXPIRED) probe.expiryDraws++;
                probe.smallestIdleHeight = Math.min(probe.smallestIdleHeight, Math.min(probe.height, probe.previousHeight));
                probe.poseDelta = Math.max(probe.poseDelta, delta(probe.baselinePose, poses.last().pose()));
            }
        }
    }

    private boolean canMeasure() {
        var client = Minecraft.getInstance();
        // The first few ticks contain the real hit and lotus animation, and the manual slot switch is a control.
        return (step == Step.COUNTDOWN || step == Step.EXPIRED) && started >= 0 && client.level != null
                && client.level.getGameTime() - started >= 45 && client.player.getInventory().selected == 0;
    }

    private static float delta(Matrix4f first, Matrix4f second) {
        float[] a = first.get(new float[16]);
        float[] b = second.get(new float[16]);
        float max = 0F;
        for (int i = 0; i < a.length; i++) max = Math.max(max, Math.abs(a[i] - b[i]));
        return max;
    }

    private static boolean isDirk(ItemStack stack) {
        return stack.is(RevivalBlossoms.DIRK.get());
    }

    private static UUID identity(ItemStack stack) {
        if (!isDirk(stack)) return null;
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.hasUUID("uwu_revival_id") ? data.getUUID("uwu_revival_id") : null;
    }

    private static long remaining(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong("uwu_revival_left");
    }

    private static void select(Minecraft client, int slot) {
        client.player.getInventory().selected = slot;
        client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
