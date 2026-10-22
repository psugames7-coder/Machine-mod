package com.kephale.machines;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public final class Tagging {

    public static final String KEY = "kmach";
    public static final String LINK = "kmach_link";

    public static final String GHOST_BARREL = "ghost_barrel";
    public static final String LINKED_TOTEM = "linked_totem";
    public static final String GHOST_TOTEM = "ghost_totem";
    public static final String ORBITAL_ROD = "orbital_rod";

    private Tagging() {}

    public static String kind(ItemStack stack) {
        NbtCompound nbt = read(stack);
        return nbt == null ? null : nbt.getString(KEY, null);
    }

    public static String link(ItemStack stack) {
        NbtCompound nbt = read(stack);
        return nbt == null ? null : nbt.getString(LINK, null);
    }

    public static boolean is(ItemStack stack, String kind) {
        return kind.equals(kind(stack));
    }

    public static void mark(ItemStack stack, String kind, String link) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            nbt.putString(KEY, kind);
            if (link != null) nbt.putString(LINK, link);
        });
    }

    public static void strip(ItemStack stack) {
        NbtCompound nbt = read(stack);
        if (nbt == null) return;
        nbt.remove(KEY);
        nbt.remove(LINK);
        if (nbt.isEmpty()) stack.remove(DataComponentTypes.CUSTOM_DATA);
        else stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static NbtCompound read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? null : c.copyNbt();
    }
}
