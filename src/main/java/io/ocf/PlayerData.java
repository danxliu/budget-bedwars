package io.ocf;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class PlayerData {
    private static NamespacedKey teamKey;
    private static NamespacedKey chatModeKey;
    private static NamespacedKey kitKey;

    private final UUID playerId;
    private final Player player;

    public enum Team {
        ATTACKERS, DEFENDERS
    }

    public enum ChatMode {
        GLOBAL, TEAM
    }

    public static void init(JavaPlugin plugin) {
        teamKey = new NamespacedKey(plugin, "team");
        chatModeKey = new NamespacedKey(plugin, "chat_mode");
        kitKey = new NamespacedKey(plugin, "kit");
    }

    public PlayerData(Player player) {
        this.player = player;
        this.playerId = player.getUniqueId();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Player getPlayer() {
        return player;
    }

    public void setTeam(Team team) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(teamKey, PersistentDataType.STRING, team.name());
    }

    public Team getTeam() {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String teamName = pdc.get(teamKey, PersistentDataType.STRING);
        if (teamName != null) {
            try {
                return Team.valueOf(teamName);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    public void setChatMode(ChatMode mode) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(chatModeKey, PersistentDataType.STRING, mode.name());
    }

    public ChatMode getChatMode() {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String modeName = pdc.get(chatModeKey, PersistentDataType.STRING);
        if (modeName != null) {
            try {
                return ChatMode.valueOf(modeName);
            } catch (IllegalArgumentException e) {
                return ChatMode.GLOBAL;
            }
        }
        return ChatMode.GLOBAL;
    }

    public void clearTeam() {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(teamKey);
    }

    public void setKit(String kitName) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(kitKey, PersistentDataType.STRING, kitName);
    }

    public String getKit() {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return pdc.get(kitKey, PersistentDataType.STRING);
    }

    public boolean hasKit() {
        return getKit() != null;
    }

    public void clearKit() {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(kitKey);
    }

    public boolean isReady() {
        return getTeam() != null && hasKit();
    }
}
