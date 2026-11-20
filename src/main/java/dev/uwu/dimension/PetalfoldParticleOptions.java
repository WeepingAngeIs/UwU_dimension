package dev.uwu.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/** A single world-space fleck, with an alternate trajectory only on the shield holder's connection. */
record PetalfoldParticleOptions(boolean heart, Optional<FirstPerson> firstPerson) implements ParticleOptions {
    record FirstPerson(Vec3 offset, Vec3 velocityOffset) {
        private static final Codec<FirstPerson> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Vec3.CODEC.fieldOf("offset").forGetter(FirstPerson::offset),
                Vec3.CODEC.fieldOf("velocity_offset").forGetter(FirstPerson::velocityOffset)
        ).apply(instance, FirstPerson::new));

        private static final StreamCodec<ByteBuf, Vec3> VECTOR = ByteBufCodecs.VECTOR3F.map(Vec3::new, Vec3::toVector3f);
        private static final StreamCodec<ByteBuf, FirstPerson> STREAM_CODEC = StreamCodec.composite(
                VECTOR, FirstPerson::offset, VECTOR, FirstPerson::velocityOffset, FirstPerson::new);
    }

    static PetalfoldParticleOptions create(boolean heart, Vec3 viewOffset, Vec3 viewVelocityOffset) {
        if ((viewOffset == null) != (viewVelocityOffset == null))
            throw new IllegalArgumentException("A first-person trajectory requires both offsets");
        return new PetalfoldParticleOptions(heart, viewOffset == null ? Optional.empty()
                : Optional.of(new FirstPerson(viewOffset, viewVelocityOffset)));
    }

    static ParticleType<PetalfoldParticleOptions> type(boolean heart) {
        return new ParticleType<>(false) {
            private final MapCodec<PetalfoldParticleOptions> codec = FirstPerson.CODEC.optionalFieldOf("first_person")
                    .xmap(view -> new PetalfoldParticleOptions(heart, view), PetalfoldParticleOptions::firstPerson);
            private final StreamCodec<ByteBuf, PetalfoldParticleOptions> stream = ByteBufCodecs.optional(FirstPerson.STREAM_CODEC)
                    .map(view -> new PetalfoldParticleOptions(heart, view), PetalfoldParticleOptions::firstPerson);

            @Override public MapCodec<PetalfoldParticleOptions> codec() { return codec; }
            @Override public StreamCodec<ByteBuf, PetalfoldParticleOptions> streamCodec() { return stream; }
        };
    }

    @Override public ParticleType<PetalfoldParticleOptions> getType() {
        return (heart ? HeartShieldEffects.PETALFOLD_HEART : HeartShieldEffects.PETALFOLD_FLOWER).get();
    }
}
