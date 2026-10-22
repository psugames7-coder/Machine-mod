package com.kephale.machines;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class MachinesMod implements ModInitializer {

    public static final String MOD_ID = "kephale_machines";
    public static final String VERSION = "1.0.4";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static MachinesMod INSTANCE;
    public static boolean suppressItemDrops = false;

    public MachineData data;
    public Path configDir;
    public MinecraftServer server;
    public long tick;

    public final MachineArrows arrows = new MachineArrows(this);
    public final ArrowCannons arrowCannons = new ArrowCannons(this);
    public final GhostBarrels ghostBarrels = new GhostBarrels(this);
    public final PortalTrap portalTrap = new PortalTrap(this);
    public final ArrowDispensers arrowDispensers = new ArrowDispensers(this);
    public final TntCannons tntCannons = new TntCannons(this);
    public final OrbitalRod orbital = new OrbitalRod(this);

    private final List<Scheduled> scheduled = new ArrayList<>();

    private record Scheduled(long at, Runnable task) {}

    @Override
    public void onInitialize() {
        INSTANCE = this;
        configDir = FabricLoader.getInstance().getConfigDir().resolve("machines");
        data = MachineData.load(configDir);

        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            data.save();
            scheduled.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);

        arrows.register();
        ghostBarrels.register();
        orbital.register();
        Commands.register(this);

        LOGGER.info("Machines {} loaded", VERSION);
    }

    private void onTick(MinecraftServer s) {
        server = s;
        tick++;
        runScheduled();
        safe("arrows", arrows::tick);
        safe("arrow cannons", () -> arrowCannons.tick(s));
        safe("arrow dispensers", () -> arrowDispensers.tick(s));
        safe("tnt cannons", () -> tntCannons.tick(s));
        safe("ghost barrels", () -> ghostBarrels.tick(s));
    }

    private void runScheduled() {
        if (scheduled.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        Iterator<Scheduled> it = scheduled.iterator();
        while (it.hasNext()) {
            Scheduled sc = it.next();
            if (tick >= sc.at()) {
                due.add(sc.task());
                it.remove();
            }
        }
        for (Runnable r : due) safe("scheduled task", r);
    }

    private void safe(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            LOGGER.error("Machines: {} failed", what, t);
        }
    }

    public void schedule(int delayTicks, Runnable task) {
        if (delayTicks <= 0) {
            safe("task", task);
            return;
        }
        scheduled.add(new Scheduled(tick + delayTicks, task));
    }

    public ServerWorld worldOf(String dimension) {
        if (server == null || dimension == null) return null;
        Identifier id = Identifier.tryParse(dimension);
        if (id == null) return null;
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }

    public static String dimOf(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    public boolean isImmune(UUID id) {
        return data.immune.contains(id.toString());
    }

    public static void sound(ServerWorld world, double x, double y, double z, String id, float volume, float pitch) {
        try {
            SoundEvent s = Registries.SOUND_EVENT.get(Identifier.ofVanilla(id));
            if (s != null) world.playSound(null, x, y, z, s, SoundCategory.BLOCKS, volume, pitch);
        } catch (Throwable ignored) {
        }
    }
}
