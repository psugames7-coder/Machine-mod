package com.kephale.machines;

import net.minecraft.block.ShapeContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArrowCannons {

    private static final double DRAG = 0.99;
    private static final double GRAVITY = 0.05;

    private final MachinesMod mod;
    private final Map<UUID, Vec3d> lastPos = new HashMap<>();
    private final Map<UUID, Vec3d> velocity = new HashMap<>();
    private final Map<String, Long> nextShot = new HashMap<>();

    public ArrowCannons(MachinesMod mod) { this.mod = mod; }

    public void tick(MinecraftServer server) {
        if (mod.data.arrowCannons.isEmpty()) {
            if (!lastPos.isEmpty()) { lastPos.clear(); velocity.clear(); }
            return;
        }

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        trackVelocity(players);

        for (Map.Entry<String, MachineData.ArrowCannon> e : mod.data.arrowCannons.entrySet()) {
            MachineData.ArrowCannon c = e.getValue();
            if (!c.enabled || !c.hasTurret || !c.zoneReady()) continue;
            ServerWorld world = mod.worldOf(c.dimension);
            if (world == null) continue;
            BlockPos turret = new BlockPos(c.turretX, c.turretY, c.turretZ);
            if (!world.isChunkLoaded(turret)) continue;

            for (ServerPlayerEntity p : players) {
                if (p.getEntityWorld() != world) continue;
                if (p.isSpectator() || p.isCreative()) continue;
                if (mod.isImmune(p.getUuid())) continue;
                if (c.glidingOnly && !p.isGliding()) continue;
                Vec3d pos = p.getEntityPos();
                if (!c.contains(pos.x, pos.y, pos.z)) continue;

                String key = e.getKey() + "|" + p.getUuid();
                if (mod.tick < nextShot.getOrDefault(key, 0L)) continue;
                if (fire(world, c, turret, p)) nextShot.put(key, mod.tick + Math.max(1, c.interval));
            }
        }

        if (mod.tick % 200 == 0) {
            Set<UUID> online = new HashSet<>();
            for (ServerPlayerEntity p : players) online.add(p.getUuid());
            lastPos.keySet().retainAll(online);
            velocity.keySet().retainAll(online);
            nextShot.entrySet().removeIf(en -> mod.tick > en.getValue() + 200);
        }
    }

    private void trackVelocity(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            UUID id = p.getUuid();
            Vec3d now = p.getEntityPos();
            Vec3d prev = lastPos.put(id, now);
            Vec3d v = prev == null ? Vec3d.ZERO : now.subtract(prev);
            if (v.lengthSquared() > 400) v = Vec3d.ZERO;
            Vec3d old = velocity.get(id);
            velocity.put(id, old == null ? v : old.multiply(0.4).add(v.multiply(0.6)));
        }
    }

    private boolean fire(ServerWorld world, MachineData.ArrowCannon c, BlockPos turret, ServerPlayerEntity p) {
        Vec3d center = Vec3d.ofCenter(turret);
        Vec3d target = p.getBoundingBox().getCenter();
        Vec3d vel = velocity.getOrDefault(p.getUuid(), Vec3d.ZERO);

        Vec3d aim = target;
        for (int i = 0; i < 4; i++) {
            double t = ticksToTravel(aim.distanceTo(center), c.speed);
            aim = target.add(vel.multiply(t)).add(0, 0.5 * GRAVITY * t * t, 0);
        }

        Vec3d dir = aim.subtract(center);
        if (dir.lengthSquared() < 1.0E-6) return false;
        dir = dir.normalize();
        Vec3d spawn = center.add(dir);

        if (c.lineOfSight) {
            BlockHitResult hit = world.raycast(new RaycastContext(spawn, target,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
            if (hit.getType() != HitResult.Type.MISS) return false;
        }

        int n = Math.max(1, c.count);
        for (int i = 0; i < n; i++) {
            mod.arrows.spawn(world, spawn, dir, c.speed, c.spread, c.damage, null, c.arrowLifetime, null);
        }
        MachinesMod.sound(world, spawn.x, spawn.y, spawn.z, "entity.arrow.shoot", 1.0f, 1.6f);
        return true;
    }

    private static double ticksToTravel(double dist, double speed) {
        if (speed <= 0.01) return 0;
        double k = 1.0 - dist * (1.0 - DRAG) / speed;
        if (k <= 0.05) return dist / speed * 1.5;
        return Math.log(k) / Math.log(DRAG);
    }
}
