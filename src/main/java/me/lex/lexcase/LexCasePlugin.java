package me.lex.lexcase;

import me.lex.lexcase.command.LcCommand;
import me.lex.lexcase.core.CaseManager;
import me.lex.lexcase.storage.KeyStorage;
import me.lex.lexcase.storage.LuckStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class LexCasePlugin extends JavaPlugin {

    private KeyStorage keyStorage;
    private LuckStorage luckStorage;
    private CaseManager caseManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.keyStorage = new KeyStorage(this);
        this.keyStorage.load();
        this.luckStorage = new LuckStorage(this);
        this.luckStorage.load();
        this.caseManager = new CaseManager(this);

        LcCommand command = new LcCommand(this, caseManager, keyStorage);
        if (getCommand("lc") != null) {
            getCommand("lc").setExecutor(command);
            getCommand("lc").setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(caseManager, this);
        caseManager.restoreConfiguredCases();
    }

    @Override
    public void onDisable() {
        if (caseManager != null) {
            caseManager.shutdown();
        }
        if (keyStorage != null) {
            keyStorage.flushNow();
        }
        if (luckStorage != null) {
            luckStorage.flushNow();
        }
    }

    public void reloadPluginData() {
        reloadConfig();
        if (keyStorage != null) {
            keyStorage.load();
        }
        if (luckStorage != null) {
            luckStorage.load();
        }
        if (caseManager != null) {
            caseManager.reloadFromConfig();
        }
    }

    public KeyStorage getKeyStorage() {
        return keyStorage;
    }

    public LuckStorage getLuckStorage() {
        return luckStorage;
    }

    public CaseManager getCaseManager() {
        return caseManager;
    }
}
