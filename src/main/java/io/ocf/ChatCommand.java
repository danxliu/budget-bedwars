package io.ocf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChatCommand implements CommandExecutor, TabCompleter {
    private final TeamManager teamManager;

    public ChatCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /chat <global|team>", NamedTextColor.RED));
            return true;
        }

        PlayerData playerData = teamManager.getPlayerData(player);
        String modeArg = args[0].toLowerCase();
        PlayerData.ChatMode mode;

        if (modeArg.equals("global")) {
            mode = PlayerData.ChatMode.GLOBAL;
        } else if (modeArg.equals("team")) {
            if (playerData.getTeam() == null) {
                player.sendMessage(Component.text("You must join a team first! Use /team <attackers|defenders>", NamedTextColor.RED));
                return true;
            }
            mode = PlayerData.ChatMode.TEAM;
        } else {
            player.sendMessage(Component.text("Invalid mode. Use 'global' or 'team'.", NamedTextColor.RED));
            return true;
        }

        playerData.setChatMode(mode);

        if (mode == PlayerData.ChatMode.GLOBAL) {
            player.sendMessage(Component.text("Chat mode set to ", NamedTextColor.GREEN)
                    .append(Component.text("Global", NamedTextColor.GOLD)));
        } else {
            player.sendMessage(Component.text("Chat mode set to ", NamedTextColor.GREEN)
                    .append(Component.text("Team", NamedTextColor.AQUA)));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("global", "team").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
