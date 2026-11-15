package dev.uwu.dimension;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

final class ReachLife {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("uwudimension");
    private static final class Figurine extends Block {
        private static final VoxelShape SHAPE = Block.box(3,0,4,13,15,12);
        Figurine() { super(BlockBehaviour.Properties.ofFullCopy(Blocks.DECORATED_POT).noOcclusion()); }
        @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }
    static void register(IEventBus bus) {
        var figurine = BLOCKS.register("keeper_figurine", Figurine::new);
        UwUDimension.ITEMS.registerSimpleBlockItem("keeper_figurine", figurine);
        BLOCKS.register(bus);
        NeoForge.EVENT_BUS.addListener(ReachArrival::onLoad);
        NeoForge.EVENT_BUS.addListener(VillageCommons::visit);
        NeoForge.EVENT_BUS.addListener(ReachLife::commands);
    }
    static int preference(ServerPlayer player, String key, int fallback) {
        var saved = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return saved.contains(key) ? saved.getInt(key) : fallback;
    }
    private static int preference(ServerPlayer player, String key, int value, String message) {
        var root = player.getPersistentData();
        var saved = root.getCompound(Player.PERSISTED_NBT_TAG);
        saved.putInt(key,value);
        root.put(Player.PERSISTED_NBT_TAG, saved);
        player.sendSystemMessage(Component.literal(message));
        return 1;
    }
    private static void commands(RegisterCommandsEvent event) {
        var cmd = Commands.literal("sakura");
        cmd.then(Commands.literal("journal").executes(ctx -> {
            ReachJournal.give(ctx.getSource().getPlayerOrException()); return 1;
        }));
        var effects = Commands.literal("effects");
        String[] modes = {"quiet","gentle","full"};
        for (int i=0; i<modes.length; i++) {
            int mode=i;
            effects.then(Commands.literal(modes[i]).executes(ctx -> preference(ctx.getSource().getPlayerOrException(),
                    "sakura_effects",mode,"Scenery effects set to " + modes[mode] + ".")));
        }
        effects.then(Commands.literal("distance").then(Commands.argument("blocks",IntegerArgumentType.integer(2,24))
                .executes(ctx -> preference(ctx.getSource().getPlayerOrException(),"sakura_distance",
                        IntegerArgumentType.getInteger(ctx,"blocks"),"Scenery viewing distance updated."))));
        cmd.then(effects);
        cmd.then(Commands.literal("trail")
                .then(Commands.literal("on").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    if (preference(player,"sakura_festival_keepsake",0) == 0) {
                        player.sendSystemMessage(Component.literal("Collect a keepsake at a restored village bell to earn the blossom trail."));
                        return 0;
                    }
                    return preference(player,"sakura_trail",1,"Blossom trail enabled.");
                }))
                .then(Commands.literal("off").executes(ctx -> preference(ctx.getSource().getPlayerOrException(),
                        "sakura_trail",0,"Blossom trail folded away."))));
        cmd.then(Commands.literal("status").requires(source -> source.hasPermission(2)).executes(ctx -> {
            var p=ctx.getSource().getPlayerOrException();
            var anchor=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(HeartwaterLocket.ANCHOR);
            String home = anchor.contains("feet") ? anchor.getString("dimension")+" "+BlockPos.of(anchor.getLong("feet")).toShortString() : "world spawn";
            ctx.getSource().sendSuccess(() -> Component.literal("Return: "+home+" | Field notes: chapter "+ReachJournal.chapter(p)
                    +" | Scenery packets sent in this dimension since startup: "+HeartwaterAmbience.packets(p.serverLevel())),false);
            return 1;
        }));
        event.getDispatcher().register(cmd);
    }
}
