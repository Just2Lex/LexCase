package me.lex.lexcase.core;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CaseSession {
    public static final int MAX_OPENED = 4;

    private final CaseSetup setup;
    private final Location center;
    private final List<Location> chestLocations;
    private final UUID openerUuid;
    private final String openerName;
    private final Map<Integer, RolledReward> rolledRewards = new HashMap<>();
    private final Map<Integer, ArmorStand> openTextHolograms = new HashMap<>();
    private final Map<Integer, ArmorStand> rewardTextHolograms = new HashMap<>();
    private final Map<Integer, Item> rewardItems = new HashMap<>();
    private final Set<Integer> opened = new HashSet<>();
    private BukkitTask timerTask;
    private BukkitTask closeTask;
    private BukkitTask finalizationTask;
    private boolean timerStarted;
    private boolean closingSequenceStarted;
    private boolean completionTriggered;
    private boolean paused;

    public CaseSession(CaseSetup setup, List<Location> chestLocations, UUID openerUuid, String openerName) {
        this.setup = setup;
        this.center = setup.location();
        this.chestLocations = chestLocations;
        this.openerUuid = openerUuid;
        this.openerName = openerName;
    }

    public CaseSetup setup() {
        return setup;
    }

    public Location getCenter() {
        return center;
    }

    public List<Location> getChestLocations() {
        return chestLocations;
    }

    public UUID openerUuid() {
        return openerUuid;
    }

    public String openerName() {
        return openerName;
    }

    public Map<Integer, RolledReward> getRolledRewards() {
        return rolledRewards;
    }

    public Map<Integer, ArmorStand> getOpenTextHolograms() {
        return openTextHolograms;
    }

    public Map<Integer, ArmorStand> getRewardTextHolograms() {
        return rewardTextHolograms;
    }

    public Map<Integer, Item> getRewardItems() {
        return rewardItems;
    }

    public boolean isOpened(int index) {
        return opened.contains(index);
    }

    public void markOpened(int index) {
        opened.add(index);
    }

    public int openedCount() {
        return opened.size();
    }

    public boolean canOpenMore() {
        return !paused && !closingSequenceStarted && opened.size() < MAX_OPENED;
    }

    public boolean isTimerStarted() {
        return timerStarted;
    }

    public void markTimerStarted() {
        this.timerStarted = true;
    }

    public BukkitTask getTimerTask() {
        return timerTask;
    }

    public void setTimerTask(BukkitTask timerTask) {
        this.timerTask = timerTask;
    }

    public BukkitTask getCloseTask() {
        return closeTask;
    }

    public void setCloseTask(BukkitTask closeTask) {
        this.closeTask = closeTask;
    }

    public BukkitTask getFinalizationTask() {
        return finalizationTask;
    }

    public void setFinalizationTask(BukkitTask finalizationTask) {
        this.finalizationTask = finalizationTask;
    }

    public void cancelTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    public void cancelCloseTask() {
        if (closeTask != null) {
            closeTask.cancel();
            closeTask = null;
        }
    }

    public void cancelFinalizationTask() {
        if (finalizationTask != null) {
            finalizationTask.cancel();
            finalizationTask = null;
        }
    }

    public boolean isClosingSequenceStarted() {
        return closingSequenceStarted;
    }

    public void markClosingSequenceStarted() {
        this.closingSequenceStarted = true;
    }

    public boolean isCompletionTriggered() {
        return completionTriggered;
    }

    public void markCompletionTriggered() {
        this.completionTriggered = true;
    }

    public boolean isPaused() {
        return paused;
    }

    public void pause() {
        this.paused = true;
    }

    public void resume() {
        this.paused = false;
    }

    public void clearState() {
        cancelTimer();
        cancelCloseTask();
        cancelFinalizationTask();
        rolledRewards.clear();
        openTextHolograms.clear();
        rewardTextHolograms.clear();
        rewardItems.clear();
        opened.clear();
        timerStarted = false;
        closingSequenceStarted = false;
        completionTriggered = false;
        paused = false;
    }
}
