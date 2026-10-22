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

public final class PortalTrap {

    private static final int SOLID_FLAGS = Block.NOTIFY_LISTENERS;
    private static final int PORTAL_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private final MachinesMod mod;

    public PortalTrap(MachinesMod mod) { this.mod = mod; }

    public void onPortalCreated(ServerWorld world, BlockLocating.Rectangle rect, Direction.Axis axis) {
        MachineData.PortalTrapSettings s = mod.data.portalTrap;
        if (!s.enabled || !s.zoneReady()) return;
        if (world.getRegistryKey() != World.NETHER) return;
        BlockPos base = rect.lowerLeft;
        if (!s.contains(base.getX(), base.getZ())) return;
        build(world, base, rect.width, rect.height, axis, s);
        MachinesMod.LOGGER.info("Machines: trapped new nether portal at {} {} {}", base.getX(), base.getY(), base.getZ());
    }

    private void build(ServerWorld world, BlockPos base, int width, int height, Direction.Axis axis,
                       MachineData.PortalTrapSettings s) {
        Direction along = Direction.from(axis, Direction.AxisDirection.POSITIVE);
        Direction.Axis normalAxis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction normal = Direction.from(normalAxis, Direction.AxisDirection.POSITIVE);

        int k = Math.max(0, s.thickness);
        int cap = k + 1;
        int shell = s.escapable ? 0 : Math.max(0, s.shell);
        int bottom = world.getBottomY();
        int top = world.getTopYInclusive();

        BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
        BlockState capBlock = s.escapable ? obsidian : Blocks.BEDROCK.getDefaultState();
        BlockState bedrock = Blocks.BEDROCK.getDefaultState();
        BlockState portal = Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, axis);

        if (shell > 0) {
            for (int w = -cap - shell; w <= cap + shell; w++) {
                for (int u = -1 - shell; u <= width + shell; u++) {
                    for (int v = -1 - shell; v <= height + shell; v++) {
                        boolean core = w >= -cap && w <= cap && u >= -1 && u <= width && v >= -1 && v <= height;
                        if (core) continue;
                        BlockPos p = at(base, along, normal, u, v, w);
                        if (p.getY() < bottom || p.getY() > top) continue;
                        world.setBlockState(p, bedrock, SOLID_FLAGS);
                    }
                }
            }
        }

        for (int w = -cap; w <= cap; w++) {
            boolean isCap = w == -cap || w == cap;
            for (int u = -1; u <= width; u++) {
                for (int v = -1; v <= height; v++) {
                    BlockPos p = at(base, along, normal, u, v, w);
                    if (p.getY() < bottom || p.getY() > top) continue;
                    boolean frame = u == -1 || u == width || v == -1 || v == height;
                    if (isCap) world.setBlockState(p, capBlock, SOLID_FLAGS);
                    else if (frame) world.setBlockState(p, obsidian, SOLID_FLAGS);
                }
            }
        }

        for (int w = -k; w <= k; w++) {
            for (int u = 0; u < width; u++) {
                for (int v = 0; v < height; v++) {
                    BlockPos p = at(base, along, normal, u, v, w);
                    if (p.getY() < bottom || p.getY() > top) continue;
                    world.setBlockState(p, portal, PORTAL_FLAGS);
                }
            }
        }
    }

    private static BlockPos at(BlockPos base, Direction along, Direction normal, int u, int v, int w) {
        return base.offset(along, u).offset(normal, w).up(v);
    }
}
