package io.ocf;

import io.ocf.items.CustomItemManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GiveItemCommand implements CommandExecutor, TabCompleter {
    private final CustomItemManager customItemManager;
    private static final List<String> ITEM_NAMES = List.of("fireball", "tnt", "alarm");

    public GiveItemCommand(CustomItemManager customItemManager) {
        this.customItemManager = customItemManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("ctf.giveitem")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /giveitem <fireball|tnt|alarm> [amount]");
            return true;
        }

        String itemName = args[0].toLowerCase();
        int amount = 1;

        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 1 || amount > 64) {
                    player.sendMessage("§cAmount must be between 1 and 64.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid amount: " + args[1]);
                return true;
            }
        }

        ItemStack item = switch (itemName) {
            case "fireball" -> customItemManager.createFireball(amount);
            case "tnt" -> customItemManager.createInstantTNT(amount);
            case "alarm" -> customItemManager.createAlarm(amount);
            default -> null;
        };

        if (item == null) {
            player.sendMessage("§cUnknown item: " + itemName);
            player.sendMessage("§7Available items: fireball, tnt, alarm");
            return true;
        }

        player.getInventory().addItem(item);
        player.sendMessage("§aGave " + amount + "x " + itemName + "!");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return ITEM_NAMES.stream()
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }
        return new ArrayList<>();
    }
}
