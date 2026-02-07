package io.ocf.items;

import io.ocf.GameManager;
import io.ocf.PlayerData;
import io.ocf.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class FlagCompassItem extends CustomItem {
    private static BukkitTask updateTask;
    private static GameManager gameManager;
    private static TeamManager teamManager;
    private static JavaPlugin plugin;

    public FlagCompassItem() {
        super(
            "compass",
            Component.text("Flag Compass", NamedTextColor.GOLD),
            Material.COMPASS,
            PlayerData.Team.ATTACKERS,
            false, // Not consumed on use
            TriggerType.INTERACT,
            List.of(
                Component.text("Points to the flag location", NamedTextColor.GRAY),
                Component.text("Attackers only", NamedTextColor.RED)
            )
        );
    }

    public static void init(JavaPlugin p, GameManager gm, TeamManager tm) {
        plugin = p;
        gameManager = gm;
        teamManager = tm;
    }

    @Override
    public boolean onUse(Player player, GameManager gameManager) {
        Location flagLoc = gameManager.getFlagLocation();
        if (flagLoc == null) {
            player.sendMessage(Component.text("The flag location is not set yet!", NamedTextColor.RED));
            return false;
        }
        
        double distance = player.getLocation().distance(flagLoc);
        player.sendMessage(Component.text("Flag is approximately ", NamedTextColor.GOLD)
                .append(Component.text(String.format("%.0f", distance), NamedTextColor.YELLOW))
                .append(Component.text(" blocks away", NamedTextColor.GOLD)));
        return true;
    }

    public static void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameManager == null || !gameManager.isRunning()) {
                    return;
                }

                Location flagLocation = gameManager.getFlagLocation();
                if (flagLocation == null) {
                    return;
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = teamManager.getPlayerData(player);
                    if (data.getTeam() != PlayerData.Team.ATTACKERS) {
                        continue;
                    }

                    updateCompassesInInventory(player, flagLocation);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Update every 10 ticks (0.5 seconds)
    }

    public static void stopUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private static void updateCompassesInInventory(Player player, Location flagLocation) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.COMPASS) {
                continue;
            }

            String customId = CustomItem.getCustomItemId(item);
            if (!"compass".equals(customId)) {
                continue;
            }

            // Update compass to point to flag
            CompassMeta meta = (CompassMeta) item.getItemMeta();
            meta.setLodestone(flagLocation);
            meta.setLodestoneTracked(false); // Don't require actual lodestone block
            item.setItemMeta(meta);
        }
    }
}
