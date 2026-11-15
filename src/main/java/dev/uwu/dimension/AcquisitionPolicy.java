package dev.uwu.dimension;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Stable server-policy API for companion mods. Registered items and owned inventory never change. */
public final class AcquisitionPolicy {
 public static final ModConfigSpec SPEC;
 public static final ModConfigSpec.BooleanValue HEART_LOCKET_CRAFTING;
 public static final ModConfigSpec.BooleanValue WEAPON_ACQUISITION;
 public static final ModConfigSpec.BooleanValue CUPID_WISHES, CUPID_PLAYER_WITNESSES;
 public static final ModConfigSpec.IntValue CUPID_WISH_RADIUS, CUPID_WISH_PERMISSION;
 static {
  var b=new ModConfigSpec.Builder();
  HEART_LOCKET_CRAFTING=b.comment("Allow crafting heartwater_locket. Restart the world/server after changing; clients receive the server policy.").worldRestart().define("heartLocketCraftingEnabled",true);
  WEAPON_ACQUISITION=b.comment("Allow new survival acquisition of all UwU weapons through crafting, trades and generated loot (including unopened Lootr rolls). Existing owned/opened inventories and operator/creative access remain. Restart after changing.").worldRestart().define("weaponAcquisitionEnabled",true);
  b.push("cupidWishes");
  CUPID_WISHES=b.comment("Allow two selected charmed targets to summon a young creature.").define("enabled",true);
  CUPID_PLAYER_WITNESSES=b.comment("Allow a player who opts in to witness a creature wish.").define("playerWitnesses",true);
  CUPID_WISH_RADIUS=b.comment("Maximum distance between the two targets in blocks.").defineInRange("pairRadius",8,2,24);
  CUPID_WISH_PERMISSION=b.comment("Required command permission level of the archer.").defineInRange("permissionLevel",0,0,4);
  b.pop();
  SPEC=b.build();
 }
 public static boolean heartLocketCraftingEnabled(){return !SPEC.isLoaded()||HEART_LOCKET_CRAFTING.get();}
 public static boolean weaponAcquisitionEnabled(){return !SPEC.isLoaded()||WEAPON_ACQUISITION.get();}
 public static boolean isWeapon(ItemStack stack){return stack.getItem() instanceof Arsenal.RelicItem||stack.getItem() instanceof CounterArsenal.CounterItem||stack.getItem() instanceof GlitchBow;}
 public static boolean allowsWeapon(ItemStack stack){return weaponAcquisitionEnabled()||!isWeapon(stack);}
 public static boolean allowsCraft(ItemStack stack){return allowsWeapon(stack)&&(heartLocketCraftingEnabled()||!stack.is(UwUDimension.LOCKET.get()));}
}
