package dev.deathcooldown;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class DeathCooldownExpansion extends PlaceholderExpansion {

    private final DeathCooldownPlugin plugin;

    public DeathCooldownExpansion(DeathCooldownPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "deathcooldown";
    }

    @Override
    public String getAuthor() {
        return "Mr712";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }
        DeathCooldownManager manager = plugin.getManager();
        if (params == null) {
            return "";
        }
        switch (params.toLowerCase()) {
            case "level":
                return String.valueOf(manager.getLevel(player));
            case "level_count":
                return String.valueOf(manager.getLevelCount());
            case "redemption":
                if (manager.getLevel(player) <= 0) {
                    return "";
                }
                return manager.formatTime(manager.getRedemptionRemaining(player));
            case "redemption_remaining":
                if (manager.getLevel(player) <= 0) {
                    return "";
                }
                return manager.formatTime(manager.getRedemptionRemaining(player));
            case "redemption_time":
                return manager.formatTime(manager.getRedemptionRemaining(player));
            case "redemption_words":
                if (manager.getLevel(player) <= 0) {
                    return "";
                }
                return manager.formatRoundedTime(manager.getRedemptionRemaining(player));
            default:
                return null;
        }
    }
}
