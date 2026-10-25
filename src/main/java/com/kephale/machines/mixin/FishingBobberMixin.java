package com.kephale.machines.mixin;

import com.kephale.machines.MachinesMod;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kmach$remote(CallbackInfo ci) {
        try {
            FishingBobberEntity self = (FishingBobberEntity) (Object) this;
            if (self.getEntityWorld().isClient()) return;
            MachinesMod mod = MachinesMod.INSTANCE;
            if (mod != null && mod.remoteRods.onBobberTick(self)) ci.cancel();
        } catch (Throwable t) {
            MachinesMod.LOGGER.error("Machines: remote rod hook failed", t);
        }
    }
}
