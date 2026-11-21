package dev.uwu.lotusclientcheck;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.CupidBowVisualConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/** Checks the Creative choices and drives the actual item overrides with four bow draws. */
final class CupidBowProbe {
    private static final ResourceLocation BOW = id("cupid_bow");
    private static final ResourceLocation UWU_TAB = id("main");
    private static final String STYLE = "uwudimension:smooth_cupid";
    private static final List<ItemDisplayContext> MOUNTS = List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED);

    private enum Pose {
        REST(""), DRAW0("_draw0"), DRAW1("_draw1"), DEEP("_deep"), DRAW2("_draw2");

        final String suffix;
        Pose(String suffix) { this.suffix = suffix; }

        static Pose at(int ticks) {
            if (ticks < 8) return DRAW0;
            if (ticks < 15) return DRAW1;
            if (ticks < 20) return DEEP;
            return DRAW2;
        }
    }

    private static final boolean FOCUSED = Boolean.getBoolean("uwu.check.cupidLantern");
    private record Trial(boolean smooth, InteractionHand hand, CameraType camera, double scale) {
        String label() {
            return "cupid-" + (smooth ? "detailed" : "original") + "-" + hand.name().toLowerCase()
                    + "-" + camera.name().toLowerCase() + "-scale" + (int) (scale * 100);
        }
    }
    private static final List<Trial> TRIALS = trials();

    private static List<Trial> trials() {
        var result = new ArrayList<Trial>();
        for (CameraType camera : FOCUSED ? List.of(CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_FRONT)
                : List.of(CameraType.FIRST_PERSON)) {
            for (boolean detailed : List.of(false, true)) {
                for (double scale : FOCUSED && detailed ? List.of(1D, 1.5D) : List.of(1D)) {
                    for (InteractionHand hand : InteractionHand.values()) result.add(new Trial(detailed, hand, camera, scale));
                }
            }
        }
        return List.copyOf(result);
    }

    private final EnumMap<ItemDisplayContext, Matrix4f> mounts = new EnumMap<>(ItemDisplayContext.class);
    private final EnumMap<Pose, Long> smoothGeometry = new EnumMap<>(Pose.class);
    private final Set<Integer> drawTicks = new HashSet<>();
    private ItemStack classic;
    private ItemStack smooth;
    private int trialIndex;
    private long deadline;
    private boolean requested;
    private boolean drawing;
    private boolean released;
    private int restingAt = -1;
    private int releasedAt;
    private final Set<String> renderedPoses = new HashSet<>();
    private double savedScale;
    private CameraType savedCamera;
    private int originalWear;
    private CompletableFuture<Void> reload;
    private volatile Throwable serverFailure;

    boolean tick(Minecraft client) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null) return false;
        if (serverFailure != null) throw new AssertionError("Could not set up Cupid fixture hands", serverFailure);
        if (classic == null) {
            savedScale = CupidBowVisualConfig.HELD_SCALE.get();
            savedCamera = client.options.getCameraType();
            verifyCreativeChoices(client);
            var rest = client.getItemRenderer().getModel(classic, client.level, null, 11);
            for (var context : MOUNTS) {
                var poseStack = new PoseStack();
                ClientHooks.handleCameraTransforms(poseStack, rest, context, left(context));
                mounts.put(context, new Matrix4f(poseStack.last().pose()));
            }
            require(!mounts.get(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                            .equals(mounts.get(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND), 1E-5F)
                    && !mounts.get(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                            .equals(mounts.get(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND), 1E-5F),
                    "Classic left/right hand mounts were not distinct");
        }
        if (FOCUSED && !CupidLanternVisualProbe.icons(classic, smooth)) return false;

        if (trialIndex == TRIALS.size()) {
            if (reload == null) {
                require(new HashSet<>(smoothGeometry.values()).size() == Pose.values().length,
                        "Smooth drawing did not produce five distinct baked position meshes: " + smoothGeometry);
                reload = client.reloadResourcePacks();
                deadline = System.nanoTime() + 90_000_000_000L;
                return false;
            }
            require(System.nanoTime() < deadline, "Timed out reloading Cupid resources");
            if (!reload.isDone()) return false;
            reload.join();
            if (FOCUSED) CupidHeldScaleProbe.verify(client);
            verifyPose(client, classic, false, Pose.REST, null);
            verifyPose(client, smooth, true, Pose.REST, null);
            CupidBowVisualConfig.HELD_SCALE.set(savedScale);
            client.options.setCameraType(savedCamera);
            System.out.println("CUPID CREATIVE AND CLIENT POSES PASS classic and smooth, 0/8/15/20 draw poses, "
                    + "both hands, release, GUI and held/world geometry after reload");
            return true;
        }

        Trial trial = TRIALS.get(trialIndex);
        if (!requested) {
            client.options.setCameraType(trial.camera);
            CupidBowVisualConfig.HELD_SCALE.set(trial.scale);
            requested = true;
            deadline = System.nanoTime() + 20_000_000_000L;
            var server = client.getSingleplayerServer();
            var playerId = player.getUUID();
            server.execute(() -> {
                try {
                    var actor = server.getPlayerList().getPlayer(playerId);
                    require(actor != null && !actor.isCreative(), "Draw check expects the actual survival use path");
                    actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    actor.setItemInHand(trial.hand, (trial.smooth ? smooth : classic).copy());
                } catch (Throwable failure) {
                    serverFailure = failure;
                }
            });
            return false;
        }
        var stack = player.getItemInHand(trial.hand);
        require(System.nanoTime() < deadline, "Timed out during " + trial + " draw=" + drawing
                + " useTicks=" + player.getTicksUsingItem() + " stack=" + stack);
        if (!cupid(stack) || marked(stack) != trial.smooth) return false;
        if (released) {
            if (player.tickCount - releasedAt < (FOCUSED ? 3 : 0)) return false;
            finishTrial();
            return false;
        }
        if (!drawing) {
            var otherHand = trial.hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (!player.getItemInHand(otherHand).isEmpty() || player.isUsingItem()) return false;
            require(stack.getUseAnimation() == UseAnim.BOW, "Cupid lost its normal draw animation");
            verifyPose(client, stack, trial.smooth, Pose.REST, player);
            if (restingAt < 0) restingAt = player.tickCount;
            if (FOCUSED && player.tickCount - restingAt == 7) CupidLanternVisualProbe.request(trial.label() + "-rest");
            if (player.tickCount - restingAt < (FOCUSED ? 8 : 0)) return false;
            originalWear = stack.getDamageValue();
            client.options.keyUse.setDown(true);
            require(client.gameMode.useItem(player, trial.hand).consumesAction(), "Cupid draw could not start for " + trial);
            require(player.isUsingItem() && player.getUsedItemHand() == trial.hand && player.getUseItem() == stack,
                    "Active Cupid draw did not hold the selected stack and hand");
            drawing = true;
        }
        require(player.isUsingItem() && player.getUsedItemHand() == trial.hand && player.getUseItem() == stack,
                "Cupid stopped drawing before release for " + trial);
        int ticks = player.getTicksUsingItem();
        if (FOCUSED && Set.of(0, 8, 15, 20).contains(ticks) && renderedPoses.add(Pose.at(ticks).name())) {
            CupidLanternVisualProbe.request(trial.label() + "-" + Pose.at(ticks).name().toLowerCase());
        }
        if (Set.of(0, 7, 8, 14, 15, 19, 20).contains(ticks)) {
            drawTicks.add(ticks);
            verifyPose(client, stack, trial.smooth, Pose.at(ticks), player);
        }
        if (ticks < (FOCUSED ? 22 : 20)) return false;

        require(drawTicks.equals(Set.of(0, 7, 8, 14, 15, 19, 20)), "Missing draw boundary observations: " + drawTicks);
        require(stack.getDamageValue() == originalWear, "Drawing wore the bow before release");
        client.options.keyUse.setDown(false);
        client.gameMode.releaseUsingItem(player);
        require(!player.isUsingItem(), "Cupid release did not clear the active item");
        verifyPose(client, stack, trial.smooth, Pose.REST, player);
        if (FOCUSED) CupidLanternVisualProbe.request(trial.label() + "-release");

        System.out.println("CUPID DRAW CLIENT " + trial + " selected, drew through 0/8/15/20 and returned to rest");
        released = true;
        releasedAt = player.tickCount;
        return false;
    }

    private void finishTrial() {
        drawTicks.clear();
        renderedPoses.clear();
        requested = false;
        drawing = false;
        released = false;
        restingAt = -1;
        trialIndex++;
    }

    private void verifyCreativeChoices(Minecraft client) {
        var level = client.level;
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        CreativeModeTab search = CreativeModeTabs.searchTab();
        var variants = search.getDisplayItems().stream().filter(CupidBowProbe::cupid).toList();
        require(variants.size() == 2 && variants.stream().filter(CupidBowProbe::marked).count() == 1,
                "Expected exactly one classic and one smooth Cupid Bow in Creative search: " + variants);
        classic = variants.stream().filter(stack -> !marked(stack)).findFirst().orElseThrow().copy();
        smooth = variants.stream().filter(CupidBowProbe::marked).findFirst().orElseThrow().copy();
        require(classic.getItem() == smooth.getItem()
                        && "Cupid Bow".equals(classic.getHoverName().getString())
                        && classic.getHoverName().equals(smooth.getHoverName())
                        && classic.getMaxDamage() == 6666 && smooth.getMaxDamage() == 6666,
                "Cupid Creative options must share one registered bow, name, and durability");
        ItemStack withoutMarker = smooth.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, withoutMarker, tag -> tag.remove(STYLE));
        ItemStack defaultBow = classic.getItem().getDefaultInstance();
        require(!marked(defaultBow) && ItemStack.isSameItemSameComponents(classic, defaultBow)
                        && ItemStack.isSameItemSameComponents(classic, withoutMarker),
                "The cosmetic stack differed in a non-appearance component or changed the normal item default");
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab == search) continue;
            var tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            long marked = tab.getDisplayItems().stream().filter(CupidBowProbe::cupid).filter(CupidBowProbe::marked).count();
            require(marked == (UWU_TAB.equals(tabId) ? 1 : 0),
                    "Smooth Cupid leaked outside its intended Creative tab: " + tabId + ", marked copies=" + marked);
        }
        require(level.getRecipeManager().getRecipes().stream().noneMatch(holder -> {
            var output = holder.value().getResultItem(level.registryAccess());
            return cupid(output) && marked(output);
        }), "A crafting recipe grants the smooth appearance");
        System.out.println("CUPID CREATIVE CHOICES PASS same name and item, two search choices, smooth in UwU tab only");
    }

    private void verifyPose(Minecraft client, ItemStack stack, boolean isSmooth, Pose pose,
                            net.minecraft.world.entity.LivingEntity holder) {
        BakedModel model = client.getItemRenderer().getModel(stack, client.level, holder, 11);
        String texture = "uwudimension:item/" + (isSmooth ? "smooth_" : "") + "cupid_bow" + pose.suffix;
        require(model.getParticleIcon().contents().name().toString().equals(texture),
                "Item override did not select " + texture + " (selected " + model.getParticleIcon().contents().name() + ")");
        if (!isSmooth) return;

        var guiPose = new PoseStack();
        var gui = transformed(model, ItemDisplayContext.GUI, guiPose);
        // Retain the rising diagonal, but assert its outward curvature from actual rendered pixels
        // in CupidLanternVisualProbe. Rotation alone did not catch the Playtest 8/9 reversed curve.
        var top = guiPose.last().pose().transformDirection(0, 1, 0, new org.joml.Vector3f());
        require(top.x > 0 && top.y > 0 && Math.abs(top.x - top.y) < 1E-5F,
                "Smooth hotbar portrait did not follow the original rising diagonal: " + top);
        var portrait = quads(gui, stack);
        require(portrait.size() == 2 && sprites(portrait).equals(Set.of(texture)),
                "Smooth creative portrait did not use the selected flat art for " + pose);
        Long hash = null;
        for (var context : MOUNTS) {
            var poseStack = new PoseStack();
            var mounted = transformed(model, context, poseStack);
            require(mounts.get(context).equals(poseStack.last().pose(), 1E-5F),
                    "Smooth bow mount differs from classic " + context + " for " + pose);
            var mesh = quads(mounted, stack);
            require(mesh.size() > 100 && sprites(mesh).equals(Set.of("uwudimension:item/smooth_cupid_palette",
                            "uwudimension:item/smooth_cupid_lace")),
                    "Smooth Cupid held/world mesh is missing or uses unexpected sprites for " + context + " " + pose);
            long geometry = fingerprint(mesh);
            if (hash != null) require(hash == geometry, "A left/right or world mount switched the underlying smooth mesh");
            hash = geometry;
        }
        Long previous = smoothGeometry.putIfAbsent(pose, hash);
        require(previous == null || previous.equals(hash), "Baked geometry changed for the same draw pose " + pose);
    }

    private static List<BakedQuad> quads(BakedModel model, ItemStack stack) {
        return model.getRenderPasses(stack, false).stream()
                .flatMap(pass -> pass.getQuads(null, null, RandomSource.create(11), ModelData.EMPTY, null).stream()).toList();
    }

    private static Set<String> sprites(List<BakedQuad> quads) {
        return quads.stream().map(quad -> quad.getSprite().contents().name().toString()).collect(Collectors.toSet());
    }

    private static long fingerprint(List<BakedQuad> quads) {
        long hash = 1;
        for (var quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int i = 0; i < vertices.length; i += stride) {
                for (int axis = 0; axis < 3; axis++) hash = hash * 31 + vertices[i + axis];
            }
        }
        return hash;
    }

    private static BakedModel transformed(BakedModel model, ItemDisplayContext context, PoseStack pose) {
        return ClientHooks.handleCameraTransforms(pose, model, context, left(context));
    }

    private static boolean left(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private static boolean cupid(ItemStack stack) {
        return !stack.isEmpty() && BOW.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean marked(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(STYLE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
