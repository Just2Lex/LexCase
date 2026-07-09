package me.lex.lexcase.core;

import me.lex.lexcase.LexCasePlugin;
import me.lex.lexcase.storage.KeyStorage;
import me.lex.lexcase.util.HologramUtil;
import me.lex.lexcase.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Locale;

public final class CaseManager implements Listener {

    private static final List<int[]> OFFSETS = List.of(
            new int[]{1, 0, 3},
            new int[]{-1, 0, 3},
            new int[]{-3, 0, 1},
            new int[]{-3, 0, -1},
            new int[]{-1, 0, -3},
            new int[]{1, 0, -3},
            new int[]{3, 0, -1},
            new int[]{3, 0, 1}
    );

    private final LexCasePlugin plugin;
    private final Random random = new Random();
    private final List<CaseSetup> setups = new ArrayList<>();
    private final List<RarityDefinition> rarities = new ArrayList<>();
    private final Map<CaseLocationKey, CaseSession> activeSessions = new HashMap<>();
    public CaseManager(LexCasePlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    public void restoreConfiguredCases() {
        for (CaseSetup setup : setups) {
            Location location = setup.location();
            if (location != null) {
                placeIdleCase(setup);
            }
        }
    }

    public void reloadFromConfig() {
        forceFinalizeAllSessions();
        loadFromConfig();
        restoreConfiguredCases();
    }

    public void shutdown() {
        forceFinalizeAllSessions();
    }

    public void addOrUpdateSetup(Location location, BlockFace facing) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Location blockLocation = location.clone().getBlock().getLocation();
        CaseSetup setup = new CaseSetup(blockLocation.getWorld().getName(), blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ(), facing);

        int existingIndex = indexOfSetup(blockLocation);
        if (existingIndex >= 0) {
            setups.set(existingIndex, setup);
        } else {
            setups.add(setup);
        }
        saveSetups();
        placeIdleCase(setup);
    }

