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

import java.util.List;
import java.util.stream.Collectors;

public class KitCommand implements CommandExecutor, TabCompleter {
    private final TeamManager teamManager;
    private final KitManager kitManager;
    private final GameManager gameManager;

    public KitCommand(TeamManager teamManager, KitManager kitManager, GameManager gameManager) {
        this.teamManager = teamManager;
        this.kitManager = kitManager;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /kit <kit_name>", NamedTextColor.RED));
            player.sendMessage(Component.text("Available kits: " + String.join(", ", kitManager.getKitNames()), NamedTextColor.GRAY));
            return true;
        }

        PlayerData playerData = teamManager.getPlayerData(player);
        if (playerData.getTeam() == null) {
            player.sendMessage(Component.text("You must join a team first! Use /team <attackers|defenders>", NamedTextColor.RED));
            return true;
        }

        gameManager.handleKitChange(player, args[0]);
        // Check if pending player is now ready (e.g. they chose their first kit)
        gameManager.checkPendingPlayer(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            PlayerData playerData = teamManager.getPlayerData(player);
            PlayerData.Team team = playerData.getTeam();
            return kitManager.getKitNamesForTeam(team).stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
