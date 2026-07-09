package me.lex.lexcase.core;

import org.bukkit.inventory.ItemStack;

public final class RolledReward {
    private final RarityDefinition rarity;
    private final RewardDefinition reward;
    private final ItemStack itemStack;
    private final String summaryLine;

    public RolledReward(RarityDefinition rarity, RewardDefinition reward) {
        this.rarity = rarity;
        this.reward = reward;
        this.itemStack = reward.toItemStack(rarity.color());
        this.summaryLine = rarity.displayName() + " &7» " + reward.displayName(rarity.color()) + " &fx" + reward.amount();
    }

    public RarityDefinition rarity() {
        return rarity;
    }

    public RewardDefinition reward() {
        return reward;
    }

    public ItemStack itemStack() {
        return itemStack.clone();
    }

    public String summaryLine() {
        return summaryLine;
    }

    public boolean isLegendary() {
        return rarity.broadcastLegendary() || "legendary".equalsIgnoreCase(rarity.id());
    }

    public String rewardDisplayName() {
        return reward.displayName(rarity.color());
    }

    public String command() {
        return reward.command();
    }
}
