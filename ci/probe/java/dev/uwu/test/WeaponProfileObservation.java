package dev.uwu.test;

import com.google.gson.*;
import dev.uwu.dimension.GlitchBow;
import dev.uwu.dimension.UwUDimension;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;

/** Observes the real loaded registry, including nulls and explicit per-stack presets. */
final class WeaponProfileObservation {
    private static final Gson GSON=new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static Object get(Object target,String name)throws ReflectiveOperationException{
        return target.getClass().getMethod(name).invoke(target);
    }
    private static JsonElement profile(Object attrs)throws ReflectiveOperationException{
        if(attrs==null)return JsonNull.INSTANCE;
        var output=new JsonObject();
        for(String name:List.of("attackRange","rangeBonus","pose","offHandPose","category","isTwoHanded","two_handed","trailAppearance"))
            output.add(name,GSON.toJsonTree(get(attrs,name)));
        Object[] attacks=(Object[])get(attrs,"attacks");
        if(attacks==null)output.add("attacks",JsonNull.INSTANCE);
        else{
            var all=new JsonArray();
            for(var attack:attacks){
                if(attack==null){all.add(JsonNull.INSTANCE);continue;}
                var row=new JsonObject();
                for(String name:List.of("conditions","hitbox","damageMultiplier","movementSpeedMultiplier","rangeMultiplier","angle","upswing","animation","swingSound","impactSound","trailParticles"))
                    row.add(name,GSON.toJsonTree(get(attack,name)));
                all.add(row);
            }
            output.add("attacks",all);
        }
        return output;
    }
    @SuppressWarnings("unchecked") static void run()throws Exception{
        if(!ModList.get().isLoaded("bettercombat")||!ModList.get().isLoaded("simplyswords"))
            throw new AssertionError("Baseline requires installed Better Combat and Simply Swords");
        var type=(DataComponentType<ResourceLocation>)BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(ResourceLocation.parse("bettercombat:preset_id")).orElseThrow();
        var registry=Class.forName("net.bettercombat.logic.WeaponRegistry");
        var resolve=registry.getMethod("getAttributes",ItemStack.class);
        var root=new JsonObject();var mods=new JsonObject();
        for(String id:List.of("uwudimension","bettercombat","simplyswords","simplymore","playeranimator","cloth_config"))
            mods.addProperty(id,ModList.get().getModContainerById(id).map(c->c.getModInfo().getVersion().toString()).orElse("absent"));
        root.add("versions",mods);var rows=new JsonArray();int uwu=0,reference=0,forms=0;
        for(Item item:BuiltInRegistries.ITEM){
            var id=BuiltInRegistries.ITEM.getKey(item);String ns=id.getNamespace();
            if(!ns.equals("uwudimension")&&!ns.equals("simplyswords")&&!ns.equals("simplymore")
                    &&!(ns.equals("minecraft")&&List.of("iron_sword","iron_axe","trident","bow").contains(id.getPath())))continue;
            var stack=new ItemStack(item);var row=observe(id.toString(),stack,resolve,type,ns.equals("uwudimension"));rows.add(row);
            if(ns.equals("uwudimension"))uwu++;else reference++;
        }
        for(int index=0;index<GlitchBow.FORMS.size();index++){
            var stack=new ItemStack(UwUDimension.GLITCH.get());int form=index;
            CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putInt("uwu_form",form));
            var row=observe("uwudimension:glitch_bow#"+GlitchBow.FORMS.get(index),stack,resolve,type,true);
            row.addProperty("form_index",index);rows.add(row);forms++;
        }
        if(uwu==0||reference==0||forms==0)throw new AssertionError("Empty registry observation");
        root.add("items",rows);root.addProperty("uwu_item_count",uwu);root.addProperty("reference_item_count",reference);root.addProperty("glitch_form_count",forms);
        Files.writeString(Path.of("weapon-profile-observation.json"),GSON.toJson(root));
        System.out.println("UWU WEAPON PROFILE OBSERVATION PASS uwu="+uwu+" references="+reference+" glitchForms="+forms+"; null/explicit presets and all attack fields captured");
    }
    private static JsonObject observe(String id,ItemStack stack,java.lang.reflect.Method resolve,
                                      DataComponentType<ResourceLocation> type,boolean explicit)throws Exception{
        var before=stack.copy();var row=new JsonObject();row.addProperty("id",id);
        row.addProperty("item_class",stack.getItem().getClass().getName());
        var priorPreset=stack.get(type);row.add("base_preset",priorPreset==null?JsonNull.INSTANCE:new JsonPrimitive(priorPreset.toString()));
        row.add("base_profile",profile(resolve.invoke(null,stack)));
        if(!ItemStack.matches(before,stack))throw new AssertionError("Registry lookup modified item "+id);
        var modifiers=new JsonArray();
        for(var e:stack.getAttributeModifiers().modifiers()){
            var m=new JsonObject();m.addProperty("attribute",String.valueOf(BuiltInRegistries.ATTRIBUTE.getKey(e.attribute().value())));
            m.addProperty("id",e.modifier().id().toString());m.addProperty("amount",e.modifier().amount());
            m.addProperty("operation",e.modifier().operation().toString());m.addProperty("slot",e.slot().toString());modifiers.add(m);
        }
        row.add("item_attribute_modifiers",modifiers);
        if(explicit){
            var override=stack.copy();var idOverride=ResourceLocation.parse("bettercombat:dagger");override.set(type,idOverride);var snapshot=override.copy();
            Object attributes=resolve.invoke(null,override);row.addProperty("explicit_preset",idOverride.toString());row.add("explicit_profile",profile(attributes));
            if(!ItemStack.matches(snapshot,override)||!idOverride.equals(override.get(type)))throw new AssertionError("Explicit preset changed by lookup "+id);
            if(attributes==null||!"dagger".equals(get(attributes,"category")))throw new AssertionError("Explicit preset ignored "+id);
        }
        return row;
    }
}
