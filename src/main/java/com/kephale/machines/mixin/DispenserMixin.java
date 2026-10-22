package com.kephale.machines.mixin;

import com.kephale.machines.MachinesMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DispenserBlock.class)
public abstract class DispenserMixin {

    @Inject(method = "dispense", at = @At("HEAD"), cancellable = true)
    private void kmach$cannon(ServerWorld world, BlockState state, BlockPos pos, CallbackInfo ci) {
        try {
            MachinesMod mod = MachinesMod.INSTANCE;
            if (mod != null && mod.tntCannons.isCannon(world, pos)) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
