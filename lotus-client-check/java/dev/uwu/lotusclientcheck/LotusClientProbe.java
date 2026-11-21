package dev.uwu.lotusclientcheck;

import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.RevivalBloom;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opens a disposable world and samples real owner-camera geometry and block light. */
@Mod("uwu_lotus_client_check")
public final class LotusClientProbe {
    private enum Stage { LOAD, POSITION, DIRK_COUNTDOWN, REPLAY, JADE, CUPID, CUPID_SHRINE, DONE }
    private static Stage stage = Stage.LOAD;
    private static final boolean DIRK_ONLY = Boolean.getBoolean("uwu.check.dirkCountdown");
    private static final boolean SHRINE_ONLY = Boolean.getBoolean("uwu.check.shrine");
    private static final boolean CAVE_ONLY = Boolean.getBoolean("uwu.check.cave");
    private static boolean opening;
    private static DirkCountdownProbe dirk;
    private static JadeFluidProbe jade;
    private static CupidBowProbe cupid;
    private static CupidShrineClientProbe shrine;
    private static int stageTick;
    private static volatile Vec3 testPosition;
    private static volatile long reviveTick = -1;
    private static volatile Throwable serverError;
    private static BlockPos lightSample;
    private static int baseLight, peakLight;
    private static int cueCount, frameAlpha, peakAlpha, frames, fadeFrames;
    private static long clientCueTick = -1;

