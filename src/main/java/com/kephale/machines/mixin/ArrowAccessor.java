package com.kephale.machines.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PersistentProjectileEntity.class)
public interface ArrowAccessor {

    @Invoker("isInGround")
    boolean kmach$isInGround();
}
