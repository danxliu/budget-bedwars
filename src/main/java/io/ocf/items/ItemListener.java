package io.ocf.items;

import io.ocf.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ItemListener implements Listener {
    private final CustomItemManager itemManager;

    public ItemListener(CustomItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        ItemStack item = event.getItem();
        if (item == null) return;

        CustomItem customItem = itemManager.getItemFromStack(item);
        if (customItem == null) return;

        // Only handle INTERACT type items here
        if (customItem.getTriggerType() != CustomItem.TriggerType.INTERACT) return;

        // Check if it's a right-click
        if (!event.getAction().name().contains("RIGHT")) return;

        Player player = event.getPlayer();
        PlayerData playerData = itemManager.getTeamManager().getPlayerData(player);

        // Check team restriction
        if (!itemManager.canUse(customItem, playerData)) {
            player.sendMessage(Component.text("Your team cannot use this item!", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Cancel the vanilla interaction
        event.setCancelled(true);

        // Execute the custom item action
        boolean success = customItem.onUse(player, itemManager.getGameManager());

        // Consume item if successful and consumable
        if (success && customItem.isConsumeOnUse()) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        
        CustomItem customItem = itemManager.getItemFromStack(item);
        if (customItem == null) return;

        // Only handle PLACE type items here
        if (customItem.getTriggerType() != CustomItem.TriggerType.PLACE) return;

        Player player = event.getPlayer();
        PlayerData playerData = itemManager.getTeamManager().getPlayerData(player);

        // Check team restriction
        if (!itemManager.canUse(customItem, playerData)) {
            player.sendMessage(Component.text("Your team cannot use this item!", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Cancel the normal block placement
        event.setCancelled(true);

        // Handle instant TNT specially
        if (customItem instanceof InstantTNTItem tntItem) {
            Location placeLoc = event.getBlockPlaced().getLocation();
            tntItem.spawnPrimedTNT(placeLoc, player);
            
            // Consume item
            if (customItem.isConsumeOnUse() && player.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
        } else {
            // Generic place handling
            boolean success = customItem.onUse(player, itemManager.getGameManager());
            if (success && customItem.isConsumeOnUse() && player.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
        }
    }
}
