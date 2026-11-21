package dev.uwu.duskcheck;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Disposable GitHub-hosted fixture; this class is not shipped in the mod. */
@Mod("uwu_dusk_check")
public final class DuskHarness {
    private final String stage = System.getProperty("uwu.check.duskStage", "fresh-forward");
    private final DuskProbe probe = new DuskProbe(stage);
    private boolean done;

    public DuskHarness() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ServerTickEvent.Post event) {
        if (done) return;
        try {
            done = probe.tick(event.getServer());
        } catch (Throwable failure) {
            done = true;
            System.err.println("DUSK CHECK FAILED stage=" + stage);
            failure.printStackTrace();
        }
        if (done) event.getServer().halt(false);
    }
}
