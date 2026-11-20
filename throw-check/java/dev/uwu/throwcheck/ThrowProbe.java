package dev.uwu.throwcheck;

import dev.uwu.dimension.RelicFlight;
import dev.uwu.dimension.RevivalBlossoms;
import dev.uwu.dimension.SharedAbilityHotbar;
import dev.uwu.throwcheck.mixin.MousePress;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.lwjgl.glfw.GLFW;

/** Exercises native throws and server damage in a disposable integrated world. */
@Mod("uwu_throw_check")
public final class ThrowProbe {
    private static final String WITNESS = "uwu_throw_check_witness";
    private static final float THROW_PITCH = 3F;
    private static final float MELEE_YAW = 35.54F;
    private static final int MELEE_SLOT = 0;
    private static final double NEAR_DISTANCE = 4.25, FAR_DISTANCE = 10.75;
    private static final double NATIVE_DISTANCE = 8.25;

    private enum Stage { LOAD, EQUIP, SELECT, ARM, FLIGHT, MELEE, COOLDOWN_SELECT, COOLDOWN, DONE }

    private enum Case {
        BASELINE(0),
        UNPRIMED_NEAR(NEAR_DISTANCE), NEAR(NEAR_DISTANCE),
        UNPRIMED_FAR(FAR_DISTANCE), FAR(FAR_DISTANCE), MISS(0),
        UNPRIMED_BELLHEART(NATIVE_DISTANCE, "bellheart_axe"), BELLHEART(NATIVE_DISTANCE, "bellheart_axe"),
        UNPRIMED_TIDELACE(NATIVE_DISTANCE, "tidelace_trident"), TIDELACE(NATIVE_DISTANCE, "tidelace_trident");

        final double distance;
        final String nativeItem;
        Case(double distance) { this(distance, null); }
        Case(double distance, String nativeItem) {
            this.distance = distance;
            this.nativeItem = nativeItem;
        }

        Case fastVariant() {
            return switch (this) {
                case UNPRIMED_NEAR -> NEAR;
                case UNPRIMED_FAR -> FAR;
                case UNPRIMED_BELLHEART -> BELLHEART;
                case UNPRIMED_TIDELACE -> TIDELACE;
                default -> this;
            };
        }

        boolean unprimed() { return fastVariant() != this; }
        boolean hasMeleeTarget() { return this != MISS; }
        boolean hasPull() { return nativeItem != null; }
        Item item() { return hasPull() ? relic(nativeItem) : RevivalBlossoms.AXE.get(); }
    }

    private static final class Trial {
        final Case kind;
        final UUID marker = UUID.randomUUID();
        volatile Vec3 position;
        volatile Cow projectileTarget, meleeTarget;
        volatile boolean armed, meleeStarted, empty, landed;
        volatile RelicFlight flight;
        volatile int launches;
        volatile long impact = -1, caught = -1, melee = -1;
        volatile float launchStrength, returnStrength, damage;
        volatile double pull = Double.NaN;
        int seenHeldAt = -1;
        int meleeAttempts;

        Trial(Case kind) { this.kind = kind; }

        boolean matches(ItemStack stack) {
            if (!stack.is(kind.item())) return false;
            var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            return tag.hasUUID(WITNESS) && marker.equals(tag.getUUID(WITNESS));
        }

        String observations(LocalPlayer player) {
            return kind + ", casts=" + launches + ", strength before throw=" + launchStrength
                    + ", empty=" + empty + ", landed=" + landed + ", impact/catch/melee=" + impact + "/"
                    + caught + "/" + melee + ", pull=" + pull + ", damage=" + damage
                    + ", melee attempts=" + meleeAttempts + ", local charge=" + player.getAttackStrengthScale(0)
                    + ", hand=" + player.getMainHandItem();
        }
    }

    private static volatile Trial active;
    private static volatile Throwable serverFailure;
    private static Stage stage = Stage.LOAD;
    private static boolean opening;
    private static int entered;
    private static float fullChargeDamage;
    private static final EnumMap<Case, Long> unprimedTicks = new EnumMap<>(Case.class);
    private static Method attackEntry;

