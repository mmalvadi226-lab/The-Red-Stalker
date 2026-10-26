package com.kdmv.redstalker;

import org.bukkit.plugin.java.JavaPlugin;

public final class RedStalkerPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("The Red Stalker has awakened!");
    }

    @Override
    public void onDisable() {
        getLogger().info("The Red Stalker has disappeared.");
    }
}
