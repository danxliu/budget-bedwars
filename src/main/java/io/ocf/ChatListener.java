package io.ocf;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {
    private final TeamManager teamManager;

    public ChatListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = teamManager.getPlayerData(player);
        PlayerData.Team team = playerData.getTeam();
        PlayerData.ChatMode chatMode = playerData.getChatMode();

        // Build the team prefix and colored name
        Component prefix;
        Component playerName;
        if (team == PlayerData.Team.ATTACKERS) {
            prefix = Component.text("[Attacker] ", NamedTextColor.RED);
            playerName = Component.text(player.getName(), NamedTextColor.RED);
        } else if (team == PlayerData.Team.DEFENDERS) {
            prefix = Component.text("[Defender] ", NamedTextColor.BLUE);
            playerName = Component.text(player.getName(), NamedTextColor.BLUE);
        } else {
            prefix = Component.empty();
            playerName = Component.text(player.getName());
        }

        // Message stays white
        Component message = event.message().color(NamedTextColor.WHITE);

        // Cancel the default chat event
        event.setCancelled(true);

        if (chatMode == PlayerData.ChatMode.TEAM && team != null) {
            // Team chat - only send to team members
            Component teamChatPrefix = Component.text("[Team] ", NamedTextColor.GRAY);
            Component fullMessage = teamChatPrefix.append(prefix).append(playerName)
                    .append(Component.text(": ", NamedTextColor.WHITE))
                    .append(message);

            for (PlayerData member : teamManager.getTeamMembers(team)) {
                member.getPlayer().sendMessage(fullMessage);
            }
            // Also log to console
            Bukkit.getConsoleSender().sendMessage(fullMessage);
        } else {
            // Global chat - send to all players
            Component fullMessage = prefix.append(playerName)
                    .append(Component.text(": ", NamedTextColor.WHITE))
                    .append(message);

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.sendMessage(fullMessage);
            }
            // Also log to console
            Bukkit.getConsoleSender().sendMessage(fullMessage);
        }
    }
}
