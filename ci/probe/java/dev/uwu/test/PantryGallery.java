package dev.uwu.test;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Uses the stock survival inventory, hover tooltip, held item renderer and hotbar on the hosted client. */
@EventBusSubscriber(modid="heartwater_probe",value=Dist.CLIENT)
public final class PantryGallery {
    private static final String[] CATALOG="sweet_berry_cheesecake_slice apple_pie_slice uwu_pancake_stack pink_melon_popsicle pink_fruit_salad sweet_berry_cookie blush_cabbage_roll strawberry_milk chorus_fruit_pie_slice chorus_blossom_tea dragon_breath_soda shulker_soup strider_moss_stew nether_fungus_skewer stuffed_hoglin_roast magma_gelatin blossom_ice_cream_bucket strawberry_ice_cream_cake flower_stuffed_chicken heartberry_burger blossom_hot_pocket uwu_ramen_bowl cherry_picnic_bento berry_cinnamon_roll starberry_waffle_stack creamy_mushroom_stew loaded_heart_potato rose_berry_pie_slice pastel_sakura_dumplings honey_glazed_ham moonberry_pudding chicken_flower_pot_pie sakura_katsu_curry heart_omurice cherry_onigiri_bento golden_taiyaki cozy_udon_hotpot sakura_melonpan strawberry_shokupan sweet_bean_dorayaki sakura_mochi_ice_cream maple_ice_cream_sundae maple_poutine tourtiere_hand_pie nanaimo_heart_bar maple_butter_tart saskatoon_berry_pie_slice maple_glazed_salmon_bowl heartberry sakura_rice starfruit frostpetal sweetbean_pods glowginger vanilla_bean moonberry sakura_flour heartberry_jam shaved_ice pink_shaved_ice chocolate_syrup sweetbean_paste maple_syrup petal_sugar vanilla_extract heartberry_seeds sakura_rice_seeds starfruit_seeds frostpetal_seeds sweetbean_seeds glowginger_seeds vanilla_bean_seeds".split(" ");
    private static final List<String> FRAMES=List.of("pantry-inventory-1","pantry-inventory-2","pantry-inventory-3",
            "pantry-tooltip-starfruit","pantry-tooltip-cake","pantry-held-starfruit","pantry-held-ice-cream");
    private static volatile boolean active,restored;
    private static volatile int applied,stage;
    private static int sent,ticks,frames,selected;
    private static CameraType previousCamera;
    private static boolean previousHide;
    private static List<ItemStack> previous;
    private PantryGallery(){}
    static boolean active(){return active;}
    private static Item item(String key){return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("uwudimension",key)).orElseThrow();}
    private static String focus(int n){return switch(n){case 4->"strawberry_ice_cream_cake";case 6->"blossom_ice_cream_bucket";default->"starfruit";};}
    private static String[] items(int n){
        int page=n<3?n*24:0;
        return Arrays.copyOfRange(CATALOG,page,Math.min(CATALOG.length,page+24));
    }

    static void begin(Minecraft mc){
        if(active||CATALOG.length!=72)throw new AssertionError("Pantry survey registration mismatch");
        previousCamera=mc.options.getCameraType();previousHide=mc.options.hideGui;
        mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;
        previous=null;restored=false;active=true;stage=ticks=frames=0;sent=applied=-1;
    }

    static boolean tick(Minecraft mc){
        if(!active)return true;
        if(++ticks>1100)throw new AssertionError("Pantry photograph stalled at "+stage+" / "+sent);
        int n=stage;
        if(sent!=n){
            sent=n;frames=0;
            if(mc.screen instanceof PantryInventory)mc.setScreen(null);
            var server=mc.getSingleplayerServer();
            var id=mc.player.getUUID();
            server.execute(()->assign(server.getPlayerList().getPlayer(id),n));
        }
        if(n==FRAMES.size()){
            if(!restored || mc.player.getInventory().selected!=selected || !ItemStack.matches(mc.player.getMainHandItem(),previous.get(selected)))return false;
            mc.options.hideGui=previousHide;mc.options.setCameraType(previousCamera);active=false;
            System.out.println("SAKURA PANTRY REAL ITEMS PASS 72 individually resolved production sprites, 3 survival inventories, hotbar, 2 hover tooltips and 2 first-person items");
            return true;
        }
        if(applied!=n||!ready(mc,n))return false;
        if(n<5&&!(mc.screen instanceof PantryInventory))mc.setScreen(new PantryInventory(mc,n>=3));
        return false;
    }

    private static void assign(ServerPlayer p,int n){
        var inv=p.getInventory();
        if(previous==null){
            previous=java.util.stream.IntStream.range(0,36).mapToObj(i->inv.getItem(i).copy()).toList();
            selected=inv.selected;
        }
        if(n==FRAMES.size()){
            for(int i=0;i<36;i++)inv.setItem(i,previous.get(i));
            inv.selected=selected;
        }else{
            var display=items(n);
            for(int i=0;i<36;i++)inv.setItem(i,ItemStack.EMPTY);
            for(int i=0;i<display.length;i++)inv.setItem(9+i,new ItemStack(item(display[i])));
            for(int i=0;i<9;i++)inv.setItem(i,new ItemStack(item(display[i])));
            if(n>=3){inv.setItem(9,new ItemStack(item(focus(n))));inv.setItem(selected,new ItemStack(item(focus(n))));}
        }
        inv.setChanged();p.inventoryMenu.broadcastChanges();
        if(n==FRAMES.size())restored=true;
        applied=n;
    }

    private static boolean ready(Minecraft mc,int n){
        if(n>=3)return mc.player.getMainHandItem().is(item(focus(n)))&&mc.player.getInventory().getItem(9).is(item(focus(n)));
        var display=items(n);
        for(int i=0;i<display.length;i++){
            var stack=mc.player.getInventory().getItem(i+9);
            if(!stack.is(item(display[i])))return false;
            var baked=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
            String actual=baked.getParticleIcon().contents().name().toString();
            if(!actual.equals("uwudimension:item/"+display[i]))throw new AssertionError(display[i]+" loaded "+actual);
        }
        return true;
    }

    @SubscribeEvent public static void photo(RenderFrameEvent.Post event){
        var mc=Minecraft.getInstance();
        int n=stage;
        if(!active||n>=FRAMES.size()||applied!=n||!ready(mc,n))return;
        if(n<5 ? !(mc.screen instanceof PantryInventory) : mc.screen!=null)return;
        if(++frames<14)return;
        Screenshot.grab(mc.gameDirectory,FRAMES.get(n)+".png",mc.getMainRenderTarget(),msg->System.out.println(msg.getString()));
        frames=0;stage++;
    }

    private static final class PantryInventory extends InventoryScreen {
        private final boolean tooltip;
        PantryInventory(Minecraft mc,boolean tooltip){super(mc.player);this.tooltip=tooltip;}
        @Override public void render(GuiGraphics graphics,int x,int y,float tick){
            if(tooltip){
                var slot=menu.slots.stream().filter(s->s.container==minecraft.player.getInventory()&&s.getContainerSlot()==9).findFirst().orElseThrow();
                super.render(graphics,leftPos+slot.x+5,topPos+slot.y+5,tick);
            }else super.render(graphics,1,1,tick);
        }
    }
}
