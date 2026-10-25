package com.kephale.machines;

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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class WitherRod {

    public static final String KIND = "wither_rod";
    public static final String TAG = "kmach_wither";

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

    public void fire(ServerPlayerEntity shooter) {
        ServerWorld world = (ServerWorld) shooter.getEntityWorld();
        MachineData.WitherSettings s = mod.data.wither;

        Vec3d eye = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(1.0f);
        HitResult hit = shooter.raycast(s.range, 0.0f, false);
        Vec3d target = hit.getType() == HitResult.Type.MISS ? eye.add(look.multiply(s.range)) : hit.getPos();

        double x = shooter.getX(), y = shooter.getY(), z = shooter.getZ();
        Vec3d center = new Vec3d(x, y + 3.0, z);
        Vec3d dir = target.subtract(center);
        dir = dir.lengthSquared() < 1.0E-6 ? look : dir.normalize();

        shoot(world, shooter, center, dir, s);
        for (int i = 1; i <= 2; i++) {
            double f = Math.toRadians(shooter.getYaw() + 180.0 * (i - 1));
            Vec3d head = new Vec3d(x + Math.cos(f) * 1.3, y + 2.2, z + Math.sin(f) * 1.3);
            Vec3d d = dir;
            if (s.converge) {
                Vec3d to = target.subtract(head);
                if (to.lengthSquared() > 1.0E-6) d = to.normalize();
            }
            shoot(world, shooter, head, d, s);
        }
        MachinesMod.sound(world, x, y + 3.0, z, "entity.wither.shoot", 1.0f, 1.0f);
    }

    private void shoot(ServerWorld world, ServerPlayerEntity owner, Vec3d pos, Vec3d dir, MachineData.WitherSettings s) {
        WitherSkullEntity skull = new WitherSkullEntity(world, owner, dir);
        skull.setOwner(owner);
        if (s.charged) skull.setCharged(true);
        skull.setPosition(pos.x, pos.y, pos.z);
        skull.addCommandTag(TAG);
        expiry.put(skull, mod.tick + Math.max(1, s.lifetime));
        world.spawnEntity(skull);
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
