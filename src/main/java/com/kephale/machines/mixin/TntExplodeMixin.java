package com.kephale.machines.mixin;

import com.kephale.machines.MachinesMod;
import com.kephale.machines.TntCannons;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(TntEntity.class)
public abstract class TntExplodeMixin {

    @Shadow private float explosionPower;

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void kmach$head(CallbackInfo ci) {
        TntEntity self = (TntEntity) (Object) this;
        Set<String> tags = self.getCommandTags();
        if (tags.contains(TntCannons.NO_DAMAGE_TAG)) {
            if (self.getEntityWorld() instanceof ServerWorld w) {
                w.createExplosion(self, self.getX(), self.getBodyY(0.0625), self.getZ(),
                        explosionPower, World.ExplosionSourceType.NONE);
            }
            ci.cancel();
            return;
        }
        if (tags.contains(TntCannons.NO_DROPS_TAG)) MachinesMod.suppressItemDrops = true;
    }

    @Inject(method = "explode", at = @At("RETURN"))
    private void kmach$ret(CallbackInfo ci) {
        MachinesMod.suppressItemDrops = false;
    }
}
