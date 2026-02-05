package io.ocf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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

public class GameCommand implements CommandExecutor, TabCompleter {
    private final GameManager gameManager;

    public GameCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /game <init|start|stop>", NamedTextColor.RED));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "init" -> handleInit(sender, args);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            default -> sender.sendMessage(Component.text("Unknown subcommand. Use: init, start, stop", NamedTextColor.RED));
        }

        return true;
    }

    private void handleInit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /game init <border_size>", NamedTextColor.RED));
            return;
        }

        int borderSize;
        try {
            borderSize = Integer.parseInt(args[1]);
            if (borderSize < 10) {
                sender.sendMessage(Component.text("Border size must be at least 10!", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid border size!", NamedTextColor.RED));
            return;
        }

        if (gameManager.getState() != GameManager.GameState.IDLE) {
            sender.sendMessage(Component.text("A game is already in progress! Use /game stop first.", NamedTextColor.RED));
            return;
        }

        if (gameManager.init(borderSize)) {
            Bukkit.broadcast(Component.text("Game initialized! ", NamedTextColor.GREEN)
                    .append(Component.text("World border set to " + borderSize + " blocks.", NamedTextColor.YELLOW)));
            Bukkit.broadcast(Component.text("Use /team to join a team and /kit to select a kit.", NamedTextColor.AQUA));
        } else {
            sender.sendMessage(Component.text("Failed to initialize game!", NamedTextColor.RED));
        }
    }

    private void handleStart(CommandSender sender) {
        if (gameManager.getState() != GameManager.GameState.INIT) {
            sender.sendMessage(Component.text("Game must be initialized first! Use /game init <border_size>", NamedTextColor.RED));
            return;
        }

        // Check all players are ready
        List<Player> unready = gameManager.getUnreadyPlayers();
        if (!unready.isEmpty()) {
            sender.sendMessage(Component.text("The following players haven't selected team/kit:", NamedTextColor.RED));
            for (Player player : unready) {
                sender.sendMessage(Component.text("  - " + player.getName(), NamedTextColor.YELLOW));
            }
            return;
        }

        if (gameManager.start()) {
            Bukkit.broadcast(Component.text("Game starting!", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Failed to start game!", NamedTextColor.RED));
        }
    }

    private void handleStop(CommandSender sender) {
        if (gameManager.getState() == GameManager.GameState.IDLE) {
            sender.sendMessage(Component.text("No game is currently running!", NamedTextColor.RED));
            return;
        }

        gameManager.stop();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("init", "start", "stop").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("init")) {
            return List.of("100", "200", "500");
        }
        return List.of();
    }
}
