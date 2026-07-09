package me.lex.lexcase.command;

import me.lex.lexcase.LexCasePlugin;
import me.lex.lexcase.core.CaseManager;
import me.lex.lexcase.storage.KeyStorage;
import me.lex.lexcase.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LcCommand implements CommandExecutor, org.bukkit.command.TabCompleter {

    private static final String PREFIX = "&8&l[&6LexCase&8&l] &7";
    private static final String HEADER = "&8&m────────────────────────&r &6&lLexCase &8&m────────────────────────";

    private final LexCasePlugin plugin;
    private final CaseManager caseManager;
    private final KeyStorage keyStorage;

    public LcCommand(LexCasePlugin plugin, CaseManager caseManager, KeyStorage keyStorage) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.keyStorage = keyStorage;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> {
                sendHelp(sender, label);
                return true;
            }
            case "setup" -> {
                if (!(sender instanceof Player player)) {
                    sendStyled(sender, plugin.getConfig().getString("messages.player-only", "&cКоманда доступна только игроку."));
                    return true;
                }
                if (!sender.hasPermission("lexcase.admin")) {
                    sendStyled(sender, plugin.getConfig().getString("messages.no-permission", "&cНет прав."));
                    return true;
                }
                caseManager.addOrUpdateSetup(player.getLocation(), yawToFacing(player.getLocation().getYaw()));
                sendStyled(sender, "&aТочка кейса установлена.");
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("lexcase.admin")) {
                    sendStyled(sender, plugin.getConfig().getString("messages.no-permission", "&cНет прав."));
                    return true;
                }
                plugin.reloadPluginData();
                sendStyled(sender, "&aLexCase перезагружен.");
                return true;
            }
            case "delete" -> {
                if (!(sender instanceof Player player)) {
                    sendStyled(sender, plugin.getConfig().getString("messages.player-only", "&cКоманда доступна только игроку."));
                    return true;
                }
                if (!sender.hasPermission("lexcase.admin")) {
                    sendStyled(sender, plugin.getConfig().getString("messages.no-permission", "&cНет прав."));
                    return true;
                }
                boolean removed = caseManager.deleteSetupAt(player.getLocation());
                sendStyled(sender, removed
                        ? "&aТекущий setup удалён."
                        : "&cНа этой позиции нет setup.");
                return true;
            }
            case "givekey" -> {
                if (!sender.hasPermission("lexcase.givekey")) {
                    sendStyled(sender, plugin.getConfig().getString("messages.no-permission", "&cНет прав."));
                    return true;
                }
                if (args.length < 3) {
                    sendStyled(sender, "&cИспользование: &e/" + label + " givekey <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sendStyled(sender, "&cИгрок не найден.");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sendStyled(sender, "&cAmount должен быть числом.");
                    return true;
                }
                if (amount <= 0) {
                    sendStyled(sender, "&cAmount должен быть больше 0.");
                    return true;
                }
                keyStorage.addKeys(target.getUniqueId(), amount);
                sendStyled(sender, "&aВыдано ключей: &e" + amount + " &aигроку &e" + target.getName());
                target.sendMessage(TextUtil.color(PREFIX + "&aТебе выдали &e" + amount + " &aключей для LexCase."));
                return true;
            }
            case "removekey" -> {
                if (!sender.hasPermission("lexcase.givekey")) {
                    sendStyled(sender, plugin.getConfig().getString("messages.no-permission", "&cНет прав."));
                    return true;
                }
                if (args.length < 3) {
                    sendStyled(sender, "&cИспользование: &e/" + label + " removekey <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sendStyled(sender, "&cИгрок не найден.");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    sendStyled(sender, "&cAmount должен быть числом.");
                    return true;
                }
                if (amount <= 0) {
                    sendStyled(sender, "&cAmount должен быть больше 0.");
                    return true;
                }
                int before = keyStorage.getKeys(target.getUniqueId());
                keyStorage.removeKeys(target.getUniqueId(), amount);
                int removed = Math.min(before, amount);
                sendStyled(sender, "&aЗабрано ключей: &e" + removed + " &aу игрока &e" + target.getName());
                target.sendMessage(TextUtil.color(PREFIX + "&cУ тебя забрали &e" + removed + " &cключей для LexCase."));
                return true;
            }
            default -> {
                sendStyled(sender, "&cНеизвестная подкоманда. Используй &e/" + label + " help&c.");
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(TextUtil.color(HEADER));
        sender.sendMessage(TextUtil.color(PREFIX + "&fУправление кейсами и ключами"));
        sender.sendMessage(TextUtil.color("&8 • &e/" + label + " setup &7- &fУстановить точку кейса на своём блоке"));
        sender.sendMessage(TextUtil.color("&8 • &e/" + label + " givekey <player> <amount> &7- &fВыдать ключи игроку"));
        sender.sendMessage(TextUtil.color("&8 • &e/" + label + " removekey <player> <amount> &7- &fЗабрать ключи у игрока"));
        sender.sendMessage(TextUtil.color("&8 • &e/" + label + " reload &7- &fПерезагрузить конфиг и данные"));
        sender.sendMessage(TextUtil.color("&8 • &e/" + label + " delete &7- &fУдалить setup на твоей позиции"));
        sender.sendMessage(TextUtil.color("&8 • &e/" + label + " help &7- &fПоказать это меню"));
    }

    private void sendStyled(CommandSender sender, String message) {
        sender.sendMessage(TextUtil.color(PREFIX + message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("setup", "givekey", "removekey", "reload", "delete", "help"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("givekey") || args[0].equalsIgnoreCase("removekey"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private BlockFace yawToFacing(float yaw) {
        float normalized = (yaw % 360.0f + 360.0f) % 360.0f;
        if (normalized >= 45.0f && normalized < 135.0f) {
            return BlockFace.WEST;
        }
        if (normalized >= 135.0f && normalized < 225.0f) {
            return BlockFace.NORTH;
        }
        if (normalized >= 225.0f && normalized < 315.0f) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }
}
