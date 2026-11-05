package dev.uwu.dimension;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

/** The same profession selects the garment print and its independently articulated accessories. */
enum KeeperOutfit {
    EVERYDAY(VillagerProfession.NONE),
    ARMORER(VillagerProfession.ARMORER),
    BUTCHER(VillagerProfession.BUTCHER),
    CARTOGRAPHER(VillagerProfession.CARTOGRAPHER),
    CLERIC(VillagerProfession.CLERIC),
    FARMER(VillagerProfession.FARMER),
    FISHERMAN(VillagerProfession.FISHERMAN),
    FLETCHER(VillagerProfession.FLETCHER),
    LEATHERWORKER(VillagerProfession.LEATHERWORKER),
    LIBRARIAN(VillagerProfession.LIBRARIAN),
    MASON(VillagerProfession.MASON),
    SHEPHERD(VillagerProfession.SHEPHERD),
    TOOLSMITH(VillagerProfession.TOOLSMITH),
    WEAPONSMITH(VillagerProfession.WEAPONSMITH);

    final String key = name().toLowerCase(Locale.ROOT);
    private final VillagerProfession profession;
    private final ResourceLocation petal;
    private final ResourceLocation dusk;
    private static final Map<VillagerProfession, KeeperOutfit> BY_PROFESSION = new HashMap<>();

    static {
        for (KeeperOutfit outfit : values()) BY_PROFESSION.put(outfit.profession, outfit);
    }

    KeeperOutfit(VillagerProfession profession) {
        this.profession = profession;
        String suffix = profession == VillagerProfession.NONE ? "_keeper.png" : "_keeper/" + key + ".png";
        petal = ResourceLocation.fromNamespaceAndPath("uwudimension", "textures/entity/petal" + suffix);
        dusk = ResourceLocation.fromNamespaceAndPath("uwudimension", "textures/entity/dusk" + suffix);
    }

    static KeeperOutfit forKeeper(Keeper keeper) {
        return keeper.isBaby() ? EVERYDAY : BY_PROFESSION.getOrDefault(keeper.getVillagerData().getProfession(), EVERYDAY);
    }

    ResourceLocation texture(boolean isDusk) { return isDusk ? dusk : petal; }

    // Four fabric swatches occupy the lower half of each skin atlas.
    private static final int FABRIC = 0, CONTRAST = 1, TRIM = 2, METAL = 3;
    private static CubeListBuilder patch(CubeListBuilder kit, int swatch,
            float x, float y, float z, int width, int height, int depth) {
        return kit.texOffs((swatch % 2) * 64, 64 + (swatch / 2) * 32)
                .addBox(x, y, z, width, height, depth);
    }

    private void attach(PartDefinition parent, CubeListBuilder kit) {
        parent.addOrReplaceChild(key, kit, PartPose.ZERO);
    }

    private static CubeListBuilder kit() { return CubeListBuilder.create(); }

