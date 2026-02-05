package io.ocf.items;

import io.ocf.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

import java.util.List;

public class InstantTNTItem extends CustomItem {
    
    public InstantTNTItem() {
        super(
            "instant_tnt",
            Component.text("Instant TNT", NamedTextColor.RED),
            Material.TNT,
            null, // Any team
            true, // Consume on use
            TriggerType.PLACE,
            List.of(Component.text("Place to instantly ignite", NamedTextColor.GRAY))
        );
    }

    @Override
    public boolean onUse(Player player, GameManager gameManager) {
        // This is handled specially in ItemListener for PLACE type
        return true;
    }

    public void spawnPrimedTNT(Location location, Player source) {
        TNTPrimed tnt = location.getWorld().spawn(location.add(0.5, 0, 0.5), TNTPrimed.class);
        tnt.setFuseTicks(40); // 2 seconds
        tnt.setSource(source);
    }
}
