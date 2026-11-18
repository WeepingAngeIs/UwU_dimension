package dev.uwu.test;

import dev.uwu.dimension.Fallsong;

import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** One local integrated world on the hosted runner, using a real survival player. */
@Mod("heartwater_probe")
@EventBusSubscriber(modid = "heartwater_probe", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class FallsongCamera {
    private enum Stage {
        MENU, LOADING, REST, BLOCK, CHARGED, GET_READY, JUMP, EQUIP_PETAL, PETAL, BLOOM,
        EQUIP_BRIAR, BUBBLE_FRONT, BUBBLE_SIDE, BUBBLE_REAR, BUBBLE_IMPACT, BUBBLE_FIRST, CLOSE
    }
    private static Stage stage = Stage.MENU;
    private static int ticks;
    private static String pendingPhoto;
    private static volatile boolean petalHit;
    private static volatile boolean briarHit;
    private static CameraType originalCamera;
    private static final InteractionHand HAND = InteractionHand.OFF_HAND;
    private static final double FLOOR = 249.0;

    private static void enter(Stage next) { stage = next; ticks = 0; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 5000) throw new AssertionError("Fallsong camera stalled at " + stage);
        if (stage == Stage.CLOSE && mc.level == null) {
            System.out.println("FALLSONG CLIENT CAPTURE COMPLETE");
            mc.stop();
            return;
        }
        if (stage == Stage.MENU && mc.screen instanceof TitleScreen menu) {
            originalCamera = mc.options.getCameraType();
            enter(Stage.LOADING);
            mc.createWorldOpenFlows().createFreshLevel("FallsongVisit",
                new LevelSettings("Fallsong Visit", GameType.SURVIVAL, false, Difficulty.NORMAL,
                    true, new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(152045, true, false), WorldPresets::createNormalWorldDimensions, menu);
            return;
        }
        if (stage == Stage.LOADING && mc.screen != null && mc.player == null) {
            for (var widget : List.copyOf(mc.screen.children())) {
                if (widget instanceof Button b && List.of("Proceed", "I know what I'm doing!").contains(b.getMessage().getString())) {
                    b.onPress();
                    break;
                }
            }
        }
        var player = mc.player;
        var server = mc.getSingleplayerServer();
        if (player == null || mc.level == null || server == null || mc.gameMode == null) return;
        var id = player.getUUID();
        switch (stage) {
            case LOADING -> {
                if (mc.screen != null) return;
                server.execute(() -> {
                    var p = server.getPlayerList().getPlayer(id);
                    ServerLevel level = p.serverLevel();
                    for (int x = -8; x <= 8; x++) for (int z = -8; z <= 10; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, 248, z), (z % 3 == 0 ?
                            Blocks.CHERRY_PLANKS : Blocks.SMOOTH_QUARTZ).defaultBlockState());
                    }
                    level.setDayTime(6000);
                    p.teleportTo(level, .5, FLOOR, .5, 0, 15);
                    p.setItemInHand(HAND, new ItemStack(shield("heartwater_window_shield")));
                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    p.containerMenu.broadcastChanges();
                });
                enter(Stage.REST);
            }
            case REST -> {
                if (!player.getItemInHand(HAND).is(shield("heartwater_window_shield")) ||
                        ticks < 70 || !player.onGround()) return;
                if (ticks == 70 || pendingPhoto == null && !restSaved) {
                    pendingPhoto = "window-rest";
                    restSaved = true;
                    return;
                }
                if (pendingPhoto == null) {
                    hold(mc);
                    enter(Stage.BLOCK);
                }
            }
            case BLOCK -> {
                if (ticks == 18 || ticks % 45 == 0 && !Fallsong.isCharged(player.getItemInHand(HAND))) {
                    server.execute(() -> hit(server.getPlayerList().getPlayer(id)));
                }
                if (Fallsong.isCharged(player.getItemInHand(HAND))) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    enter(Stage.CHARGED);
                }
            }
            case CHARGED -> {
                if (ticks == 10) pendingPhoto = "window-charged";
                if (ticks > 10 && pendingPhoto == null) {
                    mc.options.keyUse.setDown(false);
                    mc.gameMode.releaseUsingItem(player);
                    enter(Stage.GET_READY);
                }
            }
            case GET_READY -> {
                if (ticks >= 8 && player.onGround()) {
                    mc.options.keyJump.setDown(true);
                    enter(Stage.JUMP);
                }
            }
            case JUMP -> {
                if (ticks >= 3) mc.options.keyJump.setDown(false);
                if (!jumpSaved && !Fallsong.isCharged(player.getItemInHand(HAND)) && player.getY() > FLOOR + 1.25) {
                    System.out.println("FALLSONG CLIENT LIFT Y " + player.getY());
                    pendingPhoto = "waterfall-jump";
                    jumpSaved = true;
                }
                if (jumpSaved && pendingPhoto == null && player.onGround()) {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    server.execute(() -> {
                        var p = server.getPlayerList().getPlayer(id);
                        p.teleportTo(p.serverLevel(), .5, FLOOR, .5, 0, 26);
                        p.setItemInHand(HAND, new ItemStack(shield("petalfold_shield")));
                        p.containerMenu.broadcastChanges();
                    });
                    enter(Stage.EQUIP_PETAL);
                }
            }
            case EQUIP_PETAL -> {
                if (ticks > 12 && player.getItemInHand(HAND).is(shield("petalfold_shield"))) {
                    hold(mc);
                    enter(Stage.PETAL);
                }
            }
            case PETAL -> {
                if (ticks == 15 || ticks % 35 == 0 && !petalHit) {
                    server.execute(() -> petalHit = hit(server.getPlayerList().getPlayer(id)));
                }
                if (petalHit) enter(Stage.BLOOM);
            }
            case BLOOM -> {
                if (ticks == 4) pendingPhoto = "petalfold-bloom";
                if (ticks > 12 && pendingPhoto == null) {
                    mc.options.keyUse.setDown(false);
                    mc.gameMode.releaseUsingItem(player);
                    server.execute(() -> {
                        var p = server.getPlayerList().getPlayer(id);
                        p.teleportTo(p.serverLevel(), .5, FLOOR, .5, 0, 8);
                        p.setItemInHand(HAND, new ItemStack(shield("heartbriar_shield")));
                        p.containerMenu.broadcastChanges();
                    });
                    enter(Stage.EQUIP_BRIAR);
                }
            }
            case EQUIP_BRIAR -> {
                if (ticks >= 12 && player.onGround() && Math.abs(player.getY() - FLOOR) < .1
                        && player.getItemInHand(HAND).is(shield("heartbriar_shield"))) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    hold(mc);
                    enter(Stage.BUBBLE_FRONT);
                }
            }
            case BUBBLE_FRONT -> {
                if (ticks == 30) bubblePhoto(mc, "front");
                if (ticks > 30 && pendingPhoto == null) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    enter(Stage.BUBBLE_SIDE);
                }
            }
            case BUBBLE_SIDE -> {
                if (ticks == 14) bubblePhoto(mc, "side");
                if (ticks > 14 && pendingPhoto == null) enter(Stage.BUBBLE_REAR);
            }
            case BUBBLE_REAR -> {
                if (ticks == 14) bubblePhoto(mc, "rear");
                if (ticks > 14 && pendingPhoto == null) {
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    enter(Stage.BUBBLE_IMPACT);
                }
            }
            case BUBBLE_IMPACT -> {
                if (ticks == 6 || ticks % 35 == 0 && !briarHit) {
                    server.execute(() -> briarHit = hit(server.getPlayerList().getPlayer(id)));
                }
                if (briarHit && !briarImpactSaved) {
                    // Allow the dedicated hit cue to arrive on the render client.
                    if (++impactFrames >= 2) {
                        bubblePhoto(mc, "impact");
                        briarImpactSaved = true;
                    }
                } else if (briarImpactSaved && pendingPhoto == null) {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    enter(Stage.BUBBLE_FIRST);
                }
            }
            case BUBBLE_FIRST -> {
                if (ticks == 18) bubblePhoto(mc, "first");
                if (ticks > 18 && pendingPhoto == null) {
                    mc.options.keyUse.setDown(false);
                    mc.options.keyJump.setDown(false);
                    mc.gameMode.releaseUsingItem(player);
                    mc.options.setCameraType(originalCamera);
                    enter(Stage.CLOSE);
                    mc.level.disconnect();
                    mc.disconnect(new TitleScreen());
                }
            }
            default -> {}
        }
    }
    private static boolean restSaved;
    private static boolean jumpSaved;
    private static boolean briarImpactSaved;
    private static int impactFrames;

    private static void bubblePhoto(Minecraft mc, String side) {
        var player = mc.player;
        if (!player.isBlocking() || !player.getUseItem().is(shield("heartbriar_shield"))
                || Math.abs(player.getY() - FLOOR) > .95 || player.isCreative() || player.isSpectator()) {
            throw new AssertionError("Missing survival guard at the bubble camera position: " + side);
        }
        pendingPhoto = "bubble-" + side;
        System.out.println("HEARTBRIAR BUBBLE " + side + " y=" + player.getY() + " heldTicks=" + player.getTicksUsingItem());
    }

    // Camera.setup applies this angle before moving 4 blocks back along its
    // viewing axis. Only the detached camera orbits; the player keeps guarding.
    @SubscribeEvent
    public static void sideCamera(ViewportEvent.ComputeCameraAngles event) {
        if (stage == Stage.BUBBLE_SIDE) {
            event.setYaw(event.getYaw() + 90);
            event.setPitch(4);
        }
    }

    private static Item shield(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", path));
    }

    private static void hold(Minecraft mc) {
        mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(mc.player, HAND);
    }

    private static boolean hit(ServerPlayer p) {
        if (!p.isBlocking()) return false;
        var level = p.serverLevel();
        var attacker = EntityType.HUSK.create(level);
        if (attacker == null) throw new AssertionError("No fixture attacker");
        attacker.moveTo(.5, FLOOR, 4.5, 180, 0);
        attacker.setNoAi(true);
        level.addFreshEntity(attacker);
        var stack = p.getItemInHand(HAND);
        int before = stack.getDamageValue();
        p.hurt(level.damageSources().mobAttack(attacker), 4);
        // Keep the actual defender, model and particles visible in the image.
        attacker.discard();
        return stack.getDamageValue() > before;
    }

    @SubscribeEvent
    public static void photo(RenderGuiEvent.Post event) {
        if (pendingPhoto == null) return;
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "fallsong-" + pendingPhoto + ".png",
            mc.getMainRenderTarget(), message -> System.out.println(message.getString()));
        pendingPhoto = null;
    }
}
