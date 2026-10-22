# Machines 1.0.0

Fabric, Minecraft 1.21.11, Java 21. Server side only: players join with a vanilla client.
Needs Fabric API. Config and machine data live in `config/machines/machines.json`.
Every command is op only (`/machine help` lists them).

## 1. Anti elytra arrow cannon

```
/machine arrowcannon create maze1
/machine arrowcannon maze1 zone pos1            stand on one corner
/machine arrowcannon maze1 zone pos2            stand on the opposite corner
/machine arrowcannon maze1 zone circle 60       or: circle centred on you
/machine arrowcannon maze1 height 40 80
/machine arrowcannon maze1 turret               the block you are looking at
/machine arrowcannon maze1 turret 120 45 -300
/machine arrowcannon maze1 info
```

Settings: `speed` (default 12 blocks per tick), `damage` (6, applied regardless of speed),
`interval` (5 ticks between shots per target), `count` (arrows per shot, 1), `spread` (0),
`los` (line of sight check, on), `glidingonly` (off), `lifetime` (80 ticks), `on`.

Anyone in survival or adventure inside the zone between min and max Y gets shot.
Creative, spectator and players set with `/machine immune` are ignored.
Arrows use normal physics. The aim leads the target using its measured velocity and
compensates for gravity drop. Arrows cannot be picked up and vanish after their lifetime.

## 2. Ghost totem barrel

```
/machine ghostbarrel give [player]
/machine ghostbarrel reset                      look at a barrel, rearms it
/machine ghostbarrel list
```

Place the barrel: a linked totem appears in the middle slot.
Take it out: an identical ghost copy appears where it was.
Click the ghost: it disappears, nothing ends up in your hand.
Pop the linked totem: the ghost in its barrel disappears (queued if that chunk is unloaded).
Break the barrel: whatever totem is inside disappears, the barrel drops as a normal barrel.
Hoppers cannot pull either totem out. A ghost can never exist as a dropped item.

## 3. Nether portal trap

```
/machine portaltrap zone pos1                   stand in the nether
/machine portaltrap zone pos2
/machine portaltrap on true
/machine portaltrap status                      also prints the overworld equivalent area
```

Settings: `escapable` (false: bedrock caps and a bedrock shell), `thickness` (1 portal layer
each side of the arrival portal), `shell` (8 blocks, past chorus fruit range).

Only new nether side portals that vanilla generates inside the zone are trapped.
Existing portals and every overworld portal are never touched. The arrival portal becomes
a stack of portal layers sealed at both ends, so any step sends you straight back.
Keep existing nether portals away from the zone edge or new portals will link to them instead.

## 4. TNT cannon

```
/machine tntcannon add cannon1                  look at a dispenser
/machine tntcannon cannon1 info
/machine tntcannon list
```

Settings: `count` (10), `interval` (20 ticks), `mode burst|stream` (burst), `velocity` (1.5),
`spread` (0.1), `fuse` (80), `blockdamage` (on), `drops` (on), `on`.

Fires while the dispenser is powered, starting the moment it gets power.
Infinite ammo, its contents are never dispensed. Shoots out of the face it points.
Stream spreads the same count evenly across the interval.

## 5. Orbital strike rod

```
/machine orbital give                           to yourself, full durability
/machine orbital give Pablo 10                  10 durability left
/machine orbital give Pablo 500                 raises max durability to 500
/machine orbital give Pablo unbreakable
/machine orbital info
```

Settings: `radius` (22), `rings` (5), `spacing` (2 blocks between arrows on a ring),
`height` (Y 322), `velocity` (5), `damage` (8), `wavedelay` (0 ticks between rings),
`selfimmune` (on), `lifetime` (200).

First right click: no cast, the strike fires at your X and Z from Y 322 and the rod becomes a
normal fishing rod. Pattern at defaults: 1 centre arrow plus rings of 14, 28, 41, 55, 69
(208 arrows). Arrows have normal gravity; launched at 5 they sit at a falling arrow's
terminal speed, so roughly 2.5 seconds to reach Y 64.

## Immunity

```
/machine immune <player> true|false
```

Immune players are never targeted by the arrow cannon and take no damage from any machine arrow.

## Hooks (for debugging)

Mixins: BlockItem.postPlacement (ghost barrel placed), ScreenHandler.onSlotClick (ghost clicks),
LivingEntity.tryUseDeathProtector (totem pop), HopperBlockEntity.canExtract,
PortalForcer.createPortal (trap), DispenserBlock.dispense (cannon dispensers),
TntEntity.explode (block damage and drops), ServerWorld.spawnEntity (drop filtering).
All injections are non required: if one fails to apply, that feature stops but the server
still starts. Check the log for "Machines:" lines.
