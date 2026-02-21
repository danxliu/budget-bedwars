package io.ocf;

import io.ocf.items.AlarmItem;
import io.ocf.items.FlagCompassItem;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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
    private final Set<Location> goldBlocks = new HashSet<>();
    private Location attackerSpawnCenter;
    private int attackerSpawnRadius;
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private final Map<UUID, BukkitRunnable> respawnTasks = new HashMap<>();

    // Zone effect levels (upgradeable for future defender purchases)
    private int defenderRegenLevel = 1;
    private int defenderSpawnRadius;

    // Zone tasks
    private BukkitTask beamParticleTask;
    private BukkitTask zoneEffectsTask;
    private BukkitTask attackerSpawnerTask;
    private BukkitTask defenderSpawnerTask;

    // Timer
    private BossBar timerBar;
    private int remainingSeconds;
    private int totalSeconds;
    private BukkitTask timerTask;

    private long lastGoldNotificationTime = 0;

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

        // Calculate attacker spawn (opposite quadrant)
        attackerSpawnCenter = calculateOppositeQuadrant(flagLocation);

        // Get config values
        defenderSpawnRadius = plugin.getConfig().getInt("game.defender_spawn_radius", 20);
        attackerSpawnRadius = plugin.getConfig().getInt("game.attacker_spawn_radius", 50);

        // Create 5x5 stone brick platform at attacker spawn center
        createPlatform(attackerSpawnCenter);

        // Teleport players to their spawns
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = teamManager.getPlayerData(player);
            if (data.getTeam() == PlayerData.Team.DEFENDERS) {
                Location spawn = getRandomSpawnAround(flagLocation, defenderSpawnRadius);
                player.teleport(spawn);
            } else if (data.getTeam() == PlayerData.Team.ATTACKERS) {
                Location spawn = getSpawnOnPerimeter(attackerSpawnCenter, attackerSpawnRadius);
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

        int selectedX = 0, selectedZ = 0;
        boolean foundValid = false;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Random location within the chosen quadrant
            int x = (int) center.getX() + xSign * (random.nextInt(halfSize / 2) + halfSize / 4);
            int z = (int) center.getZ() + zSign * (random.nextInt(halfSize / 2) + halfSize / 4);

            Block highestBlock = gameWorld.getHighestBlockAt(x, z);

            if (isValidSpawnBlock(highestBlock.getType())) {
                selectedX = x;
                selectedZ = z;
                foundValid = true;
                break;
            }
        }

        // Fallback location if no valid spot found
        if (!foundValid) {
            selectedX = (int) center.getX() + xSign * (halfSize / 2);
            selectedZ = (int) center.getZ() + zSign * (halfSize / 2);
        }

        Block highestBlock = gameWorld.getHighestBlockAt(selectedX, selectedZ);
        
        // Move to sky platform (ground + offset, capped)
        int heightOffset = plugin.getConfig().getInt("game.sky_platform_height_offset", 50);
        int y = Math.min(highestBlock.getY() + heightOffset, gameWorld.getMaxHeight() - 5);
        Location platformLoc = new Location(gameWorld, selectedX, y, selectedZ);

        // Clear gold blocks set
        goldBlocks.clear();

        // Create 5x5 stone brick platform
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                platformLoc.clone().add(dx, 0, dz).getBlock().setType(Material.STONE_BRICKS);
            }
        }

        // Create 3x3 gold blocks on top
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location goldLoc = platformLoc.clone().add(dx, 1, dz);
                goldLoc.getBlock().setType(Material.GOLD_BLOCK);
                goldBlocks.add(goldLoc.getBlock().getLocation());
            }
        }

        return platformLoc.add(0, 1, 0); // Center of the 3x3 gold area
    }

    private boolean isValidSpawnBlock(Material type) {
        // Avoid water, lava, and tree blocks (leaves and logs)
        if (type == Material.WATER || type == Material.LAVA) {
            return false;
        }
        String name = type.name();
        if (name.contains("LEAVES") || name.contains("LOG") || name.contains("WOOD")) {
            return false;
        }
        return true;
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

    private Location getSpawnOnPerimeter(Location center, int radius) {
        Random random = new Random();
        // Try multiple angles around the perimeter
        double startAngle = random.nextDouble() * 2 * Math.PI;
        for (int attempt = 0; attempt < 36; attempt++) {
            double angle = startAngle + (attempt * Math.PI / 18); // Try every 10 degrees
            int x = (int) (center.getX() + radius * Math.cos(angle));
            int z = (int) (center.getZ() + radius * Math.sin(angle));
            Block highestBlock = gameWorld.getHighestBlockAt(x, z);

            if (highestBlock.getType() != Material.WATER && highestBlock.getType() != Material.LAVA) {
                return highestBlock.getLocation().add(0.5, 1, 0.5);
            }
        }
        // Fallback: create platform at first angle
        int x = (int) (center.getX() + radius * Math.cos(startAngle));
        int z = (int) (center.getZ() + radius * Math.sin(startAngle));
        Block highestBlock = gameWorld.getHighestBlockAt(x, z);
        Location platformLoc = highestBlock.getLocation().add(0, 1, 0);
        createPlatform(platformLoc);
        return platformLoc.add(0, 1, 0);
    }

    private void createPlatform(Location center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                center.clone().add(dx, -1, dz).getBlock().setType(Material.STONE_BRICKS);
            }
        }
    }

    private void startZoneTasks() {
        // Beam particle task - spawn END_ROD particles above flag
        beamParticleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (flagLocation == null || gameWorld == null) return;
                
                // Spawn particles in a column above the flag (denser, more particles)
                for (int y = 0; y < 60; y++) {
                    Location particleLoc = flagLocation.clone().add(0.5, y, 0.5);
                    gameWorld.spawnParticle(Particle.END_ROD, particleLoc, 3, 0.1, 0.1, 0.1, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);

        // Zone effects task - regen for defenders near flag + boundary particles
        zoneEffectsTask = new BukkitRunnable() {
            int tickCounter = 0;

            @Override
            public void run() {
                if (flagLocation == null || gameWorld == null) return;

                // Apply regen to defenders in zone around flag
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = teamManager.getPlayerData(player);
                    if (data.getTeam() != PlayerData.Team.DEFENDERS) continue;
                    if (!player.getWorld().equals(gameWorld)) continue;

                    double distance = player.getLocation().distance(flagLocation);
                    if (distance <= defenderSpawnRadius) {
                        // Apply regeneration with duration slightly longer than check interval
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.REGENERATION,
                                40, // 2 seconds (longer than 20 tick interval)
                                defenderRegenLevel - 1, // Level is 0-indexed
                                true, // Ambient
                                true, // Show particles
                                true  // Show icon
                        ));
                    }
                }

                // Spawn boundary particles every 2 seconds (40 ticks)
                tickCounter++;
                if (tickCounter >= 2) {
                    tickCounter = 0;
                    spawnBoundaryParticles();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnBoundaryParticles() {
        if (flagLocation == null || gameWorld == null) return;

        // Spawn particles around the flag zone perimeter (48 points, more particles each)
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI / 24;
            double x = flagLocation.getX() + defenderSpawnRadius * Math.cos(angle);
            double z = flagLocation.getZ() + defenderSpawnRadius * Math.sin(angle);
            
            // Spawn at the platform level
            Location particleLoc = new Location(gameWorld, x, flagLocation.getY() + 1, z);
            gameWorld.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 8, 0.3, 0.8, 0.3, 0);
        }
    }

    private void stopZoneTasks() {
        if (beamParticleTask != null) {
            beamParticleTask.cancel();
            beamParticleTask = null;
        }
        if (zoneEffectsTask != null) {
            zoneEffectsTask.cancel();
            zoneEffectsTask = null;
        }
    }

    private void startResourceSpawners() {
        int attackerRate = plugin.getConfig().getInt("game.attacker_copper_rate_seconds", 5);
        int defenderRate = plugin.getConfig().getInt("game.defender_copper_rate_seconds", 5);

        attackerSpawnerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.RUNNING) {
                    this.cancel();
                    return;
                }
                for (PlayerData data : teamManager.getTeamMembers(PlayerData.Team.ATTACKERS)) {
                    Player p = data.getPlayer();
                    if (p.isOnline()) {
                        p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.COPPER_INGOT));
                        p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.0f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, attackerRate * 20L);

        defenderSpawnerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != GameState.RUNNING) {
                    this.cancel();
                    return;
                }
                if (flagLocation != null && flagLocation.getWorld() != null) {
                    flagLocation.getWorld().dropItemNaturally(flagLocation, new org.bukkit.inventory.ItemStack(Material.COPPER_INGOT));
                    flagLocation.getWorld().playSound(flagLocation, Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, defenderRate * 20L);
    }

    private void stopResourceSpawners() {
        if (attackerSpawnerTask != null) {
            attackerSpawnerTask.cancel();
            attackerSpawnerTask = null;
        }
        if (defenderSpawnerTask != null) {
            defenderSpawnerTask.cancel();
            defenderSpawnerTask = null;
        }
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

                    // Start compass update task
                    FlagCompassItem.startUpdateTask();

                    // Start zone tasks (beam particles, regen zone)
                    startZoneTasks();

                    // Start resource spawners
                    startResourceSpawners();

                    // Start game timer
                    startTimer();

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startTimer() {
        int durationMinutes = plugin.getConfig().getInt("game.duration_minutes", 30);
        totalSeconds = durationMinutes * 60;
        remainingSeconds = totalSeconds;

        timerBar = BossBar.bossBar(
                getTimerTitle(),
                1.0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(timerBar);
        }

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                remainingSeconds--;
                if (remainingSeconds <= 0) {
                    handleTimeExpired();
                    this.cancel();
                    return;
                }
                updateTimerBar();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void updateTimerBar() {
        if (timerBar == null) return;
        timerBar.name(getTimerTitle());
        timerBar.progress((float) remainingSeconds / totalSeconds);
    }

    private Component getTimerTitle() {
        return Component.text("Time Remaining: ", NamedTextColor.WHITE)
                .append(Component.text(formatTime(remainingSeconds), NamedTextColor.YELLOW));
    }

    private String formatTime(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private void handleTimeExpired() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
            Title title = Title.title(
                    Component.text("DEFENDERS WIN!", NamedTextColor.BLUE),
                    Component.text("Time has expired!", NamedTextColor.GOLD),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(2))
            );
            player.showTitle(title);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                stop();
            }
        }.runTaskLater(plugin, 100L); // 5 seconds delay
    }

    public void handlePlayerDeath(Player player) {
        if (state != GameState.RUNNING) return;

        PlayerData data = teamManager.getPlayerData(player);
        String configKey = (data.getTeam() == PlayerData.Team.ATTACKERS)
                ? "game.attacker_respawn_cooldown_seconds"
                : "game.defender_respawn_cooldown_seconds";
        int cooldown = plugin.getConfig().getInt(configKey, 10);

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
        Location spawn;
        if (data.getTeam() == PlayerData.Team.DEFENDERS) {
            spawn = getRandomSpawnAround(flagLocation, defenderSpawnRadius);
        } else {
            // Attackers respawn at their bed spawn (natural spawnpoint)
            spawn = player.getRespawnLocation();
            if (spawn == null) {
                spawn = getSpawnOnPerimeter(attackerSpawnCenter, attackerSpawnRadius);
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

    public void notifyGoldAttacked(Player attacker, Block block) {
        if (!goldBlocks.contains(block.getLocation())) return;

        // Add 3 second cooldown to prevent spam
        long now = System.currentTimeMillis();
        if (now - lastGoldNotificationTime < 3000) return;
        lastGoldNotificationTime = now;

        // Broadcast notification
        Component msg = Component.text(attacker.getName() + " is attacking a gold block!", NamedTextColor.RED);
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.5f);
            
            // Special notification for defenders
            PlayerData data = teamManager.getPlayerData(p);
            if (data.getTeam() == PlayerData.Team.DEFENDERS) {
                p.showTitle(Title.title(
                        Component.text("GOAL UNDER ATTACK!", NamedTextColor.RED),
                        Component.text(attacker.getName() + " is breaking gold!", NamedTextColor.GOLD),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500))
                ));
            }
        }
    }

    public void handleGoldBroken(Player breaker, Block block) {
        if (!goldBlocks.contains(block.getLocation())) return;

        goldBlocks.remove(block.getLocation());
        int remaining = goldBlocks.size();

        // Broadcast remaining count
        Component msg = Component.text("A gold block has been destroyed! ", NamedTextColor.YELLOW)
                .append(Component.text("(" + remaining + "/9 remaining)", NamedTextColor.GOLD));
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.BLOCK_METAL_BREAK, 1.0f, 1.0f);
        }

        if (goldBlocks.isEmpty()) {
            handleAttackersWin(breaker);
        }
    }

    public void handleKitChange(Player player, String kitName) {
        PlayerData data = teamManager.getPlayerData(player);
        if (!kitManager.isValidKitForPlayer(player, kitName, data.getTeam())) {
            return;
        }

        if (state == GameState.RUNNING) {
            data.setKit(kitName.toLowerCase());
            player.sendMessage(Component.text("Kit selection updated to '", NamedTextColor.GREEN)
                    .append(Component.text(kitName, NamedTextColor.GOLD))
                    .append(Component.text("'. It will be applied on your next respawn!", NamedTextColor.GREEN)));
        } else {
            // Apply immediately if not running
            kitManager.applyKit(player, kitName, data);
        }
    }

    private void handleAttackersWin(Player breaker) {
        // Play ender dragon death sound globally
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
            Title title = Title.title(
                    Component.text("ATTACKERS WIN!", NamedTextColor.RED),
                    Component.text(breaker.getName() + " destroyed all gold blocks!", NamedTextColor.GOLD),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(2))
            );
            player.showTitle(title);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                stop();
            }
        }.runTaskLater(plugin, 100L); // 5 seconds delay
    }

    public void handleLateJoin(Player player) {
        if (state == GameState.RUNNING || state == GameState.COUNTDOWN) {
            PlayerData data = teamManager.getPlayerData(player);
            if (data.isReady()) {
                if (state == GameState.RUNNING) {
                    respawnPlayer(player, data);
                } else {
                    // In countdown - freeze and set to adventure
                    Location spawn;
                    if (data.getTeam() == PlayerData.Team.DEFENDERS) {
                        spawn = getRandomSpawnAround(flagLocation, defenderSpawnRadius);
                    } else {
                        spawn = getSpawnOnPerimeter(attackerSpawnCenter, attackerSpawnRadius);
                        player.setRespawnLocation(spawn, true);
                    }
                    player.teleport(spawn);
                    player.setGameMode(GameMode.ADVENTURE);
                    kitManager.applyKit(player, data.getKit(), data);
                    frozenPlayers.add(player.getUniqueId());
                }
            } else {
                player.setGameMode(GameMode.SPECTATOR);
                pendingPlayers.add(player.getUniqueId());
                player.sendMessage(Component.text("Game in progress! Use /team and /kit to join.", NamedTextColor.YELLOW));
            }

            if (timerBar != null) {
                player.showBossBar(timerBar);
            }
        }
    }

    public void checkPendingPlayer(Player player) {
        if (!pendingPlayers.contains(player.getUniqueId())) return;

        PlayerData data = teamManager.getPlayerData(player);
        if (data.isReady()) {
            pendingPlayers.remove(player.getUniqueId());

            Location spawn;
            if (data.getTeam() == PlayerData.Team.DEFENDERS) {
                spawn = getRandomSpawnAround(flagLocation, defenderSpawnRadius);
            } else {
                spawn = getSpawnOnPerimeter(attackerSpawnCenter, attackerSpawnRadius);
                player.setRespawnLocation(spawn, true);
            }

            player.teleport(spawn);
            
            if (state == GameState.COUNTDOWN) {
                player.setGameMode(GameMode.ADVENTURE);
                frozenPlayers.add(player.getUniqueId());
            } else {
                player.setGameMode(GameMode.SURVIVAL);
            }
            
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

    public Set<Location> getGoldBlocks() {
        return goldBlocks;
    }

    public void stop() {
        // Cancel timer task
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        // Remove boss bar from all players
        if (timerBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(timerBar);
            }
            timerBar = null;
        }

        // Cancel all respawn tasks
        for (BukkitRunnable task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
        frozenPlayers.clear();
        pendingPlayers.clear();

        // Deactivate any active alarm
        AlarmItem.deactivateAlarm();

        // Stop compass update task
        FlagCompassItem.stopUpdateTask();

        // Stop zone tasks
        stopZoneTasks();

        // Stop resource spawners
        stopResourceSpawners();

        // Clear scoreboard teams
        teamManager.clearScoreboardTeams();

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
        goldBlocks.clear();
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
