package com.kephale.machines.mixin;

import com.kephale.machines.MachinesMod;
import com.kephale.machines.Tagging;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class TotemPopMixin {

    @Unique
    private String kmach$link;

    @Inject(method = "tryUseDeathProtector", at = @At("HEAD"))
    private void kmach$head(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        kmach$link = null;
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            for (Hand hand : Hand.values()) {
                ItemStack s = self.getStackInHand(hand);
                if (s.contains(DataComponentTypes.DEATH_PROTECTION)) {
                    if (Tagging.is(s, Tagging.LINKED_TOTEM)) kmach$link = Tagging.link(s);
                    return;
                }
            }
        } catch (Throwable t) {
            MachinesMod.LOGGER.error("Machines: totem hook failed", t);
        }
    }

    @Inject(method = "tryUseDeathProtector", at = @At("RETURN"))
    private void kmach$ret(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        String link = kmach$link;
        kmach$link = null;
        try {
            if (link == null || !Boolean.TRUE.equals(cir.getReturnValue())) return;
            MachinesMod mod = MachinesMod.INSTANCE;
            if (mod != null) mod.ghostBarrels.onPop(link);
        } catch (Throwable t) {
            MachinesMod.LOGGER.error("Machines: totem hook failed", t);
        }
    }
}
