package com.kephale.machines;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class MachineData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Map<String, ArrowCannon> arrowCannons = new LinkedHashMap<>();
    public Map<String, TntCannon> tntCannons = new LinkedHashMap<>();
    public Map<String, GhostBarrel> ghostBarrels = new LinkedHashMap<>();
    public PortalTrapSettings portalTrap = new PortalTrapSettings();
    public OrbitalSettings orbital = new OrbitalSettings();
    public Set<String> immune = new LinkedHashSet<>();

    private transient Path file;

    public static final class ArrowCannon {
        public String dimension = "minecraft:overworld";
        public boolean enabled = true;
        public String shape = "box";
        public boolean pos1Set;
        public boolean pos2Set;
        public int x1, z1, x2, z2;
        public double centerX, centerZ, radius;
        public int minY = 40;
        public int maxY = 80;
        public boolean hasTurret;
        public int turretX, turretY, turretZ;
        public double speed = 12.0;
        public double damage = 6.0;
        public int interval = 5;
        public int count = 1;
        public double spread = 0.0;
        public boolean lineOfSight = true;
        public boolean glidingOnly = false;
        public int arrowLifetime = 80;

        public boolean zoneReady() {
            return "circle".equals(shape) ? radius > 0 : pos1Set && pos2Set;
        }

        public boolean contains(double x, double y, double z) {
            if (y < minY || y > maxY) return false;
            if ("circle".equals(shape)) {
                double dx = x - centerX, dz = z - centerZ;
                return dx * dx + dz * dz <= radius * radius;
            }
            return x >= Math.min(x1, x2) && x < Math.max(x1, x2) + 1
                    && z >= Math.min(z1, z2) && z < Math.max(z1, z2) + 1;
        }
    }

    public static final class TntCannon {
        public String dimension = "minecraft:overworld";
        public int x, y, z;
        public boolean enabled = true;
        public int count = 10;
        public int interval = 20;
        public double velocity = 1.5;
        public double spread = 0.1;
        public int fuse = 80;
        public String mode = "burst";
        public boolean blockDamage = true;
        public boolean drops = true;
    }

    public static final class GhostBarrel {
        public String id;
        public String dimension = "minecraft:overworld";
        public int x, y, z;
        public int slot = 13;
        public String state = "REAL";
        public boolean pendingRemove;
    }

    public static final class PortalTrapSettings {
        public boolean enabled = false;
        public boolean pos1Set;
        public boolean pos2Set;
        public int x1, z1, x2, z2;
        public boolean escapable = false;
        public int thickness = 1;
        public int shell = 8;

        public boolean zoneReady() { return pos1Set && pos2Set; }

        public boolean contains(int x, int z) {
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                    && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
    }

    public static final class OrbitalSettings {
        public double radius = 22.0;
        public int rings = 5;
        public double spacing = 2.0;
        public double height = 322.0;
        public double velocity = 5.0;
        public double damage = 8.0;
        public int waveDelay = 0;
        public boolean selfImmune = true;
        public int arrowLifetime = 200;
    }

    public static MachineData load(Path dir) {
        Path f = dir.resolve("machines.json");
        MachineData d;
        try {
            if (Files.exists(f)) {
                d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), MachineData.class);
                if (d == null) d = new MachineData();
            } else {
                d = new MachineData();
            }
        } catch (Exception e) {
            MachinesMod.LOGGER.error("Could not read machines.json, using defaults", e);
            d = new MachineData();
        }
        if (d.arrowCannons == null) d.arrowCannons = new LinkedHashMap<>();
        if (d.tntCannons == null) d.tntCannons = new LinkedHashMap<>();
        if (d.ghostBarrels == null) d.ghostBarrels = new LinkedHashMap<>();
        if (d.portalTrap == null) d.portalTrap = new PortalTrapSettings();
        if (d.orbital == null) d.orbital = new OrbitalSettings();
        if (d.immune == null) d.immune = new LinkedHashSet<>();
        d.file = f;
        d.save();
        return d;
    }

    public void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MachinesMod.LOGGER.error("Could not save machines.json", e);
        }
    }
}
