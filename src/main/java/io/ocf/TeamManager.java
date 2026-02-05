package io.ocf;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class TeamManager {
    private final Map<UUID, PlayerData> players = new HashMap<>();

    public TeamManager(JavaPlugin plugin) {
        PlayerData.init(plugin);
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