    public ThrowProbe() {
        NeoForge.EVENT_BUS.addListener(ThrowProbe::clientTick);
        NeoForge.EVENT_BUS.addListener(ThrowProbe::playerTick);
        NeoForge.EVENT_BUS.addListener(ThrowProbe::joined);
        NeoForge.EVENT_BUS.addListener(ThrowProbe::projectileTick);
        NeoForge.EVENT_BUS.addListener(ThrowProbe::damage);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (stage == Stage.DONE) {
            client.stop();
            return;
        }
        if (!opening && client.screen instanceof TitleScreen title) {
            opening = true;
            client.createWorldOpenFlows().createFreshLevel("ThrowCheck",
                    new LevelSettings("Throw and melee check", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(26057L, false, false), WorldPresets::createNormalWorldDimensions, title);
        } else if (opening && client.player == null && client.screen != null) {
            for (var widget : List.copyOf(client.screen.children())) {
                if (widget instanceof Button button && (button.getMessage().getString().equals("Proceed")
                        || button.getMessage().getString().equals("I know what I'm doing!"))) {
                    button.onPress();
                    break;
                }
            }
        }
        LocalPlayer player = client.player;
        if (player == null || client.screen != null) return;
        try {
            if (serverFailure != null) throw new AssertionError("Server arena setup failed", serverFailure);
            Trial trial = active;
            if (stage != Stage.LOAD) {
                require(player.tickCount - entered < 220, "Timed out in " + stage + ": " + trial.observations(player));
            }
            switch (stage) {
                case LOAD -> {
                    if (player.tickCount < 45) return;
                    require(ModList.get().isLoaded("bettercombat"), "Better Combat is required");
                    client.setWindowActive(true);
                    prepare(client, Case.BASELINE);
                }
                case EQUIP -> {
                    if (!trial.matches(player.getMainHandItem()) || trial.position == null
                            || player.distanceToSqr(trial.position) > .04 || !player.onGround()) return;
                    if (combat(client).getCurrentAttackHand() != null || combat(client).getComboCount() != 0) return;
                    require(WeaponRegistry.getAttributes(player.getMainHandItem()) != null,
                            "Better Combat did not assign a melee profile to " + trial.kind);
                    if (trial.kind == Case.BASELINE) {
                        if (player.getAttackStrengthScale(0) < 1F || !canSeeMeleeTarget(client, trial)) return;
                        tryMelee(client, trial);
                    } else {
                        if (player.getCooldowns().isOnCooldown(trial.kind.item())
                                || Math.abs(player.getYRot()) > .1F || Math.abs(player.getXRot() - THROW_PITCH) > .1F) return;
                        if (trial.kind.hasPull()) arm(client, trial);
                        else {
                            selectThrow(client);
                            enter(Stage.SELECT, player);
                        }
                    }
                }
                case SELECT -> {
                    require(SharedAbilityHotbar.selectedCell() == 1, "Dawnpetal Throw was not selected");
                    arm(client, trial);
                }
                case ARM -> {
                    if (!trial.armed) return;
                    // Better Combat gates native item use after an equip. The server stays
                    // undercharged for the throw check while the client reaches its input threshold.
                    if (trial.kind.hasPull() && player.getAttackStrengthScale(0) < 1F) return;
                    enter(Stage.FLIGHT, player);
                    rightClick(client);
                }
                case FLIGHT -> {
                    if (trial.flight != null && trial.kind.hasMeleeTarget()) {
                        player.setYRot(MELEE_YAW);
                        player.setXRot(trial.kind.hasPull() ? 12F : 5F);
                    }
                    if (trial.caught < 0 || !trial.matches(player.getMainHandItem())) return;
                    checkCatch(client, trial);
                }
                case MELEE -> {
                    if (trial.melee < 0) return;
                    if (trial.kind == Case.BASELINE) {
                        fullChargeDamage = trial.damage;
                        require(fullChargeDamage > 0, "Full-charge baseline dealt no normal melee damage");
                        System.out.println("THROW MELEE BASELINE PASS damage=" + fullChargeDamage);
                        prepare(client, Case.UNPRIMED_NEAR);
                        return;
                    }
                    long impactToMelee = trial.melee - trial.impact;
                    if (trial.kind.unprimed()) {
                        require(trial.impact >= 0 && impactToMelee > 0, "Unprimed timing missed an actual projectile hit");
                        Case variant = trial.kind.fastVariant();
                        unprimedTicks.put(variant, impactToMelee);
                        System.out.println("THROW " + trial.kind + " PASS " + timing(trial));
                        prepare(client, variant);
                        return;
                    }
                    if (!trial.kind.hasPull()) {
                        require(Math.abs(trial.damage - fullChargeDamage) < .05F,
                                "Post-catch melee damage " + trial.damage + " differs from full charge " + fullChargeDamage);
                    }
                    long previous = unprimedTicks.getOrDefault(trial.kind, 0L);
                    require(impactToMelee > 0 && 2 * impactToMelee < previous,
                            "Impact-to-melee was " + impactToMelee + "t versus " + previous
                                    + "t without the change: " + trial.observations(player));
                    System.out.println("THROW " + trial.kind + " PASS " + timing(trial)
                            + " unprimed-impact-to-melee=" + previous + "t");
                    switch (trial.kind) {
                        case NEAR -> {
                            selectThrow(client);
                            enter(Stage.COOLDOWN_SELECT, player);
                        }
                        case FAR -> prepare(client, Case.MISS);
                        case BELLHEART -> prepare(client, Case.UNPRIMED_TIDELACE);
                        case TIDELACE -> {
                            System.out.println("THROW CLIENT PASS");
                            stage = Stage.DONE;
                        }
                        default -> throw new AssertionError("Unexpected timing trial " + trial.kind);
                    }
                }
                case COOLDOWN_SELECT -> {
                    require(SharedAbilityHotbar.selectedCell() == 1
                                    && player.getCooldowns().isOnCooldown(trial.kind.item()),
                            "The fast catch removed the Throw ability cooldown");
                    enter(Stage.COOLDOWN, player);
                    rightClick(client);
                }
                case COOLDOWN -> {
                    require(trial.launches == 1 && trial.matches(player.getMainHandItem()),
                            "A second right-click bypassed the Throw cooldown");
                    if (player.tickCount - entered < 7) return;
                    System.out.println("THROW COOLDOWN PASS second real right-click rejected");
                    prepare(client, Case.UNPRIMED_FAR);
                }
                case DONE -> {}
            }
        } catch (Throwable failure) {
            System.err.println("THROW CLIENT CHECK FAILED");
            failure.printStackTrace();
            stage = Stage.DONE;
        }
    }

    private static void checkCatch(Minecraft client, Trial trial) throws ReflectiveOperationException {
        LocalPlayer player = client.player;
        if (trial.seenHeldAt < 0) trial.seenHeldAt = player.tickCount;
        require(trial.launches == 1 && trial.empty && player.getInventory().selected == MELEE_SLOT,
                "Original weapon did not leave and return to the same selected slot");
        require(trial.launchStrength < .5F, "Throw required the melee timer to be half charged");
        if (trial.kind == Case.MISS) {
            require(trial.landed && trial.impact < 0 && trial.returnStrength < .5F
                            && player.getAttackStrengthScale(0) < .5F,
                    "Ground-only return erroneously granted combat recharge: " + trial.observations(player));
            System.out.println("THROW MISS PASS block landing returned the stack without granting charge");
            prepare(client, Case.UNPRIMED_BELLHEART);
            return;
        }
        require(trial.impact >= 0 && !trial.landed, "No accepted projectile hit: " + trial.observations(player));
        require(!Double.isNaN(trial.pull) && (trial.kind.hasPull() ? trial.pull > .25 : trial.pull < .15),
                "Incorrect pull on the struck target: " + trial.observations(player));
        if (trial.kind.unprimed()) {
            require(trial.returnStrength < .5F, "Unprimed baseline received the new catch charge");
            if (canSeeMeleeTarget(client, trial)) tryMelee(client, trial);
            return;
        }
        require(trial.caught - trial.impact <= 6,
                "Accepted projectile hit did not get a fast catch: " + trial.observations(player));
        if (trial.kind.hasPull())
            require(trial.returnStrength >= .98F, "Successful hit did not recharge the server player");
        if (canSeeMeleeTarget(client, trial)) tryMelee(client, trial);
        require(player.tickCount - trial.seenHeldAt <= 4,
                "Catch did not enable immediate Better Combat input: " + trial.observations(player));
    }

    private static String timing(Trial trial) {
        return "impact-to-catch=" + (trial.caught - trial.impact) + "t"
                + " catch-to-melee=" + (trial.melee - trial.caught) + "t"
                + " impact-to-melee=" + (trial.melee - trial.impact) + "t"
                + " pre-throw charge=" + trial.launchStrength + " damage=" + trial.damage
                + " pull=" + trial.pull + " melee attempts=" + trial.meleeAttempts;
    }

    // Called only by the disposable probe mixin. Leave every other player and trial untouched.
    public static boolean disableCatchMechanics(ItemStack stack) {
        Trial trial = active;
        return trial != null && trial.kind.unprimed() && trial.matches(stack);
    }

    private static MinecraftClient_BetterCombat combat(Minecraft client) {
        return (MinecraftClient_BetterCombat)(Object)client;
    }

    private static void tryMelee(Minecraft client, Trial trial) throws ReflectiveOperationException {
        var combat = combat(client);
        if (combat.getCurrentAttackHand() != null) return;
        if (attackEntry == null) {
            attackEntry = Minecraft.class.getDeclaredMethod("startAttack");
            attackEntry.setAccessible(true);
        }
        var target = client.level.getEntity(trial.meleeTarget.getId());
        var previous = client.hitResult;
        trial.meleeAttempts++;
        try {
            client.hitResult = new EntityHitResult(target, target.getBoundingBox().getCenter());
            attackEntry.invoke(client);
        } finally {
            client.hitResult = previous;
        }
        var attack = combat.getCurrentAttackHand();
        if (attack == null) return; // The native input path decides readiness on every attempted tick.
        require(!attack.isOffHand() && trial.matches(attack.itemStack()),
                "Better Combat did not use the exact caught main-hand weapon");
        trial.meleeStarted = true;
        enter(Stage.MELEE, client.player);
    }

    private static boolean canSeeMeleeTarget(Minecraft client, Trial trial) {
        Cow target = trial.meleeTarget;
        return target != null && client.level.getEntity(target.getId()) != null;
    }

    private static void arm(Minecraft client, Trial trial) {
        serverAction(client, owner -> {
            owner.resetAttackStrengthTicker();
            trial.armed = true;
        });
        enter(Stage.ARM, client.player);
    }

    private static void prepare(Minecraft client, Case kind) {
        Trial old = active;
        Trial trial = new Trial(kind);
        active = trial;
        serverAction(client, owner -> {
            if (old == null) {
                trial.position = arena(owner);
            } else {
                if (old.projectileTarget != null) old.projectileTarget.discard();
                if (old.meleeTarget != null) old.meleeTarget.discard();
                trial.position = old.position;
            }
            if (kind.distance > 0) trial.projectileTarget = target(owner, trial.position.add(0, 0, kind.distance));
            if (kind.hasMeleeTarget()) trial.meleeTarget = target(owner, trial.position.add(-1.5, 0, 2.1));
            var stack = new ItemStack(kind.item());
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
                data.putUUID(WITNESS, trial.marker);
                if (!kind.hasPull()) data.putUUID("uwu_revival_id", trial.marker);
            });
            owner.getInventory().selected = MELEE_SLOT;
            owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
            owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            require(owner.teleportTo(owner.serverLevel(), trial.position.x, trial.position.y, trial.position.z,
                            Set.of(), kind == Case.BASELINE ? MELEE_YAW : 0F, kind == Case.BASELINE ? 5F : THROW_PITCH),
                    "Could not position the real player in the throw lane");
            owner.setDeltaMovement(Vec3.ZERO);
            owner.fallDistance = 0;
            owner.containerMenu.broadcastChanges();
        });
        enter(Stage.EQUIP, client.player);
    }

    private static Vec3 arena(ServerPlayer owner) {
        var level = owner.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        BlockPos floor = owner.blockPosition().above(16);
        for (int z = -2; z <= 15; z++) {
            for (int x = -3; x <= 3; x++) {
                var base = floor.offset(x, 0, z);
                level.setBlockAndUpdate(base, Blocks.DEEPSLATE.defaultBlockState());
                for (int y = 1; y <= 3; y++) {
                    level.setBlockAndUpdate(base.above(y),
                            z == 15 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        return Vec3.atBottomCenterOf(floor.above());
    }

    private static Cow target(ServerPlayer owner, Vec3 position) {
        var cow = EntityType.COW.create(owner.serverLevel());
        require(cow != null, "Could not make a stationary test target");
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(250);
        cow.setHealth(250);
        cow.setNoAi(true);
        cow.moveTo(position.x, position.y, position.z, 0, 0);
        require(owner.serverLevel().addFreshEntity(cow), "Could not place the test target");
        return cow;
    }

    private static void joined(EntityJoinLevelEvent event) {
        Trial trial = active;
        if (trial == null || event.getLevel().isClientSide() || !(event.getEntity() instanceof RelicFlight flight)
                || !(flight.getOwner() instanceof ServerPlayer owner) || !flight.physical()
                || !trial.matches(flight.getItem())) return;
        trial.launches++;
        trial.launchStrength = owner.getAttackStrengthScale(.5F);
        trial.flight = flight;
    }

    private static void playerTick(PlayerTickEvent.Post event) {
        Trial trial = active;
        if (!(event.getEntity() instanceof ServerPlayer owner) || trial == null || !trial.armed) return;
        if (trial.flight == null) {
            // Only prepare the deliberately weak charge before the request. The complete flight,
            // impact, re-equip, and subsequent left-click all use naturally advancing counters.
            owner.resetAttackStrengthTicker();
        } else if (owner.getMainHandItem().isEmpty()) {
            trial.empty = true;
        } else if (trial.empty && trial.caught < 0 && trial.matches(owner.getMainHandItem())) {
            trial.returnStrength = owner.getAttackStrengthScale(0);
            trial.caught = owner.level().getGameTime();
        }
    }

    private static void projectileTick(EntityTickEvent.Post event) {
        Trial trial = active;
        if (trial == null || trial.flight == null || event.getEntity() != trial.flight) return;
        trial.landed |= trial.flight.landed();
        if (trial.impact >= 0 && trial.projectileTarget != null && Double.isNaN(trial.pull)) {
            // Both targets are due south of the player and throw origin. Negative Z means toward the thrower.
            trial.pull = -trial.projectileTarget.getDeltaMovement().z;
        }
    }

    private static void damage(LivingDamageEvent.Post event) {
        Trial trial = active;
        if (trial == null || event.getNewDamage() <= 0
                || !(event.getSource().getEntity() instanceof ServerPlayer owner)) return;
        if (event.getEntity() == trial.projectileTarget && trial.flight != null && !trial.meleeStarted) {
            trial.impact = owner.level().getGameTime();
        } else if (event.getEntity() == trial.meleeTarget && trial.meleeStarted
                && event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            trial.damage = event.getNewDamage();
            trial.melee = owner.level().getGameTime();
        }
    }

    private static Item relic(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("uwudimension", path));
    }

    private static void selectThrow(Minecraft client) {
        long window = client.getWindow().getWindow();
        client.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_2, 0, GLFW.GLFW_PRESS, 0);
        client.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_2, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void rightClick(Minecraft client) {
        var mouse = (MousePress)(Object)client.mouseHandler;
        long window = client.getWindow().getWindow();
        mouse.throwCheck$press(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_PRESS, 0);
        mouse.throwCheck$press(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_RELEASE, 0);
    }

    private static void serverAction(Minecraft client, Consumer<ServerPlayer> action) {
        var server = client.getSingleplayerServer();
        UUID id = client.player.getUUID();
        server.execute(() -> {
            try {
                action.accept(server.getPlayerList().getPlayer(id));
            } catch (Throwable failure) {
                serverFailure = failure;
            }
        });
    }

    private static void enter(Stage next, LocalPlayer player) {
        entered = player.tickCount;
        stage = next;
    }

    private static void require(boolean ok, String problem) {
        if (!ok) throw new AssertionError(problem);
    }
}
