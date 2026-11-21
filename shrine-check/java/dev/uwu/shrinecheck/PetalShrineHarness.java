package dev.uwu.shrinecheck;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("uwu_petal_shrine_check")
public final class PetalShrineHarness {
    private final Probe probe;
    private boolean done;

    public PetalShrineHarness() {
        // Keep the legacy fixture independent of classes introduced by the candidate.
        if (System.getProperty("uwu.check.shrineOrder", "").equals("seed-legacy")) {
            var legacy = new LegacyPetalShrineSeed();
            probe = event -> legacy.tick(event.getServer());
        } else {
            var current = new PetalShrineProbe();
            probe = event -> current.tick(event.getServer());
        }
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ServerTickEvent.Post event) {
        if (done) return;
        try {
            done = probe.tick(event);
        } catch (Throwable failure) {
            done = true;
            System.err.println("PETAL SHRINE CHECK FAILED");
            failure.printStackTrace();
        }
        if (done) event.getServer().halt(false);
    }

    @FunctionalInterface
    private interface Probe {
        boolean tick(ServerTickEvent.Post event) throws ReflectiveOperationException;
    }
}
