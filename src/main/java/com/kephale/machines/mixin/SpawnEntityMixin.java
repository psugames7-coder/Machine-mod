package com.kephale.machines.mixin;

import com.kephale.machines.MachinesMod;
import com.kephale.machines.Tagging;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class SpawnEntityMixin {

    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void kmach$filter(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof ItemEntity item)) return;
        if (MachinesMod.suppressItemDrops || Tagging.is(item.getStack(), Tagging.GHOST_TOTEM)) cir.setReturnValue(false);
    }
}
