package me.lex.lexcase.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CaseSetup {
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final BlockFace facing;

    public CaseSetup(String worldName, int x, int y, int z, BlockFace facing) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing == null ? BlockFace.NORTH : facing;
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public BlockFace facing() {
        return facing;
    }

    public Location location() {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        var world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    public boolean matches(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        return location.getWorld().getName().equals(worldName)
                && location.getBlockX() == x
                && location.getBlockY() == y
                && location.getBlockZ() == z;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", worldName);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("facing", facing.name());
        return map;
    }

    public static CaseSetup fromMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        String world = string(map.get("world"), "");
        int x = integer(map.get("x"), 0);
        int y = integer(map.get("y"), 0);
        int z = integer(map.get("z"), 0);
        BlockFace facing = face(string(map.get("facing"), "NORTH"));
        return new CaseSetup(world, x, y, z, facing);
    }

    private static String string(Object value, String def) {
        return value == null ? def : String.valueOf(value);
    }

    private static int integer(Object value, int def) {
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static BlockFace face(String raw) {
        if (raw == null || raw.isBlank()) {
            return BlockFace.NORTH;
        }
        try {
            return BlockFace.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BlockFace.NORTH;
        }
    }
}
