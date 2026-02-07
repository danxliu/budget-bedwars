package io.ocf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class TeamManager {
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Scoreboard scoreboard;
    private Team attackersTeam;
    private Team defendersTeam;

    public TeamManager(JavaPlugin plugin) {
        PlayerData.init(plugin);
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        setupScoreboardTeams();
    }

    private void setupScoreboardTeams() {
        // Create or get Attackers team
        attackersTeam = scoreboard.getTeam("CTF_Attackers");
        if (attackersTeam == null) {
            attackersTeam = scoreboard.registerNewTeam("CTF_Attackers");
        }
        attackersTeam.prefix(Component.text("[Attacker] ", NamedTextColor.RED));
        attackersTeam.color(NamedTextColor.RED);
        attackersTeam.setAllowFriendlyFire(false);

        // Create or get Defenders team
        defendersTeam = scoreboard.getTeam("CTF_Defenders");
        if (defendersTeam == null) {
            defendersTeam = scoreboard.registerNewTeam("CTF_Defenders");
        }
        defendersTeam.prefix(Component.text("[Defender] ", NamedTextColor.BLUE));
        defendersTeam.color(NamedTextColor.BLUE);
        defendersTeam.setAllowFriendlyFire(false);
    }

    public void addPlayerToScoreboardTeam(Player player, PlayerData.Team team) {
        // Remove from any existing team first
        removePlayerFromScoreboardTeams(player);

        // Add to appropriate team
        if (team == PlayerData.Team.ATTACKERS) {
            attackersTeam.addPlayer(player);
        } else if (team == PlayerData.Team.DEFENDERS) {
            defendersTeam.addPlayer(player);
        }
    }

    public void removePlayerFromScoreboardTeams(Player player) {
        attackersTeam.removePlayer(player);
        defendersTeam.removePlayer(player);
    }

    public void clearScoreboardTeams() {
        for (String entry : new HashSet<>(attackersTeam.getEntries())) {
            attackersTeam.removeEntry(entry);
        }
        for (String entry : new HashSet<>(defendersTeam.getEntries())) {
            defendersTeam.removeEntry(entry);
        }
    }

    public PlayerData getPlayerData(Player player) {
        // Always update the player reference to ensure we have the current one
        PlayerData data = players.get(player.getUniqueId());
        if (data == null) {
            data = new PlayerData(player);
            players.put(player.getUniqueId(), data);
        } else {
            data.updatePlayer(player);
        }
        return data;
    }

    public void removePlayer(UUID playerId) {
        players.remove(playerId);
    }

    public void clearAllPlayers() {
        players.clear();
    }

    public Set<PlayerData> getTeamMembers(PlayerData.Team team) {
        Set<PlayerData> members = new HashSet<>();
        for (PlayerData data : players.values()) {
            if (data.getTeam() == team && data.getPlayer().isOnline()) {
                members.add(data);
            }
        }
        return members;
    }
}
