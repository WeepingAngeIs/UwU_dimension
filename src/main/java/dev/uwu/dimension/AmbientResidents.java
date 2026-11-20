package dev.uwu.dimension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;

/** Read-only checks for the actual resident, household and optional village bird. */
final class AmbientResidents {
    private static final ResourceLocation CROW = ResourceLocation.fromNamespaceAndPath("uwudimension", "dusk_crow");
    private static final double CROW_DISTANCE = 16.0;

    private AmbientResidents() {}

    static String sceneFor(ServerPlayer player, Entity target) {
        if (!(target instanceof Keeper keeper) || player.level() != keeper.level()
                || !(player.level() instanceof ServerLevel level) || !ReefLife.isSettlementRealm(level)
                || !player.isAlive() || player.isRemoved() || player.isSleeping() || player.isSpectator()
                || !keeper.isAlive() || keeper.isRemoved() || keeper.isBaby() || keeper.isSleeping()
                || keeper.isTrading() || keeper.getTarget() != null || keeper.ownerId() != null
                || keeper.attacks(player) || !player.hasLineOfSight(keeper)
                || !SakuraAccess.mayInteract(player, keeper.blockPosition())) return null;

        BlockPos court = keeper.villageCourt();
        if (court == null) return null;
        CompoundTag data = keeper.getPersistentData();
        if (data.contains("MoonveilHouse") || data.contains("MoonveilSlot")
                || data.contains("MoonveilBed") || data.contains("MoonveilWork"))
            return moonveilScene(level, keeper);
        if (keeper.dusk() && keeper.blockPosition().closerThan(court, 48)) return "dusk_village";
        return null;
    }

    private static String moonveilScene(ServerLevel level, Keeper keeper) {
        CompoundTag data = keeper.getPersistentData();
        if (!data.contains("MoonveilHouse", Tag.TAG_LONG) || !data.contains("MoonveilSlot", Tag.TAG_INT)) return null;
        int slot = data.getInt("MoonveilSlot");
        if (slot != 0 && slot != 1) return null;
        var profession = keeper.getVillagerData().getProfession();
        if (profession != (slot == 0 ? VillagerProfession.SHEPHERD : VillagerProfession.CARTOGRAPHER)) return null;
        BlockPos house = BlockPos.of(data.getLong("MoonveilHouse"));
        String identity = "uwudimension:sakura_reach/moonveil/" + level.getSeed() + "/" + house.asLong() + "/" + slot;
        if (!keeper.getUUID().equals(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)))) return null;
        BlockPos position = keeper.blockPosition();
        if (Math.abs((long) position.getX() - house.getX()) > MoonveilTreehouse.FOOTPRINT
                || Math.abs((long) position.getZ() - house.getZ()) > MoonveilTreehouse.FOOTPRINT
                || position.getY() < house.getY() - 3 || position.getY() > house.getY() + 35) return null;
        return slot == 0 ? "moonveil_lower" : "moonveil_upper";
    }

    /** Call only after sceneFor has returned dusk_village for this player and target. */
    static boolean hasCrow(ServerPlayer player, Entity target) {
        if (!(target instanceof Keeper keeper) || keeper.level() != player.level()
                || !keeper.dusk() || keeper.villageCourt() == null) return false;
        BlockPos court = keeper.villageCourt();
        var level = player.serverLevel();
        var box = new AABB(keeper.blockPosition()).inflate(CROW_DISTANCE);
        for (LivingEntity bird : level.getEntitiesOfClass(LivingEntity.class, box, Entity::isAlive)) {
            if (!CROW.equals(BuiltInRegistries.ENTITY_TYPE.getKey(bird.getType()))
                    || bird.distanceToSqr(keeper) > CROW_DISTANCE * CROW_DISTANCE) continue;
            CompoundTag saved = bird.saveWithoutId(new CompoundTag());
            if (saved.contains("UwUCrowCourt", Tag.TAG_LONG) && saved.getLong("UwUCrowCourt") == court.asLong())
                return true;
        }
        return false;
    }

}
