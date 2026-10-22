package com.kephale.machines;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockLocating;
import net.minecraft.world.World;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PortalTrap {

    private static final int SOLID_FLAGS = Block.NOTIFY_LISTENERS;
    private static final int PORTAL_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final int MAX_PORTAL_BLOCKS = 4096;

    private final MachinesMod mod;

    public PortalTrap(MachinesMod mod) { this.mod = mod; }

    public void onPortalCreated(ServerWorld world, BlockLocating.Rectangle rect, Direction.Axis axis) {
        if (mod.data.portalTraps.isEmpty()) return;
        if (world.getRegistryKey() != World.NETHER) return;
        BlockPos base = rect.lowerLeft;
        for (Map.Entry<String, MachineData.PortalZone> e : mod.data.portalTraps.entrySet()) {
            MachineData.PortalZone z = e.getValue();
            if (!z.enabled || !z.zoneReady() || !z.contains(base.getX(), base.getZ())) continue;
            build(world, base, axis, rect.width, rect.height, z);
            MachinesMod.LOGGER.info("Machines: portal trap {} boxed a new nether portal at {} {} {}",
                    e.getKey(), base.getX(), base.getY(), base.getZ());
            return;
        }
    }

    public int boxExisting(MachineData.PortalZone z) {
        if (mod.server == null || !z.zoneReady()) return 0;
        ServerWorld world = mod.server.getWorld(World.NETHER);
        if (world == null) return 0;

        int minX = Math.min(z.x1, z.x2), maxX = Math.max(z.x1, z.x2);
        int minZ = Math.min(z.z1, z.z2), maxZ = Math.max(z.z1, z.z2);
        BlockPos center = new BlockPos((minX + maxX) / 2, 64, (minZ + maxZ) / 2);
        int radius = Math.max(maxX - minX, maxZ - minZ) / 2 + 16;

        PointOfInterestStorage poi = world.getPointOfInterestStorage();
        try {
            poi.preloadChunks(world, center, radius);
        } catch (Throwable t) {
            MachinesMod.LOGGER.warn("Machines: could not preload portal data", t);
        }

        List<BlockPos> starts = new ArrayList<>();
        poi.getInSquare(type -> type.matchesKey(PointOfInterestTypes.NETHER_PORTAL), center, radius,
                        PointOfInterestStorage.OccupationStatus.ANY)
                .map(PointOfInterest::getPos)
                .filter(p -> z.contains(p.getX(), p.getZ()))
                .forEach(p -> starts.add(p.toImmutable()));

        Set<Long> visited = new HashSet<>();
        int boxed = 0;
        for (BlockPos start : starts) {
            if (visited.contains(start.asLong())) continue;
            BlockState state = world.getBlockState(start);
            if (!state.isOf(Blocks.NETHER_PORTAL)) continue;
            Direction.Axis axis = state.get(NetherPortalBlock.AXIS);

            int lx = start.getX(), ly = start.getY(), lz = start.getZ();
            int hx = lx, hy = ly, hz = lz;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start.asLong());
            int count = 0;
            while (!queue.isEmpty() && count < MAX_PORTAL_BLOCKS) {
                BlockPos p = queue.poll();
                count++;
                lx = Math.min(lx, p.getX()); ly = Math.min(ly, p.getY()); lz = Math.min(lz, p.getZ());
                hx = Math.max(hx, p.getX()); hy = Math.max(hy, p.getY()); hz = Math.max(hz, p.getZ());
                for (Direction d : Direction.values()) {
                    BlockPos n = p.offset(d);
                    if (visited.contains(n.asLong())) continue;
                    if (!world.getBlockState(n).isOf(Blocks.NETHER_PORTAL)) continue;
                    visited.add(n.asLong());
                    queue.add(n);
                }
            }

            int width = axis == Direction.Axis.X ? hx - lx + 1 : hz - lz + 1;
            int depth = axis == Direction.Axis.X ? hz - lz + 1 : hx - lx + 1;
            int height = hy - ly + 1;
            lids(world, new BlockPos(lx, ly, lz), axis, width, height, depth);
            boxed++;
        }
        return boxed;
    }

    private void build(ServerWorld world, BlockPos base, Direction.Axis axis, int width, int height,
                       MachineData.PortalZone z) {
        Direction along = Direction.from(axis, Direction.AxisDirection.POSITIVE);
        Direction.Axis normalAxis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction normal = Direction.from(normalAxis, Direction.AxisDirection.POSITIVE);

        int depth = Math.max(1, z.depth);
        int shell = Math.max(0, z.shell);
        int bottom = world.getBottomY();
        int top = world.getTopYInclusive();

        BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
        BlockState portal = Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, axis);

        for (int w = -1 - shell; w <= depth + shell; w++) {
            for (int u = -1 - shell; u <= width + shell; u++) {
                for (int v = -1 - shell; v <= height + shell; v++) {
                    boolean inside = w >= 0 && w < depth && u >= 0 && u < width && v >= 0 && v < height;
                    if (inside) continue;
                    BlockPos p = at(base, along, normal, u, v, w);
                    if (p.getY() < bottom || p.getY() > top) continue;
                    world.setBlockState(p, obsidian, SOLID_FLAGS);
                }
            }
        }

        for (int w = 0; w < depth; w++) {
            for (int u = 0; u < width; u++) {
                for (int v = 0; v < height; v++) {
                    BlockPos p = at(base, along, normal, u, v, w);
                    if (p.getY() < bottom || p.getY() > top) continue;
                    world.setBlockState(p, portal, PORTAL_FLAGS);
                }
            }
        }
    }

    private void lids(ServerWorld world, BlockPos base, Direction.Axis axis, int width, int height, int depth) {
        Direction along = Direction.from(axis, Direction.AxisDirection.POSITIVE);
        Direction.Axis normalAxis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction normal = Direction.from(normalAxis, Direction.AxisDirection.POSITIVE);
        int bottom = world.getBottomY();
        int top = world.getTopYInclusive();
        BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();

        int[] sides = {-1, depth};
        for (int w : sides) {
            for (int u = -1; u <= width; u++) {
                for (int v = -1; v <= height; v++) {
                    BlockPos p = at(base, along, normal, u, v, w);
                    if (p.getY() < bottom || p.getY() > top) continue;
                    world.setBlockState(p, obsidian, SOLID_FLAGS);
                }
            }
        }
    }

    private static BlockPos at(BlockPos base, Direction along, Direction normal, int u, int v, int w) {
        return base.offset(along, u).offset(normal, w).up(v);
    }
}
