package com.kephale.machines;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public final class TntCannons {

    public static final String TAG = "kmach_tnt";
    public static final String NO_DAMAGE_TAG = "kmach_tnt_nodamage";
    public static final String NO_DROPS_TAG = "kmach_tnt_nodrops";

    private final MachinesMod mod;
    private final Map<String, Boolean> wasPowered = new HashMap<>();
    private final Map<String, Long> nextFire = new HashMap<>();

    public TntCannons(MachinesMod mod) { this.mod = mod; }

    public boolean isCannon(World world, BlockPos pos) {
        if (mod.data.tntCannons.isEmpty()) return false;
        String dim = MachinesMod.dimOf(world);
        for (MachineData.TntCannon c : mod.data.tntCannons.values()) {
            if (c.x == pos.getX() && c.y == pos.getY() && c.z == pos.getZ() && dim.equals(c.dimension)) return true;
        }
        return false;
    }

    public String nameAt(World world, BlockPos pos) {
        String dim = MachinesMod.dimOf(world);
        for (Map.Entry<String, MachineData.TntCannon> e : mod.data.tntCannons.entrySet()) {
            MachineData.TntCannon c = e.getValue();
            if (c.x == pos.getX() && c.y == pos.getY() && c.z == pos.getZ() && dim.equals(c.dimension)) return e.getKey();
        }
        return null;
    }

    public int clearAll() {
        int n = mod.data.tntCannons.size();
        mod.data.tntCannons.clear();
        wasPowered.clear();
        nextFire.clear();
        return n;
    }

    public void forget(String name) {
        wasPowered.remove(name);
        nextFire.remove(name);
    }

    public void tick(MinecraftServer server) {
        if (mod.data.tntCannons.isEmpty()) return;
        for (Map.Entry<String, MachineData.TntCannon> e : mod.data.tntCannons.entrySet()) {
            String name = e.getKey();
            MachineData.TntCannon c = e.getValue();
            if (!c.enabled) { wasPowered.put(name, false); continue; }
            ServerWorld world = mod.worldOf(c.dimension);
            if (world == null) continue;
            BlockPos pos = new BlockPos(c.x, c.y, c.z);
            if (!world.isChunkLoaded(pos)) { wasPowered.put(name, false); continue; }
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(Blocks.DISPENSER)) continue;

            boolean powered = world.isReceivingRedstonePower(pos) || world.isReceivingRedstonePower(pos.up());
            boolean was = wasPowered.getOrDefault(name, false);
            wasPowered.put(name, powered);
            if (!powered) continue;
            if (!was) nextFire.put(name, mod.tick);
            if (mod.tick < nextFire.getOrDefault(name, mod.tick)) continue;

            Direction facing = state.get(DispenserBlock.FACING);
            int count = Math.max(1, c.count);
            int interval = Math.max(1, c.interval);
            if ("stream".equals(c.mode)) {
                fire(world, pos, facing, c);
                nextFire.put(name, mod.tick + Math.max(1, Math.round((double) interval / count)));
            } else {
                for (int i = 0; i < count; i++) fire(world, pos, facing, c);
                nextFire.put(name, mod.tick + interval);
            }
            MachinesMod.sound(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    "entity.tnt.primed", 1.0f, 1.0f);
        }
    }

    private void fire(ServerWorld world, BlockPos pos, Direction facing, MachineData.TntCannon c) {
        Vec3d spawn = Vec3d.ofBottomCenter(pos.offset(facing));
        TntEntity tnt = new TntEntity(world, spawn.x, spawn.y, spawn.z, null);
        Random r = world.getRandom();
        Vec3d v = facing.getDoubleVector().multiply(c.velocity).add(
                r.nextGaussian() * c.spread,
                r.nextGaussian() * c.spread,
                r.nextGaussian() * c.spread);
        tnt.setVelocity(v);
        tnt.setFuse(Math.max(0, c.fuse));
        tnt.addCommandTag(TAG);
        if (!c.blockDamage) tnt.addCommandTag(NO_DAMAGE_TAG);
        if (!c.drops) tnt.addCommandTag(NO_DROPS_TAG);
        world.spawnEntity(tnt);
    }
}
