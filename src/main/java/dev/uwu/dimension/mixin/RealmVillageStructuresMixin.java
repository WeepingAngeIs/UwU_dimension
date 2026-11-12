package dev.uwu.dimension.mixin;

import dev.uwu.dimension.RealmResidents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
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

/** Blocks vanilla village starts only inside UwU realms; other dimensions keep vanilla structures. */
@Mixin(StructureManager.class)
public abstract class RealmVillageStructuresMixin {
    @Shadow @Final private LevelAccessor level;
    @Shadow @Final private RegistryAccess registryAccess;

    @Inject(method="setStartForStructure", at=@At("HEAD"), cancellable=true)
    private void uwu$skipVanillaVillageStarts(SectionPos section, Structure structure, StructureStart start,
            StructureAccess chunk, CallbackInfo callback) {
        var dimension = level instanceof ServerLevel server ? server.dimension()
                : level instanceof WorldGenRegion region ? region.getLevel().dimension() : null;
        if (dimension == null || !RealmResidents.appliesDimension(dimension)) return;
        var id = registryAccess.registryOrThrow(Registries.STRUCTURE).getKey(structure);
        if (id != null && id.getNamespace().equals("minecraft") && id.getPath().startsWith("village_"))
            callback.cancel();
    }
}
