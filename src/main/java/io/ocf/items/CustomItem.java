package io.ocf.items;

import io.ocf.GameManager;
import io.ocf.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public abstract class CustomItem {
    private static NamespacedKey itemKey;
    
    protected final String id;
    protected final Component displayName;
    protected final Material baseMaterial;
    protected final PlayerData.Team teamRestriction; // null = any team
    protected final boolean consumeOnUse;
    protected final TriggerType triggerType;
    protected final List<Component> lore;

    public enum TriggerType {
        INTERACT,  // Right-click
        PLACE      // Block placement
    }

    public static void init(JavaPlugin plugin) {
        itemKey = new NamespacedKey(plugin, "custom_item");
    }

    public static NamespacedKey getItemKey() {
        return itemKey;
    }

    public CustomItem(String id, Component displayName, Material baseMaterial, 
                      PlayerData.Team teamRestriction, boolean consumeOnUse, 
                      TriggerType triggerType, List<Component> lore) {
        this.id = id;
        this.displayName = displayName;
        this.baseMaterial = baseMaterial;
        this.teamRestriction = teamRestriction;
        this.consumeOnUse = consumeOnUse;
        this.triggerType = triggerType;
        this.lore = lore;
    }

    public String getId() {
        return id;
    }

    public Material getBaseMaterial() {
        return baseMaterial;
    }

    public PlayerData.Team getTeamRestriction() {
        return teamRestriction;
    }

    public boolean isConsumeOnUse() {
        return consumeOnUse;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public ItemStack createItemStack(int amount) {
        ItemStack item = new ItemStack(baseMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(displayName);
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        
        // Tag with custom item ID
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(itemKey, PersistentDataType.STRING, id);
        
        item.setItemMeta(meta);
        return item;
    }

    public static String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(itemKey, PersistentDataType.STRING);
    }

    /**
     * Called when the item is used.
     * @return true if the use was successful (item should be consumed if consumeOnUse is true)
     */
    public abstract boolean onUse(Player player, GameManager gameManager);
}
