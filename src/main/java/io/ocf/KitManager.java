package io.ocf;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class KitManager {
    private final JavaPlugin plugin;
    private final Map<String, Kit> kits = new HashMap<>();

    public static class Kit {
        private final String name;
        private final PlayerData.Team team;
        private final Map<String, Material> armor;
        private final List<KitItem> items;

        public Kit(String name, PlayerData.Team team, Map<String, Material> armor, List<KitItem> items) {
            this.name = name;
            this.team = team;
            this.armor = armor;
            this.items = items;
        }

        public String getName() { return name; }
        public PlayerData.Team getTeam() { return team; }
        public Map<String, Material> getArmor() { return armor; }
        public List<KitItem> getItems() { return items; }
    }

    public static class KitItem {
        private final Material material;
        private final int amount;
        private final Map<Enchantment, Integer> enchantments;

        public KitItem(Material material, int amount, Map<Enchantment, Integer> enchantments) {
            this.material = material;
            this.amount = amount;
            this.enchantments = enchantments;
        }

        public ItemStack toItemStack() {
            ItemStack item = new ItemStack(material, amount);
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
            return item;
        }
    }

    public KitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadKits();
    }

    public void loadKits() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        kits.clear();

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection kitsSection = config.getConfigurationSection("kits");
        if (kitsSection == null) return;

        for (String kitName : kitsSection.getKeys(false)) {
            ConfigurationSection kitSection = kitsSection.getConfigurationSection(kitName);
            if (kitSection == null) continue;

            // Parse team
            String teamStr = kitSection.getString("team", "");
            PlayerData.Team team = null;
            try {
                team = PlayerData.Team.valueOf(teamStr);
            } catch (IllegalArgumentException ignored) {}

            // Parse armor
            Map<String, Material> armor = new HashMap<>();
            ConfigurationSection armorSection = kitSection.getConfigurationSection("armor");
            if (armorSection != null) {
                for (String slot : armorSection.getKeys(false)) {
                    String materialStr = armorSection.getString(slot);
                    if (materialStr != null) {
                        Material material = Material.matchMaterial(materialStr);
                        if (material != null) {
                            armor.put(slot, material);
                        }
                    }
                }
            }

            // Parse items
            List<KitItem> items = new ArrayList<>();
            List<?> itemsList = kitSection.getList("items");
            if (itemsList != null) {
                for (Object obj : itemsList) {
                    if (obj instanceof Map<?, ?> itemMap) {
                        String materialStr = (String) itemMap.get("material");
                        int amount = itemMap.get("amount") instanceof Number n ? n.intValue() : 1;
                        
                        Material material = Material.matchMaterial(materialStr);
                        if (material == null) continue;

                        Map<Enchantment, Integer> enchantments = new HashMap<>();
                        Object enchObj = itemMap.get("enchantments");
                        if (enchObj instanceof Map<?, ?> enchMap) {
                            for (Map.Entry<?, ?> entry : enchMap.entrySet()) {
                                NamespacedKey key = NamespacedKey.minecraft(entry.getKey().toString().toLowerCase());
                                Enchantment ench = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
                                if (ench != null && entry.getValue() instanceof Number n) {
                                    enchantments.put(ench, n.intValue());
                                }
                            }
                        }

                        items.add(new KitItem(material, amount, enchantments));
                    }
                }
            }

            kits.put(kitName.toLowerCase(), new Kit(kitName, team, armor, items));
        }

        plugin.getLogger().info("Loaded " + kits.size() + " kits");
    }

    public boolean applyKit(Player player, String kitName, PlayerData playerData) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit == null) {
            player.sendMessage(Component.text("Kit '" + kitName + "' not found!", NamedTextColor.RED));
            return false;
        }

        PlayerData.Team playerTeam = playerData.getTeam();
        if (kit.getTeam() != null && playerTeam != kit.getTeam()) {
            String teamName = kit.getTeam() == PlayerData.Team.ATTACKERS ? "Attackers" : "Defenders";
            player.sendMessage(Component.text("This kit is only for " + teamName + "!", NamedTextColor.RED));
            return false;
        }

        PlayerInventory inv = player.getInventory();
        inv.clear();

        // Apply armor
        Map<String, Material> armor = kit.getArmor();
        if (armor.containsKey("helmet")) inv.setHelmet(new ItemStack(armor.get("helmet")));
        if (armor.containsKey("chestplate")) inv.setChestplate(new ItemStack(armor.get("chestplate")));
        if (armor.containsKey("leggings")) inv.setLeggings(new ItemStack(armor.get("leggings")));
        if (armor.containsKey("boots")) inv.setBoots(new ItemStack(armor.get("boots")));

        // Apply items
        for (KitItem item : kit.getItems()) {
            inv.addItem(item.toItemStack());
        }

        player.sendMessage(Component.text("Kit '", NamedTextColor.GREEN)
                .append(Component.text(kit.getName(), NamedTextColor.GOLD))
                .append(Component.text("' applied!", NamedTextColor.GREEN)));
        return true;
    }

    public Set<String> getKitNames() {
        return kits.keySet();
    }

    public Set<String> getKitNamesForTeam(PlayerData.Team team) {
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, Kit> entry : kits.entrySet()) {
            if (entry.getValue().getTeam() == null || entry.getValue().getTeam() == team) {
                names.add(entry.getKey());
            }
        }
        return names;
    }
}
