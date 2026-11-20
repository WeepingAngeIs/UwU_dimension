package dev.uwu.gripcheck;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.uwu.dimension.RevivalBlossoms;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.UUID;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.client.compat.FirstPersonAnimationCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

/** Records the actual item draws from the player's camera through a complete, uncancelled attack. */
final class GripRecoveryProbe {
    private static final float EPSILON = .0005F;
    private final EnumMap<InteractionHand, Draw> draws = new EnumMap<>(InteractionHand.class);
    private final EnumMap<InteractionHand, Slide> slides = new EnumMap<>(InteractionHand.class);
    private final EnumMap<InteractionHand, HandInput> handEvents = new EnumMap<>(InteractionHand.class);
    private boolean active;
    private boolean axeFirst;
    private boolean attacking;
    private boolean animatedAttackVisible;
    private int stageTick;
    private UUID axeIdentity;

    private record HandInput(ItemStack stack, Matrix4f transform, float equip) {}
    private record Draw(ItemDisplayContext view, Matrix4f transform, float equip) {}

    private static final class Slide {
        final Draw idle;
        final String label;
        float previousY = Float.NEGATIVE_INFINITY;
        float lowestY = 0F;
        float firstEquip = Float.NaN;
        float lastMovingY;
        int recoveryFrames;
        int risingSteps;
        int idleFrames;

        Slide(Draw idle, ItemStack rendered) {
            this.idle = idle;
            label = rendered.getItem() + " " + idle.view();
        }

        void sample(Draw current) {
            if (current == null) return;
            Matrix4f transform = current.transform();
            Matrix4f target = idle.transform();
            float deltaY = transform.m31() - target.m31();
            if (Float.isNaN(firstEquip)) firstEquip = current.equip();
            require(new Matrix4f(transform).setTranslation(transform.m30(), target.m31(), transform.m32()).equals(target, EPSILON),
                    label + " changed angle, scale, or sideways position during recovery, equip=" + current.equip()
                            + ", relative Y=" + deltaY);
            require(deltaY <= EPSILON, label + " rose above the approved idle grip");
            require(deltaY + EPSILON >= previousY, label + " moved down during the recovery rise");
            lowestY = Math.min(lowestY, deltaY);
            if (deltaY < -EPSILON) {
                if (recoveryFrames != 0 && deltaY > previousY + EPSILON) risingSteps++;
                recoveryFrames++;
                lastMovingY = deltaY;
                idleFrames = 0;
            } else {
                // The complete transform, including Y, matches the original idle pose here.
                idleFrames++;
            }
            previousY = deltaY;
        }

        void finish() {
            require(recoveryFrames >= 2 && risingSteps >= 1 && lowestY < -.01F,
                    label + " skipped the visible rise from below the approved grip, moving frames=" + recoveryFrames
                            + ", rising steps=" + risingSteps + ", lowest relative Y=" + lowestY
                            + ", first vanilla equip=" + firstEquip);
            System.out.println("GRIP RECOVERY PASS " + label + " moving frames=" + recoveryFrames
                    + " lowest relative Y=" + lowestY + " last moving Y=" + lastMovingY
                    + " first vanilla equip=" + firstEquip + " matching terminal frames=" + idleFrames);
        }
    }

    void start(Minecraft client) {
        require(FirstPersonAnimationCompatibility.firstPersonMode() == FirstPersonMode.THIRD_PERSON_MODEL,
                "Better Combat must use the Player Animator model pass");
        active = true;
        equip(client, true);
    }

    private void equip(Minecraft client, boolean axeMain) {
        axeFirst = axeMain;
        attacking = false;
        animatedAttackVisible = false;
        stageTick = client.player.tickCount;
        axeIdentity = UUID.randomUUID();
        UUID itemId = axeIdentity;
        draws.clear();
        slides.clear();
        GripProbe.serverAction(client, player -> {
            var axe = new ItemStack(RevivalBlossoms.AXE.get());
            var dirk = new ItemStack(RevivalBlossoms.DIRK.get());
            CustomData.update(DataComponents.CUSTOM_DATA, axe, tag -> tag.putUUID("uwu_revival_id", itemId));
            player.setItemInHand(InteractionHand.MAIN_HAND, axeMain ? axe : dirk);
            player.setItemInHand(InteractionHand.OFF_HAND, axeMain ? dirk : axe);
            player.containerMenu.broadcastChanges();
        });
    }

