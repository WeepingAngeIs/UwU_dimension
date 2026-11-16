package dev.uwu.test;

import java.lang.reflect.Method;
import java.util.UUID;
import dev.uwu.dimension.Arsenal;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.GlitchBow;
import dev.uwu.dimension.UwUDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Calls the installed Better Combat server packet handler with a real offhand attack. */
@EventBusSubscriber(modid = "heartwater_probe")
public final class BetterCombatDaggerProof {
    private static boolean ran;

    private BetterCombatDaggerProof() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (ran || !server.isDedicatedServer() || server.getTickCount() < 80
                || !"1".equals(System.getenv("UWU_TEST_BETTERCOMBAT"))) return;
        ran = true;
        try {
            run(server.overworld());
        } catch (Throwable failure) {
            System.err.println("UWU BETTER COMBAT OFFHAND FAIL");
            failure.printStackTrace();
            server.halt(false);
        }
    }

    private static void run(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("bettercombat"))
            throw new AssertionError("Better Combat is required for this proof");
        BlockPos base = new BlockPos(5, level.getMaxBuildHeight() - 20, 5);
        level.getChunkAt(base);
        try (var visitor = new ChestProof.Visitor(level, base, "OffhandTest", UUID.randomUUID())) {
            ServerPlayer player = visitor.player;
            var target = EntityType.HUSK.create(level);
            if (target == null) throw new AssertionError("Could not create target");
            level.addNewPlayer(player);
            target.moveTo(base.getX() + .5, base.getY(), base.getZ() + .5, 0, 0);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            target.getAttribute(Attributes.ARMOR).setBaseValue(0);
            target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
            target.setHealth(200);
            level.addFreshEntity(target);
            player.setNoGravity(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            // The real fixture includes Apothic Attributes, whose independent
            // random critical strikes otherwise make angle comparisons flaky.
            var chanceKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ATTRIBUTE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("apothic_attributes", "crit_chance"));
            var chance = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(chanceKey)
                    .map(player::getAttribute).orElse(null);
            double previousChance = chance == null ? 0 : chance.getBaseValue();
            if (chance != null) chance.setBaseValue(0);
            try {
            var daggerStacks=new java.util.ArrayList<ItemStack>();
            daggerStacks.add(new ItemStack(Arsenal.WEAPONS.get(Arsenal.Relic.ROSE_DAGGER).get()));
            daggerStacks.add(new ItemStack(CounterArsenal.ITEMS.get("counter_toxic_longsword").get()));
            for(String form:java.util.List.of("rose_dagger","counter_toxic_longsword")){
                var mimic=new ItemStack(UwUDimension.GLITCH.get());int index=GlitchBow.FORMS.indexOf(form);
                if(index<0)throw new AssertionError("Missing Glitch form "+form);
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,mimic,n->n.putInt("uwu_form",index));
                daggerStacks.add(mimic);
            }
            float[] nativeFront=new float[2];int weaponNumber=0;
            for (var dagger : daggerStacks) {
                var daggerItem=dagger.getItem();
                player.setItemInHand(InteractionHand.OFF_HAND, dagger);

                Class<?> helper = Class.forName("net.bettercombat.logic.PlayerAttackHelper");
                Method current = helper.getMethod("getCurrentAttack", Player.class, int.class);
                int offhandCombo = -1;
                for (int combo = 0; combo < 32; combo++) {
                    Object attack = current.invoke(null, player, combo);
                    if (attack != null && (boolean)attack.getClass().getMethod("isOffHand").invoke(attack)
                            && ((ItemStack)attack.getClass().getMethod("itemStack").invoke(attack)).is(dagger.getItem())) {
                        Object attrs = attack.getClass().getMethod("attributes").invoke(attack);
                        if (!"dagger".equals(attrs.getClass().getMethod("category").invoke(attrs)))
                            throw new AssertionError("Better Combat used the wrong weapon category for " + dagger.getItem());
                        offhandCombo = combo;
                        break;
                    }
                }
                if (offhandCombo < 0) throw new AssertionError("Better Combat did not select the offhand dagger");

                Class<?> request = Class.forName("net.bettercombat.network.Packets$C2S_AttackRequest");
                var constructor = request.getConstructor(int.class, boolean.class, int.class, int.class, int[].class);
                Object packet = constructor.newInstance(offhandCombo, false, player.getInventory().selected,
                        target.getId(), new int[]{target.getId()});
                Class<?> network = Class.forName("net.bettercombat.network.ServerNetwork");
                Method dispatch = network.getMethod("handleAttackRequest", request,
                        net.minecraft.server.MinecraftServer.class, ServerPlayer.class,
                        ServerGamePacketListenerImpl.class);
                var ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
                ticker.setAccessible(true);

                float front = strike(level, player, target, dispatch, packet, ticker, 180, 100, false);
                float rear = strike(level, player, target, dispatch, packet, ticker, 0, 100, false);
                if(weaponNumber<2)nativeFront[weaponNumber]=front;
                else if(Math.abs(front-nativeFront[weaponNumber-2])>.1F)
                    throw new AssertionError("Glitch offhand form damage did not match native weapon: expected="
                            +nativeFront[weaponNumber-2]+" actual="+front+" form="+GlitchBow.form(dagger));
                weaponNumber++;
                if (front <= 0 || Math.abs(rear - front * 2) > .1F)
                    throw new AssertionError("Better Combat offhand front/rear damage " + front + "/" + rear);
                for (double side : new double[]{-1, 1}) {
                    float exactSide = strike(level, player, target, dispatch, packet, ticker, side * 90, 100, false);
                    float rearSide = strike(level, player, target, dispatch, packet, ticker, side * 89.9, 100, false);
                    float frontSide = strike(level, player, target, dispatch, packet, ticker, side * 90.1, 100, false);
                    if (Math.abs(exactSide - front) > .1F || Math.abs(frontSide - front) > .1F
                            || Math.abs(rearSide - rear) > .1F)
                        throw new AssertionError("Better Combat angle boundary " + side + ": front=" + front
                                + ", side=" + exactSide + ", rearSide=" + rearSide + ", frontSide=" + frontSide);
                }
                float partialFront = strike(level, player, target, dispatch, packet, ticker, 180, 2, false);
                float partialRear = strike(level, player, target, dispatch, packet, ticker, 0, 2, false);
                if (partialFront >= front || Math.abs(partialRear - partialFront * 2) > .1F)
                    throw new AssertionError("Better Combat cooldown damage front=" + partialFront + ", rear=" + partialRear);
                float airborneRear = strike(level, player, target, dispatch, packet, ticker, 0, 100, true);
                if (Math.abs(airborneRear - rear) > .1F)
                    throw new AssertionError("Better Combat airborne critical stacked: ground=" + rear + ", air=" + airborneRear);
                target.getAttribute(Attributes.ARMOR).setBaseValue(15);
                float armoredRear = strike(level, player, target, dispatch, packet, ticker, 0, 100, false);
                if (armoredRear <= 0 || armoredRear >= rear)
                    throw new AssertionError("Better Combat armor was bypassed: plain=" + rear + ", armored=" + armoredRear);
                target.getAttribute(Attributes.ARMOR).setBaseValue(0);
                if (!player.getOffhandItem().is(dagger.getItem()) || !player.getMainHandItem().is(Items.IRON_SWORD))
                    throw new AssertionError("Better Combat did not restore both hands");
                System.out.println("UWU BETTER COMBAT OBSERVED item="
                        + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(daggerItem)
                        + " category=dagger offhandCombo=" + offhandCombo + " front=" + front
                        + " rear=" + rear + " partialFront=" + partialFront + " partialRear=" + partialRear
                        + " airborneRear=" + airborneRear + " armoredRear=" + armoredRear
                        + " boundaries=passed handsRestored=true");
            }
            // The registry is stack-aware: Glitch must use its selected form's
            // Better Combat data while leaving explicit player presets untouched.
            var registry=Class.forName("net.bettercombat.logic.WeaponRegistry");
            Method attributes=registry.getMethod("getAttributes",ItemStack.class);
            var targetStack=new ItemStack(CounterArsenal.ITEMS.get("counter_runic_twinblade").get());
            var mimic=new ItemStack(UwUDimension.GLITCH.get());int index=GlitchBow.FORMS.indexOf("counter_runic_twinblade");
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,mimic,n->n.putInt("uwu_form",index));
            Object expected=attributes.invoke(null,targetStack),actual=attributes.invoke(null,mimic);
            if(expected==null||actual==null||!expected.getClass().getMethod("category").invoke(expected).equals(actual.getClass().getMethod("category").invoke(actual)))
                throw new AssertionError("Glitch Twin Chakram Better Combat category does not follow its form");
            var raw=net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(net.minecraft.resources.ResourceLocation.parse("bettercombat:preset_id")).orElseThrow();
            @SuppressWarnings("unchecked") var preset=(net.minecraft.core.component.DataComponentType<net.minecraft.resources.ResourceLocation>)raw;
            var explicit=net.minecraft.resources.ResourceLocation.parse("bettercombat:dagger");
            targetStack.set(preset,explicit);mimic.set(preset,explicit);
            expected=attributes.invoke(null,targetStack);actual=attributes.invoke(null,mimic);
            if(expected==null||actual==null||!expected.getClass().getMethod("category").invoke(expected).equals(actual.getClass().getMethod("category").invoke(actual))||!explicit.equals(mimic.get(preset)))
                throw new AssertionError("Glitch changed or ignored an explicitly supplied Better Combat preset");
            System.out.println("UWU GLITCH BETTER COMBAT PASS form-specific Twin Chakram and explicit player preset");
            } finally {
                if (chance != null) chance.setBaseValue(previousChance);
            }
            target.discard();
            player.discard();
            System.out.println("UWU BETTER COMBAT OFFHAND PASS actual packet handler front/rear and hand restoration");
            System.out.println("UWU BETTER COMBAT ANGLE PASS boundaries, cooldown, airborne critical and armor");
        }
    }

    private static float strike(ServerLevel level, ServerPlayer player, LivingEntity target,
            Method dispatch, Object packet, java.lang.reflect.Field ticker,
            double angleFromRear, int strength, boolean airborne) throws Exception {
        target.setHealth(200);
        target.invulnerableTime = 0;
        target.setYRot(0);
        target.setYHeadRot(0);
        double angle = Math.toRadians(angleFromRear);
        player.moveTo(target.getX() + Math.sin(angle) * 1.5, target.getY(),
                target.getZ() - Math.cos(angle) * 1.5, 0, 0);
        player.setSprinting(false);
        player.setOnGround(!airborne);
        player.fallDistance = airborne ? 1F : 0F;
        player.setDeltaMovement(0, 0, 0);
        ticker.setInt(player, strength);
        dispatch.invoke(null, packet, level.getServer(), player, player.connection);
        return 200 - target.getHealth();
    }
}

