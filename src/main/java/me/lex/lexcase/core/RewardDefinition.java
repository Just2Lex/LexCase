package me.lex.lexcase.core;

import me.lex.lexcase.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RewardDefinition {
    private final Material material;
    private final String name;
    private final int amount;
    private final int chance;
    private final int value;
    private final String command;
    private final List<String> lore;

    public RewardDefinition(Material material, String name, int amount, int chance, int value, String command, List<String> lore) {
        this.material = material;
        this.name = name;
        this.amount = amount;
        this.chance = chance;
        this.value = Math.max(1, value);
        this.command = command == null ? "" : command;
        this.lore = lore == null ? new ArrayList<>() : lore;
    }

    public static RewardDefinition fromMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        Material material = Material.matchMaterial(string(map.get("material"), "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        String name = string(map.get("name"), material.name());
        int amount = Math.max(1, integer(map.get("amount"), 1));
        int chance = Math.max(1, integer(map.get("chance"), 1));
        int value = Math.max(1, integer(map.get("value"), chance * amount));
        String command = string(map.get("command"), "");
        List<String> lore = new ArrayList<>();
        Object loreRaw = map.get("lore");
        if (loreRaw instanceof List<?> list) {
            for (Object line : list) {
                lore.add(String.valueOf(line));
            }
        }
        return new RewardDefinition(material, name, amount, chance, value, command, lore);
    }

    public ItemStack toItemStack(String rarityColor) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName(rarityColor));
            if (!lore.isEmpty()) {
                meta.setLore(TextUtil.colorList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public String displayName(String rarityColor) {
        String colored = TextUtil.color(name);
        if (!colored.contains("§")) {
            colored = TextUtil.color(rarityColor + name);
        }
        return colored;
    }

    public int chance() {
        return chance;
    }

    public int amount() {
        return amount;
    }

    public int value() {
        return value;
    }

    public String command() {
        return command;
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
}
