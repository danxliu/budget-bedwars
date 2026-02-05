package io.ocf;

import io.ocf.items.AlarmItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.time.Duration;
import java.util.*;

public class GameManager {
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final KitManager kitManager;
    private GameState state = GameState.IDLE;
    private World gameWorld;
    private World lobbyWorld;
    private int borderSize;
    private Location flagLocation;
    private Location attackerSpawnCenter;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private final Map<UUID, BukkitRunnable> respawnTasks = new HashMap<>();

    public enum GameState {
        IDLE,       // No game active
        INIT,       // Game initialized, players can pick teams/kits
        COUNTDOWN,  // Game starting, players frozen
        RUNNING,    // Game in progress
    }

    public GameManager(JavaPlugin plugin, TeamManager teamManager, KitManager kitManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.kitManager = kitManager;
    }

    public GameState getState() {
        return state;
    }

    public boolean init(int borderSize) {
        if (state != GameState.IDLE) {
            return false;
        }

        this.borderSize = borderSize;
        
        // Store the lobby world (first/main world)
        this.lobbyWorld = Bukkit.getWorlds().get(0);
        
        // Get config values
        String worldName = plugin.getConfig().getString("game.world_name", "ctf_arena");
        String worldTypeStr = plugin.getConfig().getString("game.world_type", "NORMAL");
        String seedStr = plugin.getConfig().getString("game.world_seed", "");
        
        // Delete existing game world if present
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            // Teleport any players in that world to lobby first
            for (Player player : existingWorld.getPlayers()) {
                player.teleport(lobbyWorld.getSpawnLocation());
            }
            Bukkit.unloadWorld(existingWorld, false);
            deleteWorldFolder(new File(Bukkit.getWorldContainer(), worldName));
        }
        
        // Create new world
        WorldCreator creator = new WorldCreator(worldName);
        
        // Set world type
        try {
            WorldType worldType = WorldType.valueOf(worldTypeStr.toUpperCase());
            creator.type(worldType);
        } catch (IllegalArgumentException e) {
            creator.type(WorldType.NORMAL);
        }
        
        // Set seed if provided
        if (!seedStr.isEmpty()) {
            try {
                creator.seed(Long.parseLong(seedStr));
            } catch (NumberFormatException e) {
                creator.seed(seedStr.hashCode());
            }
        } else {
            creator.seed(new Random().nextLong());
        }
        
        // Create the world
        Bukkit.broadcast(Component.text("Generating game world...", NamedTextColor.YELLOW));
        this.gameWorld = creator.createWorld();
        
        if (gameWorld == null) {
            plugin.getLogger().severe("Failed to create game world!");
            return false;
        }

        // Set world border
        WorldBorder border = gameWorld.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(borderSize);

        gameWorld.setGameRule(GameRules.ADVANCE_TIME, false);
        gameWorld.setGameRule(GameRules.ADVANCE_WEATHER, false);
        gameWorld.setDifficulty(Difficulty.PEACEFUL);
        gameWorld.setTime(6000); // Set to noon
        
