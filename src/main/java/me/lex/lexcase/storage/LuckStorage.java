package me.lex.lexcase.storage;

import me.lex.lexcase.core.LuckProfile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

public final class LuckStorage {
    private static final long SAVE_DELAY_TICKS = 20L;

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;
    private BukkitTask pendingSaveTask;

    public LuckStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "luck.yml");
    }

    public synchronized void load() {
        flushNow();
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create luck.yml", e);
            }
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void save() {
        if (yaml == null) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::save);
            return;
        }
        scheduleSave();
    }

    public synchronized void flushNow() {
        cancelPendingSave();
        if (yaml == null) {
            return;
        }
        writeSnapshot(yaml.saveToString());
    }

    public synchronized LuckProfile getProfile(UUID uuid) {
        if (yaml == null || uuid == null) {
            return LuckProfile.zero();
        }
        String path = path(uuid);
        int balance = yaml.getInt(path + ".balance", 0);
        int rareBonus = yaml.getInt(path + ".rare-bonus", 0);
        int tierDebt = yaml.getInt(path + ".tier-debt", 0);
        return new LuckProfile(balance, rareBonus, tierDebt);
    }

    public synchronized void saveProfile(UUID uuid, LuckProfile profile) {
        if (yaml == null || uuid == null || profile == null) {
            return;
        }
        String path = path(uuid);
        yaml.set(path + ".balance", profile.balance());
        yaml.set(path + ".rare-bonus", Math.max(0, profile.rareBonus()));
        yaml.set(path + ".tier-debt", Math.max(0, profile.tierDebt()));
        save();
    }

    public synchronized void reset(UUID uuid) {
        if (yaml == null || uuid == null) {
            return;
        }
        yaml.set(path(uuid), null);
        save();
    }

    private void scheduleSave() {
        cancelPendingSave();
        pendingSaveTask = Bukkit.getScheduler().runTaskLater(plugin, this::flushAsyncSnapshot, SAVE_DELAY_TICKS);
    }

    private void flushAsyncSnapshot() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) {
                pendingSaveTask = null;
                if (yaml == null) {
                    return;
                }
                writeSnapshot(yaml.saveToString());
            }
        });
    }

    private void writeSnapshot(String snapshot) {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(snapshot);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save luck.yml: " + e.getMessage());
        }
    }

    private void cancelPendingSave() {
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel();
            pendingSaveTask = null;
        }
    }

    private String path(UUID uuid) {
        return "players." + uuid;
    }
}
