package com.kephale.machines;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class Commands {

    private static MachinesMod mod;

    private Commands() {}

    @FunctionalInterface
    private interface Apply<T> {
        String run(CommandContext<ServerCommandSource> ctx, T value) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface Action {
        String run(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException;
    }

    public static void register(MachinesMod m) {
        mod = m;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("machine")
                        .requires(Commands::isAdmin)
                        .executes(Commands::help)
                        .then(literal("help").executes(Commands::help))
                        .then(immune())
                        .then(antiElytra())
                        .then(arrowCannon())
                        .then(ghostBarrel())
                        .then(portalTrap())
                        .then(tntCannon())
                        .then(orbital())
                        .then(wither())
                        .then(potionOrbital())
                        .then(remote())
                        .then(clear())));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> immune() {
        return literal("immune")
                .then(literal("list").executes(ctx -> show(ctx, c -> {
                    if (mod.data.immune.isEmpty()) return "Nobody is immune.";
                    StringBuilder sb = new StringBuilder("Immune players:");
                    MinecraftServer s = c.getSource().getServer();
                    for (String id : mod.data.immune) {
                        String name = id;
                        try {
                            ServerPlayerEntity p = s.getPlayerManager().getPlayer(UUID.fromString(id));
                            if (p != null) name = p.getName().getString();
                        } catch (IllegalArgumentException ignored) {
                        }
                        sb.append("\n §e").append(name);
                    }
                    return sb.toString();
                })))
                .then(argument("player", EntityArgumentType.player())
                        .then(literal("get").executes(ctx -> show(ctx, c -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
                            return p.getName().getString() + " immune: " + onOff(mod.isImmune(p.getUuid()));
                        })))
                        .then(argument("on", BoolArgumentType.bool())
                                .executes(ctx -> run(ctx, c -> {
                                    ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
                                    boolean on = BoolArgumentType.getBool(c, "on");
                                    if (on) mod.data.immune.add(p.getUuid().toString());
                                    else mod.data.immune.remove(p.getUuid().toString());
                                    return p.getName().getString() + " immune to machines: " + onOff(on);
                                }))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> antiElytra() {
        return literal("antielytra")
                .then(literal("list").executes(ctx -> show(ctx, c -> {
                    if (mod.data.arrowCannons.isEmpty()) return "No anti elytra cannons.";
                    StringBuilder sb = new StringBuilder("Anti elytra cannons:");
                    for (Map.Entry<String, MachineData.ArrowCannon> e : mod.data.arrowCannons.entrySet()) {
                        MachineData.ArrowCannon a = e.getValue();
                        sb.append("\n §e").append(e.getKey()).append(" §7").append(onOff(a.enabled))
                                .append(" §7zone ").append(a.zoneReady() ? "set" : "§cnot set")
                                .append(" §7turret ").append(a.hasTurret ? a.turretX + " " + a.turretY + " " + a.turretZ : "§cnone");
                    }
                    return sb.toString();
                })))
                .then(literal("create").then(argument("name", StringArgumentType.word()).executes(ctx -> run(ctx, c -> {
                    String name = StringArgumentType.getString(c, "name");
                    if (mod.data.arrowCannons.containsKey(name)) return "§cAnti elytra cannon " + name + " already exists.";
                    MachineData.ArrowCannon a = new MachineData.ArrowCannon();
                    a.dimension = MachinesMod.dimOf(c.getSource().getWorld());
                    mod.data.arrowCannons.put(name, a);
                    return "Created anti elytra cannon " + name + ". Next: zone pos1, zone pos2, height, turret.";
                }))))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(ctx -> run(ctx, c -> {
                    String name = StringArgumentType.getString(c, "name");
                    return mod.data.arrowCannons.remove(name) != null ? "Removed anti elytra cannon " + name + "." : "§cNo anti elytra cannon " + name + ".";
                }))))
                .then(argument("name", StringArgumentType.word())
                        .then(literal("info").executes(ctx -> show(ctx, acGet(a -> "§eAnti elytra cannon§7 " + onOff(a.enabled)
                                + "\n dimension " + a.dimension
                                + "\n zone " + zoneText(a) + ", Y " + a.minY + " to " + a.maxY
                                + "\n turret " + turretText(a)
                                + "\n speed " + fmt(a.speed) + ", damage " + fmt(a.damage) + ", interval " + a.interval
                                + ", count " + a.count + ", spread " + fmt(a.spread)
                                + "\n line of sight " + onOff(a.lineOfSight) + ", gliding only " + onOff(a.glidingOnly)
                                + ", lifetime " + a.arrowLifetime))))
                        .then(bool("on",
                                acGet(a -> "Enabled: " + onOff(a.enabled)),
                                acSet((a, v) -> { a.enabled = v; return "Anti elytra cannon " + onOff(v); })))
                        .then(literal("zone")
                                .then(literal("get").executes(ctx -> show(ctx, acGet(a -> "Zone: " + zoneText(a)))))
                                .then(literal("pos1").executes(ctx -> run(ctx, c -> {
                                    MachineData.ArrowCannon a = ac(c);
                                    if (a == null) return missing(c);
                                    BlockPos p = BlockPos.ofFloored(c.getSource().getPosition());
                                    a.shape = "box";
                                    a.x1 = p.getX();
                                    a.z1 = p.getZ();
                                    a.pos1Set = true;
                                    a.dimension = MachinesMod.dimOf(c.getSource().getWorld());
                                    return "Zone pos1 set to " + a.x1 + " " + a.z1 + ".";
                                })))
                                .then(literal("pos2").executes(ctx -> run(ctx, c -> {
                                    MachineData.ArrowCannon a = ac(c);
                                    if (a == null) return missing(c);
                                    BlockPos p = BlockPos.ofFloored(c.getSource().getPosition());
                                    a.shape = "box";
                                    a.x2 = p.getX();
                                    a.z2 = p.getZ();
                                    a.pos2Set = true;
                                    a.dimension = MachinesMod.dimOf(c.getSource().getWorld());
                                    return "Zone pos2 set to " + a.x2 + " " + a.z2 + ".";
                                })))
                                .then(literal("circle").then(argument("radius", DoubleArgumentType.doubleArg(1, 10000))
                                        .executes(ctx -> run(ctx, c -> {
                                            MachineData.ArrowCannon a = ac(c);
                                            if (a == null) return missing(c);
                                            a.shape = "circle";
                                            a.centerX = c.getSource().getPosition().x;
                                            a.centerZ = c.getSource().getPosition().z;
                                            a.radius = DoubleArgumentType.getDouble(c, "radius");
                                            a.dimension = MachinesMod.dimOf(c.getSource().getWorld());
                                            return "Zone set to a circle of radius " + fmt(a.radius) + " centred on you.";
                                        })))))
                        .then(literal("height")
                                .then(literal("get").executes(ctx -> show(ctx, acGet(a -> "Height: Y " + a.minY + " to " + a.maxY))))
                                .then(argument("min", IntegerArgumentType.integer(-2048, 4096))
                                        .then(argument("max", IntegerArgumentType.integer(-2048, 4096))
                                                .executes(ctx -> run(ctx, c -> {
                                                    MachineData.ArrowCannon a = ac(c);
                                                    if (a == null) return missing(c);
                                                    int lo = IntegerArgumentType.getInteger(c, "min");
                                                    int hi = IntegerArgumentType.getInteger(c, "max");
                                                    a.minY = Math.min(lo, hi);
                                                    a.maxY = Math.max(lo, hi);
                                                    return "Active band set to Y " + a.minY + " to " + a.maxY + ".";
                                                })))))
                        .then(literal("turret")
                                .executes(ctx -> run(ctx, c -> {
                                    MachineData.ArrowCannon a = ac(c);
                                    if (a == null) return missing(c);
                                    BlockPos p = lookedAt(c);
                                    if (p == null) return "§cLook at the turret block.";
                                    return setTurret(c, a, p);
                                }))
                                .then(literal("get").executes(ctx -> show(ctx, acGet(a -> "Turret: " + turretText(a)))))
                                .then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> run(ctx, c -> {
                                    MachineData.ArrowCannon a = ac(c);
                                    if (a == null) return missing(c);
                                    return setTurret(c, a, BlockPosArgumentType.getBlockPos(c, "pos"));
                                }))))
                        .then(dbl("speed", 0.1, 50,
                                acGet(a -> "Speed: " + fmt(a.speed) + " blocks per tick"),
                                acSet((a, v) -> { a.speed = v; return "Arrow speed " + fmt(v) + " blocks per tick."; })))
                        .then(dbl("damage", 0, 1000,
                                acGet(a -> "Damage: " + fmt(a.damage)),
                                acSet((a, v) -> { a.damage = v; return "Arrow damage " + fmt(v) + "."; })))
                        .then(integer("interval", 1, 1200,
                                acGet(a -> "Interval: " + a.interval + " ticks"),
                                acSet((a, v) -> { a.interval = v; return "Interval " + v + " ticks."; })))
                        .then(integer("count", 1, 50,
                                acGet(a -> "Count: " + a.count + " arrows per shot"),
                                acSet((a, v) -> { a.count = v; return "Arrows per shot " + v + "."; })))
                        .then(dbl("spread", 0, 50,
                                acGet(a -> "Spread: " + fmt(a.spread)),
                                acSet((a, v) -> { a.spread = v; return "Spread " + fmt(v) + "."; })))
                        .then(bool("los",
                                acGet(a -> "Line of sight check: " + onOff(a.lineOfSight)),
                                acSet((a, v) -> { a.lineOfSight = v; return "Line of sight check " + onOff(v); })))
                        .then(bool("glidingonly",
                                acGet(a -> "Gliding only: " + onOff(a.glidingOnly)),
                                acSet((a, v) -> { a.glidingOnly = v; return "Only shoot gliding players: " + onOff(v); })))
                        .then(integer("lifetime", 1, 6000,
                                acGet(a -> "Lifetime: " + a.arrowLifetime + " ticks of flight"),
                                acSet((a, v) -> { a.arrowLifetime = v; return "Arrows still flying after " + v + " ticks are removed."; }))));
    }

    private static String zoneText(MachineData.ArrowCannon a) {
        if ("circle".equals(a.shape)) {
            return "circle radius " + fmt(a.radius) + " at " + fmt(a.centerX) + " " + fmt(a.centerZ);
        }
        return "box " + (a.pos1Set ? a.x1 + " " + a.z1 : "?") + " to " + (a.pos2Set ? a.x2 + " " + a.z2 : "?");
    }

    private static String turretText(MachineData.ArrowCannon a) {
        return a.hasTurret ? a.turretX + " " + a.turretY + " " + a.turretZ : "not set";
    }

    private static String setTurret(CommandContext<ServerCommandSource> c, MachineData.ArrowCannon a, BlockPos p) {
        a.hasTurret = true;
        a.turretX = p.getX();
        a.turretY = p.getY();
        a.turretZ = p.getZ();
        a.dimension = MachinesMod.dimOf(c.getSource().getWorld());
        return "Turret set to " + p.getX() + " " + p.getY() + " " + p.getZ() + ".";
    }

    private static LiteralArgumentBuilder<ServerCommandSource> ghostBarrel() {
        return literal("ghostbarrel")
                .then(literal("give")
                        .executes(ctx -> run(ctx, c -> give(c.getSource().getPlayerOrThrow(), mod.ghostBarrels.makeBarrelItem(), "ghost barrel")))
                        .then(argument("player", EntityArgumentType.player()).executes(ctx -> run(ctx, c ->
                                give(EntityArgumentType.getPlayer(c, "player"), mod.ghostBarrels.makeBarrelItem(), "ghost barrel")))))
                .then(literal("reset").executes(ctx -> run(ctx, c -> {
                    BlockPos p = lookedAt(c);
                    if (p == null) return "§cLook at a barrel.";
                    ServerWorld w = c.getSource().getWorld();
                    if (!(w.getBlockEntity(p) instanceof BarrelBlockEntity barrel)) return "§cThat is not a barrel.";
                    mod.ghostBarrels.arm(w, p, barrel);
                    return "Barrel armed with a fresh linked totem.";
                })))
                .then(literal("get").executes(ctx -> show(ctx, c -> {
                    BlockPos p = lookedAt(c);
                    if (p == null) return "§cLook at a barrel.";
                    ServerWorld w = c.getSource().getWorld();
                    if (!(w.getBlockEntity(p) instanceof BarrelBlockEntity barrel)) return "§cThat is not a barrel.";
                    MachineData.GhostBarrel b = mod.ghostBarrels.recordFor(barrel);
                    if (b == null) return "That is a normal barrel.";
                    return "Ghost barrel " + b.id + ": " + b.state + (b.pendingRemove ? " §c(removal queued)" : "");
                })))
                .then(literal("list").executes(ctx -> show(ctx, c -> {
                    if (mod.data.ghostBarrels.isEmpty()) return "No ghost barrels.";
                    StringBuilder sb = new StringBuilder("Ghost barrels:");
                    for (MachineData.GhostBarrel b : mod.data.ghostBarrels.values()) {
                        sb.append("\n §e").append(b.id).append(" §7").append(b.dimension).append(" ")
                                .append(b.x).append(" ").append(b.y).append(" ").append(b.z)
                                .append(" §f").append(b.state).append(b.pendingRemove ? " §c(removal queued)" : "");
                    }
                    return sb.toString();
                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> portalTrap() {
        return literal("portaltrap")
                .then(literal("list").executes(ctx -> show(ctx, c -> {
                    if (mod.data.portalTraps.isEmpty()) return "No portal trap zones.";
                    StringBuilder sb = new StringBuilder("Portal trap zones:");
                    for (Map.Entry<String, MachineData.PortalZone> e : mod.data.portalTraps.entrySet()) {
                        sb.append("\n §e").append(e.getKey()).append(" §7").append(onOff(e.getValue().enabled))
                                .append(" §7").append(portalZoneText(e.getValue()));
                    }
                    return sb.toString();
                })))
                .then(literal("create").then(argument("name", StringArgumentType.word()).executes(ctx -> run(ctx, c -> {
                    String name = StringArgumentType.getString(c, "name");
                    if (mod.data.portalTraps.containsKey(name)) return "§cPortal trap " + name + " already exists.";
                    mod.data.portalTraps.put(name, new MachineData.PortalZone());
                    return "Created portal trap " + name + ". Stand in the nether and use zone pos1 and zone pos2.";
                }))))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(ctx -> run(ctx, c -> {
                    String name = StringArgumentType.getString(c, "name");
                    return mod.data.portalTraps.remove(name) != null ? "Removed portal trap " + name + "." : "§cNo portal trap " + name + ".";
                }))))
                .then(argument("name", StringArgumentType.word())
                        .then(literal("info").executes(ctx -> show(ctx, ptGet(z -> "§ePortal trap§7 " + onOff(z.enabled)
                                + "\n zone " + portalZoneText(z)
                                + "\n portal size: portal width x " + z.depth + " deep x portal height"
                                + ", extra obsidian " + z.shell))))
                        .then(literal("boxexisting").executes(ctx -> run(ctx, c -> {
                            MachineData.PortalZone z = pt(c);
                            if (z == null) return missingPt(c);
                            if (!z.zoneReady()) return "§cSet zone pos1 and pos2 first.";
                            int n = mod.portalTrap.boxExisting(z);
                            return "Sealed " + n + " existing nether portal" + (n == 1 ? "" : "s") + " with obsidian lids in "
                                    + StringArgumentType.getString(c, "name") + ".";
                        })))
                        .then(literal("zone")
                                .then(literal("get").executes(ctx -> show(ctx, ptGet(z -> "Zone: " + portalZoneText(z)))))
                                .then(literal("pos1").executes(ctx -> run(ctx, c -> {
                                    MachineData.PortalZone z = pt(c);
                                    if (z == null) return missingPt(c);
                                    if (!inNether(c)) return "§cStand in the nether to set the zone.";
                                    BlockPos p = BlockPos.ofFloored(c.getSource().getPosition());
                                    z.x1 = p.getX();
                                    z.z1 = p.getZ();
                                    z.pos1Set = true;
                                    return "Pos1 set to " + z.x1 + " " + z.z1 + ".";
                                })))
                                .then(literal("pos2").executes(ctx -> run(ctx, c -> {
                                    MachineData.PortalZone z = pt(c);
                                    if (z == null) return missingPt(c);
                                    if (!inNether(c)) return "§cStand in the nether to set the zone.";
                                    BlockPos p = BlockPos.ofFloored(c.getSource().getPosition());
                                    z.x2 = p.getX();
                                    z.z2 = p.getZ();
                                    z.pos2Set = true;
                                    return "Pos2 set to " + z.x2 + " " + z.z2 + ".";
                                }))))
                        .then(bool("on",
                                ptGet(z -> "Enabled: " + onOff(z.enabled)),
                                ptSet((z, v) -> { z.enabled = v; return "Portal trap " + onOff(v) + (v && !z.zoneReady() ? " §c(zone not set yet)" : ""); })))
                        .then(integer("depth", 1, 5,
                                ptGet(z -> "Depth: " + z.depth + " connected portal layers"),
                                ptSet((z, v) -> { z.depth = v; return "Boxes now hold " + v + " connected portal layers (a normal portal becomes 2 x " + v + " x 3)."; })))
                        .then(integer("shell", 0, 16,
                                ptGet(z -> "Shell: " + z.shell),
                                ptSet((z, v) -> { z.shell = v; return "Extra obsidian around the box: " + v + " blocks."; }))));
    }

    private static String portalZoneText(MachineData.PortalZone z) {
        if (!z.zoneReady()) {
            return "§cnot set§r (pos1 " + (z.pos1Set ? z.x1 + " " + z.z1 : "?") + ", pos2 " + (z.pos2Set ? z.x2 + " " + z.z2 : "?") + ")";
        }
        return "nether X " + Math.min(z.x1, z.x2) + " to " + Math.max(z.x1, z.x2)
                + ", Z " + Math.min(z.z1, z.z2) + " to " + Math.max(z.z1, z.z2)
                + " (overworld X " + Math.min(z.x1, z.x2) * 8 + " to " + (Math.max(z.x1, z.x2) * 8 + 7)
                + ", Z " + Math.min(z.z1, z.z2) * 8 + " to " + (Math.max(z.z1, z.z2) * 8 + 7) + ")";
    }

    private static LiteralArgumentBuilder<ServerCommandSource> clear() {
        return literal("clear")
                .then(literal("antielytra").executes(ctx -> run(ctx, c -> {
                    int n = mod.data.arrowCannons.size();
                    mod.data.arrowCannons.clear();
                    return "Removed " + n + " anti elytra cannons.";
                })))
                .then(literal("arrowcannon").executes(ctx -> run(ctx, c -> "Removed " + mod.arrowDispensers.clearAll() + " arrow cannons.")))
                .then(literal("tntcannon").executes(ctx -> run(ctx, c -> "Removed " + mod.tntCannons.clearAll() + " TNT cannons.")))
                .then(literal("portaltrap").executes(ctx -> run(ctx, c -> {
                    int n = mod.data.portalTraps.size();
                    mod.data.portalTraps.clear();
                    return "Removed " + n + " portal trap zones. Portals already boxed stay as they are.";
                })))
                .then(literal("ghostbarrel").executes(ctx -> run(ctx, c ->
                        "Removed " + mod.ghostBarrels.clearAll() + " ghost barrels and their totems.")))
                .then(literal("all").executes(ctx -> run(ctx, c -> {
                    int a = mod.data.arrowCannons.size();
                    mod.data.arrowCannons.clear();
                    int d = mod.arrowDispensers.clearAll();
                    int t = mod.tntCannons.clearAll();
                    int p = mod.data.portalTraps.size();
                    mod.data.portalTraps.clear();
                    int g = mod.ghostBarrels.clearAll();
                    return "Removed " + a + " anti elytra cannons, " + d + " arrow cannons, " + t + " TNT cannons, "
                            + p + " portal trap zones, " + g + " ghost barrels.";
                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> arrowCannon() {
        return literal("arrowcannon")
                .then(literal("list").executes(ctx -> show(ctx, c -> {
                    if (mod.data.arrowDispensers.isEmpty()) return "No arrow cannons.";
                    StringBuilder sb = new StringBuilder("Arrow cannons:");
                    for (Map.Entry<String, MachineData.ArrowDispenser> e : mod.data.arrowDispensers.entrySet()) {
                        MachineData.ArrowDispenser t = e.getValue();
                        sb.append("\n §e").append(e.getKey()).append(" §7").append(onOff(t.enabled)).append(" §7")
                                .append(t.x).append(" ").append(t.y).append(" ").append(t.z);
                    }
                    return sb.toString();
                })))
                .then(literal("add").then(argument("name", StringArgumentType.word()).executes(ctx -> run(ctx, c -> {
                    String name = StringArgumentType.getString(c, "name");
                    if (mod.data.arrowDispensers.containsKey(name)) return "§cArrow cannon " + name + " already exists.";
                    BlockPos p = lookedAt(c);
                    if (p == null) return "§cLook at a dispenser.";
                    ServerWorld w = c.getSource().getWorld();
                    if (!w.getBlockState(p).isOf(Blocks.DISPENSER)) return "§cThat is not a dispenser.";
                    String existing = mod.arrowDispensers.nameAt(w, p);
                    if (existing == null) existing = mod.tntCannons.nameAt(w, p);
                    if (existing != null) return "§cThat dispenser is already cannon " + existing + ".";
                    MachineData.ArrowDispenser t = new MachineData.ArrowDispenser();
                    t.dimension = MachinesMod.dimOf(w);
                    t.x = p.getX();
                    t.y = p.getY();
                    t.z = p.getZ();
                    mod.data.arrowDispensers.put(name, t);
                    return "Arrow cannon " + name + " added at " + t.x + " " + t.y + " " + t.z + ".";
                }))))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(ctx -> run(ctx, c -> {
                    String name = StringArgumentType.getString(c, "name");
                    mod.arrowDispensers.forget(name);
                    return mod.data.arrowDispensers.remove(name) != null ? "Removed arrow cannon " + name + "." : "§cNo arrow cannon " + name + ".";
                }))))
                .then(argument("name", StringArgumentType.word())
                        .then(literal("info").executes(ctx -> show(ctx, adGet(t -> "§eArrow cannon§7 " + onOff(t.enabled)
                                + "\n " + t.dimension + " " + t.x + " " + t.y + " " + t.z
                                + "\n count " + t.count + ", interval " + t.interval + ", mode " + t.mode
                                + "\n speed " + fmt(t.speed) + ", spread " + fmt(t.spread) + ", damage " + fmt(t.damage)
                                + ", lifetime " + t.arrowLifetime))))
                        .then(bool("on",
                                adGet(t -> "Enabled: " + onOff(t.enabled)),
                                adSet((t, v) -> { t.enabled = v; return "Arrow cannon " + onOff(v); })))
                        .then(integer("count", 1, 50,
                                adGet(t -> "Count: " + t.count + " arrows per cycle"),
                                adSet((t, v) -> { t.count = v; return "Arrows per cycle " + v + "."; })))
                        .then(integer("interval", 1, 1200,
                                adGet(t -> "Interval: " + t.interval + " ticks"),
                                adSet((t, v) -> { t.interval = v; return "Cycle length " + v + " ticks."; })))
                        .then(literal("mode")
                                .then(literal("get").executes(ctx -> show(ctx, adGet(t -> "Mode: " + t.mode))))
                                .then(literal("burst").executes(ctx -> run(ctx, c -> {
                                    MachineData.ArrowDispenser t = ad(c);
                                    if (t == null) return missingAd(c);
                                    t.mode = "burst";
                                    return "Mode burst: all arrows at once each cycle.";
                                })))
                                .then(literal("stream").executes(ctx -> run(ctx, c -> {
                                    MachineData.ArrowDispenser t = ad(c);
                                    if (t == null) return missingAd(c);
                                    t.mode = "stream";
                                    return "Mode stream: arrows spread evenly across each cycle.";
                                }))))
                        .then(dbl("speed", 0.1, 50,
                                adGet(t -> "Speed: " + fmt(t.speed) + " blocks per tick"),
                                adSet((t, v) -> { t.speed = v; return "Arrow speed " + fmt(v) + " blocks per tick."; })))
                        .then(dbl("spread", 0, 50,
                                adGet(t -> "Spread: " + fmt(t.spread)),
                                adSet((t, v) -> { t.spread = v; return "Spread " + fmt(v) + "."; })))
                        .then(dbl("damage", 0, 1000,
                                adGet(t -> "Damage: " + fmt(t.damage)),
                                adSet((t, v) -> { t.damage = v; return "Arrow damage " + fmt(v) + "."; })))
                        .then(integer("lifetime", 1, 6000,
                                adGet(t -> "Lifetime: " + t.arrowLifetime + " ticks of flight"),
                                adSet((t, v) -> { t.arrowLifetime = v; return "Arrows still flying after " + v + " ticks are removed."; }))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> tntCannon() {
        return literal("tntcannon")
                .then(literal("list").executes(ctx -> show(ctx, c -> {
                    if (mod.data.tntCannons.isEmpty()) return "No TNT cannons.";
                    StringBuilder sb = new StringBuilder("TNT cannons:");
                    for (Map.Entry<String, MachineData.TntCannon> e : mod.data.tntCannons.entrySet()) {
                        MachineData.TntCannon t = e.getValue();
                        sb.append("\n §e").append(e.getKey()).append(" §7").append(onOff(t.enabled)).append(" §7")
                                .append(t.x).append(" ").append(t.y).append(" ").append(t.z);
                    }
                    return sb.toString();
                })))
                .then(literal("add").then(argument("name", StringArgumentType.word()).executes(ctx -> run(ctx, c -> {
                    String name = StringArgumentType.getString(c, "name");
                    if (mod.data.tntCannons.containsKey(name)) return "§cTNT cannon " + name + " already exists.";
                    BlockPos p = lookedAt(c);
                    if (p == null) return "§cLook at a dispenser.";
                    ServerWorld w = c.getSource().getWorld();
                    if (!w.getBlockState(p).isOf(Blocks.DISPENSER)) return "§cThat is not a dispenser.";
                    String existing = mod.tntCannons.nameAt(w, p);
                    if (existing == null) existing = mod.arrowDispensers.nameAt(w, p);
                    if (existing != null) return "§cThat dispenser is already cannon " + existing + ".";
                    MachineData.TntCannon t = new MachineData.TntCannon();
                    t.dimension = MachinesMod.dimOf(w);
                    t.x = p.getX();
                    t.y = p.getY();
                    t.z = p.getZ();
                    mod.data.tntCannons.put(name, t);
                    return "TNT cannon " + name + " added at " + t.x + " " + t.y + " " + t.z + ".";
                }))))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(ctx -> run(ctx, c -> {
                    String name = StringArgumentType.getString(c, "name");
                    mod.tntCannons.forget(name);
                    return mod.data.tntCannons.remove(name) != null ? "Removed TNT cannon " + name + "." : "§cNo TNT cannon " + name + ".";
                }))))
                .then(argument("name", StringArgumentType.word())
                        .then(literal("info").executes(ctx -> show(ctx, tcGet(t -> "§eTNT cannon§7 " + onOff(t.enabled)
                                + "\n " + t.dimension + " " + t.x + " " + t.y + " " + t.z
                                + "\n count " + t.count + ", interval " + t.interval + ", mode " + t.mode
                                + "\n velocity " + fmt(t.velocity) + ", spread " + fmt(t.spread) + ", fuse " + t.fuse
                                + "\n block damage " + onOff(t.blockDamage) + ", drops " + onOff(t.drops)))))
                        .then(bool("on",
                                tcGet(t -> "Enabled: " + onOff(t.enabled)),
                                tcSet((t, v) -> { t.enabled = v; return "TNT cannon " + onOff(v); })))
                        .then(integer("count", 1, 100,
                                tcGet(t -> "Count: " + t.count + " TNT per cycle"),
                                tcSet((t, v) -> { t.count = v; return "TNT per cycle " + v + "."; })))
                        .then(integer("interval", 1, 1200,
                                tcGet(t -> "Interval: " + t.interval + " ticks"),
                                tcSet((t, v) -> { t.interval = v; return "Cycle length " + v + " ticks."; })))
                        .then(dbl("velocity", 0, 20,
                                tcGet(t -> "Velocity: " + fmt(t.velocity)),
                                tcSet((t, v) -> { t.velocity = v; return "Launch velocity " + fmt(v) + "."; })))
                        .then(dbl("spread", 0, 5,
                                tcGet(t -> "Spread: " + fmt(t.spread)),
                                tcSet((t, v) -> { t.spread = v; return "Spread " + fmt(v) + "."; })))
                        .then(integer("fuse", 0, 1200,
                                tcGet(t -> "Fuse: " + t.fuse + " ticks"),
                                tcSet((t, v) -> { t.fuse = v; return "Fuse " + v + " ticks."; })))
                        .then(literal("mode")
                                .then(literal("get").executes(ctx -> show(ctx, tcGet(t -> "Mode: " + t.mode))))
                                .then(literal("burst").executes(ctx -> run(ctx, c -> {
                                    MachineData.TntCannon t = tc(c);
                                    if (t == null) return missingTnt(c);
                                    t.mode = "burst";
                                    return "Mode burst: all TNT at once each cycle.";
                                })))
                                .then(literal("stream").executes(ctx -> run(ctx, c -> {
                                    MachineData.TntCannon t = tc(c);
                                    if (t == null) return missingTnt(c);
                                    t.mode = "stream";
                                    return "Mode stream: TNT spread evenly across each cycle.";
                                }))))
                        .then(bool("blockdamage",
                                tcGet(t -> "Block damage: " + onOff(t.blockDamage)),
                                tcSet((t, v) -> { t.blockDamage = v; return "Block damage " + onOff(v); })))
                        .then(bool("drops",
                                tcGet(t -> "Drops: " + onOff(t.drops)),
                                tcSet((t, v) -> { t.drops = v; return "Explosion drops " + onOff(v); }))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> orbital() {
        return literal("orbital")
                .then(literal("give")
                        .executes(ctx -> run(ctx, c -> give(c.getSource().getPlayerOrThrow(), mod.orbital.makeRod(null, false), "orbital rod")))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> run(ctx, c -> give(EntityArgumentType.getPlayer(c, "player"),
                                        mod.orbital.makeRod(null, false), "orbital rod")))
                                .then(literal("unbreakable").executes(ctx -> run(ctx, c -> give(EntityArgumentType.getPlayer(c, "player"),
                                        mod.orbital.makeRod(null, true), "unbreakable orbital rod"))))
                                .then(argument("durability", IntegerArgumentType.integer(1, 1000000))
                                        .executes(ctx -> run(ctx, c -> {
                                            int d = IntegerArgumentType.getInteger(c, "durability");
                                            return give(EntityArgumentType.getPlayer(c, "player"),
                                                    mod.orbital.makeRod(d, false), "orbital rod (durability " + d + ")");
                                        })))))
                .then(literal("info").executes(ctx -> show(ctx, c -> {
                    MachineData.OrbitalSettings s = mod.data.orbital;
                    return "Orbital strike: " + s.rings + " rings out to radius " + fmt(s.radius)
                            + ", spacing " + fmt(s.spacing) + " (" + orbitalArrowCount() + " arrows)"
                            + "\n height Y " + fmt(s.height) + ", velocity " + fmt(s.velocity) + ", damage " + fmt(s.damage)
                            + "\n wave delay " + s.waveDelay + ", caller immune " + onOff(s.selfImmune)
                            + ", lifetime " + s.arrowLifetime;
                })))
                .then(dbl("radius", 1, 200,
                        c -> "Radius: " + fmt(mod.data.orbital.radius),
                        (c, v) -> { mod.data.orbital.radius = v; return "Radius " + fmt(v) + " (" + orbitalArrowCount() + " arrows)."; }))
                .then(integer("rings", 0, 50,
                        c -> "Rings: " + mod.data.orbital.rings,
                        (c, v) -> { mod.data.orbital.rings = v; return "Rings " + v + " (" + orbitalArrowCount() + " arrows)."; }))
                .then(dbl("spacing", 0.25, 50,
                        c -> "Spacing: " + fmt(mod.data.orbital.spacing) + " blocks",
                        (c, v) -> { mod.data.orbital.spacing = v; return "Arrow spacing " + fmt(v) + " blocks (" + orbitalArrowCount() + " arrows)."; }))
                .then(dbl("height", -64, 2000,
                        c -> "Height: Y " + fmt(mod.data.orbital.height),
                        (c, v) -> { mod.data.orbital.height = v; return "Spawn height Y " + fmt(v) + "."; }))
                .then(dbl("velocity", 0.1, 50,
                        c -> "Velocity: " + fmt(mod.data.orbital.velocity) + " blocks per tick",
                        (c, v) -> { mod.data.orbital.velocity = v; return "Velocity " + fmt(v) + " blocks per tick."; }))
                .then(dbl("damage", 0, 1000,
                        c -> "Damage: " + fmt(mod.data.orbital.damage),
                        (c, v) -> { mod.data.orbital.damage = v; return "Damage " + fmt(v) + "."; }))
                .then(integer("wavedelay", 0, 200,
                        c -> "Wave delay: " + mod.data.orbital.waveDelay + " ticks",
                        (c, v) -> { mod.data.orbital.waveDelay = v; return "Wave delay " + v + " ticks."; }))
                .then(bool("selfimmune",
                        c -> "Caller immune: " + onOff(mod.data.orbital.selfImmune),
                        (c, v) -> { mod.data.orbital.selfImmune = v; return "Caller immune " + onOff(v); }))
                .then(integer("lifetime", 1, 6000,
                        c -> "Lifetime: " + mod.data.orbital.arrowLifetime + " ticks of flight",
                        (c, v) -> { mod.data.orbital.arrowLifetime = v; return "Arrows still flying after " + v + " ticks are removed."; }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> wither() {
        return literal("wither")
                .then(literal("give")
                        .executes(ctx -> run(ctx, c -> give(c.getSource().getPlayerOrThrow(), mod.witherRod.makeRod(null, false), "wither orbital rod")))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> run(ctx, c -> give(EntityArgumentType.getPlayer(c, "player"),
                                        mod.witherRod.makeRod(null, false), "wither orbital rod")))
                                .then(literal("unbreakable").executes(ctx -> run(ctx, c -> give(EntityArgumentType.getPlayer(c, "player"),
                                        mod.witherRod.makeRod(null, true), "unbreakable wither orbital rod"))))
                                .then(argument("durability", IntegerArgumentType.integer(1, 1000000))
                                        .executes(ctx -> run(ctx, c -> {
                                            int d = IntegerArgumentType.getInteger(c, "durability");
                                            return give(EntityArgumentType.getPlayer(c, "player"),
                                                    mod.witherRod.makeRod(d, false), "wither orbital rod (durability " + d + ")");
                                        })))))
                .then(literal("info").executes(ctx -> show(ctx, c -> {
                    MachineData.WitherSettings s = mod.data.wither;
                    return "Wither orbital: " + s.rings + " rings out to radius " + fmt(s.radius)
                            + ", spacing " + fmt(s.spacing) + " (" + mod.witherRod.skullCount() + " skulls)"
                            + "\n height Y " + fmt(s.height) + ", speed x" + fmt(s.speed) + ", wave delay " + s.waveDelay
                            + "\n charged " + onOff(s.charged) + ", caller immune " + onOff(s.selfImmune)
                            + ", reusable " + onOff(s.reusable) + ", lifetime " + s.lifetime;
                })))
                .then(dbl("radius", 1, 200,
                        c -> "Radius: " + fmt(mod.data.wither.radius),
                        (c, v) -> { mod.data.wither.radius = v; return "Radius " + fmt(v) + " (" + mod.witherRod.skullCount() + " skulls)."; }))
                .then(integer("rings", 0, 50,
                        c -> "Rings: " + mod.data.wither.rings,
                        (c, v) -> { mod.data.wither.rings = v; return "Rings " + v + " (" + mod.witherRod.skullCount() + " skulls)."; }))
                .then(dbl("spacing", 0.5, 50,
                        c -> "Spacing: " + fmt(mod.data.wither.spacing) + " blocks",
                        (c, v) -> { mod.data.wither.spacing = v; return "Skull spacing " + fmt(v) + " blocks (" + mod.witherRod.skullCount() + " skulls)."; }))
                .then(dbl("height", -64, 2000,
                        c -> "Height: Y " + fmt(mod.data.wither.height),
                        (c, v) -> { mod.data.wither.height = v; return "Spawn height Y " + fmt(v) + "."; }))
                .then(dbl("speed", 0.1, 20,
                        c -> "Speed: x" + fmt(mod.data.wither.speed) + " (1 = vanilla wither skull)",
                        (c, v) -> { mod.data.wither.speed = v; return "Skull speed x" + fmt(v) + " of vanilla."; }))
                .then(integer("wavedelay", 0, 200,
                        c -> "Wave delay: " + mod.data.wither.waveDelay + " ticks",
                        (c, v) -> { mod.data.wither.waveDelay = v; return "Wave delay " + v + " ticks."; }))
                .then(bool("charged",
                        c -> "Charged (blue) skulls: " + onOff(mod.data.wither.charged),
                        (c, v) -> { mod.data.wither.charged = v; return "Charged (blue) skulls " + onOff(v); }))
                .then(bool("selfimmune",
                        c -> "Caller immune: " + onOff(mod.data.wither.selfImmune),
                        (c, v) -> { mod.data.wither.selfImmune = v; return "Caller immune " + onOff(v); }))
                .then(bool("reusable",
                        c -> "Reusable: " + onOff(mod.data.wither.reusable),
                        (c, v) -> { mod.data.wither.reusable = v; return v ? "Wither rods keep working until they break." : "Wither rods work once, then become normal rods."; }))
                .then(integer("lifetime", 1, 6000,
                        c -> "Lifetime: " + mod.data.wither.lifetime + " ticks",
                        (c, v) -> { mod.data.wither.lifetime = v; return "Skulls still flying after " + v + " ticks are removed."; }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> potionOrbital() {
        return literal("potionorbital")
                .then(literal("give")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("potion", StringArgumentType.word())
                                        .suggests((ctx, b) -> mod.potionOrbital.suggest(ctx, b))
                                        .executes(ctx -> run(ctx, c -> givePotionRod(c, null, false)))
                                        .then(literal("unbreakable").executes(ctx -> run(ctx, c -> givePotionRod(c, null, true))))
                                        .then(argument("durability", IntegerArgumentType.integer(1, 1000000))
                                                .executes(ctx -> run(ctx, c -> givePotionRod(c, IntegerArgumentType.getInteger(c, "durability"), false)))))))
                .then(literal("potions").executes(ctx -> show(ctx, c -> {
                    StringBuilder sb = new StringBuilder("Potions:");
                    for (String label : mod.potionOrbital.labels().keySet()) {
                        sb.append("\n §e").append(label).append(" §7").append(mod.potionOrbital.describe(label));
                    }
                    return sb.toString();
                })))
                .then(literal("info").executes(ctx -> show(ctx, c -> {
                    MachineData.PotionOrbitalSettings s = mod.data.potionOrbital;
                    return "Potion orbital: " + s.rings + " rings out to radius " + fmt(s.radius)
                            + ", spacing " + fmt(s.spacing) + " (" + mod.potionOrbital.arrowCount() + " potions)"
                            + "\n height Y " + fmt(s.height) + ", velocity " + fmt(s.velocity) + ", wave delay " + s.waveDelay;
                })))
                .then(dbl("radius", 0.5, 100,
                        c -> "Radius: " + fmt(mod.data.potionOrbital.radius),
                        (c, v) -> { mod.data.potionOrbital.radius = v; return "Radius " + fmt(v) + " (" + mod.potionOrbital.arrowCount() + " potions)."; }))
                .then(integer("rings", 0, 20,
                        c -> "Rings: " + mod.data.potionOrbital.rings,
                        (c, v) -> { mod.data.potionOrbital.rings = v; return "Rings " + v + " (" + mod.potionOrbital.arrowCount() + " potions)."; }))
                .then(dbl("spacing", 0.25, 50,
                        c -> "Spacing: " + fmt(mod.data.potionOrbital.spacing) + " blocks",
                        (c, v) -> { mod.data.potionOrbital.spacing = v; return "Potion spacing " + fmt(v) + " blocks (" + mod.potionOrbital.arrowCount() + " potions)."; }))
                .then(dbl("height", -64, 2000,
                        c -> "Height: Y " + fmt(mod.data.potionOrbital.height),
                        (c, v) -> { mod.data.potionOrbital.height = v; return "Spawn height Y " + fmt(v) + "."; }))
                .then(dbl("velocity", 0.1, 20,
                        c -> "Velocity: " + fmt(mod.data.potionOrbital.velocity) + " blocks per tick",
                        (c, v) -> { mod.data.potionOrbital.velocity = v; return "Velocity " + fmt(v) + " blocks per tick."; }))
                .then(integer("wavedelay", 0, 200,
                        c -> "Wave delay: " + mod.data.potionOrbital.waveDelay + " ticks",
                        (c, v) -> { mod.data.potionOrbital.waveDelay = v; return "Wave delay " + v + " ticks."; }));
    }

    private static String givePotionRod(CommandContext<ServerCommandSource> c, Integer durability, boolean unbreakable)
            throws CommandSyntaxException {
        String input = StringArgumentType.getString(c, "potion");
        String id = mod.potionOrbital.resolve(input);
        if (id == null) return "§cUnknown potion " + input + ". Use /machine potionorbital potions to see them all.";
        ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
        String what = "potion orbital rod (" + input + (unbreakable ? ", unbreakable" : durability != null ? ", durability " + durability : "") + ")";
        return give(p, mod.potionOrbital.makeRod(id, durability, unbreakable), what);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> remote() {
        return literal("remote")
                .then(bool("on",
                        c -> "Remote rods: " + onOff(mod.data.remote.enabled),
                        (c, v) -> { mod.data.remote.enabled = v; return "Remote rods " + onOff(v); }))
                .then(literal("list").executes(ctx -> show(ctx, c -> {
                    if (mod.remoteRods.links().isEmpty()) return "No active remotes.";
                    StringBuilder sb = new StringBuilder("Active remotes:");
                    for (Map.Entry<String, net.minecraft.entity.projectile.FishingBobberEntity> e : mod.remoteRods.links().entrySet()) {
                        BlockPos p = e.getValue().getBlockPos();
                        sb.append("\n §e").append(e.getKey()).append(" §7")
                                .append(MachinesMod.dimOf(e.getValue().getEntityWorld())).append(" ")
                                .append(p.getX()).append(" ").append(p.getY()).append(" ").append(p.getZ());
                    }
                    return sb.toString();
                })));
    }

    private static int orbitalArrowCount() {
        MachineData.OrbitalSettings s = mod.data.orbital;
        int total = 1;
        for (int i = 1; i <= s.rings; i++) {
            double r = s.radius * i / Math.max(1, s.rings);
            total += Math.max(1, (int) Math.round(2 * Math.PI * r / Math.max(0.25, s.spacing)));
        }
        return total;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> dbl(String name, double min, double max, Action get, Apply<Double> set) {
        return literal(name)
                .then(literal("get").executes(ctx -> show(ctx, get)))
                .then(argument("value", DoubleArgumentType.doubleArg(min, max))
                        .executes(ctx -> run(ctx, c -> set.run(c, DoubleArgumentType.getDouble(c, "value")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> integer(String name, int min, int max, Action get, Apply<Integer> set) {
        return literal(name)
                .then(literal("get").executes(ctx -> show(ctx, get)))
                .then(argument("value", IntegerArgumentType.integer(min, max))
                        .executes(ctx -> run(ctx, c -> set.run(c, IntegerArgumentType.getInteger(c, "value")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> bool(String name, Action get, Apply<Boolean> set) {
        return literal(name)
                .then(literal("get").executes(ctx -> show(ctx, get)))
                .then(argument("value", BoolArgumentType.bool())
                        .executes(ctx -> run(ctx, c -> set.run(c, BoolArgumentType.getBool(c, "value")))));
    }

    private static Action acGet(Function<MachineData.ArrowCannon, String> fn) {
        return c -> {
            MachineData.ArrowCannon a = ac(c);
            return a == null ? missing(c) : fn.apply(a);
        };
    }

    private static <T> Apply<T> acSet(BiFunction<MachineData.ArrowCannon, T, String> fn) {
        return (c, v) -> {
            MachineData.ArrowCannon a = ac(c);
            return a == null ? missing(c) : fn.apply(a, v);
        };
    }

    private static Action tcGet(Function<MachineData.TntCannon, String> fn) {
        return c -> {
            MachineData.TntCannon t = tc(c);
            return t == null ? missingTnt(c) : fn.apply(t);
        };
    }

    private static <T> Apply<T> tcSet(BiFunction<MachineData.TntCannon, T, String> fn) {
        return (c, v) -> {
            MachineData.TntCannon t = tc(c);
            return t == null ? missingTnt(c) : fn.apply(t, v);
        };
    }

    private static Action adGet(Function<MachineData.ArrowDispenser, String> fn) {
        return c -> {
            MachineData.ArrowDispenser t = ad(c);
            return t == null ? missingAd(c) : fn.apply(t);
        };
    }

    private static <T> Apply<T> adSet(BiFunction<MachineData.ArrowDispenser, T, String> fn) {
        return (c, v) -> {
            MachineData.ArrowDispenser t = ad(c);
            return t == null ? missingAd(c) : fn.apply(t, v);
        };
    }

    private static Action ptGet(Function<MachineData.PortalZone, String> fn) {
        return c -> {
            MachineData.PortalZone z = pt(c);
            return z == null ? missingPt(c) : fn.apply(z);
        };
    }

    private static <T> Apply<T> ptSet(BiFunction<MachineData.PortalZone, T, String> fn) {
        return (c, v) -> {
            MachineData.PortalZone z = pt(c);
            return z == null ? missingPt(c) : fn.apply(z, v);
        };
    }

    private static int run(CommandContext<ServerCommandSource> ctx, Action action) throws CommandSyntaxException {
        String out = action.run(ctx);
        mod.data.save();
        if (out != null) msg(ctx, out);
        return 1;
    }

    private static int show(CommandContext<ServerCommandSource> ctx, Action action) throws CommandSyntaxException {
        String out = action.run(ctx);
        if (out != null) msg(ctx, out);
        return 1;
    }

    private static int help(CommandContext<ServerCommandSource> ctx) {
        msg(ctx, "§6Machines " + MachinesMod.VERSION + " §7(add §fget§7 after any setting to see its value)"
                + "\n§e/machine antielytra §7create, list, remove, <name> zone/height/turret/speed/damage/interval/count/spread/los/glidingonly/lifetime/on/info"
                + "\n§e/machine arrowcannon §7add <name> (look at dispenser), list, remove, <name> count/interval/mode/speed/spread/damage/lifetime/on/info"
                + "\n§e/machine ghostbarrel §7give [player], reset, get (look at barrel), list"
                + "\n§e/machine portaltrap §7create, list, remove, <name> zone pos1/pos2/get, on, depth, shell, boxexisting, info"
                + "\n§e/machine tntcannon §7add <name> (look at dispenser), list, remove, <name> count/interval/velocity/spread/fuse/mode/blockdamage/drops/on/info"
                + "\n§e/machine orbital §7give [player] [durability|unbreakable], radius/rings/spacing/height/velocity/damage/wavedelay/selfimmune/lifetime/info"
                + "\n§e/machine wither §7give [player] [durability|unbreakable], radius/rings/spacing/height/speed/wavedelay/charged/selfimmune/reusable/lifetime/info"
                + "\n§e/machine potionorbital §7give <player> <potion> [durability|unbreakable], potions, radius/rings/spacing/height/velocity/wavedelay/info"
                + "\n§e/machine remote §7on, list"
                + "\n§e/machine immune §7list, <player> true/false/get"
                + "\n§e/machine clear §7antielytra/arrowcannon/tntcannon/portaltrap/ghostbarrel/all");
        return 1;
    }

    private static MachineData.ArrowCannon ac(CommandContext<ServerCommandSource> c) {
        return mod.data.arrowCannons.get(StringArgumentType.getString(c, "name"));
    }

    private static MachineData.TntCannon tc(CommandContext<ServerCommandSource> c) {
        return mod.data.tntCannons.get(StringArgumentType.getString(c, "name"));
    }

    private static MachineData.ArrowDispenser ad(CommandContext<ServerCommandSource> c) {
        return mod.data.arrowDispensers.get(StringArgumentType.getString(c, "name"));
    }

    private static String missingAd(CommandContext<ServerCommandSource> c) {
        return "§cNo arrow cannon named " + StringArgumentType.getString(c, "name") + ". Use /machine arrowcannon add <name>.";
    }

    private static MachineData.PortalZone pt(CommandContext<ServerCommandSource> c) {
        return mod.data.portalTraps.get(StringArgumentType.getString(c, "name"));
    }

    private static String missingPt(CommandContext<ServerCommandSource> c) {
        return "§cNo portal trap named " + StringArgumentType.getString(c, "name") + ". Use /machine portaltrap create <name>.";
    }

    private static String missing(CommandContext<ServerCommandSource> c) {
        return "§cNo anti elytra cannon named " + StringArgumentType.getString(c, "name") + ". Use /machine antielytra create <name>.";
    }

    private static String missingTnt(CommandContext<ServerCommandSource> c) {
        return "§cNo TNT cannon named " + StringArgumentType.getString(c, "name") + ". Use /machine tntcannon add <name>.";
    }

    private static boolean inNether(CommandContext<ServerCommandSource> c) {
        return "minecraft:the_nether".equals(MachinesMod.dimOf(c.getSource().getWorld()));
    }

    private static BlockPos lookedAt(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        HitResult hit = p.raycast(64.0, 0.0f, false);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) return b.getBlockPos();
        return null;
    }

    private static String give(ServerPlayerEntity p, ItemStack stack, String what) {
        String name = p.getName().getString();
        if (!p.giveItemStack(stack)) p.dropItem(stack, false);
        return "Gave " + name + " a " + what + ".";
    }

    private static String onOff(boolean b) { return b ? "§aON§r" : "§cOFF§r"; }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.2f", d);
    }

    private static boolean isAdmin(ServerCommandSource src) {
        try {
            ServerPlayerEntity p = src.getPlayer();
            if (p == null) return true;
            MinecraftServer s = p.getEntityWorld().getServer();
            if (s == null || s.isSingleplayer()) return true;
            net.minecraft.server.PlayerConfigEntry entry =
                    new net.minecraft.server.PlayerConfigEntry(p.getGameProfile());
            if (s.isHost(entry)) return true;
            return s.getPlayerManager().isOperator(entry);
        } catch (Throwable t) {
            MachinesMod.LOGGER.warn("isAdmin check failed, allowing", t);
            return true;
        }
    }

    private static void msg(CommandContext<ServerCommandSource> ctx, String text) {
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
    }
}
