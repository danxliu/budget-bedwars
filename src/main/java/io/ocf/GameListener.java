package io.ocf;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class GameListener implements Listener {
    private final GameManager gameManager;
    private final TeamManager teamManager;

    public GameListener(GameManager gameManager, TeamManager teamManager) {
        this.gameManager = gameManager;
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (gameManager.isInInitPhase()) {
            // Reset player during init phase
            gameManager.resetPlayer(player);
        } else if (gameManager.isRunning() || gameManager.isInCountdown()) {
            // Late join during game
            gameManager.handleLateJoin(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Prevent PvP during init phase and countdown
        if (gameManager.isInInitPhase() || gameManager.isInCountdown()) {
            if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // True freeze during countdown - no movement or looking
        if (gameManager.isFrozen(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            // Cancel any movement (position or rotation)
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()
                    || from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Prevent block breaking during countdown
        if (gameManager.isInCountdown() || gameManager.isFrozen(player)) {
            event.setCancelled(true);
            return;
        }

        // Check for flag break
        if (gameManager.isRunning()) {
            Location flagLoc = gameManager.getFlagLocation();
            if (flagLoc != null && event.getBlock().getLocation().equals(flagLoc.getBlock().getLocation())) {
                if (event.getBlock().getType() == Material.ANCIENT_DEBRIS) {
                    PlayerData data = teamManager.getPlayerData(player);
                    if (data.getTeam() == PlayerData.Team.ATTACKERS) {
                        gameManager.handleFlagBroken(player);
                    } else {
                        // Defenders can't break their own flag
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (gameManager.isRunning()) {
            Player player = event.getEntity();

            // Cancel drops and normal death behavior
            event.getDrops().clear();
            event.setDroppedExp(0);

            // Handle respawn through GameManager
            gameManager.handlePlayerDeath(player);
        }
    }
}
