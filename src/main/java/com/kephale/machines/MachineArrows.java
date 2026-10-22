package com.kephale.machines;

import com.kephale.machines.mixin.ArrowAccessor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class MachineArrows {

    public static final String TAG = "kmach_arrow";

    private final MachinesMod mod;
    private final Map<PersistentProjectileEntity, Long> expiry = new IdentityHashMap<>();

    public MachineArrows(MachinesMod mod) { this.mod = mod; }

    public void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof PersistentProjectileEntity a && a.getCommandTags().contains(TAG)) {
                expiry.putIfAbsent(a, mod.tick + 100);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof PersistentProjectileEntity a) expiry.remove(a);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Entity direct = source.getSource();
            if (direct == null || !direct.getCommandTags().contains(TAG)) return true;
            boolean blocked = (entity instanceof ServerPlayerEntity p && mod.isImmune(p.getUuid()))
                    || (direct.getCommandTags().contains(OrbitalRod.TAG)
                        && mod.data.orbital.selfImmune
                        && source.getAttacker() == entity);
            if (blocked) direct.discard();
            return !blocked;
        });
    }

    public void spawn(ServerWorld world, Vec3d pos, Vec3d dir, double speed, double spread,
                      double damage, Entity owner, int lifetime, String extraTag) {
        ArrowEntity arrow = new ArrowEntity(world, pos.x, pos.y, pos.z, new ItemStack(Items.ARROW), null);
        if (owner != null) arrow.setOwner(owner);
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        arrow.setVelocity(dir.x, dir.y, dir.z, (float) speed, (float) spread);
        arrow.setDamage(speed > 0.01 ? damage / speed : damage);
        arrow.setCritical(false);
        arrow.addCommandTag(TAG);
        if (extraTag != null) arrow.addCommandTag(extraTag);
        expiry.put(arrow, mod.tick + Math.max(1, lifetime));
        world.spawnEntity(arrow);
    }

    public void tick() {
        if (expiry.isEmpty()) return;
        List<PersistentProjectileEntity> dead = new ArrayList<>();
        Iterator<Map.Entry<PersistentProjectileEntity, Long>> it = expiry.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PersistentProjectileEntity, Long> e = it.next();
            PersistentProjectileEntity a = e.getKey();
            if (a.isRemoved()) {
                it.remove();
            } else if (mod.tick >= e.getValue()) {
                it.remove();
                if (!inGround(a)) dead.add(a);
            }
        }
        for (PersistentProjectileEntity a : dead) a.discard();
    }

    private static boolean inGround(PersistentProjectileEntity a) {
        try {
            return ((ArrowAccessor) a).kmach$isInGround();
        } catch (Throwable t) {
            return false;
        }
    }
}