    public boolean deleteSetupAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        int index = indexOfSetup(location);
        if (index < 0) {
            return false;
        }
        CaseSetup setup = setups.get(index);
        CaseSession session = activeSessions.get(sessionKey(setup.location()));
        if (session != null) {
            cleanupSession(session, false);
        }
        clearCaseBlocks(setup.location());
        setups.remove(index);
        saveSetups();
        return true;
    }

    private void loadFromConfig() {
        setups.clear();
        rarities.clear();

        List<Map<?, ?>> setupMaps = plugin.getConfig().getMapList("cases.setups");
        if (!setupMaps.isEmpty()) {
            for (Map<?, ?> map : setupMaps) {
                CaseSetup setup = CaseSetup.fromMap(map);
                if (setup != null) {
                    setups.add(setup);
                }
            }
        } else {
            String worldName = plugin.getConfig().getString("case.world", "");
            if (worldName != null && !worldName.isBlank()) {
                setups.add(new CaseSetup(
                        worldName,
                        plugin.getConfig().getInt("case.x"),
                        plugin.getConfig().getInt("case.y"),
                        plugin.getConfig().getInt("case.z"),
                        parseFacing(plugin.getConfig().getString("case.facing", "NORTH"))
                ));
            }
        }

        var raritiesSection = plugin.getConfig().getConfigurationSection("rarities");
        if (raritiesSection != null) {
            for (String key : raritiesSection.getKeys(false)) {
                var section = raritiesSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                String display = section.getString("display", key);
                String color = section.getString("color", "&f");
                int chance = Math.max(1, section.getInt("chance", 1));
                boolean broadcastLegendary = section.getBoolean("broadcast-legendary", false);
                List<RewardDefinition> drops = new ArrayList<>();
                for (Map<?, ?> map : section.getMapList("drops")) {
                    RewardDefinition drop = RewardDefinition.fromMap(map);
                    if (drop != null) {
                        drops.add(drop);
                    }
                }
                if (drops.isEmpty()) {
                    plugin.getLogger().warning("[LexCase] Skipping rarity '" + key + "' because it has no valid drops.");
                    continue;
                }
                rarities.add(new RarityDefinition(key, display, color, chance, broadcastLegendary, drops));
            }
        }

        if (rarities.isEmpty()) {
            plugin.getLogger().warning("[LexCase] No valid rarities found in config. Loading fallback rarity to keep the plugin operational.");
            List<RewardDefinition> fallback = List.of(
                    new RewardDefinition(Material.STONE, "&fStone", 1, 1, 10, "give {player} stone 1", List.of())
            );
            rarities.add(new RarityDefinition("rare", "&aRare", "&a", 1, false, fallback));
        }
    }

    private void saveSetups() {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (CaseSetup setup : setups) {
            serialized.add(new LinkedHashMap<>(setup.toMap()));
        }
        plugin.getConfig().set("cases.setups", serialized);
        plugin.getConfig().set("case.world", null);
        plugin.getConfig().set("case.x", null);
        plugin.getConfig().set("case.y", null);
        plugin.getConfig().set("case.z", null);
        plugin.getConfig().set("case.facing", null);
        plugin.saveConfig();
    }

    private void cleanupAllSessions(boolean keepBlocks) {
        List<CaseSession> sessions = new ArrayList<>(activeSessions.values());
        for (CaseSession session : sessions) {
            cleanupSession(session, keepBlocks);
        }
    }

    private int indexOfSetup(Location location) {
        for (int i = 0; i < setups.size(); i++) {
            if (setups.get(i).matches(location)) {
                return i;
            }
        }
        return -1;
    }

    private CaseSetup findSetup(Location location) {
        for (CaseSetup setup : setups) {
            if (setup.matches(location)) {
                return setup;
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !event.getAction().isRightClick()) {
            return;
        }

        Block clicked = event.getClickedBlock();
        Player player = event.getPlayer();
        CaseSetup clickedSetup = findSetup(clicked.getLocation());

        if (clickedSetup != null && clicked.getType() == Material.ENDER_CHEST) {
            event.setCancelled(true);
            if (activeSessions.containsKey(sessionKey(clickedSetup.location()))) {
                TextUtil.send(player, plugin.getConfig().getString("messages.case-locked", "&cКейс сейчас занят."));
                return;
            }
            openCase(player, clickedSetup);
            return;
        }

        CaseSession session = findSessionForChest(clicked.getLocation());
        if (session == null) {
            return;
        }

        for (int i = 0; i < session.getChestLocations().size(); i++) {
            if (sameLocation(clicked.getLocation(), session.getChestLocations().get(i))) {
                event.setCancelled(true);
                if (!player.getUniqueId().equals(session.openerUuid())) {
                    TextUtil.send(player, plugin.getConfig().getString("messages.case-locked", "&cКейс сейчас занят."));
                    return;
                }
                openChest(player, session, i, true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (findSetup(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
            return;
        }
        CaseSession session = findSessionForChest(event.getBlock().getLocation());
        if (session == null) {
            return;
        }
        for (Location chestLoc : session.getChestLocations()) {
            if (sameLocation(event.getBlock().getLocation(), chestLoc)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pauseSessionForPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        resumeSessionForPlayer(event.getPlayer().getUniqueId());
    }

    private void openCase(Player player, CaseSetup setup) {
        Location center = setup.location();
        if (center == null || center.getWorld() == null) {
            TextUtil.send(player, plugin.getConfig().getString("messages.no-case-set", "&cСначала установи точку кейса через /lc setup"));
            return;
        }
        if (activeSessions.containsKey(sessionKey(center))) {
            TextUtil.send(player, plugin.getConfig().getString("messages.case-locked", "&cКейс сейчас занят."));
            return;
        }
        KeyStorage storage = plugin.getKeyStorage();
        if (!storage.consumeKey(player.getUniqueId())) {
            TextUtil.send(player, plugin.getConfig().getString("messages.no-keys", "&cУ тебя нет ключей для открытия кейса."));
            return;
        }
        if (rarities.isEmpty()) {
            TextUtil.send(player, plugin.getConfig().getString("messages.rewards-empty", "&cВ конфиге не настроены награды."));
            storage.addKeys(player.getUniqueId(), 1);
            return;
        }

        Location teleportLocation = center.clone().add(0.5, 1.0, 0.5);
        teleportLocation.setDirection(faceVector(setup.facing()));
        player.teleport(teleportLocation);

        clearCaseBlocks(center);
        center.getWorld().playSound(center.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(0.5, 1.0, 0.5), 60, 0.4, 0.5, 0.4, 0.05);

        List<Location> chestLocations = new ArrayList<>();
        for (int[] offset : OFFSETS) {
            chestLocations.add(center.clone().add(offset[0], offset[1], offset[2]));
        }

        CaseSession session = new CaseSession(setup, chestLocations, player.getUniqueId(), player.getName());
        activeSessions.put(sessionKey(center), session);
        startOpeningTimer(session);
        scheduleChestAppearanceSequence(session);
    }

    private void scheduleChestAppearanceSequence(CaseSession session) {
        int step = plugin.getConfig().getInt("case.chest-spawn-step-ticks", 5);
        for (int i = 0; i < session.getChestLocations().size(); i++) {
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isActive(session)) {
                    return;
                }
                Location chestLoc = session.getChestLocations().get(index);
                placeChestFacingCenter(chestLoc, session.getCenter());
                setChestOpenState(chestLoc, false);
                spawnOpenText(session, chestLoc, index);
                World world = chestLoc.getWorld();
                if (world != null) {
                    world.spawnParticle(Particle.CLOUD, chestLoc.clone().add(0.5, 0.9, 0.5), 10, 0.15, 0.1, 0.15, 0.01);
                    world.playSound(chestLoc.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_CHEST_OPEN, 0.55f, 1.15f);
                }
            }, (long) index * step);
        }
    }

    private void placeIdleCase(CaseSetup setup) {
        Location center = setup.location();
        if (center == null || center.getWorld() == null) {
            return;
        }
        clearCaseBlocks(center);
        placeEnderChest(center, setup.facing());
        spawnCenterHologram(center);
        center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(0.5, 1.0, 0.5), 25, 0.3, 0.5, 0.3, 0.02);
        center.getWorld().playSound(center.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
    }

    private void clearCaseBlocks(Location center) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        center.getBlock().setType(Material.AIR, false);
        for (int[] offset : OFFSETS) {
            Location loc = center.clone().add(offset[0], offset[1], offset[2]);
            loc.getBlock().setType(Material.AIR, false);
        }

        String caseTag = caseTag(center);
        center.getWorld().getNearbyEntities(center, 7, 5, 7,
                entity -> entity instanceof ArmorStand stand
                        && stand.getScoreboardTags().contains("lexcase_hologram")
                        && stand.getScoreboardTags().contains(caseTag))
                .forEach(entity -> entity.remove());
    }

    private void tagHologram(ArmorStand stand, Location center, String specificTag) {
        if (stand == null) {
            return;
        }
        stand.addScoreboardTag("lexcase_hologram");
        stand.addScoreboardTag(caseTag(center));
        if (specificTag != null && !specificTag.isBlank()) {
            stand.addScoreboardTag(specificTag);
        }
    }

    private String caseTag(Location center) {
        if (center == null || center.getWorld() == null) {
            return "lexcase_case_unknown";
        }
        String worldName = center.getWorld().getName() == null ? "world" : center.getWorld().getName().toLowerCase(Locale.ROOT);
        String raw = worldName + ':' + center.getBlockX() + ':' + center.getBlockY() + ':' + center.getBlockZ();
        return "lexcase_case_" + Integer.toHexString(raw.hashCode());
    }

    private void placeEnderChest(Location location, BlockFace facing) {
        Block block = location.getBlock();
        BlockData data = Bukkit.createBlockData(Material.ENDER_CHEST);
        if (data instanceof Directional directional) {
            directional.setFacing(facing);
            block.setBlockData(directional, false);
        } else {
            block.setType(Material.ENDER_CHEST, false);
        }
    }

    private void spawnCenterHologram(Location center) {
        double height = plugin.getConfig().getDouble("case.center-hologram-height", 1.00);
        String name = plugin.getConfig().getString("case.name", "&6&lLexCase");
        ArmorStand stand = HologramUtil.spawn(plugin, center.clone().add(0.5, height, 0.5), name);
        tagHologram(stand, center, "lexcase_center_title");
    }

    private void spawnOpenText(CaseSession session, Location chestLoc, int index) {
        double openHeight = plugin.getConfig().getDouble("case.open-hologram-height", 1.00);
        String text = plugin.getConfig().getString("case.open-text", "&2ОТКРЫТЬ");
        ArmorStand stand = HologramUtil.spawn(plugin, chestLoc.clone().add(0.5, openHeight, 0.5), text);
        tagHologram(stand, session.getCenter(), "lexcase_open_text_" + index);
        session.getOpenTextHolograms().put(index, stand);
    }

    private void openChest(Player player, CaseSession session, int index, boolean manual) {
        if (session == null || session.isPaused() || session.isClosingSequenceStarted()) {
            return;
        }
        if (session.isOpened(index)) {
            TextUtil.send(player, plugin.getConfig().getString("messages.chest-already-opened", "&eЭтот сундук уже открыт."));
            return;
        }
        if (!session.canOpenMore()) {
            return;
        }
        if (manual && !player.getUniqueId().equals(session.openerUuid())) {
            TextUtil.send(player, plugin.getConfig().getString("messages.case-locked", "&cКейс сейчас занят."));
            return;
        }
        openChestInternal(session, index, true);
    }

    private void openChestInternal(CaseSession session, int index, boolean animateItem) {
        if (session == null || session.isPaused() || session.isOpened(index) || session.isCompletionTriggered()) {
            return;
        }
        if (session.openedCount() >= CaseSession.MAX_OPENED && !session.isOpened(index)) {
            return;
        }

        session.markOpened(index);
        ArmorStand openText = session.getOpenTextHolograms().remove(index);
        if (openText != null) {
            openText.remove();
        }

        Location chestLoc = session.getChestLocations().get(index);
        World world = chestLoc.getWorld();
        if (world == null) {
            return;
        }

        RolledReward rolledReward = rollReward(session.openerUuid());
        applyLuck(session.openerUuid(), rolledReward); // luck updates immediately for the next chest roll
        session.getRolledRewards().put(index, rolledReward);

        setChestOpenState(chestLoc, true);
        world.playSound(chestLoc.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        world.spawnParticle(Particle.CLOUD, chestLoc.clone().add(0.5, 0.9, 0.5), 14, 0.2, 0.1, 0.2, 0.01);

        ItemStack reward = rolledReward.itemStack();
        Location itemStart = chestLoc.clone().add(0.5, 0.45, 0.5);
        Item item = world.dropItem(itemStart, reward);
        item.setGravity(false);
        item.setVelocity(new Vector(0, 0, 0));
        item.setPickupDelay(Integer.MAX_VALUE);
        session.getRewardItems().put(index, item);

        double textOffset = plugin.getConfig().getDouble("case.case-item-text-offset", 0.50);
        ArmorStand text = HologramUtil.spawn(plugin, itemStart.clone().add(0, textOffset, 0), rolledReward.rewardDisplayName());
        tagHologram(text, session.getCenter(), "lexcase_reward_text_" + index);
        session.getRewardTextHolograms().put(index, text);

        if (animateItem) {
            int riseTicks = 12;
            double riseHeight = plugin.getConfig().getDouble("case.item-rise-height", 0.00);
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (item.isDead() || text.isDead() || !isActive(session)) {
                        cancel();
                        return;
                    }
                    if (ticks >= riseTicks) {
                        cancel();
                        world.playSound(chestLoc.clone().add(0.5, 0.5, 0.5), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                        world.spawnParticle(Particle.HAPPY_VILLAGER, item.getLocation().clone().add(0, 0.2, 0), 8, 0.1, 0.1, 0.1, 0.0);
                        return;
                    }
                    ticks++;
                    Location next = item.getLocation().clone().add(0, riseHeight / riseTicks, 0);
                    item.teleport(next);
                    text.teleport(next.clone().add(0, textOffset, 0));
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        if (session.openedCount() >= CaseSession.MAX_OPENED) {
            triggerCompletionCountdown(session);
        }
    }

    private void startOpeningTimer(CaseSession session) {
        session.cancelTimer();
        int timerTicks = Math.max(1, plugin.getConfig().getInt("case.open-timeout-seconds", 9)) * 20;
        session.setTimerTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isActive(session) || session.isPaused() || session.isCompletionTriggered()) {
                return;
            }
            session.markClosingSequenceStarted();
            List<Integer> remaining = new ArrayList<>();
            for (int i = 0; i < session.getChestLocations().size(); i++) {
                if (!session.isOpened(i)) {
                    remaining.add(i);
                }
            }
            Collections.shuffle(remaining, random);
            int need = CaseSession.MAX_OPENED - session.openedCount();
            if (need <= 0) {
                triggerCompletionCountdown(session);
                return;
            }
            int step = plugin.getConfig().getInt("case.auto-open-step-ticks", 5);
            int limit = Math.min(need, remaining.size());
            for (int i = 0; i < limit; i++) {
                final int index = remaining.get(i);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isActive(session)) {
                        openChestInternal(session, index, true);
                    }
                }, (long) i * step);
            }
        }, timerTicks));
    }

    private void triggerCompletionCountdown(CaseSession session) {
        if (session == null || session.isPaused() || session.isCompletionTriggered()) {
            return;
        }
        session.markCompletionTriggered();
        session.cancelTimer();
        int finalRevealDelay = plugin.getConfig().getInt("case.final-reveal-delay-ticks", 60);
        session.cancelFinalizationTask();
        session.setFinalizationTask(Bukkit.getScheduler().runTaskLater(plugin, () -> finalizeCycle(session), finalRevealDelay));
    }

    private void finalizeCycle(CaseSession session) {
        if (!isActive(session)) {
            return;
        }

        Player opener = Bukkit.getPlayer(session.openerUuid());
        String openerName = resolveOpenerName(session, opener);
        List<String> summaryLines = new ArrayList<>();
        for (int i = 0; i < session.getChestLocations().size(); i++) {
            RolledReward rolledReward = session.getRolledRewards().get(i);
            if (rolledReward == null) {
                continue;
            }
            summaryLines.add(rolledReward.summaryLine());
            executeRewardCommand(rolledReward, openerName);
            if (rolledReward.isLegendary()) {
                broadcastLegendary(openerName, rolledReward);
            }
        }

        if (opener != null && opener.isOnline()) {
            String header = plugin.getConfig().getString("messages.rewards-header", "&aТы получил:");
            opener.sendMessage(TextUtil.color(header));
            for (String line : summaryLines) {
                opener.sendMessage(TextUtil.color("&7- " + line));
            }
        }

        scheduleCloseSequence(session);
    }

    private String resolveOpenerName(CaseSession session, Player opener) {
        if (opener != null) {
            String currentName = opener.getName();
            if (currentName != null && !currentName.isBlank()) {
                return currentName;
            }
        }

        String remembered = session.openerName();
        if (remembered != null && !remembered.isBlank()) {
            return remembered;
        }

        String offlineName = Bukkit.getOfflinePlayer(session.openerUuid()).getName();
        if (offlineName != null && !offlineName.isBlank()) {
            return offlineName;
        }

        return session.openerUuid().toString();
    }

    private void executeRewardCommand(RolledReward reward, String playerName) {
        String command = reward.command();
        if (command == null || command.isBlank()) {
            return;
        }
        String prepared = command.replace("{player}", playerName);
        if (prepared.startsWith("/")) {
            prepared = prepared.substring(1);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared);
    }

    private void broadcastLegendary(String playerName, RolledReward reward) {
        String template = plugin.getConfig().getString("messages.legendary-broadcast", "&6&l[LEGENDARY] &f{player} &7получил {item}");
        String message = template.replace("{player}", playerName).replace("{item}", reward.rewardDisplayName());
        Bukkit.broadcastMessage(TextUtil.color(message));
    }

    private void scheduleCloseSequence(CaseSession session) {
        if (session == null || session.isPaused() || session.isClosingSequenceStarted()) {
            return;
        }

        session.cancelCloseTask();
        session.cancelFinalizationTask();
        session.markClosingSequenceStarted();
        int step = Math.max(1, plugin.getConfig().getInt("case.chest-remove-step-ticks", 5));
        int delay = Math.max(0, plugin.getConfig().getInt("case.chest-close-delay-ticks", 35));
        int chestCount = session.getChestLocations().size();
        long totalDelay = (long) chestCount * step + delay;

        session.setCloseTask(new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    return;
                }
                for (int i = 0; i < chestCount; i++) {
                    final int index = i;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (isActive(session)) {
                            removeChestVisual(session, index);
                        }
                    }, (long) index * step);
                }
                session.setFinalizationTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!isActive(session)) {
                        return;
                    }
                    cleanupSession(session, false);
                    CaseSetup setup = session.setup();
                    if (setup != null) {
                        placeIdleCase(setup);
                    }
                }, totalDelay));
            }
        }.runTaskLater(plugin, 1L));
    }

    private void removeChestVisual(CaseSession session, int index) {
        if (!isActive(session)) {
            return;
        }
        Location chestLoc = session.getChestLocations().get(index);
        World world = chestLoc.getWorld();
        if (world == null) {
            return;
        }

        setChestOpenState(chestLoc, false);
        chestLoc.getBlock().setType(Material.AIR, false);
        ArmorStand open = session.getOpenTextHolograms().remove(index);
        if (open != null) {
            open.remove();
        }
        ArmorStand rewardText = session.getRewardTextHolograms().remove(index);
        if (rewardText != null) {
            rewardText.remove();
        }
        Item item = session.getRewardItems().remove(index);
        if (item != null) {
            item.remove();
        }

        world.spawnParticle(Particle.CLOUD, chestLoc.clone().add(0.5, 0.8, 0.5), 14, 0.15, 0.15, 0.15, 0.02);
        world.playSound(chestLoc.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_CHEST_CLOSE, 0.8f, 0.9f);
    }

    private void cleanupSession(CaseSession session, boolean keepBlocks) {
        if (session == null) {
            return;
        }

        activeSessions.remove(sessionKey(session.getCenter()), session);
        session.cancelTimer();
        session.cancelCloseTask();
        session.cancelFinalizationTask();
        for (ArmorStand stand : session.getOpenTextHolograms().values()) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        for (ArmorStand stand : session.getRewardTextHolograms().values()) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        for (Item item : session.getRewardItems().values()) {
            if (item != null && !item.isDead()) {
                item.remove();
            }
        }

        if (!keepBlocks && session.getCenter() != null) {
            clearCaseBlocks(session.getCenter());
        }

        session.clearState();
    }

    private void setChestOpenState(Location chestLoc, boolean open) {
        Block block = chestLoc.getBlock();
        if (block.getState() instanceof org.bukkit.block.Chest chestState) {
            if (open) {
                chestState.open();
            } else {
                chestState.close();
            }
            chestState.update(true, false);
        }
    }


    private BlockFace parseFacing(String raw) {
        if (raw == null || raw.isBlank()) {
            return BlockFace.NORTH;
        }
        try {
            return BlockFace.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BlockFace.NORTH;
        }
    }

    private void placeChestFacingCenter(Location chestLoc, Location center) {
        Block block = chestLoc.getBlock();
        block.setType(Material.CHEST, false);
        BlockData data = Bukkit.createBlockData(Material.CHEST);
        BlockFace face = facingToCenter(chestLoc, center);
        if (data instanceof Chest chestData) {
            chestData.setType(Chest.Type.SINGLE);
            chestData.setFacing(face);
            block.setBlockData(chestData, false);
        } else if (data instanceof Directional directional) {
            directional.setFacing(face);
            block.setBlockData(directional, false);
        }
    }

    private BlockFace facingToCenter(Location from, Location center) {
        int dx = center.getBlockX() - from.getBlockX();
        int dz = center.getBlockZ() - from.getBlockZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private Vector faceVector(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector(0, 0, -1);
            case SOUTH -> new Vector(0, 0, 1);
            case EAST -> new Vector(1, 0, 0);
            case WEST -> new Vector(-1, 0, 0);
            case NORTH_EAST -> new Vector(1, 0, -1).normalize();
            case NORTH_WEST -> new Vector(-1, 0, -1).normalize();
            case SOUTH_EAST -> new Vector(1, 0, 1).normalize();
            case SOUTH_WEST -> new Vector(-1, 0, 1).normalize();
            default -> new Vector(0, 0, 1);
        };
    }

    private RolledReward rollReward(UUID playerUuid) {
        if (!plugin.getConfig().getBoolean("luck.enabled", true) || playerUuid == null) {
            return rollRewardBase();
        }
        LuckProfile profile = plugin.getLuckStorage().getProfile(playerUuid);
        int balance = profile.balance();
        int rareBonus = profile.rareBonus();
        int tierDebt = profile.tierDebt();

        int total = 0;
        Map<RarityDefinition, Integer> weights = new LinkedHashMap<>();
        for (RarityDefinition rarity : rarities) {
            int weight = adjustedWeight(rarity, balance, rareBonus, tierDebt);
            weights.put(rarity, weight);
            total += weight;
        }
        if (weights.isEmpty()) {
            return fallbackReward();
        }
        int roll = random.nextInt(Math.max(1, total)) + 1;
        int current = 0;
        for (Map.Entry<RarityDefinition, Integer> entry : weights.entrySet()) {
            current += entry.getValue();
            if (roll <= current) {
                return entry.getKey().roll(random);
            }
        }
        return rollRewardBase();
    }

    private RolledReward rollRewardBase() {
        int total = 0;
        for (RarityDefinition rarity : rarities) {
            total += Math.max(1, rarity.chance());
        }
        if (total <= 0) {
            return fallbackReward();
        }
        int roll = random.nextInt(total) + 1;
        int current = 0;
        for (RarityDefinition rarity : rarities) {
            current += Math.max(1, rarity.chance());
            if (roll <= current) {
                return rarity.roll(random);
            }
        }
        return rarities.isEmpty() ? fallbackReward() : rarities.get(0).roll(random);
    }

    private RolledReward fallbackReward() {
        RewardDefinition reward = new RewardDefinition(Material.STONE, "&fStone", 1, 1, 10, "give {player} stone 1", List.of());
        RarityDefinition rarity = new RarityDefinition("rare", "&aRare", "&a", 1, false, List.of(reward));
        return new RolledReward(rarity, reward);
    }

    private int adjustedWeight(RarityDefinition rarity, int balance, int rareBonus, int tierDebt) {
        if (rarity == null) {
            return 1;
        }
        int base = Math.max(1, rarity.chance());
        int step = Math.max(1, plugin.getConfig().getInt("luck.balance-step", 40));
        int positive = Math.max(0, balance / step);
        int negative = Math.max(0, -balance / step);
        int rareBoostCap = Math.max(1, plugin.getConfig().getInt("luck.rare-bonus-cap", 500));
        int rareBoost = Math.min(rareBoostCap, Math.max(0, rareBonus));
        int debtStep = Math.max(0, tierDebt / step);

        String id = rarity.id().toLowerCase(Locale.ROOT);
        int weight = base;
        switch (id) {
            case "rare" -> {
                weight += rareBoost;
                weight += debtStep * Math.max(1, plugin.getConfig().getInt("luck.low-tier-boost-per-step", 1));
                weight += negative * Math.max(1, plugin.getConfig().getInt("luck.low-tier-boost-per-step", 1));
                weight -= positive * Math.max(1, plugin.getConfig().getInt("luck.rare-penalty-per-step", 1));
            }
            case "epic" -> {
                weight += Math.max(0, debtStep / 2);
                weight += negative * Math.max(1, plugin.getConfig().getInt("luck.epic-boost-per-step", 1));
                weight -= positive * Math.max(1, plugin.getConfig().getInt("luck.epic-penalty-per-step", 1));
            }
            case "mythical" -> {
                weight += positive * Math.max(1, plugin.getConfig().getInt("luck.high-tier-boost-per-step", 1));
                weight -= negative * Math.max(1, plugin.getConfig().getInt("luck.mythical-penalty-per-step", 1));
                weight -= debtStep * 4;
            }
            case "legendary" -> {
                weight += positive * Math.max(1, plugin.getConfig().getInt("luck.legendary-boost-per-step", 2));
                weight -= negative * Math.max(1, plugin.getConfig().getInt("luck.legendary-penalty-per-step", 2));
                weight -= debtStep * 6;
            }
            default -> {
                weight += positive;
                weight -= negative;
            }
        }
        return Math.max(1, weight);
    }

    private void applyLuck(UUID playerUuid, RolledReward reward) {
        if (playerUuid == null || reward == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("luck.enabled", true)) {
            return;
        }

        LuckProfile current = plugin.getLuckStorage().getProfile(playerUuid);
        int step = Math.max(1, plugin.getConfig().getInt("luck.balance-step", 40));
        int balanceMin = plugin.getConfig().getInt("luck.balance-min", -1200);
        int balanceMax = plugin.getConfig().getInt("luck.balance-max", 1200);

        int commonGainDivisor = Math.max(2, plugin.getConfig().getInt("luck.common-gain-divisor", 4));
        int rareGainDivisor = Math.max(2, plugin.getConfig().getInt("luck.rare-gain-divisor", 3));
        int epicGainDivisor = Math.max(2, plugin.getConfig().getInt("luck.epic-gain-divisor", 4));

        int rareBonusDecay = Math.max(0, plugin.getConfig().getInt("luck.rare-bonus-decay-per-roll", 3));
        int rareBonusConsume = Math.max(0, plugin.getConfig().getInt("luck.rare-bonus-consume-on-rare", 20));
        int rareBonusSpendMythical = Math.max(0, plugin.getConfig().getInt("luck.rare-bonus-spend-on-mythical", 55));
        int rareBonusSpendLegendary = Math.max(0, plugin.getConfig().getInt("luck.rare-bonus-spend-on-legendary", 95));

        int tierDebtDecay = Math.max(0, plugin.getConfig().getInt("luck.tier-debt-decay-per-roll", 4));
        int tierDebtGainRare = Math.max(0, plugin.getConfig().getInt("luck.tier-debt-gain-on-rare", 1));
        int tierDebtGainEpic = Math.max(0, plugin.getConfig().getInt("luck.tier-debt-gain-on-epic", 1));
        int tierDebtGainMythical = Math.max(0, plugin.getConfig().getInt("luck.tier-debt-gain-on-mythical", 18));
        int tierDebtGainLegendary = Math.max(0, plugin.getConfig().getInt("luck.tier-debt-gain-on-legendary", 30));

        int legendaryGainMultiplier = Math.max(1, plugin.getConfig().getInt("luck.legendary-rare-bonus-multiplier", 3));
        int legendaryRareBonusGain = Math.max(1, plugin.getConfig().getInt("luck.legendary-rare-bonus-gain", 12));
        int mythicalRareBonusGain = Math.max(1, plugin.getConfig().getInt("luck.mythical-rare-bonus-gain", 28));

        int delta = Math.max(1, reward.reward().value() / step);
        int balance = current.balance();
        int rareBonus = current.rareBonus();
        int tierDebt = Math.max(0, current.tierDebt() - tierDebtDecay);

        String rarityId = reward.rarity().id().toLowerCase(Locale.ROOT);
        switch (rarityId) {
            case "rare" -> {
                balance += Math.max(1, delta / rareGainDivisor);
                rareBonus = Math.max(0, rareBonus - rareBonusConsume);
                tierDebt += tierDebtGainRare;
            }
            case "epic" -> {
                balance += Math.max(1, delta / epicGainDivisor);
                rareBonus = Math.max(0, rareBonus - rareBonusDecay);
                tierDebt += tierDebtGainEpic;
            }
            case "mythical" -> {
                balance -= Math.max(1, delta * 5);
                rareBonus = Math.max(0, rareBonus - rareBonusSpendMythical);
                rareBonus += Math.max(1, Math.min(delta * legendaryGainMultiplier, mythicalRareBonusGain));
                tierDebt += Math.max(tierDebtGainMythical, delta * 3);
            }
            case "legendary" -> {
                balance -= Math.max(1, delta * 9);
                rareBonus = Math.max(0, rareBonus - rareBonusSpendLegendary);
                rareBonus += Math.max(1, Math.min(delta * legendaryGainMultiplier, legendaryRareBonusGain));
                tierDebt += Math.max(tierDebtGainLegendary, delta * 5);
            }
            default -> {
                balance += Math.max(1, delta / commonGainDivisor);
                rareBonus = Math.max(0, rareBonus - rareBonusDecay);
            }
        }

        balance = Math.max(balanceMin, Math.min(balanceMax, balance));
        plugin.getLuckStorage().saveProfile(playerUuid, new LuckProfile(balance, rareBonus, tierDebt));
    }

    private boolean isActive(CaseSession session) {
        return session != null && activeSessions.get(sessionKey(session.getCenter())) == session;
    }

    private void pauseSessionForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        for (CaseSession session : new ArrayList<>(activeSessions.values())) {
            if (playerUuid.equals(session.openerUuid()) && !session.isCompletionTriggered() && !session.isClosingSequenceStarted()) {
                session.pause();
                session.cancelTimer();
                session.cancelCloseTask();
                session.cancelFinalizationTask();
            }
        }
    }

    private void resumeSessionForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        for (CaseSession session : new ArrayList<>(activeSessions.values())) {
            if (!playerUuid.equals(session.openerUuid()) || !session.isPaused()) {
                continue;
            }
            session.resume();
            if (session.isCompletionTriggered()) {
                triggerCompletionCountdown(session);
            } else if (session.openedCount() >= CaseSession.MAX_OPENED) {
                triggerCompletionCountdown(session);
            } else {
                startOpeningTimer(session);
            }
            return;
        }
    }

    private void forceFinalizeAllSessions() {
        List<CaseSession> sessions = new ArrayList<>(activeSessions.values());
        for (CaseSession session : sessions) {
            forceFinalizeSession(session);
        }
    }

    private void forceFinalizeSession(CaseSession session) {
        if (session == null || !isActive(session)) {
            return;
        }
        session.cancelTimer();
        session.cancelCloseTask();
        session.cancelFinalizationTask();
        session.resume();
        session.markCompletionTriggered();

        Player opener = Bukkit.getPlayer(session.openerUuid());
        String openerName = resolveOpenerName(session, opener);
        List<String> summaryLines = new ArrayList<>();
        for (int i = 0; i < session.getChestLocations().size(); i++) {
            RolledReward rolledReward = session.getRolledRewards().get(i);
            if (rolledReward == null) {
                continue;
            }
            summaryLines.add(rolledReward.summaryLine());
            executeRewardCommand(rolledReward, openerName);
            if (rolledReward.isLegendary()) {
                broadcastLegendary(openerName, rolledReward);
            }
        }

        if (opener != null && opener.isOnline()) {
            String header = plugin.getConfig().getString("messages.rewards-header", "&aТы получил:");
            opener.sendMessage(TextUtil.color(header));
            for (String line : summaryLines) {
                opener.sendMessage(TextUtil.color("&7- " + line));
            }
        }

        cleanupSession(session, false);
    }

    private CaseSession findSessionForChest(Location location) {
        if (location == null) {
            return null;
        }
        for (CaseSession session : activeSessions.values()) {
            for (Location chestLocation : session.getChestLocations()) {
                if (sameLocation(location, chestLocation)) {
                    return session;
                }
            }
        }
        return null;
    }

    private CaseLocationKey sessionKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Location blockLocation = location.getBlock().getLocation();
        return new CaseLocationKey(blockLocation.getWorld().getUID(), blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());
    }

    private boolean sameLocation(Location a, Location b) {
        return a != null && b != null
                && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private record CaseLocationKey(UUID worldId, int x, int y, int z) {}
}
