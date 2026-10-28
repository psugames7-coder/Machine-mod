package com.kephale.machines;

import com.kephale.machines.mixin.ExplosiveProjectileAccessor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class WitherRod {

    public static final String KIND = "wither_rod";
    public static final String TAG = "kmach_wither";
    private static final Vec3d DOWN = new Vec3d(0, -1, 0);

    private final MachinesMod mod;
    private final Map<Entity, Long> expiry = new IdentityHashMap<>();

    public WitherRod(MachinesMod mod) { this.mod = mod; }

    public void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.FISHING_ROD) || !Tagging.is(stack, KIND)) return ActionResult.PASS;
            if (!mod.data.wither.reusable) Tagging.strip(stack);
            stack.damage(1, sp, hand);
            fire(sp);
            return ActionResult.SUCCESS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Entity direct = source.getSource();
            if (direct == null || !direct.getCommandTags().contains(TAG)) return true;
            if (entity instanceof ServerPlayerEntity p && mod.isImmune(p.getUuid())) return false;
            return !(mod.data.wither.selfImmune && source.getAttacker() == entity);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> expiry.remove(entity));
    }

    public ItemStack makeRod(Integer durability, boolean unbreakable) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        Tagging.mark(rod, KIND, null);
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
        MachineData.WitherSettings s = mod.data.wither;
        int rings = Math.max(0, s.rings);
        for (int i = 0; i <= rings; i++) {
            final int ring = i;
            mod.schedule(ring * Math.max(0, s.waveDelay), () -> spawnRing(world, at.x, at.z, ring, rings, s, caller));
        }
        MachinesMod.sound(world, at.x, at.y, at.z, "entity.wither.shoot", 2.0f, 0.6f);
    }

    private void spawnRing(ServerWorld world, double x, double z, int ring, int rings,
                           MachineData.WitherSettings s, ServerPlayerEntity owner) {
        if (ring == 0) {
            spawn(world, x, z, s, owner);
            return;
        }
        double r = s.radius * ring / rings;
        int n = Math.max(1, (int) Math.round(2 * Math.PI * r / Math.max(0.25, s.spacing)));
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n;
            spawn(world, x + r * Math.cos(a), z + r * Math.sin(a), s, owner);
        }
    }

    private void spawn(ServerWorld world, double x, double z, MachineData.WitherSettings s, ServerPlayerEntity owner) {
        WitherSkullEntity skull = new WitherSkullEntity(world, owner, DOWN);
        skull.setOwner(owner);
        if (s.charged) skull.setCharged(true);
        skull.setPosition(x, s.height, z);
        if (s.speed != 1.0) {
            try {
                ExplosiveProjectileAccessor acc = (ExplosiveProjectileAccessor) skull;
                acc.kmach$setAccelerationPower(acc.kmach$getAccelerationPower() * s.speed);
                skull.setVelocity(skull.getVelocity().multiply(s.speed));
            } catch (Throwable ignored) {
            }
        }
        skull.addCommandTag(TAG);
        expiry.put(skull, mod.tick + Math.max(1, s.lifetime));
        world.spawnEntity(skull);
    }

    public int skullCount() {
        MachineData.WitherSettings s = mod.data.wither;
        int total = 1;
        for (int i = 1; i <= s.rings; i++) {
            double r = s.radius * i / Math.max(1, s.rings);
            total += Math.max(1, (int) Math.round(2 * Math.PI * r / Math.max(0.25, s.spacing)));
        }
        return total;
    }

    public void tick() {
        if (expiry.isEmpty()) return;
        List<Entity> dead = new ArrayList<>();
        Iterator<Map.Entry<Entity, Long>> it = expiry.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Long> e = it.next();
            if (e.getKey().isRemoved()) {
                it.remove();
            } else if (mod.tick >= e.getValue()) {
                it.remove();
                dead.add(e.getKey());
            }
        }
        for (Entity e : dead) e.discard();
    }
}
