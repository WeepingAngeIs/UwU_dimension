package dev.uwu.test;

import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.KeeperRenderer;
import dev.uwu.dimension.People;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Exercise the actual registered client renderer and the resources available to a player. */
final class WardrobeProof {
    static void verify(Minecraft client) {
        List<String> roles = List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
                "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith");
        Set<String> artwork = new HashSet<>();
        try {
            for (var type : List.of(People.PETAL_KEEPER, People.DUSK_KEEPER)) {
                Keeper keeper = type.get().create(client.level);
                KeeperRenderer renderer = (KeeperRenderer) client.getEntityRenderDispatcher().getRenderer(keeper);
                ResourceLocation everyday = renderer.getTextureLocation(keeper);
                checkHeight(keeper, 1.78F);
                for (String role : roles) {
                    var profession = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(role));
                    if (profession == VillagerProfession.NONE) throw new AssertionError("Missing profession " + role);
                    keeper.setVillagerData(keeper.getVillagerData().setProfession(profession));
                    ResourceLocation resource = renderer.getTextureLocation(keeper);
                    String faction = type == People.PETAL_KEEPER ? "petal" : "dusk";
                    if (!resource.getPath().equals("textures/entity/" + faction + "_keeper/" + role + ".png"))
                        throw new AssertionError("Incorrect garment " + role + " -> " + resource);
                    try (var stream = client.getResourceManager().open(resource)) {
                        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                        if (!artwork.add(digest)) throw new AssertionError("Reused garment print " + resource);
                    }
                    checkAccessories(renderer, keeper, roles, role);
                }
                keeper.setAge(-24000);
                checkHeight(keeper, 1.00125F);
                if (!renderer.getTextureLocation(keeper).equals(everyday)) throw new AssertionError("Child garment");
                checkAccessories(renderer, keeper, roles, "");
                keeper.setAge(0);
                keeper.setVillagerData(keeper.getVillagerData().setProfession(VillagerProfession.NONE));
                checkAccessories(renderer, keeper, roles, "");
                keeper.discard();
            }
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new AssertionError("Keeper wardrobe assets", failure);
        }
        System.out.println("KEEPER WARDROBE PASS | 13 equipped professions in both factions, 26 distinct prints, exclusive animated accessories, adult and baby model heights");
    }

    private static void checkHeight(Keeper keeper, float expected) {
        float visible = KeeperRenderer.modelScale(keeper) * 29.5F / 16F;
        if (Math.abs(visible - expected) > .005F || Math.abs(keeper.getBbHeight() - expected) > .005F)
            throw new AssertionError("Keeper visible/collision height " + visible + "/" + keeper.getBbHeight());
    }

    private static void checkAccessories(KeeperRenderer renderer, Keeper keeper, List<String> roles, String active) {
        var model = renderer.getModel();
        model.setupAnim(keeper, 1.5F, .6F, 20, 15, -4);
        ModelPart root = model.root();
        var parents = List.of(root, root.getChild("head"), root.getChild("right_arm"), root.getChild("left_arm"));
        for (String role : roles) {
            boolean present = false;
            for (ModelPart parent : parents) {
                if (!parent.hasChild(role)) continue;
                ModelPart part = parent.getChild(role);
                if (part.isEmpty() || part.visible != role.equals(active)) throw new AssertionError("Accessory " + active + "/" + role);
                present = true;
            }
            if (!present) throw new AssertionError("No geometry for " + role);
        }
    }
}
