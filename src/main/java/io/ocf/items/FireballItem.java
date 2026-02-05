package io.ocf.items;

import io.ocf.GameManager;
import io.ocf.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;

import java.util.List;

public class FireballItem extends CustomItem {
    
    public FireballItem() {
        super(
            "fireball",
            Component.text("Fireball", NamedTextColor.RED),
            Material.FIRE_CHARGE,
            null, // Any team
            true, // Consume on use
            TriggerType.INTERACT,
            List.of(Component.text("Right-click to launch a fireball", NamedTextColor.GRAY))
        );
    }

    @Override
    public boolean onUse(Player player, GameManager gameManager) {
        // Spawn fireball in direction player is looking
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setYield(2.0f); // Explosion power
        fireball.setIsIncendiary(true);
        
        return true;
    }
}
