package dev.byflow.psaddon.tnt.command;

import dev.byflow.psaddon.tnt.CustomTntManager;
import dev.byflow.psaddon.tnt.config.CustomTntType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CustomTntCommand implements CommandExecutor, TabCompleter {
    private final CustomTntManager manager;

    public CustomTntCommand(CustomTntManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.RED + "Использование: /" + label + " give <игрок> <тип> [кол-во]");
            return true;
        }
        if (!sender.hasPermission("psaddon.customtnt.give")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав на эту команду.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Игрок не найден: " + args[1]);
            return true;
        }

        Optional<CustomTntType> typeOptional = manager.getType(args[2]);
        if (typeOptional.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Неизвестный тип динамита: " + args[2]);
            return true;
        }
        CustomTntType type = typeOptional.get();

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Неверное количество: " + args[3]);
                return true;
            }
        }

        ItemStack item = manager.createItem(type, amount);
        target.getInventory().addItem(item);
        sender.sendMessage(ChatColor.GREEN + "Выдано " + amount + " шт. динамита " + type.id() + " игроку " + target.getName());
        if (!sender.equals(target)) {
            target.sendMessage(ChatColor.GREEN + "Вы получили " + amount + " шт. динамита " + type.id() + ".");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            if ("give".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                suggestions.add("give");
            }
            return suggestions;
        }
        if (!args[0].equalsIgnoreCase("give")) {
            return suggestions;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(player.getName());
                }
            }
            return suggestions;
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (CustomTntType type : manager.getTypes()) {
                if (type.id().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(type.id());
                }
            }
            return suggestions;
        }
        if (args.length == 4) {
            suggestions.add("1");
            suggestions.add("8");
            suggestions.add("16");
            suggestions.add("32");
            suggestions.add("64");
            return suggestions;
        }
        return suggestions;
    }
}
