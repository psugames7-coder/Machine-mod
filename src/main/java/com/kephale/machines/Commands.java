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
                        .executes(ctx -> help(ctx))
                        .then(literal("help").executes(Commands::help))
                        .then(literal("immune")
                                .then(argument("player", EntityArgumentType.player())
                                        .then(argument("on", BoolArgumentType.bool())
                                                .executes(ctx -> run(ctx, c -> {
                                                    ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
                                                    boolean on = BoolArgumentType.getBool(c, "on");
                                                    if (on) mod.data.immune.add(p.getUuid().toString());
                                                    else mod.data.immune.remove(p.getUuid().toString());
                                                    return p.getName().getString() + " immune to machines: " + onOff(on);
                                                })))))
                        .then(arrowCannon())
                        .then(ghostBarrel())
                        .then(portalTrap())
                        .then(tntCannon())
                        .then(orbital())));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> arrowCannon() {
        return literal("arrowcannon")
                .then(literal("list").executes(ctx -> run(ctx, c -> {
                    if (mod.data.arrowCannons.isEmpty()) return "No arrow cannons.";
                    StringBuilder sb = new StringBuilder("Arrow cannons:");
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
                    if (mod.data.arrowCannons.containsKey(name)) return "§cArrow cannon " + name + " already exists.";
                    MachineData.ArrowCannon a = new MachineData.ArrowCannon();
                    a.dimension = MachinesMod.dimOf(c.getSource().getWorld());
                    mod.data.arrowCannons.put(name, a);
                    return "Created arrow cannon " + name + ". Next: zone pos1, zone pos2, height, turret.";
                }))))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(ctx -> run(ctx, c -> {
                    String name = StringArgumentType.getString(c, "name");
                    return mod.data.arrowCannons.remove(name) != null ? "Removed arrow cannon " + name + "." : "§cNo arrow cannon " + name + ".";
                }))))
                .then(argument("name", StringArgumentType.word())
                        .then(literal("info").executes(ctx -> run(ctx, c -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            String zone = "circle".equals(a.shape)
                                    ? "circle r" + fmt(a.radius) + " at " + fmt(a.centerX) + " " + fmt(a.centerZ)
                                    : "box " + (a.pos1Set ? a.x1 + " " + a.z1 : "?") + " to " + (a.pos2Set ? a.x2 + " " + a.z2 : "?");
                            return "§e" + StringArgumentType.getString(c, "name") + "§7 " + onOff(a.enabled)
                                    + "\n dimension " + a.dimension
                                    + "\n zone " + zone + ", Y " + a.minY + " to " + a.maxY
                                    + "\n turret " + (a.hasTurret ? a.turretX + " " + a.turretY + " " + a.turretZ : "none")
                                    + "\n speed " + fmt(a.speed) + ", damage " + fmt(a.damage) + ", interval " + a.interval
                                    + ", count " + a.count + ", spread " + fmt(a.spread)
                                    + "\n line of sight " + onOff(a.lineOfSight) + ", gliding only " + onOff(a.glidingOnly)
                                    + ", lifetime " + a.arrowLifetime;
                        })))
                        .then(bool("on", (c, v) -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            a.enabled = v;
                            return "Arrow cannon " + onOff(v);
                        }))
                        .then(literal("zone")
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
                                .then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> run(ctx, c -> {
                                    MachineData.ArrowCannon a = ac(c);
                                    if (a == null) return missing(c);
                                    return setTurret(c, a, BlockPosArgumentType.getBlockPos(c, "pos"));
                                }))))
                        .then(dbl("speed", 0.1, 50, (c, v) -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            a.speed = v;
                            return "Arrow speed " + fmt(v) + " blocks per tick.";
                        }))
                        .then(dbl("damage", 0, 1000, (c, v) -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            a.damage = v;
                            return "Arrow damage " + fmt(v) + ".";
                        }))
                        .then(integer("interval", 1, 1200, (c, v) -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            a.interval = v;
                            return "Interval " + v + " ticks.";
                        }))
                        .then(integer("count", 1, 50, (c, v) -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            a.count = v;
                            return "Arrows per shot " + v + ".";
                        }))
                        .then(dbl("spread", 0, 50, (c, v) -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            a.spread = v;
                            return "Spread " + fmt(v) + ".";
                        }))
                        .then(bool("los", (c, v) -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            a.lineOfSight = v;
                            return "Line of sight check " + onOff(v);
                        }))
                        .then(bool("glidingonly", (c, v) -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            a.glidingOnly = v;
                            return "Only shoot gliding players: " + onOff(v);
                        }))
                        .then(integer("lifetime", 1, 6000, (c, v) -> {
                            MachineData.ArrowCannon a = ac(c);
                            if (a == null) return missing(c);
                            a.arrowLifetime = v;
                            return "Arrow lifetime " + v + " ticks.";
                        })));
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
                .then(literal("list").executes(ctx -> run(ctx, c -> {
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
                .then(literal("zone")
                        .then(literal("pos1").executes(ctx -> run(ctx, c -> {
                            if (!inNether(c)) return "§cStand in the nether to set the zone.";
                            BlockPos p = BlockPos.ofFloored(c.getSource().getPosition());
                            MachineData.PortalTrapSettings s = mod.data.portalTrap;
                            s.x1 = p.getX();
                            s.z1 = p.getZ();
                            s.pos1Set = true;
                            return "Nether zone pos1 set to " + s.x1 + " " + s.z1 + ".";
                        })))
                        .then(literal("pos2").executes(ctx -> run(ctx, c -> {
                            if (!inNether(c)) return "§cStand in the nether to set the zone.";
                            BlockPos p = BlockPos.ofFloored(c.getSource().getPosition());
                            MachineData.PortalTrapSettings s = mod.data.portalTrap;
                            s.x2 = p.getX();
                            s.z2 = p.getZ();
                            s.pos2Set = true;
                            return "Nether zone pos2 set to " + s.x2 + " " + s.z2 + ".";
                        }))))
                .then(bool("on", (c, v) -> {
                    mod.data.portalTrap.enabled = v;
                    return "Portal trap " + onOff(v) + (v && !mod.data.portalTrap.zoneReady() ? " §c(zone not set yet)" : "");
                }))
                .then(bool("escapable", (c, v) -> {
                    mod.data.portalTrap.escapable = v;
                    return v ? "New trapped portals are breakable obsidian." : "New trapped portals are sealed in bedrock.";
                }))
                .then(integer("thickness", 0, 4, (c, v) -> {
                    mod.data.portalTrap.thickness = v;
                    return "Portal layers on each side of the arrival portal: " + v + ".";
                }))
                .then(integer("shell", 0, 16, (c, v) -> {
                    mod.data.portalTrap.shell = v;
                    return "Bedrock shell thickness " + v + ".";
                }))
                .then(literal("status").executes(ctx -> run(ctx, c -> {
                    MachineData.PortalTrapSettings s = mod.data.portalTrap;
                    String zone = s.zoneReady()
                            ? "nether X " + Math.min(s.x1, s.x2) + " to " + Math.max(s.x1, s.x2)
                            + ", Z " + Math.min(s.z1, s.z2) + " to " + Math.max(s.z1, s.z2)
                            + "\n overworld equivalent X " + Math.min(s.x1, s.x2) * 8 + " to " + (Math.max(s.x1, s.x2) * 8 + 7)
                            + ", Z " + Math.min(s.z1, s.z2) * 8 + " to " + (Math.max(s.z1, s.z2) * 8 + 7)
                            : "§cnot set";
                    return "Portal trap " + onOff(s.enabled)
                            + "\n zone " + zone
                            + "\n " + (s.escapable ? "escapable (obsidian)" : "sealed (bedrock, shell " + s.shell + ")")
                            + ", thickness " + s.thickness;
                })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> tntCannon() {
        return literal("tntcannon")
                .then(literal("list").executes(ctx -> run(ctx, c -> {
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
                        .then(literal("info").executes(ctx -> run(ctx, c -> {
                            MachineData.TntCannon t = tc(c);
                            if (t == null) return missingTnt(c);
                            return "§e" + StringArgumentType.getString(c, "name") + "§7 " + onOff(t.enabled)
                                    + "\n " + t.dimension + " " + t.x + " " + t.y + " " + t.z
                                    + "\n count " + t.count + ", interval " + t.interval + ", mode " + t.mode
                                    + "\n velocity " + fmt(t.velocity) + ", spread " + fmt(t.spread) + ", fuse " + t.fuse
                                    + "\n block damage " + onOff(t.blockDamage) + ", drops " + onOff(t.drops);
                        })))
                        .then(bool("on", (c, v) -> {
                            MachineData.TntCannon t = tc(c);
                            if (t == null) return missingTnt(c);
                            t.enabled = v;
                            return "TNT cannon " + onOff(v);
                        }))
                        .then(integer("count", 1, 100, (c, v) -> {
                            MachineData.TntCannon t = tc(c);
                            if (t == null) return missingTnt(c);
                            t.count = v;
                            return "TNT per cycle " + v + ".";
                        }))
                        .then(integer("interval", 1, 1200, (c, v) -> {
                            MachineData.TntCannon t = tc(c);
                            if (t == null) return missingTnt(c);
                            t.interval = v;
                            return "Cycle length " + v + " ticks.";
                        }))
                        .then(dbl("velocity", 0, 20, (c, v) -> {
                            MachineData.TntCannon t = tc(c);
                            if (t == null) return missingTnt(c);
                            t.velocity = v;
                            return "Launch velocity " + fmt(v) + ".";
                        }))
                        .then(dbl("spread", 0, 5, (c, v) -> {
                            MachineData.TntCannon t = tc(c);
                            if (t == null) return missingTnt(c);
                            t.spread = v;
                            return "Spread " + fmt(v) + ".";
                        }))
                        .then(integer("fuse", 0, 1200, (c, v) -> {
                            MachineData.TntCannon t = tc(c);
                            if (t == null) return missingTnt(c);
                            t.fuse = v;
                            return "Fuse " + v + " ticks.";
                        }))
                        .then(literal("mode")
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
                        .then(bool("blockdamage", (c, v) -> {
                            MachineData.TntCannon t = tc(c);
                            if (t == null) return missingTnt(c);
                            t.blockDamage = v;
                            return "Block damage " + onOff(v);
                        }))
                        .then(bool("drops", (c, v) -> {
                            MachineData.TntCannon t = tc(c);
                            if (t == null) return missingTnt(c);
                            t.drops = v;
                            return "Explosion drops " + onOff(v);
                        })));
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
                .then(literal("info").executes(ctx -> run(ctx, c -> {
                    MachineData.OrbitalSettings s = mod.data.orbital;
                    int total = 1;
                    for (int i = 1; i <= s.rings; i++) {
                        double r = s.radius * i / Math.max(1, s.rings);
                        total += Math.max(1, (int) Math.round(2 * Math.PI * r / Math.max(0.25, s.spacing)));
                    }
                    return "Orbital strike: " + s.rings + " rings out to radius " + fmt(s.radius)
                            + ", spacing " + fmt(s.spacing) + " (" + total + " arrows)"
                            + "\n height Y " + fmt(s.height) + ", velocity " + fmt(s.velocity) + ", damage " + fmt(s.damage)
                            + "\n wave delay " + s.waveDelay + ", caller immune " + onOff(s.selfImmune)
                            + ", lifetime " + s.arrowLifetime;
                })))
                .then(dbl("radius", 1, 200, (c, v) -> { mod.data.orbital.radius = v; return "Radius " + fmt(v) + "."; }))
                .then(integer("rings", 0, 50, (c, v) -> { mod.data.orbital.rings = v; return "Rings " + v + "."; }))
                .then(dbl("spacing", 0.25, 50, (c, v) -> { mod.data.orbital.spacing = v; return "Arrow spacing along rings " + fmt(v) + " blocks."; }))
                .then(dbl("height", -64, 2000, (c, v) -> { mod.data.orbital.height = v; return "Spawn height Y " + fmt(v) + "."; }))
                .then(dbl("velocity", 0.1, 50, (c, v) -> { mod.data.orbital.velocity = v; return "Velocity " + fmt(v) + " blocks per tick."; }))
                .then(dbl("damage", 0, 1000, (c, v) -> { mod.data.orbital.damage = v; return "Damage " + fmt(v) + "."; }))
                .then(integer("wavedelay", 0, 200, (c, v) -> { mod.data.orbital.waveDelay = v; return "Wave delay " + v + " ticks."; }))
                .then(bool("selfimmune", (c, v) -> { mod.data.orbital.selfImmune = v; return "Caller immune " + onOff(v); }))
                .then(integer("lifetime", 1, 6000, (c, v) -> { mod.data.orbital.arrowLifetime = v; return "Arrow lifetime " + v + " ticks."; }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> dbl(String name, double min, double max, Apply<Double> fn) {
        return literal(name).then(argument("value", DoubleArgumentType.doubleArg(min, max))
                .executes(ctx -> run(ctx, c -> fn.run(c, DoubleArgumentType.getDouble(c, "value")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> integer(String name, int min, int max, Apply<Integer> fn) {
        return literal(name).then(argument("value", IntegerArgumentType.integer(min, max))
                .executes(ctx -> run(ctx, c -> fn.run(c, IntegerArgumentType.getInteger(c, "value")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> bool(String name, Apply<Boolean> fn) {
        return literal(name).then(argument("value", BoolArgumentType.bool())
                .executes(ctx -> run(ctx, c -> fn.run(c, BoolArgumentType.getBool(c, "value")))));
    }

    private static int run(CommandContext<ServerCommandSource> ctx, Action action) throws CommandSyntaxException {
        String out = action.run(ctx);
        mod.data.save();
        if (out != null) msg(ctx, out);
        return 1;
    }

    private static int help(CommandContext<ServerCommandSource> ctx) {
        msg(ctx, "§6Machines " + MachinesMod.VERSION
                + "\n§e/machine arrowcannon §7create, list, remove, <name> zone/height/turret/speed/damage/interval/count/spread/los/glidingonly/lifetime/on/info"
                + "\n§e/machine ghostbarrel §7give [player], reset (look at barrel), list"
                + "\n§e/machine portaltrap §7zone pos1/pos2, on, escapable, thickness, shell, status"
                + "\n§e/machine tntcannon §7add <name> (look at dispenser), list, remove, <name> count/interval/velocity/spread/fuse/mode/blockdamage/drops/on/info"
                + "\n§e/machine orbital §7give [player] [durability|unbreakable], radius/rings/spacing/height/velocity/damage/wavedelay/selfimmune/lifetime/info"
                + "\n§e/machine immune <player> <true|false>");
        return 1;
    }

    private static MachineData.ArrowCannon ac(CommandContext<ServerCommandSource> c) {
        return mod.data.arrowCannons.get(StringArgumentType.getString(c, "name"));
    }

    private static MachineData.TntCannon tc(CommandContext<ServerCommandSource> c) {
        return mod.data.tntCannons.get(StringArgumentType.getString(c, "name"));
    }

    private static String missing(CommandContext<ServerCommandSource> c) {
        return "§cNo arrow cannon named " + StringArgumentType.getString(c, "name") + ". Use /machine arrowcannon create <name>.";
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
