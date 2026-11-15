package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** One ledger per bell in Sakura Reach. Contributions and claims live with the world. */
final class VillageCommons extends SavedData {
    enum Project {
        GARDEN("kitchen garden", "sakura_rice_seeds", 8, "heartbloom_sapling"),
        PATH("lantern walk", "heart_neon_lantern", 3, "heart_neon_lantern"),
        ORCHARD("moonberry orchard", "moonberry_sapling", 3, "starpetal_sapling"),
        BATH("bathhouse screens", "tideglass", 12, "rosette_mosaic"),
        FEAST("lantern feast", "cozy_udon_hotpot", 4, "keeper_figurine");
        final String title, material, prize;
        final int need;
        Project(String title,String material,int need,String prize) {
            this.title=title; this.material=material; this.need=need; this.prize=prize;
        }
    }

    private static final Factory<VillageCommons> FACTORY = new Factory<>(VillageCommons::new, VillageCommons::load, null);
    private CompoundTag villages = new CompoundTag();
    private static VillageCommons ledger(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "uwudimension_commons");
    }
    private static VillageCommons load(CompoundTag tag, HolderLookup.Provider lookups) {
        var data = new VillageCommons();
        data.villages = tag.getCompound("Bells");
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookups) {
        tag.put("Bells", villages);
        return tag;
    }
    static boolean festival(ServerLevel level, BlockPos pos) {
        return ledger(level).villages.getCompound(Long.toString(pos.asLong())).getLong("FestivalUntil") > level.getGameTime();
    }
    static boolean nearFestival(ServerLevel level, BlockPos pos) {
        int gx = Math.floorDiv(pos.getX()+GroveSites.SPACING/2,GroveSites.SPACING);
        int gz = Math.floorDiv(pos.getZ()+GroveSites.SPACING/2,GroveSites.SPACING);
        var book = ledger(level).villages;
        for (String side : new String[]{"petal","dusk"}) {
            String key = "festival_"+gx+"_"+gz+"_"+side;
            if (!book.contains(key)) continue;
            BlockPos bell = BlockPos.of(book.getLong(key));
            if (bell.closerThan(pos, 32) && festival(level,bell)) return true;
        }
        return false;
    }

    static void visit(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ReefLife.isReach(player.level())
                || event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.serverLevel().getBlockState(event.getPos()).is(Blocks.BELL)) return;
        var held = player.getMainHandItem();
        Project match = null;
        for (var project : Project.values())
            if (held.is(BuiltInRegistries.ITEM.get(UwUDimension.id(project.material)))) match = project;
        if (match == null && !ReachJournal.journal(held) && !held.isEmpty()) return;
        var level = player.serverLevel();
        BlockPos pos = event.getPos();
        var site = GroveSites.forChunk(level, new net.minecraft.world.level.ChunkPos(pos));
        if (!site.usable()) return;
        String district = null;
        for (boolean dusk : new boolean[]{false,true}) {
            var plan = BlossomVillage.plan(level,site,dusk);
            if (plan.isPresent()) {
                var village = plan.get();
                if (site.pos(village.x()+2,village.y()+1,village.z()-2).equals(pos)) district = dusk ? "dusk" : "petal";
            }
        }
        if (district == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        var data = ledger(level);
        String bell = Long.toString(pos.asLong());
        CompoundTag book = data.villages.getCompound(bell);
        if (match == null) {
            for (var project : Project.values()) {
                CompoundTag entry = book.getCompound(project.name());
                int paid = entry.getInt("Total");
                if (paid >= project.need && entry.getBoolean("Gave"+player.getUUID()) && !entry.getBoolean("Claim"+player.getUUID())) {
                    var gift = new ItemStack(BuiltInRegistries.ITEM.get(UwUDimension.id(project.prize)));
                    if (!player.addItem(gift)) player.drop(gift, false);
                    entry.putBoolean("Claim"+player.getUUID(),true);
                    book.put(project.name(),entry);
                    tell(player, "Thank you for helping with the " + project.title + ". Your keepsake is ready.");
                } else tell(player, project.title + " · " + paid + "/" + project.need + " " + project.material.replace('_',' '));
            }
            tell(player, "Sneak-use a listed supply on this bell to contribute. Return with an empty hand to collect.");
        } else {
            CompoundTag entry = book.getCompound(match.name());
            int remaining = match.need - entry.getInt("Total");
            if (remaining <= 0) { tell(player, "That project is complete. Sneak-use an empty hand to claim."); return; }
            int contribution = Math.min(held.getCount(),remaining);
            held.shrink(contribution);
            entry.putBoolean("Gave"+player.getUUID(),true);
            entry.putInt("Total", match.need - remaining + contribution);
            book.put(match.name(), entry);
            tell(player, "Contributed " + contribution + " to the " + match.title + ".");
            if (remaining == contribution) {
                data.villages.putLong("festival_"+site.gx()+"_"+site.gz()+"_"+district,pos.asLong());
                book.putLong("FestivalUntil", level.getGameTime()+24000);
                level.playSound(null,pos,net.minecraft.sounds.SoundEvents.BELL_RESONATE,net.minecraft.sounds.SoundSource.BLOCKS,1,1.2f);
                tell(player,"The village opens a lantern festival for one day. Contributors can collect with an empty hand.");
            }
        }
        data.villages.put(bell, book);
        data.setDirty();
    }
    private static void tell(ServerPlayer player,String message) {
        player.sendSystemMessage(Component.literal(message));
    }
}
