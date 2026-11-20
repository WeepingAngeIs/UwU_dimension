package dev.uwu.guardiancheck;

import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.People;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class GuardianArena {
    private final ServerLevel level;
    private final Set<ChunkPos> tickets = new HashSet<>();

    GuardianArena(ServerLevel level) {
        this.level = level;
    }

    boolean entitiesReady() {
        for (ChunkPos chunk : tickets) {
            if (!level.areEntitiesLoaded(chunk.toLong())
                    || !level.isPositionEntityTicking(chunk.getWorldPosition())) return false;
        }
        return !tickets.isEmpty();
    }

    /** Keep only the short lanes this test touches entity-ticking. */
    void floor(BlockPos home, int fromX, int toX, int halfWidth) {
        for (int x = fromX; x <= toX; x++) {
            for (int z = -halfWidth; z <= halfWidth; z++) {
                BlockPos at = home.offset(x, 0, z);
                ChunkPos chunk = new ChunkPos(at);
                if (tickets.add(chunk)) level.setChunkForced(chunk.x, chunk.z, true);
                level.setBlock(at.below(), Blocks.CALCITE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int dy = 0; dy < 4; dy++) {
                    level.setBlock(at.above(dy), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The only passage in the central partition is one two-block-high door. */
    BlockPos doorLane(BlockPos home, boolean iron) {
        floor(home, -3, 21, 3);
        BlockPos door = home.east(9);
        for (int x = -3; x <= 21; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z : new int[]{-2, 2}) wall(home.offset(x, y, z));
            }
        }
        for (int y = 0; y < 4; y++) {
            for (int z = -1; z <= 1; z++) {
                wall(home.offset(-3, y, z));
                wall(home.offset(21, y, z));
                if (z != 0 || y >= 2) wall(door.offset(0, y, z));
            }
        }
        var lower = (iron ? Blocks.IRON_DOOR : Blocks.OAK_DOOR).defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.EAST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(door, lower, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return door;
    }

    private void wall(BlockPos at) {
        level.setBlock(at, Blocks.CALCITE.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    BlossomWarden natural(BlockPos court) throws ReflectiveOperationException {
        Method create = BlossomWarden.class.getDeclaredMethod("ensureVillageGuard", ServerLevel.class, BlockPos.class);
        create.setAccessible(true);
        var guard = (BlossomWarden) create.invoke(null, level, court);
        require(guard != null && guard.isAddedToLevel(), "Village guardian spawn failed");
        return guard;
    }

    BlossomWarden egg(BlockPos feet, boolean female) {
        require(level.isPositionEntityTicking(feet), "Egg used before entity tracking at " + feet);
        var before = level.getEntitiesOfClass(BlossomWarden.class, new AABB(feet).inflate(5));
        var player = FakePlayerFactory.getMinecraft(level);
        var stack = People.guardianEgg(female);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(feet), Direction.UP, feet.below(), false);
        var result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        var after = level.getEntitiesOfClass(BlossomWarden.class, new AABB(feet).inflate(5))
                .stream().filter(guard -> !before.contains(guard)).toList();
        require(result.consumesAction() && stack.isEmpty(), "Guardian egg was not consumed at " + feet);
        require(after.size() == 1, "Expected one new tracked Guardian at " + feet + ", found " + after.size());
        require(after.getFirst().female() == female, "Guardian spawned with the wrong variant at " + feet
                + ", expected female=" + female);
        return after.getFirst();
    }

    BlossomWarden restore(BlossomWarden guard) {
        CompoundTag state = guard.saveWithoutId(new CompoundTag());
        var restored = People.BLOSSOM_WARDEN.get().create(level);
        require(restored != null, "Could not construct the saved guardian type");
        restored.load(state);
        guard.discard();
        require(level.addFreshEntity(restored), "Could not add the reloaded guardian");
        return restored;
    }

    static void move(BlossomWarden guard, BlockPos feet) {
        guard.getNavigation().stop();
        guard.moveTo(feet.getX() + .5, feet.getY(), feet.getZ() + .5, 0, 0);
        guard.setDeltaMovement(Vec3.ZERO);
        guard.fallDistance = 0;
    }

    boolean open(BlockPos door) {
        return level.getBlockState(door).getValue(DoorBlock.OPEN);
    }

    static void require(boolean ok, String problem) {
        if (!ok) throw new AssertionError(problem);
    }
}
