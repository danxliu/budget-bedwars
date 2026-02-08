package io.ocf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ShopCommand implements CommandExecutor {
    private final TeamManager teamManager;
    private final ShopManager shopManager;
    private final GameManager gameManager;

    public ShopCommand(TeamManager teamManager, ShopManager shopManager, GameManager gameManager) {
        this.teamManager = teamManager;
        this.shopManager = shopManager;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (!gameManager.isRunning()) {
            player.sendMessage(Component.text("The shop is only available during an active game!", NamedTextColor.RED));
            return true;
        }

        PlayerData playerData = teamManager.getPlayerData(player);
        PlayerData.Team team = playerData.getTeam();

        if (team == null) {
            player.sendMessage(Component.text("You must join a team first! Use /team <attackers|defenders>", NamedTextColor.RED));
            return true;
        }

        shopManager.openShop(player, team);
        return true;
    }
}
