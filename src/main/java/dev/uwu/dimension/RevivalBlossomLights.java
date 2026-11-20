package dev.uwu.dimension;

import java.util.HashMap;
import java.util.Map;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** This class is loaded by the optional LambDynamicLights client entry point alone. */
public final class RevivalBlossomLights implements DynamicLightsInitializer {
    private final Map<Player,Point> active=new HashMap<>();
    private DynamicLightBehaviorManager manager;
    private ClientLevel level;

    @Override public void onInitializeDynamicLights(DynamicLightsContext context) {
        manager=context.dynamicLightBehaviorManager();
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    // Legacy abstract overload on the 1.21.1 API. The installed runtime calls the context overload.
    @Override @SuppressWarnings("removal") public void onInitializeDynamicLights(ItemLightSourceManager unused) {}

    private void tick(ClientTickEvent.Post event) {
        Minecraft client=Minecraft.getInstance();
        if (client.level!=level) {
            active.values().forEach(manager::remove);
            active.clear();
            level=client.level;
        }
        if (level==null) return;
        Vec3 camera=client.gameRenderer.getMainCamera().getPosition();
        for (var iterator=active.entrySet().iterator();iterator.hasNext();) {
            var entry=iterator.next();
            if (entry.getKey().isRemoved() || !RevivalBlossomClient.nearby(entry.getKey(),camera)) {
                manager.remove(entry.getValue());
                iterator.remove();
            }
        }
        for (Player player:level.players()) {
            if (!RevivalBlossomClient.nearby(player,camera)) continue;
            Vec3 at=RevivalBlossomClient.anchor(player,1);
            Point light=active.get(player);
            if (light==null) {
                light=new Point(at);
                active.put(player,light);
                manager.add(light);
            } else light.move(at);
        }
    }

    static final class Point implements DynamicLightBehavior {
        private volatile Vec3 center;
        private Vec3 lastUpdate;
        Point(Vec3 center) { this.center=center; }
        void move(Vec3 at) { center=at; }
        @Override public boolean hasChanged() {
            Vec3 p=center;
            if (lastUpdate!=null && p.distanceToSqr(lastUpdate)<1.0/256) return false;
            lastUpdate=p;
            return true;
        }
        @Override public BoundingBox getBoundingBox() {
            BlockPos p=BlockPos.containing(center);
            return new BoundingBox(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY()+1,p.getZ()+1);
        }
        @Override public double lightAtPos(BlockPos pos,double falloffRatio) {
            Vec3 p=center;
            return Math.max(0,11-Math.sqrt(p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5))*falloffRatio);
        }
    }
}
