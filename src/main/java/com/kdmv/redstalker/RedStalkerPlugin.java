package com.kdmv.redstalker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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

        // ពិនិត្យរៀងរាល់ 5 នាទី
        stalkerTask = Bukkit.getScheduler().runTaskTimer(
                this, this::randomSpawn, 6000L, 6000L
        );

        getLogger().info("The Red Stalker has awakened!");
    }

    @Override
    public void onDisable() {
        if (stalkerTask != null) {
            stalkerTask.cancel();
        }

        getLogger().info("The Red Stalker has disappeared.");
    }

    private void randomSpawn() {
        if (waitingToReturn) return;

        // កុំបង្កើត Stalker ថ្មី បើមានមួយកំពុងរស់
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Zombie zombie && isStalker(zombie)) {
                    return;
                }
            }
        }

        // ឱកាស 25% ក្នុងមួយការពិនិត្យ
        if (random.nextInt(4) != 0) return;

        var players = Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.isDead())
                .toList();

        if (players.isEmpty()) return;

        Player player = players.get(random.nextInt(players.size()));
        Location location = player.getLocation().clone();

        double angle = random.nextDouble() * Math.PI * 2;
        int distance = 12 + random.nextInt(9);

        location.add(
                Math.cos(angle) * distance,
                0,
                Math.sin(angle) * distance
        );

        World world = location.getWorld();
        if (world == null) return;

        location.setY(world.getHighestBlockYAt(location) + 1);

        if (!location.getBlock().isPassable()
                || !location.clone().add(0, 1, 0)
                .getBlock().isPassable()) {
            return;
        }

        spawnRedStalker(location);
    }

    private boolean isStalker(Zombie zombie) {
        return zombie.getPersistentDataContainer()
                .has(stalkerKey, PersistentDataType.BYTE);
    }

    private ItemStack createTrimmedArmor(Material material) {
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

    private void spawnRedStalker(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        Zombie zombie = (Zombie) world.spawnEntity(
                location, EntityType.ZOMBIE
        );

        zombie.getPersistentDataContainer().set(
                stalkerKey, PersistentDataType.BYTE, (byte) 1
        );

        zombie.setCustomName("§c§lThe Red Stalker");
        zombie.setCustomNameVisible(false);
        zombie.setSilent(true);
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(false);

        zombie.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION,
                0,
                false,
                false
        ));

        zombie.getEquipment().setHelmet(
                createTrimmedArmor(Material.NETHERITE_HELMET));
        zombie.getEquipment().setChestplate(
                createTrimmedArmor(Material.NETHERITE_CHESTPLATE));
        zombie.getEquipment().setLeggings(
                createTrimmedArmor(Material.NETHERITE_LEGGINGS));
        zombie.getEquipment().setBoots(
                createTrimmedArmor(Material.NETHERITE_BOOTS));
        zombie.getEquipment().setItemInMainHand(
                new ItemStack(Material.FISHING_ROD));

        zombie.getEquipment().setHelmetDropChance(0.0f);
        zombie.getEquipment().setChestplateDropChance(0.0f);
        zombie.getEquipment().setLeggingsDropChance(0.0f);
        zombie.getEquipment().setBootsDropChance(0.0f);
        zombie.getEquipment().setItemInMainHandDropChance(0.0f);

        getLogger().info("The Red Stalker spawned!");
    }

    @EventHandler
    public void onStalkerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (!isStalker(zombie)) return;

        event.setCancelled(true);

        if (waitingToReturn) return;

        waitingToReturn = true;
        Location returnLocation = zombie.getLocation().clone();

        zombie.remove();

        // ត្រឡប់មកវិញក្រោយ 60 វិនាទី
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    spawnRedStalker(returnLocation);
                }
            } finally {
                waitingToReturn = false;
            }
        }, 1200L);
    }
            }
