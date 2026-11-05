package dev.uwu.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("uwudimension")
public final class UwUDimension {
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    static final java.util.function.Supplier<Item> LOCKET = ITEMS.register("heartwater_locket", () -> new HeartwaterLocket());
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, "uwudimension");

    public UwUDimension(IEventBus bus) {
        ReefLife.register(bus);
        ITEMS.register(bus);
        SOUNDS.register("music.heartwater", () -> SoundEvent.createVariableRangeEvent(id("music.heartwater")));
        SOUNDS.register(bus);
        Arsenal.register(bus);
        People.register(bus);
        MirrorCourt.register(bus);
        HeartwaterFeature.register(bus);
        bus.addListener(UwUDimension::creative);
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(LOCKET.get());
    }
}

