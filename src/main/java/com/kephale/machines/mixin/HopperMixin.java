package com.kephale.machines.mixin;

import com.kephale.machines.MachinesMod;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperMixin {

    @Inject(method = "canExtract", at = @At("HEAD"), cancellable = true)
    private static void kmach$noExtract(Inventory hopperInventory, Inventory fromInventory, ItemStack stack,
                                        int slot, Direction facing, CallbackInfoReturnable<Boolean> cir) {
        try {
            MachinesMod mod = MachinesMod.INSTANCE;
            if (mod != null && !mod.ghostBarrels.canHopperTake(fromInventory, stack)) cir.setReturnValue(false);
        } catch (Throwable ignored) {
        }
    }
}
