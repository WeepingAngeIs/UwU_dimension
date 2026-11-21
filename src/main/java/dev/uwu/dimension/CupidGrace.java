package dev.uwu.dimension;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Independent shrine status. Ordinary Regeneration owns every tick in which the two overlap. */
@EventBusSubscriber(modid = "uwudimension")
public final class CupidGrace extends MobEffect {
    static final ResourceKey<MobEffect> KEY = ResourceKey.create(Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("uwudimension", "cupid_grace"));
    private static final Map<MinecraftServer, TickPulses> SERVERS = new IdentityHashMap<>();

    private CupidGrace() {
        super(MobEffectCategory.BENEFICIAL, 0xF6A6CA);
    }

    // The target FML routes RegisterEvent (IModBusEvent) to this mod's bus automatically.
    @EventBusSubscriber(modid = "uwudimension")
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent public static void register(RegisterEvent event) {
            event.register(Registries.MOB_EFFECT, KEY.location(), CupidGrace::new);
        }
    }

    static Holder.Reference<MobEffect> holder() {
        return BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(KEY);
    }

    @Override public Component getDisplayName() {
        return Component.translatableWithFallback(getDescriptionId(), "Cupid Grace");
    }

    @Override public boolean shouldApplyEffectTickThisTick(int remaining, int amplifier) {
        return MobEffects.REGENERATION.value().shouldApplyEffectTickThisTick(remaining, amplifier);
    }

    @Override public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            TickPulses pulses = pulses(level.getServer());
            if (entity.hasEffect(MobEffects.REGENERATION)) pulses.ordinaryPresent(entity);
            pulses.due(entity, amplifier);
        }
        return true;
    }

    // Decide after native effects tick. This also covers native Regeneration arriving after
    // Grace's callback, or expiring before Grace's callback on the same tick.
    static final class TickPulses {
        final int tick;
        private final Map<LivingEntity, Integer> due = new IdentityHashMap<>();
        private final Set<LivingEntity> ordinary = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean settling;

        TickPulses(int tick) { this.tick = tick; }
        void due(LivingEntity entity, int amplifier) {
            if (!settling) due.putIfAbsent(entity, amplifier);
        }
        void ordinaryPresent(LivingEntity entity) { ordinary.add(entity); }
        boolean isDue(LivingEntity entity) { return due.containsKey(entity); }

        void finish(BiConsumer<LivingEntity, Integer> apply) {
            if (settling) return;
            settling = true;
            due.forEach((entity, amplifier) -> {
                if (!ordinary.contains(entity) && !entity.hasEffect(MobEffects.REGENERATION) && !entity.isRemoved())
                    apply.accept(entity, amplifier);
            });
        }
    }

    private static TickPulses pulses(MinecraftServer server) {
        TickPulses current = SERVERS.get(server);
        if (current == null || current.tick != server.getTickCount()) {
            current = new TickPulses(server.getTickCount());
            SERVERS.put(server, current);
        }
        return current;
    }

    private static void recordOrdinary(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        TickPulses current = SERVERS.get(server);
        // Leave unrelated regeneration alone. Keep observing a due pulse even if its dose expired.
        if (entity.hasEffect(holder()) || (current != null && current.isDue(entity)))
            pulses(server).ordinaryPresent(entity);
    }

    @SubscribeEvent public static void added(MobEffectEvent.Added event) {
        if (event.getEffectInstance().getEffect().equals(MobEffects.REGENERATION))
            recordOrdinary(event.getEntity());
    }

    @SubscribeEvent public static void expiring(MobEffectEvent.Expired event) {
        if (event.getEffectInstance().getEffect().equals(MobEffects.REGENERATION))
            recordOrdinary(event.getEntity());
    }

    @SubscribeEvent public static void removing(MobEffectEvent.Remove event) {
        if (event.getEffect().equals(MobEffects.REGENERATION) && event.getEntity().hasEffect(MobEffects.REGENERATION))
            recordOrdinary(event.getEntity());
    }

    static void applyVanillaHealing(LivingEntity entity, int amplifier) {
        MobEffects.REGENERATION.value().applyEffectTick(entity, amplifier);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finishTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        TickPulses pending = SERVERS.get(server);
        if (pending == null) return;
        try {
            if (pending.tick == server.getTickCount()) pending.finish(CupidGrace::applyVanillaHealing);
        } finally {
            SERVERS.remove(server);
        }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SERVERS.remove(event.getServer());
    }
}
