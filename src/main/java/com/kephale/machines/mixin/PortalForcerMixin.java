package com.kephale.machines.mixin;

import com.kephale.machines.MachinesMod;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockLocating;
import net.minecraft.world.dimension.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PortalForcer.class)
public abstract class PortalForcerMixin {

    @Shadow @Final private ServerWorld world;

    @Inject(method = "createPortal", at = @At("RETURN"))
    private void kmach$trap(BlockPos pos, Direction.Axis axis,
                            CallbackInfoReturnable<Optional<BlockLocating.Rectangle>> cir) {
        try {
            MachinesMod mod = MachinesMod.INSTANCE;
            Optional<BlockLocating.Rectangle> rect = cir.getReturnValue();
            if (mod == null || rect == null || rect.isEmpty()) return;
            mod.portalTrap.onPortalCreated(world, rect.get(), axis);
        } catch (Throwable t) {
            MachinesMod.LOGGER.error("Machines: portal trap failed", t);
        }
    }
}
