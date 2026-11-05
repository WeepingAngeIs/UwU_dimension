package dev.uwu.test;

import circuitlord.reactivemusic.*;
import circuitlord.reactivemusic.entries.RMRuntimeEntry;

/** Evaluates the installed pack's event expressions with the real Reactive Music 1.3.5 matcher. */
final class MusicProof {
    private static boolean matches(String song,String... events) {
        var config=new SongpackConfig(); config.name="Cobblemon Unyielding";config.version="2.3.6";
        var descriptor=new SongpackZip();descriptor.config=config;
        var entry=new SongpackEntry();entry.events=events;entry.songs=new String[]{song};
        var parsed=RMRuntimeEntry.create(descriptor,entry);
        if(!parsed.errorString.isEmpty())throw new AssertionError(parsed.errorString);
        SongPicker.tickEventMap();
        return SongPicker.isEntryValid(parsed);
    }
    static void surface() {
        if(!matches("pinwheel","BIOME=flower_forest || BIOME=cherry_grove || BIOMETAG=FLORAL"))
            throw new AssertionError("Real Reactive Music matcher rejected flowering surface: "+SongPicker.currentBiomeName);
        if(matches("river_night","NIGHT","DIM=OVERWORLD","BIOME=biomeswevegone:sakura_grove || BIOME=minecraft:cherry_grove || BIOME=natures_spirit:aspen_forest"))
            throw new AssertionError("Overworld-specific night song leaked into Reach");
        System.out.println("REACTIVE MUSIC PASS | pinwheel eligible | "+SongPicker.currentBiomeName+" | "+SongPicker.currentDimName);
    }
    static void water() {
        if(!matches("distant_shores","UNDERWATER || BIOME=ocean"))throw new AssertionError("Reactive Music did not detect underwater listener");
        System.out.println("REACTIVE MUSIC PASS | underwater condition eligible");
    }
}

