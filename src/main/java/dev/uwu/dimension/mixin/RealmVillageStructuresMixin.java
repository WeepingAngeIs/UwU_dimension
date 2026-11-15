package dev.uwu.dimension.mixin;

import dev.uwu.dimension.RealmResidents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercepts new vanilla and pack-defined villager settlements inside Sakura Reach. */
@Mixin(StructureManager.class)
public abstract class RealmVillageStructuresMixin {
    @Shadow @Final private LevelAccessor level;
    @Shadow public abstract RegistryAccess registryAccess();

    @Inject(method="setStartForStructure", at=@At("HEAD"), cancellable=true)
    private void uwu$skipVanillaVillageStarts(SectionPos section, Structure structure, StructureStart start,
            StructureAccess chunk, CallbackInfo callback) {
        var dimension = level instanceof ServerLevel server ? server.dimension()
                : level instanceof WorldGenRegion region ? region.getLevel().dimension() : null;
        if (dimension == null || !RealmResidents.appliesDimension(dimension)) return;
        var registry=registryAccess().registryOrThrow(Registries.STRUCTURE);
        var tag=TagKey.create(Registries.STRUCTURE,ResourceLocation.fromNamespaceAndPath("uwudimension","replaced_villages"));
        if(registry.wrapAsHolder(structure).is(tag))callback.cancel();
    }
}
