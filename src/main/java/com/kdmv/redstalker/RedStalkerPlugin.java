package com.kdmv.redstalker;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RedStalkerPlugin extends JavaPlugin implements Listener {

    private NamespacedKey stalkerKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private BukkitTask stalkerTask;

    @Override
    public void onEnable() {
        stalkerKey = new NamespacedKey(this, "red_stalker");
        Bukkit.getPluginManager().registerEvents(this, this);

        stalkerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (!(entity instanceof Zombie zombie)) continue;
                    if (!isStalker(zombie)) continue;

                    Player target = null;
                    double nearest = 36 * 36;

                    for (Player player : world.getPlayers()) {
                        if (player.isDead() || !player.isOnline()) continue;

                        double distance = player.getLocation()
                                .distanceSquared(zombie.getLocation());

                        if (distance < nearest) {
                            nearest = distance;
                            target = player;
                        }
                    }

                    if (target != null) {
                        zombie.setTarget(target);
                    } else {
                        zombie.setTarget(null);
                    }
                }
            }
        }, 20L, 40L);

        getLogger().info("The Red Stalker has awakened!");
    }

    @Override
    public void onDisable() {
        if (stalkerTask != null) {
            stalkerTask.cancel();
        }
        getLogger().info("The Red Stalker has disappeared.");
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

    public void spawnRedStalker(Location location) {
        Zombie zombie = (Zombie) location.getWorld()
                .spawnEntity(location, EntityType.ZOMBIE);

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
    }

    @EventHandler
    public void onStalkerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        if (!isStalker(zombie)) return;

        event.setCancelled(true);

        UUID id = zombie.getUniqueId();
        long now = System.currentTimeMillis();

        if (cooldowns.getOrDefault(id, 0L) > now) return;

        cooldowns.put(id, now + 60_000L);

        Location oldLocation = zombie.getLocation();
        World world = oldLocation.getWorld();

        zombie.teleport(world.getSpawnLocation());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!zombie.isValid() || zombie.isDead()) return;

            Location returnLocation = oldLocation.clone();
            returnLocation.add(3, 0, 3);

            if (returnLocation.getWorld() != null) {
                zombie.teleport(returnLocation);
            }
        }, 1200L);
    }
                        }
