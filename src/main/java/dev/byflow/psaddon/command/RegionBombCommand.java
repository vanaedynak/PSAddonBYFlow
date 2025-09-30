package dev.byflow.psaddon.command;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.tnt.RegionBombManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RegionBombCommand implements CommandExecutor, TabCompleter {
    private final PSAddonPlugin plugin;

    public RegionBombCommand(PSAddonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        RegionBombManager manager = plugin.getRegionBombManager();
        if (manager == null || !manager.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Регионный динамит отключён на сервере.");
            return true;
        }

        if (!sender.hasPermission("psaddon.regionbomb")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для выполнения этой команды.");
            return true;
        }

        Player target;
        int amount = 1;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Использование: /" + label + " <ник> [количество]");
                return true;
            }
            target = player;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок не найден: " + args[0]);
                return true;
            }
            if (args.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[1]));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Количество должно быть числом.");
                    return true;
                }
            }
        }

        ItemStack item = manager.createItem(amount);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        }

        if (sender != target) {
            sender.sendMessage(ChatColor.GREEN + "Вы выдали " + target.getName() + " " + amount
                    + " шт. регионального динамита.");
        }
        target.sendMessage(ChatColor.GREEN + "Вы получили " + amount + " шт. регионального динамита.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                String name = player.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(name);
                }
            }
            Collections.sort(suggestions, String.CASE_INSENSITIVE_ORDER);
            return suggestions;
        }
        return List.of();
    }
}
