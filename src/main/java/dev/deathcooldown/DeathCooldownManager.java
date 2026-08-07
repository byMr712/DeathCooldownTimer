package dev.deathcooldown;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathCooldownManager {

    private static final class Cooldown {
        private final long deathTime;
        private final long durationMillis;

        private Cooldown(long deathTime, long durationMillis) {
            this.deathTime = deathTime;
            this.durationMillis = durationMillis;
        }

        private long remaining() {
            return deathTime + durationMillis - System.currentTimeMillis();
        }
    }

    private final Plugin plugin;
    private final ProtocolManager protocolManager;
    private final Map<UUID, Integer> deathCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Cooldown> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> suppressNextDeath = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> rejoinLivesAtKill = new ConcurrentHashMap<>();

    private List<Long> levels = new ArrayList<>();
    private boolean resetAfterLastLevel = true;
    private boolean persistCounter = true;
    private boolean useTitle = false;
    private String titleText = "&cYou died!";
    private boolean useSubtitle = false;
    private String subtitleText = "&eRespawning in &c{time}";
    private boolean useActionbar = true;
    private boolean actionbarBold = true;
    private String actionbarText = "&eYou will respawn in &c{time}";
    private long updateTicks = 20;
    private String timeFormat = "short";
    private boolean useDeathMessage = true;
    private String deathMessage = "&cYou died! Respawn in &6{time}&c. Total deaths: &6{deaths}";
    private boolean useRespawnedMessage = true;
    private String respawnedMessage = "&aYou respawned!";
    private Set<String> enabledWorlds = new HashSet<>();
    private boolean debug;
    private boolean limitedLivesEnabled;
    private int limitedSkipLivesAtOrBelow = 1;
    private boolean resumeOnRejoin = true;
    private final Map<UUID, Integer> playerLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dropLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> respawnTimes = new ConcurrentHashMap<>();
    private boolean redemptionEnabled;
    private double dropMultiplier = 3;
    private String language = "en";
    private String levelUpMessage = "";
    private String levelDownMessage = "";
    private String livesMessage = "&eLives left: &6{lives}";
    private String redemptionMessage = "&eTime left until redemption: &6{time}";

    public DeathCooldownManager(Plugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void loadConfig(FileConfiguration cfg) {
        debug = cfg.getBoolean("debug", false);
        language = cfg.getString("language", "en");
        levels = new ArrayList<>();
        for (String raw : cfg.getStringList("levels")) {
            Long ms = parseLevel(raw);
            if (ms != null) {
                levels.add(ms);
            }
        }
        if (levels.isEmpty()) {
            levels.add(10_000L);
        }
        resetAfterLastLevel = cfg.getBoolean("reset-after-last-level", true);
        persistCounter = cfg.getBoolean("persist-death-counter", true);

        ConfigurationSection display = cfg.getConfigurationSection("display");
        if (display != null) {
            useTitle = display.getBoolean("use-title", false);
            titleText = localized(display, "title", "&cYou died!", "&cТы умер!");
            useSubtitle = display.getBoolean("use-subtitle", false);
            subtitleText = localized(display, "subtitle", "&eRespawning in &c{time}", "&eВозрождение через &c{time}");
            useActionbar = display.getBoolean("use-actionbar", true);
            actionbarBold = display.getBoolean("actionbar-bold", true);
            actionbarText = localized(display, "actionbar", "&eYou will respawn in &c{time}", "&eВы возродитесь через &c{time}");
            updateTicks = Math.max(1, display.getLong("update-ticks", 20));
        }
        timeFormat = cfg.getString("time-format", "short");
        useDeathMessage = cfg.getBoolean("use-death-message", true);
        deathMessage = localized(cfg, "death-message",
                "&cYou died! Respawn in &6{time}&c. Total deaths: &6{deaths}",
                "&cТы умер! Возрождение через &6{time}&c. Всего смертей: &6{deaths}");
        useRespawnedMessage = cfg.getBoolean("use-respawned-message", true);
        respawnedMessage = localized(cfg, "respawned-message", "&aYou respawned!", "&aТы возродился!");
        enabledWorlds = new HashSet<>(cfg.getStringList("enabled-worlds"));
        resumeOnRejoin = cfg.getBoolean("resume-on-rejoin", true);
        redemptionEnabled = cfg.getBoolean("redemption-enabled", false);
        dropMultiplier = Math.max(1, cfg.getDouble("drop-multiplier", 3));
        levelUpMessage = localized(cfg, "level-up-message", "&cYour redemption level increased: &6{level}", "&cТвой уровень искупления повышен: &6{level}");
        levelDownMessage = localized(cfg, "level-down-message", "&aRedemption level decreased: &6{level}", "&aУровень искупления понижен: &6{level}");
        livesMessage = localized(cfg, "lives-message", "&eLives left: &6{lives}", "&eЖизней осталось: &6{lives}");
        redemptionMessage = localized(cfg, "redemption-message", "&eTime left until redemption: &6{time}", "&eОсталось до искупления: &6{time}");

        ConfigurationSection limited = cfg.getConfigurationSection("limited-lives");
        if (limited != null) {
            limitedSkipLivesAtOrBelow = Math.max(0, limited.getInt("skip-if-lives-at-or-below", 1));
        } else {
            limitedSkipLivesAtOrBelow = 1;
        }
        limitedLivesEnabled = isLimitedLivesLoaded()
                && (limited == null || limited.getBoolean("enabled", true));
    }

    public boolean isRussian() {
        return "ru".equalsIgnoreCase(language);
    }

    public String lang(String en, String ru) {
        return isRussian() ? ru : en;
    }

    private String localized(FileConfiguration cfg, String key, String enDefault, String ruDefault) {
        String value = cfg.getString(key);
        if (value == null || value.isEmpty()) {
            return isRussian() ? ruDefault : enDefault;
        }
        if (value.equals(enDefault) || value.equals(ruDefault)) {
            return isRussian() ? ruDefault : enDefault;
        }
        return value;
    }

    private String localized(ConfigurationSection section, String key, String enDefault, String ruDefault) {
        String value = section.getString(key);
        if (value == null || value.isEmpty()) {
            return isRussian() ? ruDefault : enDefault;
        }
        if (value.equals(enDefault) || value.equals(ruDefault)) {
            return isRussian() ? ruDefault : enDefault;
        }
        return value;
    }

    private boolean isLimitedLivesLoaded() {
        try {
            return Bukkit.getPluginManager().getPlugin("LimitedLives")
                    instanceof xyz.srnyx.limitedlives.LimitedLives;
        } catch (Throwable ex) {
            return false;
        }
    }

    public void debugDeathBeforeLL(Player player) {
        debug("[onDeath-BEFORE-LL] " + player.getName() + " world=" + player.getWorld().getName()
                + " livesBeforeLL=" + getLimitedLives(player)
                + " bypassPerm=" + player.hasPermission("limitedlives.bypass")
                + " op=" + player.isOp());
    }

    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        debug("[onDeath] " + player.getName() + " world=" + player.getWorld().getName()
                + " limitedLivesEnabled=" + limitedLivesEnabled
                + " livesNow=" + getLimitedLives(player));
        Long suppressUntil = suppressNextDeath.remove(player.getUniqueId());
        if (suppressUntil != null && suppressUntil > System.currentTimeMillis()) {
            debug("[onDeath] " + player.getName() + " -> suppressed (suppressNextDeath), выход");
            event.setDeathMessage(null);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            return;
        }
        if (!worldEnabled(player)) {
            debug("[onDeath] " + player.getName() + " -> мир не в списке, выход");
            return;
        }
        if (shouldSkipLimitedLives(player)) {
            debug("[onDeath] " + player.getName() + " -> shouldSkipLimitedLives=true, выход (кулдаун не запускаем)");
            return;
        }
        UUID uuid = player.getUniqueId();
        int deaths = deathCounts.merge(uuid, 1, Integer::sum);
        long duration;
        int levelNumber;
        if (redemptionEnabled) {
            int current = Math.max(0, playerLevels.getOrDefault(uuid, 0));
            int next = Math.min(current + 1, levels.size());
            duration = levelTime(next);
            levelNumber = next;
            dropLevels.put(uuid, next);
            playerLevels.put(uuid, next);
            respawnTimes.put(uuid, 0L);
            debug("[onDeath] " + player.getName() + " redemption level " + current + " -> " + next
                    + " duration=" + duration);
            if (!levelUpMessage.isEmpty() && !mutedByZeroLives(player)) {
                player.sendMessage(colorize(levelUpMessage
                        .replace("{level}", String.valueOf(next))
                        .replace("{oldlevel}", String.valueOf(current))));
            }
        } else {
            duration = levelDuration(deaths);
            levelNumber = ((deaths - 1) % levels.size()) + 1;
        }
        cooldowns.put(uuid, new Cooldown(System.currentTimeMillis(), duration));
        debug("[onDeath] " + player.getName() + " deaths=" + deaths + " duration=" + duration
                + " levelNumber=" + levelNumber + " livesAfterDeath=" + getLimitedLives(player));
        if (persistCounter) {
            saveDeathCountsAsync();
        }
        if (useDeathMessage && !mutedByZeroLives(player)) {
            player.sendMessage(colorize(deathMessage
                    .replace("{time}", formatTime(duration))
                    .replace("{deaths}", String.valueOf(deaths))
                    .replace("{level}", String.valueOf(levelNumber))));
        }
        sendDisplay(player);
    }

    public void onJoin(Player player) {
        UUID uuid = player.getUniqueId();
        Cooldown cooldown = cooldowns.get(uuid);
        debug("[onJoin] " + player.getName() + " world=" + player.getWorld().getName()
                + " cooldown=" + (cooldown != null ? cooldown.remaining() + "ms" : "null")
                + " lives=" + getLimitedLives(player));
        if (redemptionEnabled && respawnTimes.getOrDefault(uuid, 0L) <= 0) {
            respawnTimes.put(uuid, System.currentTimeMillis());
        }
        if (cooldown == null) {
            return;
        }
        if (cooldown.remaining() <= 0 || !resumeOnRejoin) {
            debug("[onJoin] " + player.getName() + " -> кулдаун истёк/выключен resume, не убиваем");
            cooldowns.remove(uuid);
            return;
        }
        debug("[onJoin] " + player.getName() + " -> таймер активен, планируем rejoinKill");
        plugin.getServer().getScheduler().runTask(plugin, () -> rejoinKill(uuid));
    }

    private void rejoinKill(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            debug("[rejoinKill] " + uuid + " игрок оффлайн, выход");
            return;
        }
        if (!isOnCooldown(uuid)) {
            debug("[rejoinKill] " + player.getName() + " кулдауна больше нет, выход");
            return;
        }
        Integer livesBefore = getLimitedLives(player);
        if (livesBefore != null) {
            rejoinLivesAtKill.put(uuid, livesBefore);
        }
        debug("[rejoinKill] " + player.getName() + " livesBefore=" + livesBefore + " -> setHealth(0)");
        suppressNextDeath.put(uuid, System.currentTimeMillis() + 5_000L);
        player.setHealth(0);
        debug("[rejoinKill] " + player.getName() + " after setHealth(0): lives=" + getLimitedLives(player));
        restoreLivesIfLost(uuid, livesBefore);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (rejoinLivesAtKill.containsKey(uuid)) {
                debug("[rejoinKill] " + player.getName() + " повторная проверка через 20 тиков");
                restoreLivesIfLost(uuid, rejoinLivesAtKill.get(uuid));
            }
        }, 20L);
    }

    private void restoreLivesIfLost(UUID uuid, Integer livesBefore) {
        if (livesBefore == null) {
            debug("[restoreLivesIfLost] " + uuid + " livesBefore=null, выход");
            return;
        }
        if (!limitedLivesEnabled) {
            debug("[restoreLivesIfLost] " + uuid + " limitedLives выключен, удаляем маркер");
            rejoinLivesAtKill.remove(uuid);
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            debug("[restoreLivesIfLost] " + uuid + " игрок оффлайн, выход");
            return;
        }
        Integer livesNow = getLimitedLives(player);
        debug("[restoreLivesIfLost] " + player.getName() + " livesBefore=" + livesBefore
                + " livesNow=" + livesNow);
        if (livesNow != null && livesNow < livesBefore) {
            debug("[restoreLivesIfLost] " + player.getName() + " -> жизнь снята, возвращаем (addLives(1))");
            restoreLimitedLivesLife(player);
            rejoinLivesAtKill.remove(uuid);
        } else {
            debug("[restoreLivesIfLost] " + player.getName() + " -> жизнь НЕ снята или не прочиталась, не возвращаем");
        }
    }

    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        debug("[onQuit] " + player.getName() + " очистка suppress/rejoinLivesAtKill");
        suppressNextDeath.remove(uuid);
        rejoinLivesAtKill.remove(uuid);
    }

    private Integer getLimitedLives(Player player) {
        if (!limitedLivesEnabled) {
            return null;
        }
        try {
            Plugin llPlugin = Bukkit.getPluginManager().getPlugin("LimitedLives");
            if (!(llPlugin instanceof xyz.srnyx.limitedlives.LimitedLives)) {
                debug("[getLimitedLives] " + player.getName() + " плагин LimitedLives не найден, null");
                return null;
            }
            int lives = new xyz.srnyx.limitedlives.managers.player.PlayerManager(
                    (xyz.srnyx.limitedlives.LimitedLives) llPlugin, player).getLives();
            debug("[getLimitedLives] " + player.getName() + " lives=" + lives);
            return lives;
        } catch (Throwable ex) {
            debug("[getLimitedLives] " + player.getName() + " исключение: " + ex);
            return null;
        }
    }

    private boolean mutedByZeroLives(Player player) {
        if (!limitedLivesEnabled) {
            return false;
        }
        Integer lives = getLimitedLives(player);
        return lives != null && lives <= 0;
    }

    private boolean shouldSkipLimitedLives(Player player) {
        if (!limitedLivesEnabled) {
            return false;
        }
        try {
            Plugin llPlugin = Bukkit.getPluginManager().getPlugin("LimitedLives");
            if (!(llPlugin instanceof xyz.srnyx.limitedlives.LimitedLives)) {
                debug("[shouldSkipLimitedLives] " + player.getName() + " плагин не найден, skip=false");
                return false;
            }
            xyz.srnyx.limitedlives.managers.player.PlayerManager llPlayer =
                    new xyz.srnyx.limitedlives.managers.player.PlayerManager(
                            (xyz.srnyx.limitedlives.LimitedLives) llPlugin, player);
            int livesAfter = llPlayer.getLives();
            int livesBeforeDeath = livesAfter + 1;
            boolean skip = livesBeforeDeath <= limitedSkipLivesAtOrBelow;
            debug("[shouldSkipLimitedLives] " + player.getName() + " livesAfter=" + livesAfter
                    + " livesBeforeDeath=" + livesBeforeDeath + " skipAtOrBelow=" + limitedSkipLivesAtOrBelow
                    + " -> skip=" + skip);
            return skip;
        } catch (Throwable ex) {
            plugin.getLogger().warning("Не удалось проверить жизни LimitedLives: " + ex.getMessage());
            debug("[shouldSkipLimitedLives] " + player.getName() + " исключение: " + ex);
            return false;
        }
    }

    private void restoreLimitedLivesLife(Player player) {
        if (!limitedLivesEnabled) {
            return;
        }
        try {
            Plugin llPlugin = Bukkit.getPluginManager().getPlugin("LimitedLives");
            if (!(llPlugin instanceof xyz.srnyx.limitedlives.LimitedLives)) {
                debug("[restoreLimitedLivesLife] " + player.getName() + " плагин не найден, выход");
                return;
            }
            xyz.srnyx.limitedlives.managers.player.PlayerManager llPlayer =
                    new xyz.srnyx.limitedlives.managers.player.PlayerManager(
                            (xyz.srnyx.limitedlives.LimitedLives) llPlugin, player);
            llPlayer.addLives(1);
            debug("[restoreLimitedLivesLife] " + player.getName() + " addLives(1) выполнено, lives=" + getLimitedLives(player));
        } catch (Throwable ex) {
            plugin.getLogger().warning("Не удалось вернуть жизнь игроку " + player.getName()
                    + " после возврата на экран смерти: " + ex.getMessage());
            debug("[restoreLimitedLivesLife] " + player.getName() + " исключение: " + ex);
        }
    }

    private void sendRespawnInfo(Player player) {
        if (mutedByZeroLives(player)) {
            return;
        }
        if (limitedLivesEnabled && !livesMessage.isEmpty()) {
            try {
                Plugin llPlugin = Bukkit.getPluginManager().getPlugin("LimitedLives");
                if (llPlugin instanceof xyz.srnyx.limitedlives.LimitedLives) {
                    xyz.srnyx.limitedlives.managers.player.PlayerManager llPlayer =
                            new xyz.srnyx.limitedlives.managers.player.PlayerManager(
                                    (xyz.srnyx.limitedlives.LimitedLives) llPlugin, player);
                    player.sendMessage(colorize(livesMessage.replace("{lives}", String.valueOf(llPlayer.getLives()))));
                }
            } catch (Throwable ex) {
                plugin.getLogger().warning("Не удалось показать количество жизней игроку "
                        + player.getName() + ": " + ex.getMessage());
            }
        }
        if (redemptionEnabled && !redemptionMessage.isEmpty()) {
            player.sendMessage(colorize(redemptionMessage
                    .replace("{time}", formatRoundedTime(getRedemptionRemaining(player)))));
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Cooldown> entry : cooldowns.entrySet()) {
            UUID uuid = entry.getKey();
            Cooldown cooldown = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                if (cooldown.deathTime + cooldown.durationMillis <= now) {
                    cooldowns.remove(uuid);
                    if (redemptionEnabled) {
                        respawnTimes.put(uuid, now);
                    }
                }
                continue;
            }
            if (cooldown.deathTime + cooldown.durationMillis <= now) {
                cooldowns.remove(uuid);
                if (redemptionEnabled) {
                    respawnTimes.put(uuid, now);
                }
                if (useRespawnedMessage && !mutedByZeroLives(player)) {
                    player.sendMessage(colorize(respawnedMessage));
                }
                try {
                    player.spigot().respawn();
                } catch (Exception ex) {
                    plugin.getLogger().warning("Не удалось возродить " + player.getName() + ": " + ex.getMessage());
                }
                sendRespawnInfo(player);
            } else {
                sendDisplay(player);
            }
        }
        if (redemptionEnabled) {
            checkLevelDrops(now);
        }
    }

    private void checkLevelDrops(long now) {
        for (Map.Entry<UUID, Integer> entry : playerLevels.entrySet()) {
            UUID uuid = entry.getKey();
            int level = entry.getValue();
            if (level <= 0) {
                continue;
            }
            long respawn = respawnTimes.getOrDefault(uuid, 0L);
            if (respawn <= 0) {
                continue;
            }
            if (isOnCooldown(uuid)) {
                continue;
            }
            int dropLevel = Math.max(0, dropLevels.getOrDefault(uuid, level));
            long required = (long) (dropMultiplier * levelTime(dropLevel));
            if (now - respawn < required) {
                continue;
            }
            int newLevel = level - 1;
            playerLevels.put(uuid, newLevel);
            dropLevels.put(uuid, newLevel);
            respawnTimes.put(uuid, now);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && !levelDownMessage.isEmpty() && !mutedByZeroLives(player)) {
                player.sendMessage(colorize(levelDownMessage
                        .replace("{level}", String.valueOf(newLevel))
                        .replace("{oldlevel}", String.valueOf(level))));
            }
            saveDeathCountsAsync();
        }
    }

    public void registerPacketListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                PacketType.Play.Client.CLIENT_COMMAND,
                PacketType.Play.Client.SPECTATE
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.CLIENT_COMMAND) {
                    EnumWrappers.ClientCommand action = event.getPacket().getClientCommands().readSafely(0);
                    if (action == EnumWrappers.ClientCommand.PERFORM_RESPAWN && isOnCooldown(event.getPlayer())) {
                        event.setCancelled(true);
                    }
                } else if (event.getPacketType() == PacketType.Play.Client.SPECTATE) {
                    if (isOnCooldown(event.getPlayer())) {
                        event.setCancelled(true);
                    }
                }
            }
        });
    }

    public void unregisterPacketListener() {
        protocolManager.removePacketListeners(plugin);
    }

    public boolean isOnCooldown(Player player) {
        if (player == null) {
            return false;
        }
        return isOnCooldown(player.getUniqueId());
    }

    private boolean isOnCooldown(UUID uuid) {
        Cooldown cooldown = cooldowns.get(uuid);
        return cooldown != null && cooldown.remaining() > 0;
    }

    public void resetDeathCount(Player player) {
        deathCounts.remove(player.getUniqueId());
        playerLevels.remove(player.getUniqueId());
        dropLevels.remove(player.getUniqueId());
        respawnTimes.remove(player.getUniqueId());
        saveDeathCountsAsync();
    }

    public void revivePlayer(Player player) {
        cooldowns.remove(player.getUniqueId());
        if (redemptionEnabled) {
            respawnTimes.put(player.getUniqueId(), System.currentTimeMillis());
        }
        if (useRespawnedMessage && !mutedByZeroLives(player)) {
            player.sendMessage(colorize(respawnedMessage));
        }
        try {
            player.spigot().respawn();
        } catch (Exception ex) {
            plugin.getLogger().warning("Не удалось возродить " + player.getName() + ": " + ex.getMessage());
        }
        sendRespawnInfo(player);
    }

    public long getUpdateTicks() {
        return updateTicks;
    }

    // ---------- утилиты ----------

    private void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    private long levelDuration(int deaths) {
        int size = levels.size();
        int index;
        if (resetAfterLastLevel) {
            index = (deaths - 1) % size;
        } else {
            index = Math.min(deaths - 1, size - 1);
        }
        return levels.get(index);
    }

    private long levelTime(int level) {
        if (level <= 0) {
            return 0L;
        }
        int size = levels.size();
        int index = Math.max(0, Math.min(level - 1, size - 1));
        return levels.get(index);
    }

    public long getLevelTime(int level) {
        return levelTime(level);
    }

    public int getLevelCount() {
        return levels.size();
    }

    public int getLevel(Player player) {
        return Math.max(0, playerLevels.getOrDefault(player.getUniqueId(), 0));
    }

    public void setLevel(Player player, int level) {
        UUID uuid = player.getUniqueId();
        int clamped = Math.max(0, Math.min(level, levels.size()));
        playerLevels.put(uuid, clamped);
        dropLevels.put(uuid, clamped);
        respawnTimes.put(uuid, System.currentTimeMillis());
        saveDeathCountsAsync();
    }

    public long getRedemptionRemaining(Player player) {
        UUID uuid = player.getUniqueId();
        int dropLevel = Math.max(0, dropLevels.getOrDefault(uuid, playerLevels.getOrDefault(uuid, 0)));
        long required = (long) (dropMultiplier * levelTime(dropLevel));
        long respawn = respawnTimes.getOrDefault(uuid, 0L);
        if (respawn <= 0) {
            return required;
        }
        return Math.max(0, required - (System.currentTimeMillis() - respawn));
    }

    public String formatRoundedTime(long millis) {
        long totalSeconds = (millis + 999) / 1000;
        if (totalSeconds >= 3600) {
            long hours = totalSeconds / 3600;
            long minutes = Math.round((totalSeconds % 3600) / 60.0);
            if (minutes == 60) {
                hours++;
                minutes = 0;
            }
            String result;
            if (isRussian()) {
                result = hours + " " + pluralHours(hours);
                if (minutes > 0) {
                    result += " " + minutes + " " + pluralMinutes(minutes);
                }
            } else {
                result = hours + (hours == 1 ? " hour" : " hours");
                if (minutes > 0) {
                    result += " " + minutes + (minutes == 1 ? " minute" : " minutes");
                }
            }
            return result;
        }
        if (totalSeconds >= 60) {
            long minutes = Math.round(totalSeconds / 60.0);
            return minutes + (isRussian() ? " мин" : " min");
        }
        return totalSeconds + (isRussian() ? " сек" : " sec");
    }

    private String pluralHours(long n) {
        long lastTwo = n % 100;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return "часов";
        }
        long last = n % 10;
        if (last == 1) {
            return "час";
        }
        if (last >= 2 && last <= 4) {
            return "часа";
        }
        return "часов";
    }

    private String pluralMinutes(long n) {
        long lastTwo = n % 100;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return "минут";
        }
        long last = n % 10;
        if (last == 1) {
            return "минута";
        }
        if (last >= 2 && last <= 4) {
            return "минуты";
        }
        return "минут";
    }

    private boolean worldEnabled(Player player) {
        return enabledWorlds.isEmpty() || enabledWorlds.contains(player.getWorld().getName());
    }

    private void sendDisplay(Player player) {
        if (mutedByZeroLives(player)) {
            return;
        }
        Cooldown cooldown = cooldowns.get(player.getUniqueId());
        if (cooldown == null) {
            return;
        }
        String time = formatTime(Math.max(0, cooldown.remaining()));
        int stay = Math.max(20, (int) updateTicks * 2);
        if (useTitle) {
            String title = colorize(replace(titleText, time, player));
            String subtitle = useSubtitle ? colorize(replace(subtitleText, time, player)) : "";
            player.sendTitle(title, subtitle, 0, stay, 0);
        }
        if (useActionbar) {
            String text = colorize(replace(actionbarText, time, player));
            if (actionbarBold) {
                player.sendActionBar(LegacyComponentSerializer.legacySection()
                        .deserialize(text).decoration(TextDecoration.BOLD, true));
            } else {
                player.sendActionBar(text);
            }
        }
    }

    private String replace(String text, String time, Player player) {
        return text
                .replace("{time}", time)
                .replace("{deaths}", String.valueOf(deathCounts.getOrDefault(player.getUniqueId(), 0)));
    }

    public String formatTime(long millis) {
        long totalSeconds = (millis + 999) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        switch (timeFormat) {
            case "seconds":
                return totalSeconds + (isRussian() ? " сек" : " sec");
            case "mmss":
                if (hours > 0) {
                    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
                }
                return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
            default:
                if (hours > 0) {
                    if (minutes == 0) {
                        return hours + (isRussian() ? "ч" : "h");
                    }
                    return hours + (isRussian() ? "ч " : "h ") + minutes + (isRussian() ? "м" : "m");
                }
                if (minutes == 0) {
                    return seconds + (isRussian() ? "с" : "s");
                }
                if (seconds == 0) {
                    return minutes + (isRussian() ? "м" : "m");
                }
                return minutes + (isRussian() ? "м " : "m ") + seconds + (isRussian() ? "с" : "s");
        }
    }

    private Long parseLevel(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            long number = Long.parseLong(digits);
            if (s.endsWith("day") || s.endsWith("days") || s.endsWith("d")) {
                return number * 86_400_000L;
            }
            if (s.endsWith("hour") || s.endsWith("hours")
                    || s.endsWith("hr") || s.endsWith("hrs") || s.endsWith("h")) {
                return number * 3_600_000L;
            }
            if (s.endsWith("minute") || s.endsWith("minutes")
                    || s.endsWith("min") || s.endsWith("mins") || s.endsWith("m")) {
                return number * 60_000L;
            }
            if (s.endsWith("sec") || s.endsWith("secs")
                    || s.endsWith("second") || s.endsWith("seconds") || s.endsWith("s")) {
                return number * 1000L;
            }
            return number * 1000L;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public String colorize(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '#' && i + 7 < text.length()) {
                    String hex = text.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        sb.append('§').append('x');
                        for (char h : hex.toCharArray()) {
                            sb.append('§').append(Character.toLowerCase(h));
                        }
                        i += 8;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // ---------- сохранение счётчика смертей ----------

    private File dataFile() {
        return new File(plugin.getDataFolder(), "data.yml");
    }

    public void loadDeathCounts() {
        deathCounts.clear();
        File file = dataFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("data");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                deathCounts.put(UUID.fromString(key), section.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // битый ключ — пропускаем
            }
        }
        ConfigurationSection cooldownSection = data.getConfigurationSection("cooldown");
        if (cooldownSection != null) {
            long now = System.currentTimeMillis();
            for (String key : cooldownSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long start = cooldownSection.getLong(key + ".start");
                    long duration = cooldownSection.getLong(key + ".duration");
                    if (start > 0 && duration > 0 && start + duration > now) {
                        cooldowns.put(uuid, new Cooldown(start, duration));
                    }
                } catch (IllegalArgumentException ignored) {
                    // битый ключ — пропускаем
                }
            }
        }
        ConfigurationSection redeemSection = data.getConfigurationSection("redeem");
        if (redeemSection != null) {
            for (String key : redeemSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    playerLevels.put(uuid, Math.max(0, redeemSection.getInt(key + ".level", 0)));
                    dropLevels.put(uuid, Math.max(0, redeemSection.getInt(key + ".drop-level", playerLevels.get(uuid))));
                    respawnTimes.put(uuid, redeemSection.getLong(key + ".respawn", 0L));
                } catch (IllegalArgumentException ignored) {
                    // битый ключ — пропускаем
                }
            }
        }
    }

    public void saveDeathCounts() {
        if (!persistCounter) {
            return;
        }
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : deathCounts.entrySet()) {
            data.set("data." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, Cooldown> entry : cooldowns.entrySet()) {
            Cooldown cd = entry.getValue();
            data.set("cooldown." + entry.getKey() + ".start", cd.deathTime);
            data.set("cooldown." + entry.getKey() + ".duration", cd.durationMillis);
        }
        for (Map.Entry<UUID, Integer> entry : playerLevels.entrySet()) {
            UUID uuid = entry.getKey();
            data.set("redeem." + uuid + ".level", entry.getValue());
            data.set("redeem." + uuid + ".drop-level", dropLevels.getOrDefault(uuid, entry.getValue()));
            data.set("redeem." + uuid + ".respawn", respawnTimes.getOrDefault(uuid, 0L));
        }
        try {
            data.save(dataFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить data.yml: " + ex.getMessage());
        }
    }

    private void saveDeathCountsAsync() {
        if (!persistCounter) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveDeathCounts);
    }
}
