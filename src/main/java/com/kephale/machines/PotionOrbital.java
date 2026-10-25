package com.kephale.machines;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.thrown.SplashPotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class PotionOrbital {

    public static final String KIND = "potion_orbital";
    public static final String TAG = "kmach_potion";
    private static final Vec3d DOWN = new Vec3d(0, -1, 0);

    private final MachinesMod mod;
    private final Map<String, Identifier> labels = new LinkedHashMap<>();
    private final Map<String, String> descriptions = new LinkedHashMap<>();

    public PotionOrbital(MachinesMod mod) { this.mod = mod; }

    public void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.FISHING_ROD) || !Tagging.is(stack, KIND)) return ActionResult.PASS;
            String potionId = Tagging.link(stack);
            Tagging.strip(stack);
            stack.damage(1, sp, hand);
            fire(sp, potionId);
            return ActionResult.SUCCESS;
        });
    }

    public Map<String, Identifier> labels() {
        if (labels.isEmpty()) build();
        return labels;
    }

    public String describe(String label) {
        if (labels.isEmpty()) build();
        return descriptions.getOrDefault(label, label);
    }

    private void build() {
        for (Potion potion : Registries.POTION) {
            Identifier id = Registries.POTION.getId(potion);
            if (id == null) continue;
            List<StatusEffectInstance> effects = potion.getEffects();
            if (effects.isEmpty()) continue;
            String path = id.getPath();
            boolean strong = path.startsWith("strong_");
            String base = path.replaceFirst("^(long_|strong_)", "");
            StatusEffectInstance first = effects.get(0);
            boolean instant = first.getEffectType().value().isInstant();
            String label = base + "_" + (strong ? "2" : "1") + (instant ? "" : "_" + shortTime(first.getDuration()));
            if (labels.containsKey(label)) label = path;
            labels.put(label, id);

            StringBuilder d = new StringBuilder();
            for (StatusEffectInstance e : effects) {
                if (d.length() > 0) d.append(", ");
                Identifier eid = Registries.STATUS_EFFECT.getId(e.getEffectType().value());
                d.append(eid == null ? "effect" : eid.getPath().replace('_', ' '))
                        .append(' ').append(roman(e.getAmplifier() + 1));
                if (!e.getEffectType().value().isInstant()) d.append(' ').append(clock(e.getDuration()));
            }
            descriptions.put(label, d.toString());
        }
    }

    public CompletableFuture<Suggestions> suggest(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String label : labels().keySet()) {
            if (label.startsWith(typed) || label.contains(typed)) builder.suggest(label, Text.literal(describe(label)));
        }
        return builder.buildFuture();
    }

    public String resolve(String input) {
        Identifier id = labels().get(input.toLowerCase(Locale.ROOT));
        if (id != null) return id.toString();
        Identifier parsed = Identifier.tryParse(input);
        if (parsed != null && Registries.POTION.containsId(parsed)) return parsed.toString();
        return null;
    }

    public ItemStack makeRod(String potionId, Integer durability, boolean unbreakable) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        Tagging.mark(rod, KIND, potionId);
        if (unbreakable) {
            rod.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        } else if (durability != null) {
            int max = rod.getOrDefault(DataComponentTypes.MAX_DAMAGE, 64);
            if (durability > max) {
                rod.set(DataComponentTypes.MAX_DAMAGE, durability);
                rod.set(DataComponentTypes.DAMAGE, 0);
            } else {
                rod.set(DataComponentTypes.DAMAGE, max - durability);
            }
        }
        return rod;
    }

    public void fire(ServerPlayerEntity caller, String potionId) {
        Identifier id = potionId == null ? null : Identifier.tryParse(potionId);
        if (id == null) return;
        Optional<RegistryEntry.Reference<Potion>> entry = Registries.POTION.getEntry(id);
        if (entry.isEmpty()) return;

        ItemStack potion = new ItemStack(Items.SPLASH_POTION);
        potion.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(entry.get()));

        ServerWorld world = (ServerWorld) caller.getEntityWorld();
        Vec3d at = caller.getEntityPos();
        MachineData.PotionOrbitalSettings s = mod.data.potionOrbital;
        int rings = Math.max(0, s.rings);
        for (int i = 0; i <= rings; i++) {
            final int ring = i;
            mod.schedule(ring * Math.max(0, s.waveDelay), () -> spawnRing(world, at.x, at.z, ring, rings, s, caller, potion));
        }
    }

    private void spawnRing(ServerWorld world, double x, double z, int ring, int rings,
                           MachineData.PotionOrbitalSettings s, ServerPlayerEntity owner, ItemStack potion) {
        if (ring == 0) {
            spawn(world, x, z, s, owner, potion);
            return;
        }
        double r = s.radius * ring / rings;
        int n = Math.max(1, (int) Math.round(2 * Math.PI * r / Math.max(0.25, s.spacing)));
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n;
            spawn(world, x + r * Math.cos(a), z + r * Math.sin(a), s, owner, potion);
        }
    }

    private void spawn(ServerWorld world, double x, double z, MachineData.PotionOrbitalSettings s,
                       ServerPlayerEntity owner, ItemStack potion) {
        SplashPotionEntity e = new SplashPotionEntity(world, x, s.height, z, potion.copy());
        e.setOwner(owner);
        e.setVelocity(DOWN.x, DOWN.y, DOWN.z, (float) s.velocity, 0.0f);
        e.addCommandTag(TAG);
        world.spawnEntity(e);
    }

    public int arrowCount() {
        MachineData.PotionOrbitalSettings s = mod.data.potionOrbital;
        int total = 1;
        for (int i = 1; i <= s.rings; i++) {
            double r = s.radius * i / Math.max(1, s.rings);
            total += Math.max(1, (int) Math.round(2 * Math.PI * r / Math.max(0.25, s.spacing)));
        }
        return total;
    }

    private static String shortTime(int ticks) {
        int sec = ticks / 20;
        int m = sec / 60, r = sec % 60;
        if (m == 0) return r + "s";
        return r == 0 ? m + "m" : m + "m" + r + "s";
    }

    private static String clock(int ticks) {
        int sec = ticks / 20;
        return (sec / 60) + ":" + String.format(Locale.ROOT, "%02d", sec % 60);
    }

    private static String roman(int n) {
        String[] r = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}
