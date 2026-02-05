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

public class TeamCommand implements CommandExecutor, TabCompleter {
    private final TeamManager teamManager;
    private final GameManager gameManager;

    public TeamCommand(TeamManager teamManager, GameManager gameManager) {
        this.teamManager = teamManager;
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
            player.sendMessage(Component.text("Usage: /team <attackers|defenders>", NamedTextColor.RED));
            return true;
        }

        String teamArg = args[0].toLowerCase();
        PlayerData.Team team;

        if (teamArg.equals("attackers")) {
            team = PlayerData.Team.ATTACKERS;
        } else if (teamArg.equals("defenders")) {
            team = PlayerData.Team.DEFENDERS;
        } else {
            player.sendMessage(Component.text("Invalid team. Use 'attackers' or 'defenders'.", NamedTextColor.RED));
            return true;
        }

        PlayerData playerData = teamManager.getPlayerData(player);
        playerData.setTeam(team);

        if (team == PlayerData.Team.ATTACKERS) {
            player.sendMessage(Component.text("You have joined the ", NamedTextColor.GREEN)
                    .append(Component.text("Attackers", NamedTextColor.RED))
                    .append(Component.text(" team!", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("You have joined the ", NamedTextColor.GREEN)
                    .append(Component.text("Defenders", NamedTextColor.BLUE))
                    .append(Component.text(" team!", NamedTextColor.GREEN)));
        }

        // Check if pending player is now ready
        gameManager.checkPendingPlayer(player);

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("attackers", "defenders").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
