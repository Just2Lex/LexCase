package me.lex.lexcase.core;

public record LuckProfile(int balance, int rareBonus, int tierDebt) {
    public LuckProfile {
        rareBonus = Math.max(0, rareBonus);
        tierDebt = Math.max(0, tierDebt);
    }

    public static LuckProfile zero() {
        return new LuckProfile(0, 0, 0);
    }
}
