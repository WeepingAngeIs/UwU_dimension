package dev.uwu.dimension;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class People {
    private static final String MOD = "uwudimension";
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MOD);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MOD);

    public static final DeferredHolder<EntityType<?>, EntityType<Keeper>> PETAL_KEEPER = keeper("petal_keeper");
    public static final DeferredHolder<EntityType<?>, EntityType<Keeper>> DUSK_KEEPER = keeper("dusk_keeper");
    public static final DeferredHolder<SoundEvent, SoundEvent> PETAL_VOICE = voice("petal_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUSK_VOICE = voice("dusk_voice");

    public static final DeferredHolder<EntityType<?>,EntityType<BlossomWarden>> BLOSSOM_WARDEN =
            TYPES.register("blossom_warden", () -> EntityType.Builder.<BlossomWarden>of(BlossomWarden::new, MobCategory.CREATURE)
                    .sized(1.45F, 2.7F).clientTrackingRange(12).build(MOD + ":blossom_warden"));

    private static DeferredHolder<EntityType<?>, EntityType<Keeper>> keeper(String name) {
        return TYPES.register(name, () -> EntityType.Builder.<Keeper>of(Keeper::new, MobCategory.CREATURE)
                .sized(0.65F, 1.81F).clientTrackingRange(8).build(MOD + ":" + name));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> voice(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD, name)));
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SOUNDS.register(bus);
        bus.addListener(People::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        var stats = Keeper.createAttributes().add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.31).add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.FOLLOW_RANGE, 24).build();
        event.put(BLOSSOM_WARDEN.get(), BlossomWarden.createMobAttributes()
                .add(Attributes.MAX_HEALTH,320).add(Attributes.ARMOR,24).add(Attributes.ARMOR_TOUGHNESS,12)
                .add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.MOVEMENT_SPEED,.17)
                .add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,1.2).build());
        event.put(PETAL_KEEPER.get(), stats);
        event.put(DUSK_KEEPER.get(), stats);
    }
}


