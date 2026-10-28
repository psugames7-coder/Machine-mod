package com.kephale.machines.mixin;

import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExplosiveProjectileEntity.class)
public interface ExplosiveProjectileAccessor {

    @Accessor("accelerationPower")
    double kmach$getAccelerationPower();

    @Accessor("accelerationPower")
    void kmach$setAccelerationPower(double value);
}
