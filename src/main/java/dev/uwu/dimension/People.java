package dev.uwu.dimension;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
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
    public static final DeferredHolder<EntityType<?>, EntityType<KeeperZombieVillager>> PETAL_ZOMBIE = zombieKeeper("petal_zombie_villager");
    public static final DeferredHolder<EntityType<?>, EntityType<KeeperZombieVillager>> DUSK_ZOMBIE = zombieKeeper("dusk_zombie_villager");
    public static final DeferredHolder<SoundEvent, SoundEvent> PETAL_VOICE = voice("petal_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUSK_VOICE = voice("dusk_voice");

    public static final DeferredHolder<EntityType<?>,EntityType<BlossomWarden>> BLOSSOM_WARDEN =
            TYPES.register("blossom_warden", () -> EntityType.Builder.<BlossomWarden>of(BlossomWarden::new, MobCategory.CREATURE)
                    .sized(1.45F, 2.7F).clientTrackingRange(12).build(MOD + ":blossom_warden"));
    // Each entity type has one canonical spawn egg for NeoForge's type -> item lookup.
    public static final java.util.function.Supplier<DeferredSpawnEggItem> WARDEN_EGG = egg("uwu_guardian_spawn_egg", BLOSSOM_WARDEN);
    public static final java.util.function.Supplier<DeferredSpawnEggItem> PETAL_EGG = egg("petal_keeper_spawn_egg", PETAL_KEEPER);
    public static final java.util.function.Supplier<DeferredSpawnEggItem> DUSK_EGG = egg("dusk_keeper_spawn_egg", DUSK_KEEPER);
    public static final java.util.function.Supplier<DeferredSpawnEggItem> PETAL_ZOMBIE_EGG = egg("petal_zombie_villager_spawn_egg", PETAL_ZOMBIE);
    public static final java.util.function.Supplier<DeferredSpawnEggItem> DUSK_ZOMBIE_EGG = egg("dusk_zombie_villager_spawn_egg", DUSK_ZOMBIE);

    private static java.util.function.Supplier<DeferredSpawnEggItem> egg(String name,
            java.util.function.Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> type) {
        return UwUDimension.ITEMS.register(name, () ->
                new DeferredSpawnEggItem(type, 0xffffff, 0xffffff, new net.minecraft.world.item.Item.Properties()));
    }

    public static net.minecraft.world.item.ItemStack guardianEgg(boolean girl) {
        var stack = new net.minecraft.world.item.ItemStack(WARDEN_EGG.get());
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "uwudimension:blossom_warden");
        tag.putBoolean("FemaleGuardian", girl);
        stack.set(net.minecraft.core.component.DataComponents.ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(girl ? 2 : 1));
        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.translatable(
                girl ? "item.uwudimension.owl_guardian_girl_egg" : "item.uwudimension.owl_guardian_male_egg"));
        return stack;
    }

    private static DeferredHolder<EntityType<?>, EntityType<Keeper>> keeper(String name) {
        return TYPES.register(name, () -> EntityType.Builder.<Keeper>of(Keeper::new, MobCategory.CREATURE)
                .sized(0.65F, 1.78F).eyeHeight(1.62F).clientTrackingRange(8).build(MOD + ":" + name));
    }

    private static DeferredHolder<EntityType<?>, EntityType<KeeperZombieVillager>> zombieKeeper(String name) {
        return TYPES.register(name, () -> EntityType.Builder.<KeeperZombieVillager>of(KeeperZombieVillager::new, MobCategory.MONSTER)
                .sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).updateInterval(3).build(MOD + ":" + name));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> voice(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD, name)));
    }

    public static void register(IEventBus bus) {
        KeeperSociety.register();
        TYPES.register(bus);
        SOUNDS.register(bus);
        bus.addListener(People::attributes);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e)->{
            if (!e.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) return;
            e.accept(guardianEgg(false));
            e.accept(guardianEgg(true));
            for (var resident : java.util.List.of(PETAL_EGG,DUSK_EGG,PETAL_ZOMBIE_EGG,DUSK_ZOMBIE_EGG)) e.accept(resident.get());
        });
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        var stats = Keeper.createAttributes().add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.31).add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.FOLLOW_RANGE, 24).build();
        event.put(BLOSSOM_WARDEN.get(), BlossomWarden.createMobAttributes()
                .add(Attributes.MAX_HEALTH,320).add(Attributes.ATTACK_DAMAGE,15).add(Attributes.ARMOR,24).add(Attributes.ARMOR_TOUGHNESS,12)
                .add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.MOVEMENT_SPEED,.17)
                .add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,1.2).build());
        event.put(PETAL_KEEPER.get(), stats);
        event.put(DUSK_KEEPER.get(), stats);
        var zombieStats=net.minecraft.world.entity.monster.ZombieVillager.createAttributes().build();
        event.put(PETAL_ZOMBIE.get(), zombieStats);
        event.put(DUSK_ZOMBIE.get(), zombieStats);
    }
}


