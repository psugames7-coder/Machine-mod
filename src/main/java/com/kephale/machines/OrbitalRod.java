package com.kephale.machines;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

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
            String shooter = Tagging.link(stack);
            Tagging.strip(stack);
            stack.damage(1, sp, hand);
            fire(sp, shooter);
            return ActionResult.SUCCESS;
        });
    }

    public ItemStack makeRod(Integer durability, boolean unbreakable, ServerPlayerEntity shooter) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        Tagging.mark(rod, Tagging.ORBITAL_ROD, shooter == null ? null : shooter.getUuidAsString());
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

    public void fire(ServerPlayerEntity caller, String shooterUuid) {
        ServerWorld world = (ServerWorld) caller.getEntityWorld();
        Vec3d at = caller.getEntityPos();
        MachineData.OrbitalSettings s = mod.data.orbital;
        Entity owner = shooterUuid == null ? caller : resolve(shooterUuid);
        String callerTag = MachineArrows.CALLER_PREFIX + caller.getUuidAsString();
        int[] powers = s.version == 1 ? new int[]{5} : new int[]{2, 3, 5};
        int rings = Math.max(0, s.rings);
        for (int round = 0; round < powers.length; round++) {
            final int power = powers[round];
            int roundStart = round * Math.max(0, s.roundDelay);
            for (int i = 0; i <= rings; i++) {
                final int ring = i;
                mod.schedule(roundStart + ring * Math.max(0, s.waveDelay),
                        () -> spawnRing(world, at.x, at.z, ring, rings, s, owner, power, callerTag));
            }
        }
    }

    private Entity resolve(String uuid) {
        if (mod.server == null) return null;
        try {
            return mod.server.getPlayerManager().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void spawnRing(ServerWorld world, double x, double z, int ring, int rings,
                           MachineData.OrbitalSettings s, Entity owner, int power, String callerTag) {
        if (ring == 0) {
            mod.arrows.spawnPowered(world, new Vec3d(x, s.height, z), DOWN, s.velocity, 0, power, owner, s.arrowLifetime, TAG, callerTag);
            return;
        }
        double r = s.radius * ring / rings;
        int n = Math.max(1, (int) Math.round(2 * Math.PI * r / Math.max(0.25, s.spacing)));
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n;
            Vec3d pos = new Vec3d(x + r * Math.cos(a), s.height, z + r * Math.sin(a));
            mod.arrows.spawnPowered(world, pos, DOWN, s.velocity, 0, power, owner, s.arrowLifetime, TAG, callerTag);
        }
    }
}
