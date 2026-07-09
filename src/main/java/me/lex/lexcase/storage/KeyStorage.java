package me.lex.lexcase.storage;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

public final class KeyStorage {
    private static final long SAVE_DELAY_TICKS = 20L;

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;
    private BukkitTask pendingSaveTask;

    public KeyStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "keys.yml");
    }

    public synchronized void load() {
        flushNow();
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create keys.yml", e);
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

    public synchronized int getKeys(UUID uuid) {
        if (yaml == null || uuid == null) {
            return 0;
        }
        return yaml.getInt(uuid.toString(), 0);
    }

    public synchronized void addKeys(UUID uuid, int amount) {
        if (amount <= 0 || uuid == null || yaml == null) {
            return;
        }
        int current = getKeys(uuid);
        yaml.set(uuid.toString(), current + amount);
        save();
    }

    public synchronized boolean removeKeys(UUID uuid, int amount) {
        if (amount <= 0 || uuid == null || yaml == null) {
            return false;
        }
        int current = getKeys(uuid);
        int next = Math.max(0, current - amount);
        yaml.set(uuid.toString(), next);
        save();
        return current >= amount;
    }

    public synchronized boolean consumeKey(UUID uuid) {
        return removeKeys(uuid, 1);
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
            plugin.getLogger().severe("Failed to save keys.yml: " + e.getMessage());
        }
    }

    private void cancelPendingSave() {
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel();
            pendingSaveTask = null;
        }
    }
}
