package io.ocf;

import io.ocf.items.CustomItemManager;
import io.ocf.items.ItemListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class CTF extends JavaPlugin implements Listener {
    private TeamManager teamManager;
    private KitManager kitManager;
    private GameManager gameManager;
    private CustomItemManager customItemManager;

    @Override
    public void onEnable() {
        teamManager = new TeamManager(this);
        kitManager = new KitManager(this);
        gameManager = new GameManager(this, teamManager, kitManager);
        customItemManager = new CustomItemManager(this, gameManager, teamManager);
        kitManager.setCustomItemManager(customItemManager);

        // Register commands
        TeamCommand teamCommand = new TeamCommand(teamManager, gameManager);
        KitCommand kitCommand = new KitCommand(teamManager, kitManager, gameManager);
        getCommand("team").setExecutor(teamCommand);
        getCommand("chat").setExecutor(new ChatCommand(teamManager));
        getCommand("kit").setExecutor(kitCommand);
        getCommand("game").setExecutor(new GameCommand(gameManager));

        GiveItemCommand giveItemCommand = new GiveItemCommand(customItemManager);
        getCommand("giveitem").setExecutor(giveItemCommand);
        getCommand("giveitem").setTabCompleter(giveItemCommand);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(teamManager), this);
        Bukkit.getPluginManager().registerEvents(new GameListener(gameManager, teamManager), this);
        Bukkit.getPluginManager().registerEvents(new ItemListener(customItemManager), this);
    }

    public CustomItemManager getCustomItemManager() {
        return customItemManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Hello, " + event.getPlayer().getName() + "!"));
    }
}