    static void addAccessories(PartDefinition body, PartDefinition head, PartDefinition leftArm, PartDefinition rightArm) {
        CubeListBuilder plate = patch(kit(), FABRIC, -3.5F, 4, -2.75F, 7, 6, 1);
        ARMORER.attach(body, patch(plate, TRIM, -2.5F, 9.5F, -3, 5, 2, 1));
        for (PartDefinition arm : new PartDefinition[]{leftArm, rightArm}) {
            CubeListBuilder pauldron = patch(kit(), FABRIC, -2.5F, -1, -2.5F, 5, 2, 5);
            ARMORER.attach(arm, patch(pauldron, TRIM, -2, 1, -2, 4, 1, 4));
        }

        CubeListBuilder toque = patch(kit(), CONTRAST, -4.5F, -9.5F, -4.5F, 9, 2, 9);
        BUTCHER.attach(head, patch(toque, FABRIC, -5, -11, -5, 10, 3, 10));
        BUTCHER.attach(body, patch(kit(), FABRIC, -3.5F, 11.5F, -3.7F, 7, 5, 1));

        CubeListBuilder monocle = patch(kit(), METAL, -3.5F, -4.75F, -4.65F, 3, 3, 1);
        CARTOGRAPHER.attach(head, patch(monocle, TRIM, -4, -2, -4.35F, 1, 3, 1));
        CubeListBuilder maps = patch(kit(), CONTRAST, 3.7F, 9, 1, 3, 7, 3);
        patch(maps, FABRIC, 4.4F, 7, 1.5F, 2, 3, 2);
        CARTOGRAPHER.attach(body, patch(maps, TRIM, 3.5F, 12, .8F, 3, 1, 3));

        CubeListBuilder circlet = patch(kit(), METAL, -4.75F, -7.5F, -4.8F, 9, 1, 9);
        CLERIC.attach(head, patch(circlet, TRIM, -.5F, -7, -5, 1, 2, 1));
        CubeListBuilder stole = patch(kit(), CONTRAST, -3, 3, -2.75F, 2, 9, 1);
        patch(stole, CONTRAST, 1, 3, -2.75F, 2, 9, 1);
        for (int x : new int[]{-3, 1}) patch(stole, TRIM, x, 11.8F, -3.4F, 2, 5, 1);
        CLERIC.attach(body, stole);

        CubeListBuilder straw = patch(kit(), FABRIC, -7, -8.7F, -6.5F, 14, 1, 13);
        patch(straw, FABRIC, -4.5F, -11, -4.5F, 9, 3, 9);
        FARMER.attach(head, patch(straw, CONTRAST, -4.6F, -9.5F, -4.6F, 9, 1, 9));
        CubeListBuilder apron = patch(kit(), CONTRAST, -2.5F, 5, -2.7F, 5, 5, 1);
        patch(apron, CONTRAST, -3.5F, 11, -3.6F, 7, 5, 1);
        FARMER.attach(body, patch(apron, TRIM, -.5F, 13, -3.85F, 3, 2, 1));

        CubeListBuilder souwester = patch(kit(), FABRIC, -5, -9.5F, -5, 10, 2, 10);
        patch(souwester, CONTRAST, -5.5F, -8, -5.5F, 11, 1, 11);
        FISHERMAN.attach(head, patch(souwester, METAL, 4.8F, -7.8F, -2, 1, 3, 1));
        CubeListBuilder creel = patch(kit(), TRIM, -6, 9.5F, 0, 4, 2, 3);
        FISHERMAN.attach(body, patch(creel, FABRIC, -6.5F, 11, -.5F, 5, 4, 4));

        CubeListBuilder plume = patch(kit(), FABRIC, 3.5F, -9.5F, 0, 2, 4, 1);
        FLETCHER.attach(head, patch(plume, TRIM, 4.5F, -11, 0, 2, 3, 1));
        CubeListBuilder quiver = patch(kit(), CONTRAST, -3, 5, 3.4F, 4, 9, 3);
        for (int x = -3; x <= 0; x += 2) {
            patch(quiver, METAL, x + .5F, 2.5F, 4, 1, 4, 1);
            patch(quiver, TRIM, x, 1.5F, 4, 2, 2, 1);
        }
        FLETCHER.attach(body, quiver);

        CubeListBuilder hide = patch(kit(), FABRIC, -3.5F, 5, -2.65F, 6, 7, 1);
        patch(hide, FABRIC, -3.5F, 12, -3.6F, 6, 5, 1);
        patch(hide, CONTRAST, 2, 10.5F, -3, 3, 2, 3);
        LEATHERWORKER.attach(body, patch(hide, METAL, 2.5F, 12, -3.2F, 1, 3, 1));

        CubeListBuilder glasses = kit();
        for (float x : new float[]{-3.5F, .5F}) patch(glasses, METAL, x, -4.5F, -4.65F, 3, 2, 1);
        LIBRARIAN.attach(head, patch(glasses, TRIM, -.6F, -4.25F, -4.68F, 2, 1, 1));
        CubeListBuilder book = patch(kit(), CONTRAST, 2.8F, 10, -2.5F, 3, 5, 4);
        patch(book, FABRIC, 3.2F, 9.5F, -2, 2, 1, 3);
        LIBRARIAN.attach(body, patch(book, TRIM, 2.7F, 10, -2.85F, 3, 2, 1));

        MASON.attach(head, patch(kit(), CONTRAST, -4.6F, -7.4F, -4.7F, 9, 2, 9));
        CubeListBuilder mallet = patch(kit(), TRIM, -5.5F, 11, .5F, 1, 6, 1);
        MASON.attach(body, patch(mallet, FABRIC, -6.5F, 10, -.5F, 3, 3, 3));

        CubeListBuilder fleece = patch(kit(), FABRIC, -4.8F, -9.5F, -4.6F, 10, 2, 9);
        SHEPHERD.attach(head, patch(fleece, CONTRAST, 2.5F, -10.5F, 0, 3, 3, 3));
        CubeListBuilder shawl = patch(kit(), FABRIC, -4.5F, 3, -2.65F, 9, 3, 5);
        SHEPHERD.attach(body, patch(shawl, CONTRAST, -5.5F, 11, 0, 4, 4, 4));

        CubeListBuilder goggles = patch(kit(), CONTRAST, -4.65F, -7.9F, -4.6F, 9, 2, 9);
        for (float x : new float[]{-3.5F, .5F}) patch(goggles, METAL, x, -8, -5.2F, 3, 3, 1);
        TOOLSMITH.attach(head, goggles);
        for (PartDefinition arm : new PartDefinition[]{leftArm, rightArm}) {
            TOOLSMITH.attach(arm, patch(kit(), FABRIC, -1.8F, 5.5F, -1.8F, 4, 4, 4));
        }
        CubeListBuilder belt = patch(kit(), TRIM, -4.2F, 10, -2.85F, 8, 2, 5);
        TOOLSMITH.attach(body, patch(belt, METAL, 2.7F, 11, -3, 1, 3, 1));

        CubeListBuilder patch = patch(kit(), CONTRAST, -3.5F, -4.8F, -4.75F, 3, 3, 1);
        WEAPONSMITH.attach(head, patch(patch, TRIM, -.5F, -5.75F, -4.5F, 2, 1, 1));
        CubeListBuilder scabbard = patch(kit(), FABRIC, -5.6F, 10.5F, 1, 2, 8, 2);
        patch(scabbard, METAL, -6.6F, 9.5F, 1, 4, 1, 2);
        WEAPONSMITH.attach(body, patch(scabbard, TRIM, -5.1F, 7, 1, 1, 3, 2));
    }
}
