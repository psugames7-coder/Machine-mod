package com.kephale.machines;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RemoteRods {

    public static final String KIND = "remote_rod";
    public static final String TAG = "kmach_remote";

    private final MachinesMod mod;
    private final Map<FishingBobberEntity, UUID> owners = new IdentityHashMap<>();
    private final Map<String, FishingBobberEntity> links = new LinkedHashMap<>();

    public RemoteRods(MachinesMod mod) { this.mod = mod; }

    public void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.FISHING_ROD) || !Tagging.is(stack, KIND)) return ActionResult.PASS;
            String link = Tagging.link(stack);
            Tagging.strip(stack);
            stack.damage(1, sp, hand);
            FishingBobberEntity bobber = link == null ? null : links.remove(link);
            if (bobber != null && !bobber.isRemoved()) bobber.discard();
            MachinesMod.sound((net.minecraft.server.world.ServerWorld) sp.getEntityWorld(),
                    sp.getX(), sp.getY(), sp.getZ(), "entity.fishing_bobber.retrieve", 1.0f, 1.0f);
            return ActionResult.SUCCESS;
        });
    }

    public void tick(MinecraftServer server) {
        owners.keySet().removeIf(FishingBobberEntity::isRemoved);
        links.values().removeIf(FishingBobberEntity::isRemoved);
        if (!mod.data.remote.enabled) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            FishingBobberEntity hook = p.fishHook;
            if (hook != null && !hook.isRemoved()) owners.put(hook, p.getUuid());
        }
    }

    public boolean onBobberTick(FishingBobberEntity bobber) {
        if (bobber.getCommandTags().contains(TAG)) return true;
        if (!mod.data.remote.enabled || mod.server == null) return false;
        UUID ownerId = owners.get(bobber);
        if (ownerId == null) return false;
        ServerPlayerEntity owner = mod.server.getPlayerManager().getPlayer(ownerId);
        if (owner == null || owner.getEntityWorld() == bobber.getEntityWorld()) return false;
        if (!onPressurePlate(bobber)) return false;
        ItemStack rod = findRod(owner);
        if (rod == null) return false;

        String link = UUID.randomUUID().toString().substring(0, 8);
        bobber.addCommandTag(TAG);
        links.put(link, bobber);
        Tagging.mark(rod, KIND, link);
        if (owner.fishHook == bobber) owner.fishHook = null;
        owners.remove(bobber);
        MachinesMod.LOGGER.info("Machines: {} made remote {} for the pressure plate at {}",
                owner.getName().getString(), link, bobber.getBlockPos());
        return true;
    }

    public Map<String, FishingBobberEntity> links() {
        return links;
    }

    private static boolean onPressurePlate(FishingBobberEntity bobber) {
        World world = bobber.getEntityWorld();
        BlockPos pos = bobber.getBlockPos();
        return world.getBlockState(pos).getBlock() instanceof AbstractPressurePlateBlock;
    }

    private static ItemStack findRod(ServerPlayerEntity p) {
        for (Hand hand : Hand.values()) {
            ItemStack s = p.getStackInHand(hand);
            if (s.isOf(Items.FISHING_ROD) && Tagging.kind(s) == null) return s;
        }
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.FISHING_ROD) && Tagging.kind(s) == null) return s;
        }
        return null;
    }
}
