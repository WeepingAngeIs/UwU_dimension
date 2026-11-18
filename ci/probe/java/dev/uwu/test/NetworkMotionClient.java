package dev.uwu.test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Normal player renders and physical X11 input; no synthetic poses or projectile creation. */
@EventBusSubscriber(modid="heartwater_probe", value=Dist.CLIENT)
public final class NetworkMotionClient {
    private static final boolean ACTIVE = "network-weapon-motion".equals(System.getenv("UWU_VISIT"));
    private static final String ROLE = System.getenv().getOrDefault("WISH_ROLE", "");
    private static final Path SYNC = Path.of(System.getenv().getOrDefault("WISH_SYNC_DIR", "."));
    private static final ConcurrentHashMap<String,AtomicInteger> PARTICLES = new ConcurrentHashMap<>();
    private static final Set<String> SPAWNS = ConcurrentHashMap.newKeySet();
    private static final Set<String> VIEWS = new HashSet<>();
    private static boolean connecting, watching, finished, held, reported, sawLive;
    private static int scene = -1, ticks, photos, lastPhoto, warmup;
    private static long started;
    private static Properties task;

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError("MOTION CLIENT " + ROLE + " scene=" + scene + " " + detail);
    }

    private static void mouse(String action, int button) throws Exception {
        var process = new ProcessBuilder("xdotool", action, Integer.toString(button)).redirectErrorStream(true).start();
        require(process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0,
                "physical mouse input " + action);
    }

    private static void observe(Minecraft mc) {
        var channel = mc.getConnection().getConnection().channel();
        channel.eventLoop().execute(() -> channel.pipeline().addBefore("packet_handler", "uwu_motion_client", new ChannelInboundHandlerAdapter() {
            @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                received(message);
                super.channelRead(context, message);
            }
        }));
    }

    private static void received(Object message) {
        // Entity tracking delivers add-entity, metadata and attributes in one bundle.
        // The observer sits before the packet listener unpacks that bundle.
        if (message instanceof ClientboundBundlePacket bundle) {
            for (var packet : bundle.subPackets()) received(packet);
        } else if (message instanceof ClientboundLevelParticlesPacket packet) {
            String name = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString();
            PARTICLES.computeIfAbsent(name, unused -> new AtomicInteger()).incrementAndGet();
        } else if (message instanceof ClientboundAddEntityPacket packet) {
            var name = BuiltInRegistries.ENTITY_TYPE.getKey(packet.getType());
            if (name.getNamespace().equals("uwudimension")) {
                SPAWNS.add(name.toString());
                System.out.println("MOTION CLIENT TRACKED " + ROLE + " " + name + " entity=" + packet.getId());
            }
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!ACTIVE || finished) return;
        if (started == 0) started = System.nanoTime();
        require(System.nanoTime() - started < 600_000_000_000L, "ten minute client deadline");
        var mc = Minecraft.getInstance();
        if (!connecting && mc.screen instanceof TitleScreen menu) {
            connecting = true;
            mc.options.bobView().set(false);
            ConnectScreen.startConnecting(menu, mc, ServerAddress.parseString("127.0.0.1:25576"),
                    new ServerData("Weapon motion proof", "127.0.0.1:25576", ServerData.Type.OTHER), false, null);
            return;
        }
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.screen != null) return;
        require(mc.getSingleplayerServer() == null && !mc.getConnection().getConnection().isMemoryConnection(), "expected dedicated TCP connection");
        if (!watching) { watching = true; observe(mc); }
        if (Files.exists(SYNC.resolve("motion-complete"))) {
            if (held) mouse("mouseup", task.getProperty("action").equals("attack") ? 1 : 3);
            finished = true;
            System.out.println("MOTION CLIENT PASS " + ROLE + " actual TCP entity/particle receipt and first/third/live screenshots");
            mc.stop();
            return;
        }
        Path file = SYNC.resolve("motion-task.properties");
        if (!Files.exists(file)) return;
        var next = new Properties();
        try (var reader = Files.newBufferedReader(file)) { next.load(reader); }
        int id = Integer.parseInt(next.getProperty("id"));
        if (id != scene) {
            scene = id;
            ticks = photos = 0;
            warmup = 0;
            lastPhoto = -20;
            reported = held = sawLive = false;
            PARTICLES.clear();
            SPAWNS.clear();
            VIEWS.clear();
        }
        task = next;
        // A small floating arena can occupy one rendered section. Looking vertically
        // can legitimately draw none. Frustum count is not a world-readiness signal.
        var actor = ROLE.equals("archer") ? mc.player : mc.level.getEntity(Integer.parseInt(task.getProperty("shooter")));
        if (actor == null || !mc.level.hasChunkAt(mc.player.blockPosition())) return;
        warmup++;
        if (warmup < 20) return;
        var remote = mc.level.players().stream().filter(player -> player != mc.player).findFirst().orElse(null);
        if (remote == null || !FiguraPivotProof.ready(mc, remote.getUUID())) return;
        if (ROLE.equals("archer")) {
            mc.player.setYRot(0);
            mc.player.setXRot(Float.parseFloat(task.getProperty("pitch")));
            mc.options.setCameraType(ticks >= 20 ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        } else {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            var flight = mc.level.getEntity(Integer.parseInt(task.getProperty("projectile")));
            var target = mc.level.getEntity(Integer.parseInt(task.getProperty("target")));
            Vec3 look = flight != null && flight.isAlive() ? flight.getBoundingBox().getCenter()
                    : target != null ? target.position().add(0, .7, 0) : actor.position().add(0, 1, 0);
            Vec3 direction = look.subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)(Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
        }
        var live = mc.level.getEntity(Integer.parseInt(task.getProperty("projectile")));
        sawLive |= live != null && live.isAlive();
        if (task.getProperty("state").equals("prepare")) {
            if (ROLE.equals("archer") && !BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).getPath().equals(task.getProperty("item"))) return;
            Files.writeString(SYNC.resolve("motion-ready-" + scene + "-" + ROLE), "ready");
            return;
        }
        ticks++;
        String action = task.getProperty("action");
        int button = action.equals("attack") ? 1 : 3;
        if (ROLE.equals("archer")) {
            if (ticks == 12) { mouse("mousedown", button); held = true; }
            if (ticks == (action.equals("draw") ? 40 : 16)) { mouse("mouseup", button); held = false; }
        }
        if (reported || ticks < 135) return;
        require(!SPAWNS.isEmpty(), "never received the actual UwU cast spawn packet");
        require(photos >= 4, "too few framebuffer captures " + photos);
        if (ROLE.equals("archer")) require(VIEWS.contains("first") && VIEWS.contains("third"), "missing first/third render views " + VIEWS);
        Properties evidence = new Properties();
        PARTICLES.forEach((name,count) -> evidence.setProperty(name, count.toString()));
        evidence.setProperty("spawns", SPAWNS.toString());
        evidence.setProperty("sawLiveEntity", Boolean.toString(sawLive));
        evidence.setProperty("photos", Integer.toString(photos));
        evidence.setProperty("views", VIEWS.toString());
        try (var writer = Files.newBufferedWriter(SYNC.resolve("motion-client-" + scene + "-" + ROLE + ".properties"))) {
            evidence.store(writer, "Actual remote entity/particle packet receipt and framebuffer views");
        }
        Files.writeString(SYNC.resolve("motion-done-" + scene + "-" + ROLE), "captured");
        reported = true;
    }

    @SubscribeEvent
    public static void frame(RenderGuiEvent.Post event) throws Exception {
        if (!ACTIVE || finished || task == null || reported || ticks < 10 || ticks-lastPhoto < 4 || photos >= 12) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        String view = ROLE.equals("archer") && mc.options.getCameraType() != CameraType.FIRST_PERSON ? "third" : "first";
        String phase = task.getProperty("phase");
        VIEWS.add(view);
        var graphics = event.getGuiGraphics();
        graphics.fill(4, 4, 480, 35, 0xb0191d2c);
        graphics.drawString(mc.font, ROLE + " " + scene + " " + task.getProperty("name") + " " + view + " " + phase, 9, 9, 0xffffffff, false);
        var entity = mc.level.getEntity(Integer.parseInt(task.getProperty("projectile")));
        graphics.drawString(mc.font, "tracked entity " + (entity == null ? "absent" : entity.getId()+" age "+entity.tickCount)
                + " held " + mc.player.getMainHandItem().getCount(), 9, 22, 0xffa4e5d7, false);
        graphics.flush();
        Path dir = mc.gameDirectory.toPath().resolve("screenshots");
        Files.createDirectories(dir);
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(dir.resolve(String.format(Locale.ROOT, "motion-%02d-%s-%02d-%s-%s.png", scene, ROLE, photos, view, phase)));
        }
        photos++;
        lastPhoto = ticks;
    }
}
