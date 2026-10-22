package com.kephale.machines;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Vec3d;

public final class OrbitalRod {

    public static final String TAG = "kmach_orbital";
    private static final Vec3d DOWN = new Vec3d(0, -1, 0);

    private final MachinesMod mod;

    public OrbitalRod(MachinesMod mod) { this.mod = mod; }

    public void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.FISHING_ROD) || !Tagging.is(stack, Tagging.ORBITAL_ROD)) return ActionResult.PASS;
            Tagging.strip(stack);
            fire(sp);
            return ActionResult.SUCCESS;
        });
    }

    public ItemStack makeRod(Integer durability, boolean unbreakable) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        Tagging.mark(rod, Tagging.ORBITAL_ROD, null);
        if (unbreakable) {
            rod.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        } else if (durability != null) {
            int max = rod.getOrDefault(DataComponentTypes.MAX_DAMAGE, 64);
            if (durability > max) {
                rod.set(DataComponentTypes.MAX_DAMAGE, durability);
                rod.set(DataComponentTypes.DAMAGE, 0);
            } else {
                rod.set(DataComponentTypes.DAMAGE, max - durability);
            }
        }
        return rod;
    }

    public void fire(ServerPlayerEntity caller) {
        ServerWorld world = (ServerWorld) caller.getEntityWorld();
        Vec3d at = caller.getEntityPos();
        MachineData.OrbitalSettings s = mod.data.orbital;
        int rings = Math.max(0, s.rings);
        for (int i = 0; i <= rings; i++) {
            final int ring = i;
            mod.schedule(ring * Math.max(0, s.waveDelay), () -> spawnRing(world, at.x, at.z, ring, rings, s, caller));
        }
    }

    private void spawnRing(ServerWorld world, double x, double z, int ring, int rings,
                           MachineData.OrbitalSettings s, ServerPlayerEntity owner) {
        if (ring == 0) {
            mod.arrows.spawn(world, new Vec3d(x, s.height, z), DOWN, s.velocity, 0, s.damage, owner, s.arrowLifetime, TAG);
            return;
        }
        double r = s.radius * ring / rings;
        int n = Math.max(1, (int) Math.round(2 * Math.PI * r / Math.max(0.25, s.spacing)));
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n;
            Vec3d pos = new Vec3d(x + r * Math.cos(a), s.height, z + r * Math.sin(a));
            mod.arrows.spawn(world, pos, DOWN, s.velocity, 0, s.damage, owner, s.arrowLifetime, TAG);
        }
    }
}
