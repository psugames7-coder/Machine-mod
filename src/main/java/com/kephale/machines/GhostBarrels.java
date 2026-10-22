package com.kephale.machines;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GhostBarrels {

    private final MachinesMod mod;
    private final List<PendingPlace> pendingPlace = new ArrayList<>();

    private record PendingPlace(ServerWorld world, BlockPos pos) {}

    public GhostBarrels(MachinesMod mod) { this.mod = mod; }

    public void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld sw) onBroken(sw, pos);
            return true;
        });
    }

    public ItemStack makeBarrelItem() {
        ItemStack stack = new ItemStack(Items.BARREL);
        Tagging.mark(stack, Tagging.GHOST_BARREL, null);
        return stack;
    }

    public void onPlaced(World world, BlockPos pos, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;
        if (!Tagging.is(stack, Tagging.GHOST_BARREL)) return;
        pendingPlace.add(new PendingPlace(sw, pos.toImmutable()));
    }

    public void tick(MinecraftServer server) {
        if (!pendingPlace.isEmpty()) {
            List<PendingPlace> list = new ArrayList<>(pendingPlace);
            pendingPlace.clear();
            for (PendingPlace pp : list) {
                BlockEntity be = pp.world().getBlockEntity(pp.pos());
                if (be instanceof BarrelBlockEntity barrel) arm(pp.world(), pp.pos(), barrel);
            }
        }

        if (mod.tick % 20 != 0 || mod.data.ghostBarrels.isEmpty()) return;
        boolean changed = false;
        Iterator<Map.Entry<String, MachineData.GhostBarrel>> it = mod.data.ghostBarrels.entrySet().iterator();
        while (it.hasNext()) {
            MachineData.GhostBarrel rec = it.next().getValue();
            ServerWorld world = mod.worldOf(rec.dimension);
            if (world == null) continue;
            BlockPos pos = new BlockPos(rec.x, rec.y, rec.z);
            if (!world.isChunkLoaded(pos)) continue;
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof BarrelBlockEntity barrel)) {
                it.remove();
                changed = true;
                continue;
            }
            if (rec.pendingRemove) {
                removeTagged(barrel, rec.id, true, false);
                rec.pendingRemove = false;
                rec.state = "EMPTY";
                changed = true;
            }
        }
        if (changed) mod.data.save();
    }

    public MachineData.GhostBarrel arm(ServerWorld world, BlockPos pos, BarrelBlockEntity barrel) {
        String dim = MachinesMod.dimOf(world);
        MachineData.GhostBarrel old = findAt(dim, pos);
        if (old != null) {
            removeTagged(barrel, old.id, true, true);
            mod.data.ghostBarrels.remove(old.id);
        }

        MachineData.GhostBarrel rec = new MachineData.GhostBarrel();
        rec.id = UUID.randomUUID().toString().substring(0, 8);
        rec.dimension = dim;
        rec.x = pos.getX();
        rec.y = pos.getY();
        rec.z = pos.getZ();
        rec.slot = 13;
        rec.state = "REAL";

        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        Tagging.mark(totem, Tagging.LINKED_TOTEM, rec.id);
        int slot = barrel.getStack(13).isEmpty() ? 13 : firstEmpty(barrel);
        if (slot < 0) {
            slot = 13;
        }
        rec.slot = slot;
        barrel.setStack(slot, totem);
        barrel.markDirty();

        mod.data.ghostBarrels.put(rec.id, rec);
        mod.data.save();
        return rec;
    }

    public boolean onSlotClickHead(ScreenHandler handler, int slotIndex, ServerPlayerEntity player) {
        if (mod.data.ghostBarrels.isEmpty()) return false;
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return false;
        Slot slot = handler.getSlot(slotIndex);
        if (!Tagging.is(slot.getStack(), Tagging.GHOST_TOTEM)) return false;

        slot.setStackNoCallbacks(ItemStack.EMPTY);
        slot.inventory.markDirty();
        if (slot.inventory instanceof BarrelBlockEntity barrel) {
            MachineData.GhostBarrel rec = recordFor(barrel);
            if (rec != null) {
                rec.state = "EMPTY";
                rec.pendingRemove = false;
                mod.data.save();
            }
        }
        handler.syncState();
        return true;
    }

    public void onSlotClickReturn(ScreenHandler handler, ServerPlayerEntity player) {
        if (mod.data.ghostBarrels.isEmpty()) return;

        BarrelBlockEntity barrel = null;
        for (Slot s : handler.slots) {
            if (s.inventory instanceof BarrelBlockEntity b) { barrel = b; break; }
        }

        boolean dirty = false;
        if (barrel != null) {
            MachineData.GhostBarrel rec = recordFor(barrel);
            if (rec != null && "REAL".equals(rec.state) && !containsLinked(barrel, rec.id)) {
                int slot = rec.slot >= 0 && rec.slot < barrel.size() && barrel.getStack(rec.slot).isEmpty()
                        ? rec.slot : firstEmpty(barrel);
                if (slot >= 0) {
                    ItemStack ghost = new ItemStack(Items.TOTEM_OF_UNDYING);
                    Tagging.mark(ghost, Tagging.GHOST_TOTEM, rec.id);
                    barrel.setStack(slot, ghost);
                    barrel.markDirty();
                    rec.slot = slot;
                    rec.state = "GHOST";
                } else {
                    rec.state = "EMPTY";
                }
                mod.data.save();
                dirty = true;
            }
        }

        if (Tagging.is(handler.getCursorStack(), Tagging.GHOST_TOTEM)) {
            handler.setCursorStack(ItemStack.EMPTY);
            dirty = true;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (Tagging.is(inv.getStack(i), Tagging.GHOST_TOTEM)) {
                inv.setStack(i, ItemStack.EMPTY);
                dirty = true;
            }
        }
        if (dirty) handler.sendContentUpdates();
    }

    public void onPop(String link) {
        MachineData.GhostBarrel rec = mod.data.ghostBarrels.get(link);
        if (rec == null || !"GHOST".equals(rec.state)) return;
        ServerWorld world = mod.worldOf(rec.dimension);
        BlockPos pos = new BlockPos(rec.x, rec.y, rec.z);
        if (world != null && world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
            removeTagged(barrel, rec.id, true, false);
            rec.state = "EMPTY";
            rec.pendingRemove = false;
        } else {
            rec.pendingRemove = true;
        }
        mod.data.save();
    }

    public void onBroken(ServerWorld world, BlockPos pos) {
        if (mod.data.ghostBarrels.isEmpty()) return;
        MachineData.GhostBarrel rec = findAt(MachinesMod.dimOf(world), pos);
        if (rec == null) return;
        if (world.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
            removeTagged(barrel, rec.id, true, true);
        }
        mod.data.ghostBarrels.remove(rec.id);
        mod.data.save();
    }

    public boolean canHopperTake(Inventory from, ItemStack stack) {
        String kind = Tagging.kind(stack);
        if (kind == null) return true;
        if (Tagging.GHOST_TOTEM.equals(kind)) return false;
        return !(Tagging.LINKED_TOTEM.equals(kind) && from instanceof BarrelBlockEntity);
    }

    public MachineData.GhostBarrel recordFor(BarrelBlockEntity be) {
        World w = be.getWorld();
        if (w == null) return null;
        return findAt(MachinesMod.dimOf(w), be.getPos());
    }

    private MachineData.GhostBarrel findAt(String dim, BlockPos pos) {
        for (MachineData.GhostBarrel r : mod.data.ghostBarrels.values()) {
            if (r.x == pos.getX() && r.y == pos.getY() && r.z == pos.getZ() && dim.equals(r.dimension)) return r;
        }
        return null;
    }

    private static boolean containsLinked(Inventory inv, String id) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (Tagging.is(s, Tagging.LINKED_TOTEM) && id.equals(Tagging.link(s))) return true;
        }
        return false;
    }

    private static void removeTagged(BarrelBlockEntity barrel, String id, boolean ghosts, boolean linked) {
        boolean changed = false;
        for (int i = 0; i < barrel.size(); i++) {
            ItemStack s = barrel.getStack(i);
            String kind = Tagging.kind(s);
            if (kind == null || !id.equals(Tagging.link(s))) continue;
            if ((ghosts && Tagging.GHOST_TOTEM.equals(kind)) || (linked && Tagging.LINKED_TOTEM.equals(kind))) {
                barrel.setStack(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) barrel.markDirty();
    }

    private static int firstEmpty(Inventory inv) {
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isEmpty()) return i;
        return -1;
    }
}
