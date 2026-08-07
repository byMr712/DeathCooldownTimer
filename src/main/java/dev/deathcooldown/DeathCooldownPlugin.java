package dev.deathcooldown;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DeathCooldownPlugin extends JavaPlugin implements Listener {

    private DeathCooldownManager manager;

    public DeathCooldownManager getManager() {
        return manager;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        manager = new DeathCooldownManager(this);
        manager.loadConfig(getConfig());
        manager.loadDeathCounts();
        manager.registerPacketListener();

        Bukkit.getPluginManager().registerEvents(this, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DeathCooldownExpansion(this).register();
            getLogger().info(manager.lang("PlaceholderAPI found: %deathcooldown_% placeholders registered.",
                    "PlaceholderAPI найден: плейсхолдеры %deathcooldown_% зарегистрированы."));
        }

        long updateTicks = Math.max(1, manager.getUpdateTicks());
        getServer().getScheduler().runTaskTimer(this, manager::tick, updateTicks, updateTicks);

        getLogger().info(manager.lang("DeathCooldownTimer enabled. Respawn timer active.",
                "DeathCooldownTimer включён. Таймер возрождения активен."));
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveDeathCounts();
            manager.unregisterPacketListener();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeathBeforeLives(PlayerDeathEvent event) {
        manager.debugDeathBeforeLL(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        manager.onDeath(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("deathcooldown.admin")) {
                reloadConfig();
                manager.loadConfig(getConfig());
                sender.sendMessage(manager.colorize(manager.lang("&aDeathCooldownTimer: config reloaded.",
                        "&aDeathCooldownTimer: конфиг перезагружен.")));
                return true;
            }
            if (args[0].equalsIgnoreCase("reset") && sender.hasPermission("deathcooldown.admin")) {
                String targetArg = args.length >= 2 ? args[1] : (sender instanceof Player ? sender.getName() : null);
                if (targetArg == null) {
                    sender.sendMessage(manager.colorize(manager.lang("&cSpecify a player: /" + label + " reset <player>",
                            "&cУкажи игрока: /" + label + " reset <игрок>")));
                    return true;
                }
                List<Player> targets = resolveTargets(targetArg, sender);
                if (targets.isEmpty()) {
                    sender.sendMessage(manager.colorize(manager.lang("&cPlayer " + targetArg + " not found online.",
                            "&cИгрок " + targetArg + " не найден в сети.")));
                    return true;
                }
                for (Player target : targets) {
                    manager.resetDeathCount(target);
                }
                if (targets.size() == 1) {
                    sender.sendMessage(manager.colorize(manager.lang("&aDeath counter reset for " + targets.get(0).getName() + ".",
                            "&aСчётчик смертей сброшен для " + targets.get(0).getName() + ".")));
                } else {
                    sender.sendMessage(manager.colorize(manager.lang("&aDeath counter reset for &6" + targets.size() + "&a player(s).",
                            "&aСчётчик смертей сброшен для &6" + targets.size() + "&a игрока(ов).")));
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("revive") && sender.hasPermission("deathcooldown.admin")) {
                if (args.length < 2) {
                    sender.sendMessage(manager.colorize(manager.lang("&cSpecify a player: /" + label + " revive <player>",
                            "&cУкажи игрока: /" + label + " revive <игрок>")));
                    return true;
                }
                List<Player> targets = resolveTargets(args[1], sender);
                if (targets.isEmpty()) {
                    sender.sendMessage(manager.colorize(manager.lang("&cPlayer " + args[1] + " not found online.",
                            "&cИгрок " + args[1] + " не найден в сети.")));
                    return true;
                }
                if (targets.size() == 1) {
                    Player target = targets.get(0);
                    if (!manager.isOnCooldown(target)) {
                        sender.sendMessage(manager.colorize(manager.lang("&ePlayer " + target.getName() + " is not on cooldown.",
                                "&eИгрок " + target.getName() + " сейчас не в кулдауне.")));
                        return true;
                    }
                    manager.revivePlayer(target);
                    sender.sendMessage(manager.colorize(manager.lang("&aPlayer " + target.getName() + " revived by an admin.",
                            "&aИгрок " + target.getName() + " возрождён админом.")));
                    return true;
                }
                int count = 0;
                for (Player target : targets) {
                    if (manager.isOnCooldown(target)) {
                        manager.revivePlayer(target);
                        count++;
                    }
                }
                sender.sendMessage(manager.colorize(manager.lang("&aRevived &6" + count + "&a player(s).",
                        "&aВозрождено игроков: &6" + count + "&a.")));
                return true;
            }
            if (args[0].equalsIgnoreCase("level") && sender.hasPermission("deathcooldown.admin")) {
                if (args.length < 3) {
                            sender.sendMessage(manager.colorize(manager.lang("&cUsage: /" + label
                                    + " level <player> <status|plus <number>|minus <number>|set <level>|reset>",
                                    "&cИспользование: /" + label
                                    + " level <игрок> <status|plus <число>|minus <число>|set <уровень>|reset>")));
                    return true;
                }
                List<Player> targets = resolveTargets(args[1], sender);
                if (targets.isEmpty()) {
                    sender.sendMessage(manager.colorize(manager.lang("&cPlayer " + args[1] + " not found online.",
                            "&cИгрок " + args[1] + " не найден в сети.")));
                    return true;
                }
                String action = args[2].toLowerCase();
                switch (action) {
                    case "status": {
                        for (Player target : targets) {
                            int level = manager.getLevel(target);
                            long remaining = manager.getRedemptionRemaining(target);
                            sender.sendMessage(manager.colorize(manager.lang("&6" + target.getName() + " &7-> redemption level &6" + level
                                    + " &7[" + manager.formatTime(manager.getLevelTime(level)) + "]",
                                    "&6" + target.getName() + " &7-> уровень искупления &6" + level
                                    + " &7[" + manager.formatTime(manager.getLevelTime(level)) + "]")));
                            sender.sendMessage(manager.colorize(manager.lang("&7Time left until redemption: &6"
                                    + manager.formatRoundedTime(remaining),
                                    "&7Осталось до искупления: &6"
                                    + manager.formatRoundedTime(remaining))));
                            sender.sendMessage(manager.colorize(manager.lang("&7Next death timer: &6"
                                    + manager.formatTime(manager.getLevelTime(level + 1)),
                                    "&7Таймер следующей смерти: &6"
                                    + manager.formatTime(manager.getLevelTime(level + 1)))));
                        }
                        return true;
                    }
                    case "plus":
                    case "minus": {
                        if (args.length < 4) {
                            sender.sendMessage(manager.colorize(manager.lang("&cSpecify a number: /" + label
                                    + " level <player> " + action + " <number>",
                                    "&cУкажи число: /" + label
                                    + " level <игрок> " + action + " <число>")));
                            return true;
                        }
                        int amount;
                        try {
                            amount = Integer.parseInt(args[3]);
                        } catch (NumberFormatException ex) {
                            sender.sendMessage(manager.colorize(manager.lang("&cThe number must be a number.",
                                    "&cЧисло должно быть числом.")));
                            return true;
                        }
                        if (amount < 1) {
                            sender.sendMessage(manager.colorize(manager.lang("&cThe number must be at least 1.",
                                    "&cЧисло должно быть не меньше 1.")));
                            return true;
                        }
                        for (Player target : targets) {
                            if (action.equals("plus")) {
                                int current = manager.getLevel(target);
                                int max = manager.getLevelCount();
                                if (current >= max) {
                                    sender.sendMessage(manager.colorize(manager.lang("&cCannot raise the redemption level of &6" + target.getName()
                                            + "&c: they are already at the maximum level (&6" + max + "&c).",
                                            "&cНельзя повысить уровень искупления &6" + target.getName()
                                            + "&c: он уже на максимальном уровне (&6" + max + "&c).")));
                                    continue;
                                }
                                int next = Math.min(current + amount, max);
                                manager.setLevel(target, next);
                                sender.sendMessage(manager.colorize(manager.lang("&aRedemption level of &6" + target.getName()
                                        + " &araised: &6" + next,
                                        "&aУровень искупления &6" + target.getName()
                                        + " &aповышен: &6" + next)));
                            } else {
                                int current = manager.getLevel(target);
                                if (current <= 0) {
                                    sender.sendMessage(manager.colorize(manager.lang("&cCannot lower the redemption level of &6" + target.getName()
                                            + "&c: it is already at the zero level.",
                                            "&cНельзя понизить уровень искупления &6" + target.getName()
                                            + "&c: он уже на нулевом уровне.")));
                                    continue;
                                }
                                int next = Math.max(0, current - amount);
                                manager.setLevel(target, next);
                                sender.sendMessage(manager.colorize(manager.lang("&aRedemption level of &6" + target.getName()
                                        + " &alowered: &6" + next,
                                        "&aУровень искупления &6" + target.getName()
                                        + " &aпонижен: &6" + next)));
                            }
                        }
                        return true;
                    }
                    case "set": {
                        if (args.length < 4) {
                            sender.sendMessage(manager.colorize(manager.lang("&cSpecify a level: /" + label
                                    + " level <player> set <level>",
                                    "&cУкажи уровень: /" + label
                                    + " level <игрок> set <уровень>")));
                            return true;
                        }
                        int value;
                        try {
                            value = Integer.parseInt(args[3]);
                        } catch (NumberFormatException ex) {
                            sender.sendMessage(manager.colorize(manager.lang("&cThe level must be a number.",
                                    "&cУровень должен быть числом.")));
                            return true;
                        }
                        if (value < 0) {
                            sender.sendMessage(manager.colorize(manager.lang("&cThe redemption level must be at least 0.",
                                    "&cУровень искупления должен быть не меньше 0.")));
                            return true;
                        }
                        for (Player target : targets) {
                            manager.setLevel(target, value);
                            sender.sendMessage(manager.colorize(manager.lang("&aRedemption level of &6" + target.getName()
                                    + " &aset: &6" + value,
                                    "&aУровень искупления &6" + target.getName()
                                    + " &aустановлен: &6" + value)));
                        }
                        return true;
                    }
                    case "reset": {
                        for (Player target : targets) {
                            manager.setLevel(target, 0);
                            sender.sendMessage(manager.colorize(manager.lang("&aRedemption level of &6" + target.getName()
                                    + " &areset to zero.",
                                    "&aУровень искупления &6" + target.getName()
                                    + " &aсброшен до нулевого.")));
                        }
                        return true;
                    }
                    default:
                        sender.sendMessage(manager.colorize(manager.lang("&cUnknown action: &6" + action
                                + "&c. Available: status, plus, minus, set, reset",
                                "&cНеизвестное действие: &6" + action
                                + "&c. Доступно: status, plus, minus, set, reset")));
                        return true;
                }
            }
        }
        sender.sendMessage(manager.lang("Usage: /" + label
                + " reload | reset [player] | revive <player|@a|@r|@p> | level <player> <status|plus|minus|set|reset>",
                "Использование: /" + label
                + " reload | reset [игрок] | revive <игрок|@a|@r|@p> | level <игрок> <status|plus|minus|set|reset>"));
        return true;
    }

    private List<Player> resolveTargets(String input, CommandSender sender) {
        List<Player> result = new ArrayList<>();
        if (input.equalsIgnoreCase("@a")) {
            result.addAll(Bukkit.getOnlinePlayers());
        } else if (input.equalsIgnoreCase("@r")) {
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (!online.isEmpty()) {
                result.add(online.get(ThreadLocalRandom.current().nextInt(online.size())));
            }
        } else if (input.equalsIgnoreCase("@p")) {
            if (sender instanceof Player) {
                Player source = (Player) sender;
                Player nearest = null;
                double best = Double.MAX_VALUE;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    double d = source.getLocation().distanceSquared(online.getLocation());
                    if (d < best) {
                        best = d;
                        nearest = online;
                    }
                }
                if (nearest != null) {
                    result.add(nearest);
                }
            } else {
                List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (!online.isEmpty()) {
                    result.add(online.get(ThreadLocalRandom.current().nextInt(online.size())));
                }
            }
        } else {
            Player exact = Bukkit.getPlayerExact(input);
            if (exact != null) {
                result.add(exact);
            }
        }
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("deathcooldown.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String sub : new String[]{"reload", "reset", "revive", "level"}) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("reset")
                || args[0].equalsIgnoreCase("revive")
                || args[0].equalsIgnoreCase("level"))) {
            String prefix = args[1].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String sel : new String[]{"@a", "@r", "@p"}) {
                if (sel.startsWith(prefix)) {
                    result.add(sel);
                }
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    result.add(player.getName());
                }
            }
            return result;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("level")) {
            String prefix = args[2].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String action : new String[]{"status", "plus", "minus", "set", "reset"}) {
                if (action.startsWith(prefix)) {
                    result.add(action);
                }
            }
            return result;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("level")
                && (args[2].equalsIgnoreCase("set") || args[2].equalsIgnoreCase("plus") || args[2].equalsIgnoreCase("minus"))) {
            String prefix = args[3];
            List<String> result = new ArrayList<>();
            if (args[2].equalsIgnoreCase("set")) {
                for (int i = 0; i <= manager.getLevelCount(); i++) {
                    String level = String.valueOf(i);
                    if (level.startsWith(prefix)) {
                        result.add(level);
                    }
                }
            } else {
                for (int i = 1; i <= 9; i++) {
                    String level = String.valueOf(i);
                    if (level.startsWith(prefix)) {
                        result.add(level);
                    }
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
