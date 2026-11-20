package dev.uwu.guardiancheck;

import static dev.uwu.guardiancheck.GuardianArena.require;

import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.People;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives real server ticks in a short disposable patrol lane and enclosed door lanes. */
@Mod("uwu_guardian_home_check")
public final class GuardianHomeProbe {
    private enum Phase { PREPARE, LOAD_ARENA, PERSONAL, BIND, DAY_EDGE, DAY_CAP, DAY_HOME, IDLE_START, IDLE_BEFORE, IDLE_AFTER, NIGHT, NIGHT_HOME, MORNING, DOOR, DONE }
    private Phase phase = Phase.PREPARE;
    private int tick, phaseStarted;
    private ServerLevel level;
    private GuardianArena arena;
    private BlockPos court, eggStart, remoteEggStart, femaleHome, maleHome, ironHome;
    private BlockPos femaleDoor, maleDoor, ironDoor;
    private BlossomWarden natural, nearbyEgg, remoteEgg, female, male, iron;
    private boolean femaleOpened;
    private double walkSpeed;
    private Object patrol;

    public GuardianHomeProbe() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ServerTickEvent.Post event) {
        if (phase == Phase.DONE) return;
        tick++;
        try {
            switch (phase) {
                case PREPARE -> prepare(event.getServer().overworld());
                case LOAD_ARENA -> {
                    if (!arena.entitiesReady()) {
                        require(elapsed() < 600, "Arena failed to reach entity-ticking status within 600 server ticks");
                        return;
                    }
                    remoteEgg = arena.egg(remoteEggStart, false);
                    next(Phase.PERSONAL);
                }
                case PERSONAL -> {
                    if (elapsed() < 6) return;
                    require(villageHome(remoteEgg) == null && savedCourt(remoteEgg).equals(remoteEggStart),
                            "Egg Guardian without a known settlement should keep a personal patrol");
                    remoteEgg = arena.restore(remoteEgg);
                    require(villageHome(remoteEgg) == null && savedCourt(remoteEgg).equals(remoteEggStart)
                            && !remoteEgg.female(), "Personal egg anchor or sex changed on reload");
                    remoteEgg.discard();
                    seedSettlement();
                    next(Phase.BIND);
                }
                case BIND -> {
                    if (elapsed() < 6) return;
                    require(court.equals(villageHome(natural)), "Natural guardian lost its supplied village plaza");
                    require(court.equals(villageHome(nearbyEgg)), "Nearby egg Guardian did not bind to the loaded village");
                    require(nearbyEgg.female(), "Village egg variant did not survive initial ticks");

                    GuardianArena.move(natural, court.east(38));
                    GuardianArena.move(nearbyEgg, court.east(40));
                    natural = arena.restore(natural);
                    nearbyEgg = arena.restore(nearbyEgg);
                    require(court.equals(villageHome(natural)) && court.equals(villageHome(nearbyEgg)) && nearbyEgg.female(),
                            "Guardian reload changed its home or sex");
                    String dimension = level.dimension().location().toString();
                    require(dimension.equals(natural.saveWithoutId(new CompoundTag()).getString("VillageHomeDimension"))
                                    && dimension.equals(nearbyEgg.saveWithoutId(new CompoundTag()).getString("VillageHomeDimension")),
                            "Guardian save lost its authoritative home dimension");
                    nearbyEgg.discard();
                    System.out.println("GUARDIAN HOME BINDING PASS natural village, nearby egg, personal egg and entity save/reload");

                    patrol = patrolFor(natural);
                    var speed = natural.getAttribute(Attributes.MOVEMENT_SPEED);
                    walkSpeed = speed.getBaseValue();
                    speed.setBaseValue(0);
                    GuardianArena.move(natural, court.east(48 + 64));
                    next(Phase.DAY_EDGE);
                }
                case DAY_EDGE -> {
                    if (elapsed() < 5) return;
                    require(outside() == 64 * 64 && !returning(), "Allowed daytime edge is not 64 blocks beyond the precinct");
                    GuardianArena.move(natural, court.east(48 + 65));
                    next(Phase.DAY_CAP);
                }
                case DAY_CAP -> {
                    if (elapsed() < 8) return;
                    require(outside() == 65 * 65 && natural.getNavigation().isDone(),
                            "Guardian tried to patrol beyond the 64-block daylight boundary");
                    GuardianArena.move(natural, court);
                    next(Phase.DAY_HOME);
                }
                case DAY_HOME -> {
                    if (elapsed() < 4) return;
                    GuardianArena.move(natural, court.east(36));
                    next(Phase.IDLE_START);
                }
                case IDLE_START -> {
                    if (elapsed() < 6) return;
                    require(!returning() && stationaryTicks() > 0 && stationaryTicks() < 4800,
                            "Daytime guardian did not count real stationary, noncombat ticks");
                    // Skip the uneventful wait, then let two real entity ticks cross the boundary.
                    Field timer = patrol.getClass().getDeclaredField("stationaryTicks");
                    timer.setAccessible(true);
                    timer.setInt(patrol, 4798);
                    next(Phase.IDLE_BEFORE);
                }
                case IDLE_BEFORE -> {
                    require(stationaryTicks() == 4799 && !returning(), "Idle return fired before 4800 stationary ticks");
                    next(Phase.IDLE_AFTER);
                }
                case IDLE_AFTER -> {
                    require(stationaryTicks() == 4800 && returning(), "Idle return did not fire at 4800 stationary ticks");
                    // Arriving home completes the idle return. A new short outing then tests night separately.
                    GuardianArena.move(natural, court);
                    next(Phase.NIGHT);
                }
                case NIGHT -> {
                    if (elapsed() == 3) {
                        GuardianArena.move(natural, court.east(36));
                        level.setDayTime(14000);
                    }
                    if (elapsed() < 11) return;
                    require(returning() && stationaryTicks() < 20 && natural.getNavigation().getPath() != null,
                            "Night return waited for the four-minute timer or failed to plan an inward route");
                    require(natural.isSprinting(), "Night return was not a sprint");
                    natural.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(walkSpeed);
                    next(Phase.NIGHT_HOME);
                }
                case NIGHT_HOME -> {
                    require(elapsed() <= 300, "Guardian did not return to the plaza at night: " + natural.position());
                    if (returning()) return;
                    require(natural.distanceToSqr(Vec3.atBottomCenterOf(court)) <= 8 * 8,
                            "Guardian resumed normal travel outside its nighttime home");
                    if (elapsed() < 45) return;
                    require(natural.getNavigation().isDone() && !natural.isSprinting(), "Guardian kept patrolling at night");
                    GuardianArena.move(natural, court);
                    level.setDayTime(0);
                    next(Phase.MORNING);
                }
                case MORNING -> {
                    require(elapsed() <= 120, "Guardian did not resume its daytime patrol");
                    if (natural.distanceToSqr(Vec3.atBottomCenterOf(court)) < 16) return;
                    require(!returning(), "Daytime travel still used the nighttime return");
                    natural.discard();
                    level.setDayTime(14000);
                    System.out.println("GUARDIAN HOME SCHEDULE PASS daylight limit, 4800 idle ticks, night sprint and morning patrol");
                    startDoors();
                    next(Phase.DOOR);
                }
                case DOOR -> {
                    femaleOpened |= arena.open(femaleDoor);
                    require(!arena.open(maleDoor), "Male guardian opened the ordinary wooden door");
                    require(!arena.open(ironDoor), "Female guardian opened an iron door");
                    if (elapsed() > 280) throw new AssertionError("Female door route did not finish: opened="
                            + femaleOpened + ", position=" + female.position() + ", navigation="
                            + female.getNavigation().getTargetPos());
                    if (!femaleOpened || female.getX() >= femaleDoor.getX() || arena.open(femaleDoor)) return;
                    System.out.println("GUARDIAN HOME DOORS PASS female navigated wooden door and reclosed it,"
                            + " male wooden and female iron stayed closed");
                    System.out.println("GUARDIAN HOME SERVER PASS");
                    phase = Phase.DONE;
                    event.getServer().halt(false);
                }
                case DONE -> {}
            }
        } catch (Throwable failure) {
            phase = Phase.DONE;
            System.err.println("GUARDIAN HOME CHECK FAILED");
            failure.printStackTrace();
            event.getServer().halt(false);
        }
    }

    private void prepare(ServerLevel world) throws ReflectiveOperationException {
        level = world;
        arena = new GuardianArena(level);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(6000);
        court = level.getSharedSpawnPos().above(16);
        eggStart = court.west(7);
        remoteEggStart = court.offset(0, 0, 150);
        arena.floor(remoteEggStart, -3, 3, 3);
        arena.floor(court, -11, 119, 2);
        femaleHome = court.offset(0, 0, 200);
        maleHome = femaleHome.south(10);
        ironHome = maleHome.south(10);
        femaleDoor = arena.doorLane(femaleHome, false);
        maleDoor = arena.doorLane(maleHome, false);
        ironDoor = arena.doorLane(ironHome, true);
        // A force ticket loads terrain first. Entity visibility and ticking follow on later server ticks.
        next(Phase.LOAD_ARENA);
    }

    private void seedSettlement() throws ReflectiveOperationException {
        plaza(court);
        natural = arena.natural(court);
        nearbyEgg = arena.egg(eggStart, true);
    }

    private void plaza(BlockPos center) {
        var quilt = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("uwudimension", "petal_quilt"));
        level.setBlock(center.below(), quilt.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockPos bell = center.offset(2, 0, -2);
        level.setBlock(bell, Blocks.BELL.defaultBlockState(), Block.UPDATE_CLIENTS);
        var keeper = People.PETAL_KEEPER.get().create(level);
        require(keeper != null, "Failed to make settlement witness");
        Vec3 at = Vec3.atBottomCenterOf(center.north(1));
        keeper.moveTo(at.x, at.y, at.z, 0, 0);
        keeper.bindVillage(center);
        keeper.setNoAi(true);
        require(level.addFreshEntity(keeper), "Failed to register settlement witness");
    }

    private void startDoors() {
        female = arena.egg(femaleHome, true);
        male = arena.egg(maleHome, false);
        iron = arena.egg(ironHome, true);
        for (var pair : List.of(new Lane(female, femaleHome), new Lane(male, maleHome), new Lane(iron, ironHome))) {
            pair.guard().bindVillage(pair.home());
            GuardianArena.move(pair.guard(), pair.home().east(17));
        }
    }

    private record Lane(BlossomWarden guard, BlockPos home) {}

    private static BlockPos savedCourt(BlossomWarden guard) {
        CompoundTag state = guard.saveWithoutId(new CompoundTag());
        require(state.contains("VillageCourt"), "Guardian has no saved home at " + guard.position());
        return BlockPos.of(state.getLong("VillageCourt"));
    }

    private static Object patrolFor(BlossomWarden guard) {
        return guard.goalSelector.getAvailableGoals().stream().map(entry -> entry.getGoal())
                .filter(goal -> goal.getClass().getSimpleName().equals("GuardianPatrolGoal"))
                .findFirst().orElseThrow(() -> new AssertionError("Guardian patrol goal is not attached"));
    }

    private double outside() throws ReflectiveOperationException {
        var method = patrol.getClass().getDeclaredMethod("outsideDistanceSquared", Vec3.class, BlockPos.class);
        method.setAccessible(true);
        return (Double) method.invoke(null, natural.position(), court);
    }

    private static BlockPos villageHome(BlossomWarden guard) throws ReflectiveOperationException {
        var method = BlossomWarden.class.getDeclaredMethod("villageHome");
        method.setAccessible(true);
        return (BlockPos) method.invoke(guard);
    }

    private int stationaryTicks() throws ReflectiveOperationException {
        var method = patrol.getClass().getDeclaredMethod("stationaryTicks");
        method.setAccessible(true);
        return (Integer) method.invoke(patrol);
    }

    private boolean returning() throws ReflectiveOperationException {
        var method = patrol.getClass().getDeclaredMethod("returningHome");
        method.setAccessible(true);
        return (Boolean) method.invoke(patrol);
    }

    private int elapsed() {
        return tick - phaseStarted;
    }

    private void next(Phase next) {
        phase = next;
        phaseStarted = tick;
    }
}
