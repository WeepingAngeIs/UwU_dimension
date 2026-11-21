package dev.uwu.dimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("uwudimension")
public final class UwUDimension {
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    public static final java.util.function.Supplier<Item> GLITCH = ITEMS.register("glitch_bow", GlitchBow::new);
    static final java.util.function.Supplier<Item> LOCKET = ITEMS.register("heartwater_locket", () -> new HeartwaterLocket());
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, "uwudimension");
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "uwudimension");

    static final java.util.function.Supplier<SoundEvent> CUPID_RIPPLE = SOUNDS.register("cupid.ripple",
            () -> SoundEvent.createVariableRangeEvent(id("cupid.ripple")));

    public UwUDimension(IEventBus bus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,AcquisitionPolicy.SPEC,"uwudimension-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,HeartLauncher.SPEC,"uwudimension-launcher.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,SacredSpring.CLIENT_SPEC,"uwudimension-client.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,CupidBowVisualConfig.SPEC,"uwudimension-cupid-visuals-client.toml");
        bus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> {
            if(event.getConfig().getSpec()==AcquisitionPolicy.SPEC){
                AcquisitionPolicy.heartLocketCraftingEnabled();AcquisitionPolicy.weaponAcquisitionEnabled();
            }
        });
        CupidParticles.TYPES.register(bus);
        MagicGlowParticles.TYPES.register(bus);
        ArtParticles.TYPES.register(bus);
        HeartFloor.register(bus);
        PinkRoofTiles.register(bus);
        HeartLanterns.register(bus);
        LanternStorybook.register(bus);
        CupidShrine.register(bus);
        CupidWishes.register();
        ReefLife.register(bus);
        dev.uwu.dimension.lush.LushGeodes.register(bus);
        ReachLife.register(bus);
        SakuraCraft.register(bus);
        SakuraMaterials.register(bus);
        SakuraTools.register(bus);
        HeartShields.register(bus);
        SacredSpring.register(bus);
        WeatherParticles.register(bus);
        MoonveilWood.register(bus);
        NativeTeaFurniture.register(bus);
        NativeDrawer.register(bus);
        NativeShelf.register(bus);
        NativeFlowerBox.register(bus);
        BakerToaster.register(bus);
        NativeSeatMount.register(bus);
        GiantSakuraSapling.register(bus);
        Understory.register(bus);
        Moonbells.register(bus);
        RevivalBlossoms.register(bus);
        ReachArchitecture.register(bus);
        ITEMS.register(bus);
        SOUNDS.register("music.heartwater", () -> SoundEvent.createVariableRangeEvent(id("music.heartwater")));
        SOUNDS.register(bus);
        Arsenal.register(bus);
        HeartLauncher.register(bus);
        BellHammer.register(bus);
        CounterArsenal.register(bus);
        People.register(bus);
        RealmResidents.register();
        GolemSwap.register();
        GuardianCommands.register();
        GuardianTargeting.register();
        GuardianFlight.register(bus);
        dev.uwu.dimension.traversal.TraversalIntegration.register(bus);
        MirrorCourt.register(bus);
        NativeRewardChests.register(bus);
        HeartwaterFeature.register(bus);
        PantryPlants.register(bus);
        PantryTrees.register(bus);
        Pantry.register(bus);
        SakuraAchievements.register();
        CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.uwudimension.main"))
                .icon(() -> LOCKET.get().getDefaultInstance())
                .displayItems(LanternBookStacks::display).build());
        CREATIVE_TABS.register(bus);
        bus.addListener(UwUDimension::creative);
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("uwudimension", path);
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(GLITCH.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(LOCKET.get());
    }
}