        // Teleport all players to game world and reset them
        Location spawnLoc = new Location(gameWorld, 0.5, gameWorld.getHighestBlockYAt(0, 0) + 1, 0.5);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLoc);
            resetPlayer(player);
        }

        this.state = GameState.INIT;
        return true;
    }

    public void resetPlayer(Player player) {
        // Clear inventory
        player.getInventory().clear();

        // Reset health/hunger/saturation
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        // Set to adventure mode to prevent block breaking
        player.setGameMode(GameMode.ADVENTURE);

        // Clear team data from PDC
        PlayerData playerData = teamManager.getPlayerData(player);
        playerData.clearTeam();
        playerData.clearKit();
        playerData.setChatMode(PlayerData.ChatMode.GLOBAL);
    }

    public List<Player> getUnreadyPlayers() {
        List<Player> unready = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = teamManager.getPlayerData(player);
            if (!data.isReady()) {
                unready.add(player);
            }
        }
        return unready;
    }

    public boolean start() {
        if (state != GameState.INIT) {
            return false;
        }

        // Find flag location
        flagLocation = findFlagLocation();
        if (flagLocation == null) {
            return false;
        }

        // Place the flag (ancient_debris)
        flagLocation.getBlock().setType(Material.ANCIENT_DEBRIS);

        // Calculate attacker spawn (opposite quadrant)
        attackerSpawnCenter = calculateOppositeQuadrant(flagLocation);

        // Get config values
        int defenderRadius = plugin.getConfig().getInt("game.defender_spawn_radius", 20);
        int attackerRadius = plugin.getConfig().getInt("game.attacker_spawn_radius", 50);

        // Teleport players to their spawns
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = teamManager.getPlayerData(player);
            if (data.getTeam() == PlayerData.Team.DEFENDERS) {
                Location spawn = getRandomSpawnAround(flagLocation, defenderRadius);
                player.teleport(spawn);
            } else if (data.getTeam() == PlayerData.Team.ATTACKERS) {
                Location spawn = getRandomSpawnAround(attackerSpawnCenter, attackerRadius);
                player.teleport(spawn);
                player.setRespawnLocation(spawn, true);
            }
            // Freeze player
            frozenPlayers.add(player.getUniqueId());
        }

        // Set difficulty to normal for PvP
        gameWorld.setDifficulty(Difficulty.NORMAL);

        // Start countdown
        state = GameState.COUNTDOWN;
        startCountdown();

        return true;
    }

    private Location findFlagLocation() {
        int maxAttempts = plugin.getConfig().getInt("game.flag_location_max_attempts", 100);
        Location center = gameWorld.getWorldBorder().getCenter();
        int halfSize = borderSize / 2;
        Random random = new Random();

        // Choose a random quadrant
        int quadrant = random.nextInt(4);
        int xSign = (quadrant % 2 == 0) ? 1 : -1;
        int zSign = (quadrant < 2) ? 1 : -1;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Random location within the chosen quadrant
            int x = (int) center.getX() + xSign * (random.nextInt(halfSize / 2) + halfSize / 4);
            int z = (int) center.getZ() + zSign * (random.nextInt(halfSize / 2) + halfSize / 4);

            Block highestBlock = gameWorld.getHighestBlockAt(x, z);
            Material type = highestBlock.getType();

            // Check if it's not water
            if (type != Material.WATER && type != Material.LAVA) {
                return highestBlock.getLocation().add(0, 1, 0);
            }
        }

        // Fallback: create dirt platform above water
        int x = (int) center.getX() + xSign * (halfSize / 2);
        int z = (int) center.getZ() + zSign * (halfSize / 2);
        Block highestBlock = gameWorld.getHighestBlockAt(x, z);
        Location platformLoc = highestBlock.getLocation().add(0, 1, 0);

        // Create 5x5 dirt platform
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                platformLoc.clone().add(dx, 0, dz).getBlock().setType(Material.DIRT);
            }
        }

        return platformLoc.add(0, 1, 0);
    }

    private Location calculateOppositeQuadrant(Location flagLoc) {
        Location center = gameWorld.getWorldBorder().getCenter();
        int halfSize = borderSize / 2;

        // Determine which quadrant the flag is in, then go to opposite
        int xSign = (flagLoc.getX() > center.getX()) ? -1 : 1;
        int zSign = (flagLoc.getZ() > center.getZ()) ? -1 : 1;

        int x = (int) center.getX() + xSign * (halfSize / 2);
        int z = (int) center.getZ() + zSign * (halfSize / 2);

        Block highestBlock = gameWorld.getHighestBlockAt(x, z);
        return highestBlock.getLocation().add(0, 1, 0);
    }

    private Location getRandomSpawnAround(Location center, int radius) {
        Random random = new Random();
        for (int attempt = 0; attempt < 50; attempt++) {
            int x = (int) center.getX() + random.nextInt(radius * 2) - radius;
            int z = (int) center.getZ() + random.nextInt(radius * 2) - radius;
            Block highestBlock = gameWorld.getHighestBlockAt(x, z);

            if (highestBlock.getType() != Material.WATER && highestBlock.getType() != Material.LAVA) {
                return highestBlock.getLocation().add(0.5, 1, 0.5);
            }
        }
        // Fallback to center
        return center.clone().add(0.5, 1, 0.5);
    }

    private void startCountdown() {
        int countdownSeconds = plugin.getConfig().getInt("game.countdown_seconds", 5);

        new BukkitRunnable() {
            int count = countdownSeconds;

            @Override
            public void run() {
                if (count > 0) {
                    // Display countdown
                    Title title = Title.title(
                            Component.text(String.valueOf(count), NamedTextColor.YELLOW),
                            Component.text("Get ready!", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)
                    );
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.showTitle(title);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    count--;
                } else {
                    // GO!
                    Title title = Title.title(
                            Component.text("GO!", NamedTextColor.GREEN),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500))
                    );
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.showTitle(title);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                        player.setGameMode(GameMode.SURVIVAL);
                    }

                    // Unfreeze all players
                    frozenPlayers.clear();
                    state = GameState.RUNNING;

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void handlePlayerDeath(Player player) {
        if (state != GameState.RUNNING) return;

        PlayerData data = teamManager.getPlayerData(player);
        int cooldown = plugin.getConfig().getInt("game.respawn_cooldown_seconds", 10);

        // Set to spectator and freeze
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();

        // Start respawn countdown
        BukkitRunnable task = new BukkitRunnable() {
            int count = cooldown;

            @Override
            public void run() {
                if (count > 0) {
                    Title title = Title.title(
                            Component.text("Respawning in " + count + "s", NamedTextColor.RED),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)
                    );
                    player.showTitle(title);
                    count--;
                } else {
                    respawnPlayer(player, data);
                    respawnTasks.remove(player.getUniqueId());
                    cancel();
                }
            }
        };
        respawnTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    private void respawnPlayer(Player player, PlayerData data) {
        int defenderRadius = plugin.getConfig().getInt("game.defender_spawn_radius", 20);

        Location spawn;
        if (data.getTeam() == PlayerData.Team.DEFENDERS) {
            spawn = getRandomSpawnAround(flagLocation, defenderRadius);
        } else {
            // Attackers respawn at their bed spawn (natural spawnpoint)
            spawn = player.getRespawnLocation();
            if (spawn == null) {
                spawn = getRandomSpawnAround(attackerSpawnCenter, 
                        plugin.getConfig().getInt("game.attacker_spawn_radius", 50));
            }
        }

        player.teleport(spawn);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        // Re-apply kit
        String kitName = data.getKit();
        if (kitName != null) {
            kitManager.applyKit(player, kitName, data);
        }
    }

    public void handleFlagBroken(Player breaker) {
        // Play ender dragon death sound globally
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
            Title title = Title.title(
                    Component.text("ATTACKERS WIN!", NamedTextColor.RED),
                    Component.text(breaker.getName() + " captured the flag!", NamedTextColor.GOLD),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(2))
            );
            player.showTitle(title);
        }
    }

    public void handleLateJoin(Player player) {
        if (state == GameState.RUNNING || state == GameState.COUNTDOWN) {
            player.setGameMode(GameMode.SPECTATOR);
            pendingPlayers.add(player.getUniqueId());
            player.sendMessage(Component.text("Game in progress! Use /team and /kit to join.", NamedTextColor.YELLOW));
        }
    }

    public void checkPendingPlayer(Player player) {
        if (!pendingPlayers.contains(player.getUniqueId())) return;

        PlayerData data = teamManager.getPlayerData(player);
        if (data.isReady()) {
            pendingPlayers.remove(player.getUniqueId());

            int defenderRadius = plugin.getConfig().getInt("game.defender_spawn_radius", 20);
            int attackerRadius = plugin.getConfig().getInt("game.attacker_spawn_radius", 50);

            Location spawn;
            if (data.getTeam() == PlayerData.Team.DEFENDERS) {
                spawn = getRandomSpawnAround(flagLocation, defenderRadius);
            } else {
                spawn = getRandomSpawnAround(attackerSpawnCenter, attackerRadius);
                player.setRespawnLocation(spawn, true);
            }

            player.teleport(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            kitManager.applyKit(player, data.getKit(), data);
        }
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    public boolean isPending(Player player) {
        return pendingPlayers.contains(player.getUniqueId());
    }

    public boolean isInInitPhase() {
        return state == GameState.INIT;
    }

    public boolean isRunning() {
        return state == GameState.RUNNING;
    }

    public boolean isInCountdown() {
        return state == GameState.COUNTDOWN;
    }

    public Location getFlagLocation() {
        return flagLocation;
    }

    public void stop() {
        // Cancel all respawn tasks
        for (BukkitRunnable task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
        frozenPlayers.clear();
        pendingPlayers.clear();

        // Deactivate any active alarm
        AlarmItem.deactivateAlarm();

        // Teleport all players back to lobby
        if (lobbyWorld != null) {
            Location lobbySpawn = lobbyWorld.getSpawnLocation();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.teleport(lobbySpawn);
                player.setGameMode(GameMode.ADVENTURE);
            }
        }

        // Unload and delete game world
        if (gameWorld != null) {
            String worldName = gameWorld.getName();
            Bukkit.unloadWorld(gameWorld, false);
            deleteWorldFolder(new File(Bukkit.getWorldContainer(), worldName));
            gameWorld = null;
        }

        flagLocation = null;
        attackerSpawnCenter = null;
        state = GameState.IDLE;

        Bukkit.broadcast(Component.text("Game stopped! Returned to lobby.", NamedTextColor.YELLOW));
    }

    private void deleteWorldFolder(File folder) {
        if (folder.exists()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteWorldFolder(file);
                    } else {
                        file.delete();
                    }
                }
            }
            folder.delete();
        }
    }

    public World getGameWorld() {
        return gameWorld;
    }

    public World getLobbyWorld() {
        return lobbyWorld;
    }
}
