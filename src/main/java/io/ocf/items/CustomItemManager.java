package io.ocf.items;

import io.ocf.GameManager;
import io.ocf.PlayerData;
import io.ocf.TeamManager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class CustomItemManager {
    private final Map<String, CustomItem> items = new HashMap<>();
    private final JavaPlugin plugin;
    private final GameManager gameManager;
    private final TeamManager teamManager;

    // Specific item instances for easy access
    private final FireballItem fireballItem;
    private final InstantTNTItem instantTNTItem;
    private final AlarmItem alarmItem;
    private final FlagCompassItem flagCompassItem;

    public CustomItemManager(JavaPlugin plugin, GameManager gameManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.teamManager = teamManager;

        // Initialize static keys
        CustomItem.init(plugin);
        AlarmItem.init(plugin, teamManager);
        FlagCompassItem.init(plugin, gameManager, teamManager);

        // Create and register items
        fireballItem = new FireballItem();
        instantTNTItem = new InstantTNTItem();
        alarmItem = new AlarmItem();
        flagCompassItem = new FlagCompassItem();

        registerItem(fireballItem);
        registerItem(instantTNTItem);
        registerItem(alarmItem);
        registerItem(flagCompassItem);

        plugin.getLogger().info("Registered " + items.size() + " custom items");
    }

    private void registerItem(CustomItem item) {
        items.put(item.getId(), item);
    }

    public CustomItem getItem(String id) {
        return items.get(id);
    }

    public CustomItem getItemFromStack(ItemStack stack) {
        String id = CustomItem.getCustomItemId(stack);
        if (id == null) return null;
        return items.get(id);
    }

    public boolean canUse(CustomItem item, PlayerData playerData) {
        if (item.getTeamRestriction() == null) return true;
        return item.getTeamRestriction() == playerData.getTeam();
    }

    // Convenience methods to create item stacks
    public ItemStack createFireball(int amount) {
        return fireballItem.createItemStack(amount);
    }

    public ItemStack createInstantTNT(int amount) {
        return instantTNTItem.createItemStack(amount);
    }

    public ItemStack createAlarm(int amount) {
        return alarmItem.createItemStack(amount);
    }

    public ItemStack createFlagCompass(int amount) {
        return flagCompassItem.createItemStack(amount);
    }

    public FireballItem getFireballItem() {
        return fireballItem;
    }

    public InstantTNTItem getInstantTNTItem() {
        return instantTNTItem;
    }

    public AlarmItem getAlarmItem() {
        return alarmItem;
    }

    public FlagCompassItem getFlagCompassItem() {
        return flagCompassItem;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }
}
