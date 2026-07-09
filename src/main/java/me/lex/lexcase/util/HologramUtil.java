package me.lex.lexcase.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class HologramUtil {
    private HologramUtil() {}

    public static ArmorStand spawn(JavaPlugin plugin, Location location, String text) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world is null");
        }
        return world.spawn(location, ArmorStand.class, armorStand -> {
            armorStand.setVisible(false);
            armorStand.setInvulnerable(true);
            armorStand.setGravity(false);
            armorStand.setSilent(true);
            armorStand.setSmall(true);
            armorStand.setMarker(true);
            armorStand.setCollidable(false);
            armorStand.setCustomName(TextUtil.color(text));
            armorStand.setCustomNameVisible(true);
            armorStand.addEquipmentLock(EquipmentSlot.HAND, ArmorStand.LockType.ADDING_OR_CHANGING);
            armorStand.addEquipmentLock(EquipmentSlot.OFF_HAND, ArmorStand.LockType.ADDING_OR_CHANGING);
            armorStand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.ADDING_OR_CHANGING);
        });
    }
}
