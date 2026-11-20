package dev.uwu.gripcheck;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.DawnpetalSpinCue;
import dev.uwu.dimension.DawnpetalSwing;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.client.DawnpetalSpinClient;
import dev.uwu.dimension.client.RevivalHandPose;
import dev.uwu.gripcheck.mixin.HandDrawInvoker;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

@Mod("uwu_grip_check")
public final class GripProbe {
    private static boolean starting, done;
    private enum Stage { START, EQUIP, WINDUP, CLEAR, COMBAT, RECOVER, SLIDE }
    private static Stage stage = Stage.START;
    private static int stageTick;
    private static final UUID networkAxe = UUID.randomUUID();
    private static ItemStack inspecting;
    private static Sample observed;
    private static final GripRecoveryProbe RECOVERY = new GripRecoveryProbe();
    private record Sample(ItemDisplayContext view, boolean scoped, boolean idleModel, Matrix4f hand, Matrix4f model) {}

    public GripProbe() {
        NeoForge.EVENT_BUS.addListener(GripProbe::tick);
        NeoForge.EVENT_BUS.addListener(GripProbe::frame);
        NeoForge.EVENT_BUS.addListener(GripProbe::afterFrame);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!starting && client.screen instanceof TitleScreen title) {
            starting = true;
            client.createWorldOpenFlows().createFreshLevel("GripCheck", new LevelSettings("Grip Check", GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(152045L, false, false), WorldPresets::createNormalWorldDimensions, title);
        } else if (starting && client.player == null && client.screen != null) {
            for (var widget : List.copyOf(client.screen.children())) {
                if (widget instanceof Button b && (b.getMessage().getString().equals("Proceed")
                        || b.getMessage().getString().equals("I know what I'm doing!"))) { b.onPress(); break; }
            }
        }
        if (done) client.stop();
    }

    public static void observe(ItemStack item, ItemDisplayContext view, boolean left, PoseStack pose, BakedModel model) {
        if (inspecting == null) {
            RECOVERY.observe(item, view, left, pose, model);
            return;
        }
        if (item != inspecting) return;
        PoseStack transform = new PoseStack();
        model.applyTransform(view, transform, left);
        observed = new Sample(view, RevivalHandPose.drawsAxe(item),
                model.getClass().getName().startsWith("dev.uwu.dimension.client.RevivalHandPose$"),
                new Matrix4f(pose.last().pose()), new Matrix4f(transform.last().pose()));
    }

