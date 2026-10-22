package com.kephale.machines.mixin;

import com.kephale.machines.MachinesMod;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "postPlacement", at = @At("HEAD"))
    private void kmach$placed(BlockPos pos, World world, PlayerEntity player, ItemStack stack, BlockState state,
                              CallbackInfoReturnable<Boolean> cir) {
        try {
            MachinesMod mod = MachinesMod.INSTANCE;
            if (mod != null) mod.ghostBarrels.onPlaced(world, pos, stack);
        } catch (Throwable t) {
            MachinesMod.LOGGER.error("Machines: placement hook failed", t);
        }
    }
}