    public LotusClientProbe() {
        NeoForge.EVENT_BUS.addListener(LotusClientProbe::tick);
        NeoForge.EVENT_BUS.addListener(LotusClientProbe::frame);
        if (DIRK_ONLY) NeoForge.EVENT_BUS.addListener(DirkCountdownProbe::replayBeforeFrame);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!opening && client.screen instanceof TitleScreen title) {
            opening = true;
            client.createWorldOpenFlows().createFreshLevel("LotusClientCheck",
                    new LevelSettings("Lotus client check", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(152045L, false, false), WorldPresets::createNormalWorldDimensions, title);
        } else if (opening && client.player == null && client.screen != null) {
            for (var widget : List.copyOf(client.screen.children())) {
                if (widget instanceof Button button && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("I know what I'm doing!"))) {
                    button.onPress();
                    break;
                }
            }
        }
        if (stage == Stage.DIRK_COUNTDOWN) {
            try {
                if (serverError != null) throw new AssertionError("Dirk server action failed", serverError);
                if (dirk.tick(client)) stage = Stage.DONE;
            } catch (Throwable failure) {
                System.err.println("DIRK COUNTDOWN CHECK FAILED");
                failure.printStackTrace();
                stage = Stage.DONE;
            }
        }
        if (stage == Stage.JADE) {
            try {
                if (jade == null) jade = new JadeFluidProbe();
                if (jade.tick(client)) stage = CAVE_ONLY ? Stage.DONE : Stage.CUPID;
            } catch (Throwable failure) {
                System.err.println("JADE FLUID CHECK FAILED");
                failure.printStackTrace();
                stage = Stage.DONE;
            }
        }
        if (stage == Stage.CUPID) {
            try {
                if (cupid == null) cupid = new CupidBowProbe();
                if (cupid.tick(client)) stage = Stage.DONE;
            } catch (Throwable failure) {
                client.options.keyUse.setDown(false);
                System.err.println("CUPID CLIENT CHECK FAILED");
                failure.printStackTrace();
                stage = Stage.DONE;
            }
        }
        if (stage == Stage.CUPID_SHRINE) {
            try {
                if (shrine.tick(client)) stage = Stage.DONE;
            } catch (Throwable failure) {
                System.err.println("CUPID SHRINE CLIENT CHECK FAILED");
                failure.printStackTrace();
                stage = Stage.DONE;
            }
        }
        if (stage == Stage.DONE) client.stop();
    }

    private static void frame(RenderFrameEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.level == null || client.screen != null
                || stage == Stage.DIRK_COUNTDOWN || stage == Stage.JADE || stage == Stage.CUPID
                || stage == Stage.CUPID_SHRINE || stage == Stage.DONE) return;
        try {
            if (serverError != null) throw new AssertionError("Server action failed", serverError);
            if (stage != Stage.LOAD) {
                require(player.tickCount - stageTick < 180, "Timed out in " + stage + ", cues=" + cueCount
                        + ", frames=" + frames + ", peak light=" + peakLight);
            }
            switch (stage) {
                case LOAD -> {
                    if (player.tickCount < 85) return;
                    if (CAVE_ONLY) {
                        stage = Stage.JADE;
                        return;
                    }
                    if (SHRINE_ONLY) {
                        shrine = new CupidShrineClientProbe(client);
                        stage = Stage.CUPID_SHRINE;
                        return;
                    }
                    require(RevivalBloom.DURATION_TICKS == 34, "Unexpected bloom duration");
                    if (!DIRK_ONLY) {
                        CompanionMotionProbe.verify();
                        LanternCoverProbe.verify(client);
                    }
                    require(!ModList.get().isLoaded("lambdynlights"), "Block-light test needs no dynamic lighting mod");
                    client.options.setCameraType(CameraType.FIRST_PERSON);
                    serverAction(client, actor -> {
                        BlockPos floor = actor.blockPosition().above(16);
                        actor.serverLevel().setBlockAndUpdate(floor, Blocks.DEEPSLATE.defaultBlockState());
                        testPosition = Vec3.atBottomCenterOf(floor.above());
                        actor.teleportTo(testPosition.x, testPosition.y, testPosition.z);
                        actor.fallDistance = 0;
                    });
                    stageTick = player.tickCount;
                    stage = Stage.POSITION;
                }
                case POSITION -> {
                    if (testPosition == null || player.distanceToSqr(testPosition) > .04
                            || player.tickCount - stageTick < 12) return;
                    if (DIRK_ONLY) {
                        dirk = new DirkCountdownProbe(client, action -> serverAction(client, action));
                        stage = Stage.DIRK_COUNTDOWN;
                        return;
                    }
                    lightSample = BlockPos.containing(testPosition.add(2, 1, 0));
                    baseLight = client.level.getBrightness(LightLayer.BLOCK, lightSample);
                    peakLight = baseLight;
                    require(baseLight == 0 && client.level.getBlockState(lightSample).isAir(),
                            "Fixture requires a dark air sample beside the stone floor: " + baseLight);
                    serverAction(client, actor -> {
                        actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RevivalBlossoms.DIRK.get()));
                        actor.setHealth(1);
                        actor.invulnerableTime = 0;
                        reviveTick = actor.serverLevel().getServer().overworld().getGameTime();
                        actor.hurt(actor.damageSources().generic(), 8);
                        require(actor.isAlive() && actor.getHealth() == actor.getMaxHealth(), "Real lethal hit did not revive target");
                    });
                    stageTick = player.tickCount;
                    stage = Stage.REPLAY;
                }
                case REPLAY -> {
                    if (clientCueTick < 0) return;
                    require(client.getCameraEntity() == player && client.options.getCameraType().isFirstPerson(),
                            "Lotus geometry did not use the revived player's own first-person camera");
                    long age = client.level.getGameTime() - clientCueTick;
                    if (frameAlpha > 0) {
                        require(age >= 0 && age < RevivalBloom.DURATION_TICKS, "Vertex after deadline: age " + age);
                        frames++;
                        peakAlpha = Math.max(peakAlpha, frameAlpha);
                        if (age >= 26 && frameAlpha < 180) fadeFrames++;
                    }
                    int current = client.level.getBrightness(LightLayer.BLOCK, lightSample);
                    if (age < RevivalBloom.DURATION_TICKS) peakLight = Math.max(peakLight, current);
                    if (age < RevivalBloom.DURATION_TICKS + 8) return;
                    require(cueCount == 1 && frames >= 5 && peakAlpha >= 220 && fadeFrames > 0,
                            "Missing bloom/fade: cues=" + cueCount + ", frames=" + frames + ", peak alpha=" + peakAlpha
                                    + ", fade frames=" + fadeFrames);
                    require(peakLight >= baseLight + 4 && current == baseLight
                                    && client.level.getBlockState(lightSample).isAir(),
                            "Missing light or cleanup: baseline=" + baseLight + ", peak=" + peakLight + ", final=" + current);
                    System.out.println("LOTUS FIRST PERSON PASS one accepted revive, frames=" + frames
                            + ", peak alpha=" + peakAlpha + ", low alpha fade frames=" + fadeFrames + ", cutoff=34");
                    System.out.println("LOTUS DYNAMIC LIGHT PASS server block light in shader-free client at " + lightSample
                            + ", block light " + baseLight + " -> " + peakLight + " -> " + current);
                    stage = Boolean.getBoolean("uwu.check.decor") ? Stage.CUPID : Stage.JADE;
                }
                case DIRK_COUNTDOWN, JADE, CUPID, CUPID_SHRINE, DONE -> {}
            }
        } catch (Throwable failure) {
            System.err.println("LOTUS CLIENT CHECK FAILED");
            failure.printStackTrace();
            stage = Stage.DONE;
        } finally {
            frameAlpha = 0;
        }
    }

    public static void accepted(UUID owner, long start, Vec3 origin) {
        var player = Minecraft.getInstance().player;
        if (stage != Stage.REPLAY || player == null || !owner.equals(player.getUUID())) return;
        if (start != reviveTick || origin.distanceToSqr(testPosition) > .04) {
            serverError = new AssertionError("Cue timing or origin differs from the server revive");
            return;
        }
        cueCount++;
        clientCueTick = start;
    }

    public static void submitted(int opacity) {
        if (stage == Stage.REPLAY) frameAlpha = Math.max(frameAlpha, opacity);
    }

    private static void serverAction(Minecraft client, Consumer<ServerPlayer> action) {
        var server = client.getSingleplayerServer();
        UUID id = client.player.getUUID();
        server.execute(() -> {
            try {
                action.accept(server.getPlayerList().getPlayer(id));
            } catch (Throwable failure) {
                serverError = failure;
            }
        });
    }

    private static void require(boolean ok, String problem) {
        if (!ok) throw new AssertionError(problem);
    }
}
