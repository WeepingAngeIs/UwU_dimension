package dev.uwu.test;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.uwu.dimension.CounterArsenal;
import dev.uwu.dimension.GlitchBow;
import dev.uwu.dimension.CounterRetuning;
import dev.uwu.dimension.AcquisitionPolicy;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Contract captured from 6fd6826 before curation, including the save-sensitive bow indices. */
final class RosterProof {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void run(ServerLevel level,ServerPlayer player) throws Exception {
        List<String> nativeForms=List.of("cupid_bow","ripple_bow","ribbonhare_scepter","blossom_greatsword",
                "bellheart_axe","lockkeeper_axe","vesper_scythe","axolotl_whip","frost_whip","rift_whip",
                "rose_dagger","satin_whip","lantern_spear","tidelace_trident","blue_hour_wand");
        var stream=RosterProof.class.getResourceAsStream("/legacy-counter-roster.json");
        require(stream!=null,"Missing legacy roster fixture");
        CounterArsenal.Entry[] old;
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){old=new Gson().fromJson(reader,CounterArsenal.Entry[].class);}
        require(old.length==133&&CounterArsenal.ROSTER.size()==old.length,"Legacy counter count changed");
        require(GlitchBow.FORMS.size()==nativeForms.size()+old.length,"Saved Glitch form count changed");
        for(int i=0;i<nativeForms.size();i++)require(GlitchBow.FORMS.get(i).equals(nativeForms.get(i)),"Saved native Glitch index changed "+i);
        for(int i=0;i<old.length;i++){
            var before=old[i];var now=CounterArsenal.ROSTER.get(i);
            require(before.id().equals(now.id())&&before.target().equals(now.target())
                    &&before.school().equals(now.school())&&before.shape().equals(now.shape())
                    &&before.runic()==now.runic()&&before.damage()==now.damage(),"Legacy mechanics changed for "+before.id());
            require(GlitchBow.FORMS.get(nativeForms.size()+i).equals(before.id()),"Saved counter Glitch index changed for "+before.id());
            var item=CounterArsenal.ITEMS.get(before.id());
            require(item!=null,"Legacy item no longer registered: "+before.id());
            var stack=new ItemStack(item.get());stack.setDamageValue(37);
            stack.set(DataComponents.CUSTOM_NAME,Component.literal("Saved "+before.id()));
            CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putString("roster_test","preserved"));
            var loaded=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)stack.save(level.registryAccess()));
            require(ItemStack.isSameItemSameComponents(stack,loaded),"Saved item components lost: "+before.id());
            require(loaded.getDamageValue()==37,"Saved item damage lost: "+before.id());
        }
        var root=level.getServer().getCommands().getDispatcher().getRoot().getChild("sakura");
        require(root!=null&&root.getChild("counter")!=null&&root.getChild("journal")!=null
                &&root.getChild("effects")!=null&&root.getChild("cupid")!=null&&root.getChild("trail")!=null,
                "Counter registration replaced existing Sakura commands");
        acquisition();
        System.out.println("UWU LEGACY ROSTER PASS all 133 ids, target/mechanics, saved Glitch indices and custom stack round trips");
        retuning(player);
    }
    private static String mechanics(CounterArsenal.Entry e) {return e.school()+"|"+e.shape()+"|"+e.theme()+"|"+e.runic();}
    private static void acquisition() throws Exception {
        Set<String> direct=new HashSet<>(),allMechanics=new HashSet<>(),directMechanics=new HashSet<>();
        for(var e:CounterArsenal.ROSTER) {
            if(CounterArsenal.selectable(e))allMechanics.add(mechanics(e));
            if(CounterArsenal.active(e)){require(direct.add(e.id()),"Duplicate direct counter "+e.id());directMechanics.add(mechanics(e));}
        }
        require(direct.size()==29,"Wrong direct counter count: "+direct.size());
        require(directMechanics.equals(allMechanics),"A unique school/shape/theme/runic combination is absent from direct acquisition");
        for(String name:List.of("rose_bathhouse","inverted_shrine","tide_archive","mirror_court")) {
            var stream=CounterArsenal.class.getResourceAsStream("/data/uwudimension/loot_table/chests/"+name+".json");
            require(stream!=null,"Missing loot table "+name);
            var found=new HashSet<String>();int weight=0;
            try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var pools=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("pools");
                for(var pool:pools)for(var value:pool.getAsJsonObject().getAsJsonArray("entries")) {
                    var entry=value.getAsJsonObject();
                    if(!entry.has("name"))continue;
                    String id=entry.get("name").getAsString();
                    if(!id.startsWith("uwudimension:counter_"))continue;
                    require(found.add(id.substring("uwudimension:".length())),"Duplicate counter in loot "+name+" "+id);
                    weight+=entry.has("weight")?entry.get("weight").getAsInt():1;
                }
            }
            require(found.equals(direct),"Loot/direct counter mismatch "+name);
            require(weight==123,"Counter loot weight changed "+name+" "+weight);
        }
        System.out.println("UWU ROSTER ACQUISITION PASS 29 direct, complete ability families and duplicate-free weighted loot in four tables");
    }
    private static void retuning(ServerPlayer player) {
        for(int i=0;i<player.getInventory().getContainerSize();i++)player.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        var initial=CounterArsenal.ITEMS.get(CounterArsenal.ROSTER.getFirst().id()).get();
        for(InteractionHand hand:InteractionHand.values()) {
            player.setItemInHand(InteractionHand.MAIN_HAND,hand==InteractionHand.MAIN_HAND?new ItemStack(initial):new ItemStack(Items.DIRT));
            player.setItemInHand(InteractionHand.OFF_HAND,hand==InteractionHand.OFF_HAND?new ItemStack(initial):new ItemStack(Items.DIRT));
            ItemStack start=player.getItemInHand(hand);
            start.setDamageValue(37);
            start.set(DataComponents.CUSTOM_NAME,Component.literal("Precious weapon"));
            start.enchant(player.serverLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING),3);
            start.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(4231));
            start.set(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(ResourceLocation.parse("uwudimension:retune_fixture"),3.25,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build());
            var expectedEnchantments=start.get(DataComponents.ENCHANTMENTS);
            var expectedAttributes=start.get(DataComponents.ATTRIBUTE_MODIFIERS);
            CustomData.update(DataComponents.CUSTOM_DATA,start,tag->tag.putString("roster_test","preserved"));
            for(var entry:CounterArsenal.ROSTER) {
                if(!CounterArsenal.selectable(entry)) {
                    var before=player.getItemInHand(hand).copy();
                    require(!CounterRetuning.retune(player,entry.id()),"Retired bow accepted "+entry.id());
                    require(ItemStack.isSameItemSameComponents(before,player.getItemInHand(hand)),"Retired bow attempt mutated stack");
                    continue;
                }
                require(CounterRetuning.retune(player,entry.id()),"Cannot retune "+hand+" to "+entry.id());
                var actual=player.getItemInHand(hand);
                require(actual.is(CounterArsenal.ITEMS.get(entry.id()).get()),"Wrong retuned ID "+entry.id());
                require(actual.getDamageValue()==37&&actual.getCount()==1
                    &&Component.literal("Precious weapon").equals(actual.get(DataComponents.CUSTOM_NAME))
                    &&expectedEnchantments.equals(actual.get(DataComponents.ENCHANTMENTS))
                    &&expectedAttributes.equals(actual.get(DataComponents.ATTRIBUTE_MODIFIERS))
                    &&new CustomModelData(4231).equals(actual.get(DataComponents.CUSTOM_MODEL_DATA))
                    &&actual.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("roster_test").equals("preserved"),
                    "Retune lost ownership, name, wear or custom data "+entry.id());
                require(player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND).is(Items.DIRT),"Retune changed opposite hand");
            }
            var before=player.getItemInHand(hand).copy();
            require(!CounterRetuning.retune(player,"not_a_real_counter"),"Invalid counter accepted");
            require(ItemStack.isSameItemSameComponents(before,player.getItemInHand(hand)),"Invalid retune mutated stack");
            var destination=CounterArsenal.ITEMS.get(CounterArsenal.ROSTER.getFirst().id()).get();
            player.getCooldowns().addCooldown(before.getItem(),100);
            require(!CounterRetuning.retune(player,CounterArsenal.ROSTER.getFirst().id()),"Source cooldown bypass");
            player.getCooldowns().removeCooldown(before.getItem());
            player.getCooldowns().addCooldown(destination,100);
            require(!CounterRetuning.retune(player,CounterArsenal.ROSTER.getFirst().id()),"Destination cooldown bypass");
            player.getCooldowns().removeCooldown(destination);
            require(ItemStack.isSameItemSameComponents(before,player.getItemInHand(hand)),"Blocked retune mutated stack");
        }
        player.getInventory().clearContent();
        System.out.println("UWU RETUNING PASS 118 selectable targets and 15 retired bows in both hands, full inventory, stack data, invalid IDs and both cooldowns");
    }
    /** Runs after a real server restart, as required by the world-restart policy setting. */
    static void policy(ServerPlayer player) {
        require(AcquisitionPolicy.SPEC.isLoaded()&&!AcquisitionPolicy.weaponAcquisitionEnabled(),"Disabled acquisition policy did not load at startup");
        player.setGameMode(GameType.SURVIVAL);
        require(!player.hasPermissions(2),"Policy fixture unexpectedly has operator permissions");
        var initial=CounterArsenal.ITEMS.get(CounterArsenal.ROSTER.getFirst().id()).get();
        player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(initial));
        String original=CounterArsenal.ROSTER.getFirst().id(),other=CounterArsenal.ROSTER.get(1).id();
        require(CounterRetuning.retune(player,original),"Same-form no-op rejected");
        require(!CounterRetuning.retune(player,other)&&player.getMainHandItem().is(initial),"Disabled policy permitted survival acquisition");
        player.setGameMode(GameType.CREATIVE);
        require(CounterRetuning.retune(player,other),"Creative policy exception rejected");
        player.setGameMode(GameType.SURVIVAL);
        try {
            player.getServer().getPlayerList().op(player.getGameProfile());
            require(player.hasPermissions(2)&&CounterRetuning.retune(player,original),"Operator policy exception rejected");
        } finally { player.getServer().getPlayerList().deop(player.getGameProfile()); }
        System.out.println("UWU RETUNING RESTART POLICY PASS survival restriction, same-form no-op, creative and operator exceptions");
    }
}