    private static void frame(RenderHandEvent event) {
        if (stage == Stage.SLIDE) {
            RECOVERY.beforeHand(event);
            return;
        }
        var client = Minecraft.getInstance();
        if (!starting || done || client.screen != null || client.player == null || client.player.tickCount < 35
                || event.getHand() != InteractionHand.MAIN_HAND) return;
        try {
            var p = client.player;
            var combat = (MinecraftClient_BetterCombat)(Object)client;
            if (stage != Stage.START) require(p.tickCount-stageTick < 300, "Timed out in " + stage);
            switch (stage) {
                case START -> {
                    require(ModList.get().isLoaded("bettercombat") && ModList.get().isLoaded("playeranimator"), "Combat mods missing");
                    client.options.setCameraType(CameraType.FIRST_PERSON);
                    for (var hand : InteractionHand.values()) {
                        for (boolean axe : List.of(false, true)) checkHand(client, p, hand, axe);
                    }
                    checkItemUse(client,p);
                    checkSpin(client,p);
                    serverAction(client, player -> {
                        var axe = new ItemStack(RevivalBlossoms.AXE.get());
                        CustomData.update(DataComponents.CUSTOM_DATA, axe, tag -> tag.putUUID("uwu_revival_id",networkAxe));
                        player.setItemInHand(InteractionHand.MAIN_HAND,axe);
                        player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(RevivalBlossoms.DIRK.get()));
                        player.containerMenu.broadcastChanges();
                    });
                    next(p,Stage.EQUIP);
                }
                case EQUIP -> {
                    var tag = p.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
                    if (!tag.hasUUID("uwu_revival_id") || !networkAxe.equals(tag.getUUID("uwu_revival_id"))
                            || !p.getOffhandItem().is(RevivalBlossoms.DIRK.get())) return;
                    require(axeIdle(client,p), "Network-synchronized axe missing its idle presentation");
                    serverAction(client, player -> DawnpetalSwing.start(player,195,200));
                    next(p,Stage.WINDUP);
                }
                case WINDUP -> {
                    if (!DawnpetalSwing.hasLocalCue(p,.5F)) return;
                    require(DawnpetalSwing.expansion(p,p.getMainHandItem(),.5F) == 0, "Missed opening windup");
                    require(!axeIdle(client,p) && !dirkAtRest(p), "Native opening windup retained idle grip");
                    serverAction(client,DawnpetalSwing::cancel);
                    next(p,Stage.CLEAR);
                }
                case CLEAR -> {
                    if (DawnpetalSwing.hasLocalCue(p,.5F)) return;
                    require(axeIdle(client,p) && dirkAtRest(p),"Native cancellation did not restore idle");
                    System.out.println("GRIP NATIVE WINDUP PASS transmitted start and cancellation");
                    next(p,Stage.COMBAT);
                }
                case COMBAT -> {
                    if (p.tickCount-stageTick < 30 || p.getAttackStrengthScale(0) < 1F) return;
                    require(combat.getCurrentAttackHand() == null, "Previous Better Combat swing pending");
                    var hit = client.hitResult;
                    try {
                        var point = p.getEyePosition().add(0,4,0);
                        client.hitResult = BlockHitResult.miss(point,Direction.UP,BlockPos.containing(point));
                        var attack = Minecraft.class.getDeclaredMethod("startAttack");
                        attack.setAccessible(true);
                        attack.invoke(client);
                    } finally {
                        client.hitResult = hit;
                    }
                    require(combat.getCurrentAttackHand() != null,"Actual attack did not start a Better Combat swing");
                    require(!axeIdle(client,p) && !dirkAtRest(p),"Better Combat swing retained idle grip");
                    combat.cancelUpswing();
                    next(p,Stage.RECOVER);
                }
                case RECOVER -> {
                    if (combat.getCurrentAttackHand() != null || DawnpetalSwing.hasLocalCue(p,.5F)
                            || p.swinging || p.getAttackAnim(.5F) != 0) return;
                    require(axeIdle(client,p) && dirkAtRest(p),"Better Combat completion did not restore idle");
                    System.out.println("GRIP BETTER COMBAT PASS actual attack start and cancellation");
                    RECOVERY.start(client);
                    next(p, Stage.SLIDE);
                }
                case SLIDE -> throw new AssertionError("Slide sampling must run after rendering");
            }
        } catch (Throwable failure) {
            done = true;
            System.err.println("HELD GRIPS CLIENT FAILED");
            failure.printStackTrace();
        } finally {
            inspecting = null;
        }
    }

    private static void afterFrame(RenderFrameEvent.Post event) {
        if (stage != Stage.SLIDE || done) return;
        try {
            if (!RECOVERY.endFrame(Minecraft.getInstance())) return;
            System.out.println("HELD GRIPS CLIENT PASS both hands idle, swing, equip, use, observer, native windup, Better Combat recovery slide and Spin");
            done = true;
        } catch (Throwable failure) {
            done = true;
            System.err.println("HELD GRIPS CLIENT FAILED");
            failure.printStackTrace();
        }
    }

    private static void next(LocalPlayer p, Stage nextStage) {
        stageTick = p.tickCount;
        stage = nextStage;
    }

    static void serverAction(Minecraft client, Consumer<ServerPlayer> action) {
        var server = client.getSingleplayerServer();
        require(server != null, "Fixture world needs an integrated server");
        UUID id = client.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static boolean axeIdle(Minecraft client, LocalPlayer p) {
        var s = draw(client,p,InteractionHand.MAIN_HAND,p.getMainHandItem(),0,0);
        require(s.scoped == s.idleModel,"Axe draw and baked model disagree");
        return s.idleModel;
    }

    private static boolean dirkAtRest(LocalPlayer p) {
        return RevivalHandPose.usesRestingGrip(p,InteractionHand.OFF_HAND,p.getOffhandItem(),.5F,0);
    }

    private static void checkItemUse(Minecraft client, LocalPlayer p) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RevivalBlossoms.AXE.get()));
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.BOW));
        require(axeIdle(client,p),"Pre-use idle missing");
        p.startUsingItem(InteractionHand.OFF_HAND);
        require(p.isUsingItem() && !axeIdle(client,p),"Opposite-hand item use retained idle grip");
        p.stopUsingItem();
        require(axeIdle(client,p),"Stopped item use did not restore idle");
    }

    private static void checkHand(Minecraft client, LocalPlayer p, InteractionHand hand, boolean axe) {
        var stack = new ItemStack(axe ? RevivalBlossoms.AXE.get() : RevivalBlossoms.DIRK.get());
        var swapped = stack.copy();
        swapped.set(DataComponents.CUSTOM_NAME, Component.literal("Fixture comparison"));
        p.setItemInHand(hand, swapped);
        var raw = draw(client, p, hand, stack, 0, 0);
        require(!raw.scoped && !raw.idleModel, "Mismatched stack used idle pose");
        p.setItemInHand(hand, stack);
        var idle = draw(client, p, hand, stack, 0, 0);
        boolean left = (p.getMainArm() == HumanoidArm.LEFT) == (hand == InteractionHand.MAIN_HAND);
        var view = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        require(idle.view == view, "Wrong physical hand");
        if (axe) {
            require(idle.scoped && idle.idleModel && !raw.model.equals(idle.model, .0001F), "Idle axe model missing");
        } else {
            require(new Matrix4f(raw.hand).translate(0,-.18F,0).equals(idle.hand,.0001F), "Dirk idle translation incorrect");
        }
        var moving = draw(client, p, hand, stack, .45F, 0);
        p.setItemInHand(hand, swapped);
        var reference = draw(client, p, hand, stack, .45F, 0);
        require(!moving.idleModel && !moving.scoped && moving.hand.equals(reference.hand,.0001F)
                && moving.model.equals(reference.model,.0001F), "Active hand changed under idle patch");
        p.setItemInHand(hand, stack);
        var rising = draw(client, p, hand, stack, 0, .5F);
        require(rising.scoped == idle.scoped && rising.idleModel == idle.idleModel
                && new Matrix4f(idle.hand).translate(0, -.3F, 0).equals(rising.hand, .0001F)
                && rising.model.equals(idle.model,.0001F), "Equip rise used a different grip");
        var restored = draw(client, p, hand, stack, 0, 0);
        require(restored.hand.equals(idle.hand,.0001F) && restored.model.equals(idle.model,.0001F), "Idle did not return");

        var third = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var observer = third(client, p, stack, third, left);
        RevivalHandPose.drawAxe(stack, () -> {
            var nested = third(client, p, stack, third, left);
            require(!nested.idleModel && nested.model.equals(observer.model,.0001F), "Observer pose leaked from scoped draw");
        });
        require(!RevivalHandPose.drawsAxe(stack), "Scope leaked outside hand draw");
        System.out.println("GRIP HAND PASS " + stack.getItem() + " " + view + " swing/equip/observer");
    }

    private static Sample draw(Minecraft client, LocalPlayer p, InteractionHand hand, ItemStack stack, float swing, float equip) {
        inspecting = stack;
        observed = null;
        ((HandDrawInvoker) client.gameRenderer.itemInHandRenderer).gripcheck$draw(p, .5F, 0, hand, swing, stack, equip,
                new PoseStack(), buffer(client), LightTexture.FULL_BRIGHT);
        inspecting = null;
        require(observed != null, "Ordinary hand draw did not reach mapped item renderer");
        return observed;
    }

    private static Sample third(Minecraft client, LocalPlayer p, ItemStack stack, ItemDisplayContext view, boolean left) {
        inspecting = stack;
        observed = null;
        client.getItemRenderer().renderStatic(p,stack,view,left,new PoseStack(),buffer(client),p.level(),
                LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,p.getId());
        inspecting = null;
        require(observed != null && observed.view == view, "Third-person item missing");
        return observed;
    }

    private static MultiBufferSource buffer(Minecraft client) { return client.renderBuffers().bufferSource(); }

    private static void checkSpin(Minecraft client, LocalPlayer p) throws Exception {
        var axe = new ItemStack(RevivalBlossoms.AXE.get());
        var dirk = new ItemStack(RevivalBlossoms.DIRK.get());
        UUID axeId = UUID.randomUUID(), cast = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, axe, tag -> tag.putUUID("uwu_revival_id",axeId));
        p.setItemInHand(InteractionHand.MAIN_HAND, axe);
        p.setItemInHand(InteractionHand.OFF_HAND, dirk);
        require(draw(client,p,InteractionHand.MAIN_HAND,axe,0,0).idleModel,"Pre-Spin idle missing");
        var idleDirk = draw(client,p,InteractionHand.OFF_HAND,dirk,0,0);
        Class<?> packet = Class.forName("dev.uwu.dimension.DawnpetalSpinCue$Accepted");
        Constructor<?> ctor = packet.getDeclaredConstructor(int.class, UUID.class, UUID.class, int.class, ResourceLocation.class,
                UUID.class, long.class, double.class, boolean.class, boolean.class);
        Method receive = DawnpetalSpinCue.class.getDeclaredMethod("receive", Player.class, packet);
        ctor.setAccessible(true);
        receive.setAccessible(true);
        long start = p.level().getGameTime();
        for (boolean cancel : List.of(false, true)) {
            Object cue = ctor.newInstance(p.getId(),p.getUUID(),axeId,p.getInventory().selected,p.level().dimension().location(),
                    cast,start,0d,p.getMainArm()==HumanoidArm.LEFT,cancel);
            receive.invoke(null,p,cue);
            require((DawnpetalSpinClient.renderFrame(p,.5F) == null) == cancel,"Spin frame did not transition");
            var main = draw(client,p,InteractionHand.MAIN_HAND,axe,0,0);
            var off = draw(client,p,InteractionHand.OFF_HAND,dirk,0,0);
            require(main.scoped == cancel && main.idleModel == cancel, "Main-hand Spin did not yield or restore idle");
            require(idleDirk.hand.equals(off.hand,.0001F) == cancel,"Off-hand Dirk did not yield or restore idle");
        }
        System.out.println("GRIP SPIN PASS accepted and cancelled cues across both hands");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
