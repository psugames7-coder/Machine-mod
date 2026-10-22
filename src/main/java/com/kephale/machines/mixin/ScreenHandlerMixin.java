package com.kephale.machines.mixin;

import com.kephale.machines.MachinesMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void kmach$head(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        try {
            MachinesMod mod = MachinesMod.INSTANCE;
            if (mod == null || !(player instanceof ServerPlayerEntity sp)) return;
            if (mod.ghostBarrels.onSlotClickHead((ScreenHandler) (Object) this, slotIndex, sp)) ci.cancel();
        } catch (Throwable t) {
            MachinesMod.LOGGER.error("Machines: slot click hook failed", t);
        }
    }

    @Inject(method = "onSlotClick", at = @At("RETURN"))
    private void kmach$ret(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        try {
            MachinesMod mod = MachinesMod.INSTANCE;
            if (mod == null || !(player instanceof ServerPlayerEntity sp)) return;
            mod.ghostBarrels.onSlotClickReturn((ScreenHandler) (Object) this, sp);
        } catch (Throwable t) {
            MachinesMod.LOGGER.error("Machines: slot click hook failed", t);
        }
    }
}
