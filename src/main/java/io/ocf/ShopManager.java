package io.ocf;

import io.ocf.items.CustomItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ShopManager {
    private final JavaPlugin plugin;
    private CustomItemManager customItemManager;
    
    private final List<ShopTrade> attackerTrades = new ArrayList<>();
    private final List<ShopTrade> defenderTrades = new ArrayList<>();
    private String attackerTitle = "Attacker Shop";
    private String defenderTitle = "Defender Shop";
    
    // Track open shop inventories
    private final Map<UUID, PlayerData.Team> openShops = new HashMap<>();

    public record ShopTrade(
            Material inputMaterial,
            int inputAmount,
            Material outputMaterial,
            String outputCustomItem,
            int outputAmount
    ) {
        public boolean isCustomOutput() {
            return outputCustomItem != null;
        }
    }

    public ShopManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setCustomItemManager(CustomItemManager customItemManager) {
        this.customItemManager = customItemManager;
        loadShops(); // Load after customItemManager is set
    }

    public void loadShops() {
        attackerTrades.clear();
        defenderTrades.clear();

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection shopSection = config.getConfigurationSection("shop");
        if (shopSection == null) {
            plugin.getLogger().warning("No shop configuration found!");
            return;
        }

        // Load attacker shop
        ConfigurationSection attackerSection = shopSection.getConfigurationSection("attackers");
        if (attackerSection != null) {
            attackerTitle = attackerSection.getString("title", "Attacker Shop");
            loadTrades(attackerSection, attackerTrades);
        }

        // Load defender shop
        ConfigurationSection defenderSection = shopSection.getConfigurationSection("defenders");
        if (defenderSection != null) {
            defenderTitle = defenderSection.getString("title", "Defender Shop");
            loadTrades(defenderSection, defenderTrades);
        }

        plugin.getLogger().info("Loaded " + attackerTrades.size() + " attacker trades and " + defenderTrades.size() + " defender trades");
    }

    private void loadTrades(ConfigurationSection section, List<ShopTrade> trades) {
        List<?> tradesList = section.getList("trades");
        if (tradesList == null) return;

        for (Object obj : tradesList) {
            if (obj instanceof Map<?, ?> tradeMap) {
                String inputItemStr = (String) tradeMap.get("input_item");
                Material inputMaterial = Material.matchMaterial(inputItemStr != null ? inputItemStr : "");
                if (inputMaterial == null) {
                    plugin.getLogger().warning("Invalid input item: " + inputItemStr);
                    continue;
                }

                int inputAmount = tradeMap.get("input_amount") instanceof Number n ? n.intValue() : 1;
                int outputAmount = tradeMap.get("output_amount") instanceof Number n ? n.intValue() : 1;

                String outputItemStr = (String) tradeMap.get("output_item");
                if (outputItemStr == null) {
                    plugin.getLogger().warning("Missing output_item in trade");
                    continue;
                }

                // Check for custom item first, then fall back to material
                String outputCustomItem = null;
                Material outputMaterial = null;

                if (customItemManager != null && customItemManager.getItem(outputItemStr) != null) {
                    outputCustomItem = outputItemStr;
                } else {
                    outputMaterial = Material.matchMaterial(outputItemStr);
                    if (outputMaterial == null) {
                        plugin.getLogger().warning("Invalid output item (not a custom item or material): " + outputItemStr);
                        continue;
                    }
                }

                trades.add(new ShopTrade(inputMaterial, inputAmount, outputMaterial, outputCustomItem, outputAmount));
            }
        }
    }

    public Inventory createShopInventory(PlayerData.Team team) {
        List<ShopTrade> trades = team == PlayerData.Team.ATTACKERS ? attackerTrades : defenderTrades;
        String title = team == PlayerData.Team.ATTACKERS ? attackerTitle : defenderTitle;

        int size = trades.size() <= 9 ? 9 : (trades.size() <= 18 ? 18 : (trades.size() <= 27 ? 27 : 54));
        Inventory inv = Bukkit.createInventory(null, size, Component.text(title, NamedTextColor.DARK_GREEN));

        for (int i = 0; i < trades.size(); i++) {
            ShopTrade trade = trades.get(i);
            ItemStack displayItem = createDisplayItem(trade);
            inv.setItem(i, displayItem);
        }

        return inv;
    }

    private ItemStack createDisplayItem(ShopTrade trade) {
        ItemStack item;

        if (trade.isCustomOutput() && customItemManager != null) {
            var customItem = customItemManager.getItem(trade.outputCustomItem());
            if (customItem != null) {
                item = customItem.createItemStack(trade.outputAmount());
            } else {
                item = new ItemStack(Material.BARRIER, 1);
            }
        } else {
            item = new ItemStack(trade.outputMaterial(), trade.outputAmount());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.lore());
            }
            lore.add(Component.empty());
            lore.add(Component.text("Cost: ", NamedTextColor.GRAY)
                    .append(Component.text(trade.inputAmount() + " ", NamedTextColor.GOLD))
                    .append(Component.text(formatMaterialName(trade.inputMaterial()), NamedTextColor.GOLD)));
            lore.add(Component.text("Left-click to purchase", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public void openShop(Player player, PlayerData.Team team) {
        Inventory shop = createShopInventory(team);
        openShops.put(player.getUniqueId(), team);
        player.openInventory(shop);
    }

    public boolean isShopInventory(Player player) {
        return openShops.containsKey(player.getUniqueId());
    }

    public void handleClose(Player player) {
        openShops.remove(player.getUniqueId());
    }

    public boolean handleClick(Player player, int slot) {
        PlayerData.Team team = openShops.get(player.getUniqueId());
        if (team == null) return false;

        List<ShopTrade> trades = team == PlayerData.Team.ATTACKERS ? attackerTrades : defenderTrades;
        if (slot < 0 || slot >= trades.size()) return true;

        ShopTrade trade = trades.get(slot);

        // Count input material
        int inputCount = countMaterial(player, trade.inputMaterial());

        if (inputCount < trade.inputAmount()) {
            player.sendMessage(Component.text("Not enough ", NamedTextColor.RED)
                    .append(Component.text(formatMaterialName(trade.inputMaterial()), NamedTextColor.GOLD))
                    .append(Component.text("! Need " + trade.inputAmount() + ", have " + inputCount, NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // Remove input materials
        removeMaterial(player, trade.inputMaterial(), trade.inputAmount());

        // Give output item
        ItemStack outputItem;
        if (trade.isCustomOutput() && customItemManager != null) {
            var customItem = customItemManager.getItem(trade.outputCustomItem());
            if (customItem != null) {
                outputItem = customItem.createItemStack(trade.outputAmount());
            } else {
                player.sendMessage(Component.text("Error: Custom item not found!", NamedTextColor.RED));
                return true;
            }
        } else {
            outputItem = new ItemStack(trade.outputMaterial(), trade.outputAmount());
        }

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(outputItem);
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage(Component.text("Purchased ", NamedTextColor.GREEN)
                .append(Component.text(trade.outputAmount() + "x ", NamedTextColor.GOLD))
                .append(Component.text(trade.isCustomOutput() ? trade.outputCustomItem() : formatMaterialName(trade.outputMaterial()), NamedTextColor.GOLD)));

        return true;
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (item.getAmount() <= 0) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }
}
