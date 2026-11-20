package dev.uwu.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class LanternStorybookEntity extends BlockEntity {
    private ItemStack storedBook = ItemStack.EMPTY;

    LanternStorybookEntity(BlockPos pos, BlockState state) {
        super(LanternStorybook.ENTITY.get(), pos, state);
    }

    void store(ItemStack book) {
        storedBook = book.copyWithCount(1);
        setChanged();
    }

    ItemStack book(HolderLookup.Provider registries) {
        return storedBook.isEmpty() ? LanternBookStacks.create(registries) : storedBook.copy();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!storedBook.isEmpty()) tag.put("Book", storedBook.save(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedBook = tag.contains("Book", Tag.TAG_COMPOUND)
                ? ItemStack.parse(registries, tag.getCompound("Book")).filter(LanternBookStacks::isLanternBook)
                        .map(book -> book.copyWithCount(1)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }
}