    void beforeHand(RenderHandEvent event) {
        if (active) handEvents.put(event.getHand(), new HandInput(event.getItemStack(),
                new Matrix4f(event.getPoseStack().last().pose()), event.getEquipProgress()));
    }

    void observe(ItemStack item, ItemDisplayContext view, boolean left, PoseStack pose, BakedModel model) {
        if (!active) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive()) return;
        InteractionHand hand = (player.getMainArm() == (left ? HumanoidArm.LEFT : HumanoidArm.RIGHT))
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (attacking && hand == InteractionHand.MAIN_HAND && FirstPersonMode.isFirstPersonPass()
                && view == (left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                && ItemStack.isSameItemSameComponents(item, player.getMainHandItem())) {
            animatedAttackVisible = true;
        }
        if (!view.firstPerson()) return;
        HandInput input = handEvents.get(hand);
        if (input == null || input.stack() != item) return;
        // Subtract the camera/view turn already applied at RenderHandEvent. Keep all item transforms.
        PoseStack transform = new PoseStack();
        model.applyTransform(view, transform, left);
        Matrix4f matrix = new Matrix4f(input.transform()).invert()
                .mul(pose.last().pose()).mul(transform.last().pose());
        draws.put(hand, new Draw(view, matrix, input.equip()));
    }

    boolean endFrame(Minecraft client) throws ReflectiveOperationException {
        if (!active) return false;
        LocalPlayer player = client.player;
        var combat = (MinecraftClient_BetterCombat)(Object)client;
        try {
            require(player != null && player.tickCount - stageTick < 220,
                    "Timed out measuring recovery, main axe=" + axeFirst + ", attack=" + attacking
                            + ", saw animated attack=" + animatedAttackVisible);
            if (!attacking) {
                if (!ready(player, combat)) return false;
                for (InteractionHand hand : InteractionHand.values()) {
                    slides.put(hand, new Slide(draws.get(hand), player.getItemInHand(hand)));
                }
                startAttack(client, player);
                var attack = combat.getCurrentAttackHand();
                require(attack != null && !attack.isOffHand()
                        && ItemStack.isSameItemSameComponents(attack.itemStack(), player.getMainHandItem()),
                        "Better Combat did not start the expected normal attack");
                attacking = true;
                stageTick = player.tickCount;
                return false;
            }
            if (!animatedAttackVisible) return false;
            for (InteractionHand hand : InteractionHand.values()) slides.get(hand).sample(draws.get(hand));
            if (slides.values().stream().anyMatch(slide -> slide.idleFrames < 3)) return false;
            slides.values().forEach(Slide::finish);
            if (axeFirst) {
                equip(client, false);
                return false;
            }
            active = false;
            return true;
        } finally {
            draws.clear();
            handEvents.clear();
        }
    }

    private boolean ready(LocalPlayer player, MinecraftClient_BetterCombat combat) {
        var axe = player.getItemInHand(axeFirst ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        var dirk = player.getItemInHand(axeFirst ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        var tag = axe.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return player.tickCount - stageTick > 25 && combat.getCurrentAttackHand() == null
                && combat.getComboCount() == 0 && !player.swinging && player.getAttackStrengthScale(0) >= 1
                && axe.is(RevivalBlossoms.AXE.get()) && tag.hasUUID("uwu_revival_id")
                && axeIdentity.equals(tag.getUUID("uwu_revival_id")) && dirk.is(RevivalBlossoms.DIRK.get())
                && draws.size() == 2 && draws.values().stream().allMatch(draw -> draw.equip() <= EPSILON);
    }

    private static void startAttack(Minecraft client, LocalPlayer player) throws ReflectiveOperationException {
        var oldHitResult = client.hitResult;
        try {
            var point = player.getEyePosition().add(0, 4, 0);
            client.hitResult = BlockHitResult.miss(point, Direction.UP, BlockPos.containing(point));
            Method attack = Minecraft.class.getDeclaredMethod("startAttack");
            attack.setAccessible(true);
            attack.invoke(client);
        } finally {
            client.hitResult = oldHitResult;
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
