package dev.uwu.dimension;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/** Sparse, client-local accents accompany vanilla precipitation in Sakura Reach. */
@EventBusSubscriber(modid = "uwudimension", value = Dist.CLIENT)
public final class SakuraWeatherClient {
    private static final ResourceKey<Biome> SNOW = biome("starlace_frostlands");
    private static final ResourceKey<Biome> DUSK_SNOW = biome("dusk_frostlands");
    private static final ResourceKey<Biome> DUSK = biome("velvet_highlands");
    private static final ResourceKey<Biome> REEF = biome("blush_reef");
    private static final ResourceKey<Biome> OCEAN = biome("moonwake_trench");
    private static final DustParticleOptions ROSE = dust(1.0F,.76F,.86F);
    private static final DustParticleOptions PEARL = dust(.84F,.94F,1F);
    private static final DustParticleOptions LILAC = dust(.74F,.72F,.95F);
    private SakuraWeatherClient() {}

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, UwUDimension.id(name));
    }
    private static DustParticleOptions dust(float r, float g, float b) {
        return new DustParticleOptions(new Vector3f(r,g,b),.5F);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        var player=mc.player;
        var level=mc.level;
        if (player==null || level==null || !level.dimension().equals(ReefLife.REACH)
                || !level.isRaining() || SacredSpring.WEATHER.get()==SacredSpring.Overlay.OFF) return;
        int interval=SacredSpring.WEATHER.get()==SacredSpring.Overlay.REDUCED ? 32 : 10;
        if (player.tickCount%interval!=0) return;
        var random=player.getRandom();
        double x=player.getX()+(random.nextDouble()-.5)*10;
        double y=player.getY()+3+random.nextDouble()*2;
        double z=player.getZ()+(random.nextDouble()-.5)*10;
        BlockPos at=BlockPos.containing(x,y,z);
        if (!level.hasChunkAt(at) || !level.isEmptyBlock(at) || !level.canSeeSky(at)) return;
        var biome=level.getBiome(at);
        if (biome.is(SNOW) || biome.is(DUSK_SNOW)) {
            level.addParticle(WeatherParticles.SNOW_HEART.get(),x,y,z,0,0,0);
            return;
        }
        if ((biome.is(REEF) || biome.is(OCEAN)) && random.nextInt(3)!=0) return;
        DustParticleOptions color=biome.is(DUSK) ? LILAC : random.nextBoolean() ? PEARL : ROSE;
        level.addParticle(color,x,y,z,0,-.08,0);
    }
}

