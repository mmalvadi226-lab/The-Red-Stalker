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

    private NamespacedKey stalkerKey;
    private final Random random = new Random();
    private BukkitTask stalkerTask;
    private boolean waitingToReturn = false;

    @Override
    public void onEnable() {
        stalkerKey = new NamespacedKey(this, "red_stalker");
        Bukkit.getPluginManager().registerEvents(this, this);

        stalkerTask = Bukkit.getScheduler().runTaskTimer(
                this, this::randomSpawn, 200L, 200L
        );

        getLogger().info("The Red Stalker has awakened!");
    }

    @Override
    public void onDisable() {
        if (stalkerTask != null) stalkerTask.cancel();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Zombie zombie && isStalker(zombie)) {
                    zombie.remove();
                }
            }
        }
    }

    private boolean isStalker(Zombie zombie) {
        return zombie.getPersistentDataContainer()
                .has(stalkerKey, PersistentDataType.BYTE);
    }

    private ItemStack createArmor(Material material) {
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

    private Location findLocation(Player player, int min, int max) {
        World world = player.getWorld();

        for (int i = 0; i < 20; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = min + random.nextInt(max - min + 1);

            Location loc = player.getLocation().clone().add(
                    Math.cos(angle) * distance, 0,
                    Math.sin(angle) * distance
            );

            loc.setY(world.getHighestBlockYAt(loc) + 1);

            if (loc.getBlock().isPassable()
                    && loc.clone().add(0, 1, 0).getBlock().isPassable()
                    && loc.distance(player.getLocation()) >= min) {
                return loc;
            }
        }

        return null;
    }

    private void randomSpawn() {
        if (waitingToReturn) return;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Zombie zombie && isStalker(zombie)) {
                    return;
                }
            }
        }

        if (random.nextInt(4) != 0) return;

        var players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.isDead() && p.getGameMode() != GameMode.SPECTATOR)
                .toList();

        if (players.isEmpty()) return;

        Player target = players.get(random.nextInt(players.size()));
        Location loc = findLocation(target, 12, 22);

        if (loc != null) spawnStalker(loc, target);
    }

    private void spawnStalker(Location loc, Player target) {
        World world = loc.getWorld();
        if (world == null) return;

        Zombie zombie = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);

        zombie.getPersistentDataContainer().set(
                stalkerKey, PersistentDataType.BYTE, (byte) 1
        );

        zombie.setCustomName("§c§lThe Red Stalker");
        zombie.setCustomNameVisible(false);
        zombie.setSilent(true);
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setShouldBurnInDay(false);
        zombie.setAI(false);
        zombie.setInvulnerable(true);

        zombie.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0,
                false, false, false
        ));

        zombie.getEquipment().setHelmet(
                createArmor(Material.NETHERITE_HELMET));
        zombie.getEquipment().setChestplate(
                createArmor(Material.NETHERITE_CHESTPLATE));
        zombie.getEquipment().setLeggings(
                createArmor(Material.NETHERITE_LEGGINGS));
        zombie.getEquipment().setBoots(
                createArmor(Material.NETHERITE_BOOTS));
        zombie.getEquipment().setItemInMainHand(
                new ItemStack(Material.FISHING_ROD));

        zombie.getEquipment().setHelmetDropChance(0);
        zombie.getEquipment().setChestplateDropChance(0);
        zombie.getEquipment().setLeggingsDropChance(0);
        zombie.getEquipment().setBootsDropChance(0);
        zombie.getEquipment().setItemInMainHandDropChance(0);

        lookAt(zombie, target);

        target.playSound(
                target.getLocation(),
                Sound.AMBIENT_CAVE, 1.0f, 0.5f
        );

        getLogger().info("The Red Stalker appeared!");

        // Watch silently, then vanish after a random interval.
        int watchTime = 80 + random.nextInt(101);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!zombie.isValid()) return;

            Player nearest = nearestPlayer(zombie.getLocation(), 30);

            if (nearest != null) {
                // Red particles and a frightening sound.
                world.spawnParticle(
                        Particle.DUST,
                        zombie.getLocation().add(0, 1, 0),
                        18, 0.4, 0.7, 0.4,
                        new Particle.DustOptions(Color.RED, 1.5f)
                );

                nearest.playSound(
                        nearest.getLocation(),
                        Sound.ENTITY_ENDERMAN_TELEPORT,
                        1.0f, 0.6f
                );

                // Occasionally appear behind the player.
                if (random.nextInt(4) == 0) {
                    Location behind = nearest.getLocation().clone()
                            .add(nearest.getLocation().getDirection()
                                    .multiply(-5));

                    behind.setY(nearest.getLocation().getY());

                    if (behind.getBlock().isPassable()
                            && behind.clone().add(0, 1, 0)
                            .getBlock().isPassable()) {
                        zombie.teleport(behind);
                        lookAt(zombie, nearest);
                    }
                }

                // Occasionally throw a harmless fishing rod item
                // as a visual prank. It does not hook or damage players.
                if (random.nextInt(3) == 0) {
                    Item dropped = world.dropItem(
                            zombie.getLocation().add(0, 1, 0),
                            new ItemStack(Material.FISHING_ROD)
                    );

                    dropped.setPickupDelay(40);
                    dropped.setVelocity(
                            nearest.getLocation().toVector()
                                    .subtract(zombie.getLocation().toVector())
                                    .normalize().multiply(0.5)
                                    .add(new Vector(0, 0.25, 0))
                    );
                }
            }

            vanish(zombie);
        }, watchTime);
    }

    private void lookAt(Zombie zombie, Player target) {
        Location from = zombie.getLocation();
        Vector direction = target.getEyeLocation().toVector()
                .subtract(from.toVector()).normalize();

        Location facing = from.clone();
        facing.setDirection(direction);
        zombie.teleport(facing);
    }

    private Player nearestPlayer(Location loc, double range) {
        return loc.getWorld().getPlayers().stream()
                .filter(p -> !p.isDead()
                        && p.getGameMode() != GameMode.SPECTATOR)
                .filter(p -> p.getLocation().distanceSquared(loc)
                        <= range * range)
                .min((a, b) -> Double.compare(
                        a.getLocation().distanceSquared(loc),
                        b.getLocation().distanceSquared(loc)))
                .orElse(null);
    }

    private void vanish(Zombie zombie) {
        if (!zombie.isValid()) return;

        Location oldLocation = zombie.getLocation().clone();
        World world = oldLocation.getWorld();

        if (world != null) {
            world.spawnParticle(
                    Particle.DUST,
                    oldLocation.add(0, 1, 0),
                    25, 0.5, 0.8, 0.5,
                    new Particle.DustOptions(Color.RED, 1.8f)
            );
        }

        zombie.remove();

        waitingToReturn = true;

        // Return after 3–4 minutes.
        long delay = (180 + random.nextInt(61)) * 20L;

        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                if (Bukkit.getOnlinePlayers().isEmpty()) return;

                var players = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.isDead()
                                && p.getGameMode() != GameMode.SPECTATOR)
                        .toList();

                if (players.isEmpty()) return;

                Player target = players.get(
                        random.nextInt(players.size()));

                Location loc = findLocation(target, 12, 22);

                if (loc != null) spawnStalker(loc, target);
            } finally {
                waitingToReturn = false;
            }
        }, delay);
    }

    @EventHandler
    public void onStalkerDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Zombie zombie
                && isStalker(zombie)) {
            event.setCancelled(true);
        }
    }
                }
