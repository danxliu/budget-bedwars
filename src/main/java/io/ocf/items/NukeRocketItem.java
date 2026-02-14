package io.ocf.items;

import io.ocf.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class NukeRocketItem extends CustomItem {
    private final JavaPlugin plugin;
    
    public NukeRocketItem(JavaPlugin plugin) {
        super(
            "nuke",
            Component.text("Nuke Rocket", NamedTextColor.DARK_RED),
            Material.FIREWORK_ROCKET,
            null, // Any team
            true, // Consume on use
            TriggerType.INTERACT,
            List.of(
                Component.text("Right-click to launch a devastating rocket", NamedTextColor.GRAY),
                Component.text("Warning: Massive explosion and TNT cluster", NamedTextColor.RED)
            )
        );
        this.plugin = plugin;
    }

    @Override
    public boolean onUse(Player player, GameManager gameManager) {
        LargeFireball fireball = player.launchProjectile(LargeFireball.class);
        fireball.setYield(8.0f); // High explosion power
        fireball.setIsIncendiary(true);
        // Add metadata to identify it on impact
        fireball.setMetadata("nuke_rocket", new FixedMetadataValue(plugin, true));
        
        return true;
    }
}
