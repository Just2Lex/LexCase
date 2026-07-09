package me.lex.lexcase.core;

import me.lex.lexcase.util.TextUtil;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class RarityDefinition {
    private final String id;
    private final String displayName;
    private final String color;
    private final int chance;
    private final boolean broadcastLegendary;
    private final List<RewardDefinition> drops;

    public RarityDefinition(String id, String displayName, String color, int chance, boolean broadcastLegendary, List<RewardDefinition> drops) {
        this.id = id;
        this.displayName = TextUtil.color(displayName);
        this.color = color;
        this.chance = Math.max(1, chance);
        this.broadcastLegendary = broadcastLegendary;
        this.drops = drops == null ? new ArrayList<>() : drops;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    public int chance() {
        return chance;
    }

    public boolean broadcastLegendary() {
        return broadcastLegendary;
    }

    public List<RewardDefinition> drops() {
        return drops;
    }

    public RolledReward roll(Random random) {
        if (drops.isEmpty()) {
            RewardDefinition fallback = new RewardDefinition(
                    Material.STONE,
                    displayName + " Reward",
                    1,
                    1,
                    1,
                    "",
                    List.of()
            );
            return new RolledReward(this, fallback);
        }
        int total = 0;
        for (RewardDefinition reward : drops) {
            total += Math.max(1, reward.chance());
        }
        int roll = random.nextInt(Math.max(1, total)) + 1;
        int current = 0;
        RewardDefinition selected = drops.get(0);
        for (RewardDefinition reward : drops) {
            current += Math.max(1, reward.chance());
            if (roll <= current) {
                selected = reward;
                break;
            }
        }
        return new RolledReward(this, selected);
    }
}
