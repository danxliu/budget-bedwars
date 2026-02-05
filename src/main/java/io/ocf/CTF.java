package io.ocf;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class CTF extends JavaPlugin implements Listener {
    private TeamManager teamManager;
    private KitManager kitManager;

    @Override
    public void onEnable() {
        teamManager = new TeamManager(this);
        kitManager = new KitManager(this);

        // Register commands
        getCommand("team").setExecutor(new TeamCommand(teamManager));
        getCommand("chat").setExecutor(new ChatCommand(teamManager));
        getCommand("kit").setExecutor(new KitCommand(teamManager, kitManager));

        // Register listeners
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(teamManager), this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Hello, " + event.getPlayer().getName() + "!"));
    }
}