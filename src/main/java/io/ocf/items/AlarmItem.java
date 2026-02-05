package io.ocf.items;

import io.ocf.GameManager;
import io.ocf.PlayerData;
import io.ocf.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.List;

public class AlarmItem extends CustomItem {
    private static final int DETECTION_RADIUS = 30;
    private static final int CHECK_INTERVAL_TICKS = 20; // Check every second
    
    private static BukkitRunnable activeAlarm = null;
    private static JavaPlugin plugin;
    private static TeamManager teamManager;

    public AlarmItem() {
        super(
            "alarm",
            Component.text("Alarm", NamedTextColor.GOLD),
            Material.REDSTONE_TORCH,
            PlayerData.Team.DEFENDERS, // Defenders only
            true, // Consume on use
            TriggerType.INTERACT,
            List.of(
                Component.text("Right-click to activate", NamedTextColor.GRAY),
                Component.text("Alerts when attackers near flag", NamedTextColor.GRAY)
            )
        );
    }

    public static void init(JavaPlugin p, TeamManager tm) {
        plugin = p;
        teamManager = tm;
    }

    public static boolean isAlarmActive() {
        return activeAlarm != null;
    }

    public static void deactivateAlarm() {
        if (activeAlarm != null) {
            activeAlarm.cancel();
            activeAlarm = null;
        }
    }

    @Override
    public boolean onUse(Player player, GameManager gameManager) {
        if (activeAlarm != null) {
            player.sendMessage(Component.text("An alarm is already active!", NamedTextColor.RED));
            return false;
        }

        Location flagLocation = gameManager.getFlagLocation();
        if (flagLocation == null) {
            player.sendMessage(Component.text("No flag has been placed yet!", NamedTextColor.RED));
            return false;
        }

        // Start monitoring task
        activeAlarm = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameManager.isRunning()) {
                    deactivateAlarm();
                    return;
                }

                Location flag = gameManager.getFlagLocation();
                if (flag == null) {
                    deactivateAlarm();
                    return;
                }

                // Check for attackers near flag
                for (Player online : Bukkit.getOnlinePlayers()) {
                    PlayerData data = teamManager.getPlayerData(online);
                    if (data.getTeam() == PlayerData.Team.ATTACKERS) {
                        if (online.getWorld().equals(flag.getWorld()) && 
                            online.getLocation().distance(flag) <= DETECTION_RADIUS) {
                            // Attacker detected! Trigger alarm
                            triggerAlarm(online);
                            deactivateAlarm();
                            return;
                        }
                    }
                }
            }
        };
        activeAlarm.runTaskTimer(plugin, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);

        return true;
    }

    private void triggerAlarm(Player detectedAttacker) {
        // Alert all defenders
        Title title = Title.title(
            Component.text("⚠ ALARM ⚠", NamedTextColor.RED),
            Component.text("Attacker detected near the flag!", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(1))
        );

        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerData data = teamManager.getPlayerData(online);
            if (data.getTeam() == PlayerData.Team.DEFENDERS) {
                online.showTitle(title);
                online.playSound(online.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.5f);
            }
        }
    }
}
