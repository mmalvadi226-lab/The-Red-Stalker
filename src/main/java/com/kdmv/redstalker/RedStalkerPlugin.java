package com.kdmv.redstalker;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Random;

public final class RedStalkerPlugin extends JavaPlugin
        implements Listener {

    private NamespacedKey key;
    private final Random random = new Random();
    private BukkitTask task;
    private boolean waiting = false;

    @Override
    public void onEnable() {
        key = new NamespacedKey(this, "red_stalker");
        getServer().getPluginManager().registerEvents(this, this);

        task = getServer().getScheduler().runTaskTimer(
                this, this::spawnRandom, 200L, 200L
        );

        getLogger().info("The Red Stalker is awake!");
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Zombie z && isStalker(z)) {
                    z.remove();
                }
            }
        }
    }

    private boolean isStalker(Zombie z) {
        return z.getPersistentDataContainer()
                .has(key, PersistentDataType.BYTE);
    }

    private ItemStack armor(Material material) {
        ItemStack item = new ItemStack(material);
        ArmorMeta meta = (ArmorMeta) item.getItemMeta();

        if (meta != null) {
            meta.setTrim(new ArmorTrim(
                    TrimMaterial.REDSTONE,
                    TrimPattern.SENTRY
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    private Location findLocation(Player player) {
        World world = player.getWorld();

        for (int i = 0; i < 30; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = 12 + random.nextInt(11);

            Location loc = player.getLocation().clone().add(
                    Math.cos(angle) * distance,
                    0,
                    Math.sin(angle) * distance
            );

            loc.setY(world.getHighestBlockYAt(loc) + 1);

            if (loc.getBlock().isPassable()
                    && loc.clone().add(0, 1, 0)
                    .getBlock().isPassable()) {
                return loc;
            }
        }

        return null;
    }

    private void spawnRandom() {
        if (waiting) return;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Zombie z && isStalker(z)) {
                    return;
                }
            }
        }

        if (random.nextInt(4) != 0) return;

        var players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.isDead())
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .toList();

        if (players.isEmpty()) return;

        Player target = players.get(random.nextInt(players.size()));
        Location loc = findLocation(target);

        if (loc != null) spawnStalker(loc, target);
    }

    private void spawnStalker(Location loc, Player target) {
        World world = loc.getWorld();
        if (world == null) return;

        Zombie z = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);

        z.getPersistentDataContainer().set(
                key, PersistentDataType.BYTE, (byte) 1
        );

        z.setCustomName("§c§lThe Red Stalker");
        z.setCustomNameVisible(false);
        z.setSilent(true);
        z.setAI(false);
        z.setInvulnerable(true);
        z.setCanPickupItems(false);
        z.setRemoveWhenFarAway(false);

        z.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION,
                0, false, false, false
        ));

        z.getEquipment().setHelmet(
                armor(Material.NETHERITE_HELMET));
        z.getEquipment().setChestplate(
                armor(Material.NETHERITE_CHESTPLATE));
        z.getEquipment().setLeggings(
                armor(Material.NETHERITE_LEGGINGS));
        z.getEquipment().setBoots(
                armor(Material.NETHERITE_BOOTS));
        z.getEquipment().setItemInMainHand(
                new ItemStack(Material.FISHING_ROD));

        z.getEquipment().setHelmetDropChance(0);
        z.getEquipment().setChestplateDropChance(0);
        z.getEquipment().setLeggingsDropChance(0);
        z.getEquipment().setBootsDropChance(0);
        z.getEquipment().setItemInMainHandDropChance(0);

        facePlayer(z, target);

        target.playSound(
                target.getLocation(),
                Sound.AMBIENT_CAVE, 1f, 0.6f
        );

        getLogger().info("The Red Stalker appeared!");

        taskWatch(z);
    }

    private void facePlayer(Zombie z, Player p) {
        Location loc = z.getLocation();
        Vector direction = p.getEyeLocation().toVector()
                .subtract(loc.toVector()).normalize();

        loc.setDirection(direction);
        z.teleport(loc);
    }

    private void taskWatch(Zombie z) {
        final int[] ticks = {0};

        Bukkit.getScheduler().runTaskTimer(this, timer -> {
            if (!z.isValid() || !isStalker(z)) {
                timer.cancel();
                return;
            }

            ticks[0]++;

            Player nearest = nearestPlayer(z.getLocation(), 30);

            if (nearest == null) return;

            Location eye = nearest.getEyeLocation();
            Vector toStalker = z.getLocation().add(0, 1, 0)
                    .toVector().subtract(eye.toVector());

            double distance = toStalker.length();

            if (distance > 0 && distance < 25) {
                double dot = eye.getDirection().normalize()
                        .dot(toStalker.normalize());

                // If the player looks directly at the stalker,
                // it vanishes immediately.
                if (dot > 0.985
                        && nearest.hasLineOfSight(z)) {
                    timer.cancel();
                    vanish(z, nearest);
                    return;
                }
            }

            if (ticks[0] % 40 == 0) {
                z.getWorld().spawnParticle(
                        Particle.DUST,
                        z.getLocation().add(0, 1, 0),
                        5, 0.25, 0.4, 0.25,
                        new Particle.DustOptions(Color.RED, 1.5f)
                );
            }

            // Occasionally frighten a nearby player.
            if (ticks[0] % 60 == 0 && random.nextInt(4) == 0) {
                nearest.playSound(
                        nearest.getLocation(),
                        Sound.ENTITY_ENDERMAN_STARE,
                        1f, 0.6f
                );
            }

            // Disappear even if nobody notices it.
            if (ticks[0] >= 240) {
                timer.cancel();
                vanish(z, nearest);
            }

        }, 1L, 5L);
    }

    private Player nearestPlayer(Location loc, double range) {
        return loc.getWorld().getPlayers().stream()
                .filter(p -> !p.isDead())
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .filter(p -> p.getLocation().distanceSquared(loc)
                        <= range * range)
                .min((a, b) -> Double.compare(
                        a.getLocation().distanceSquared(loc),
                        b.getLocation().distanceSquared(loc)))
                .orElse(null);
    }

    private void vanish(Zombie z, Player player) {
        if (!z.isValid()) return;

        Location loc = z.getLocation().clone();
        World world = loc.getWorld();

        if (world != null) {
            world.spawnParticle(
                    Particle.DUST,
                    loc.add(0, 1, 0),
                    30, 0.5, 0.7, 0.5,
                    new Particle.DustOptions(Color.RED, 2f)
            );
        }

        z.remove();
        waiting = true;

        long delay = (180 + random.nextInt(61)) * 20L;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                var players = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.isDead())
                        .filter(p -> p.getGameMode()
                                != GameMode.SPECTATOR)
                        .toList();

                if (players.isEmpty()) return;

                Player target = players.get(
                        random.nextInt(players.size()));

                Location next = findLocation(target);

                if (next != null) {
                    spawnStalker(next, target);
                }
            } finally {
                waiting = false;
            }
        }, delay);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Zombie z && isStalker(z)) {
            event.setCancelled(true);
        }
    }
        }
