# DeathCooldownTimer

Death respawn timer. When a player dies, they can't click "Respawn" — instead a countdown timer is shown on the death screen, and when it runs out the player is respawned automatically.

A plugin for Paper/Spigot servers. The wait time grows with each death, with a flexible level and redemption system, plus integration with the [LimitedLives](https://modrinth.com/plugin/limitedlives) plugin.

## Features

- 🕐 Countdown timer on the death screen (Title / Subtitle / ActionBar)
- 📈 Wait time grows with each death — 34 levels by default (from 10 seconds to 30 minutes)
- 🔄 Two level modes: `cycle` (simple cycling) and `redeem` (redemption system)
- 💀 Redemption system: your level goes up on death and goes down for surviving
- 💛 Optional integration with **[LimitedLives](https://modrinth.com/plugin/limitedlives)**: message displaying the number of lives, timer skip on the last life.
- 🌍 World whitelist (`enabled-worlds`)
- 🔁 `resume-on-rejoin` — the timer continues if the player logs out and back in
- 📝 Every message is fully configurable in the config file
- 🌐 Built-in bilingual messages: `language: en` (default) and `language: ru`
- 📊 PlaceholderAPI placeholders (`%deathcooldown_...%`)
- ⚡ Admin commands with `@a`, `@r`, `@p` selectors


## Screenshots

### The revive command instantly respawns a player still on the death screen

![The revive command instantly respawns a player still on the death screen](/images/revive.png)

`/dctimer revive` instantly respawns a player who is still on the death screen, skipping the remaining countdown. Selectors like `@a` let you revive everyone at once.

### The status command showing the redemption level and remaining time

![The status command showing the redemption level and remaining time](/images/status.png)

`/dctimer level status` shows a player's current redemption level and the time left until redemption.

### The you died screen with the live respawn countdown

![The you died screen with the live respawn countdown](/images/died_screen.png)

When a player dies, they can't respawn — the "You died!" screen shows a live countdown until the automatic respawn.

### Chat messages on death and after respawning

![Chat messages on death and after respawning](/images/chat_messages.png)

Players get clear chat messages on death and after respawning, including the total death counter and the time until redemption.

### PlaceholderAPI placeholders shown on

![PlaceholderAPI placeholders shown on a scoreboard](/images/placeholderAPI.png)

PlaceholderAPI placeholders in action on a scoreboard (for example with TAB) — showing the redemption level and the time until redemption.


## Requirements

- **[Java 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)**
- **[Paper 1.21.X](https://fill-ui.papermc.io/projects/paper/family/1.21)** (or Spigot)
- **[ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)** — required
- **[PlaceholderAPI](https://modrinth.com/plugin/placeholderapi)** — optional (for placeholders)
- **[LimitedLives](https://modrinth.com/plugin/limitedlives)** — optional (for lives integration)

## Installation

1. Install [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) into the `plugins/` folder
2. Put `DeathCooldownTimer.jar` into the `plugins/` folder
3. Restart the server
4. Open `plugins/DeathCooldownTimer/config.yml` and configure it to your liking


## Commands

All commands require the `deathcooldown.admin` permission (default: operators).

| Command | Description |
| --- | --- |
| `/dctimer reload` | Reload the config |
| `/dctimer reset [player]` | Reset the death counter (no argument — yourself) |
| `/dctimer revive <player\|@a\|@r\|@p>` | Revive a player before the timer ends |
| `/dctimer level <player\|@a\|@r\|@p> status` | Show the redemption level and remaining time |
| `/dctimer level <player\|@a\|@r\|@p> plus <amount>` | Increase the redemption level |
| `/dctimer level <player\|@a\|@r\|@p> minus <amount>` | Decrease the redemption level |
| `/dctimer level <player\|@a\|@r\|@p> set <level>` | Set the redemption level |
| `/dctimer level <player\|@a\|@r\|@p> reset` | Reset the redemption level |

### Selectors

| Selector | Value |
| --- | --- |
| `@a` | All players on the server |
| `@r` | A random online player |
| `@p` | The nearest player to you (from console — random) |

> Example: `/dctimer revive @a` revives all players currently on cooldown.


## Permissions

| Permission | Description |
| --- | --- |
| `deathcooldown.admin` | Access to all plugin commands (default: operators) |


## Placeholders

The plugin registers a PlaceholderAPI expansion with the `deathcooldown` identifier.

| Placeholder | Description |
| --- | --- |
| `%deathcooldown_level%` | The player's current redemption level |
| `%deathcooldown_level_count%` | The number of levels in the config (maximum level) |
| `%deathcooldown_redemption%` | Time left until redemption (empty if level is 0) |
| `%deathcooldown_redemption_remaining%` | Same as `redemption` |
| `%deathcooldown_redemption_time%` | Time left until redemption (always, even at level 0) |
| `%deathcooldown_redemption_words%` | Time left in words (e.g. `15 min`), empty at level 0 |

> Examples: `&6%deathcooldown_level%&7 level`, `&eRedemption in %deathcooldown_redemption%`.


## Configuration

All comments in the config file are in English. Key options:

```yaml
# Language of the built-in messages: en (default) or ru
language: en

# Levels: wait time in order of deaths
levels:
  - 10s
  - 20s
  # ... up to 30m

# Redemption mode: true/false
redemption-enabled: true
# How many times the level time the player must survive to drop a level
drop-multiplier: 3
```

### Message placeholders

| Placeholder | Description |
| --- | --- |
| `{time}` | Remaining wait time |
| `{deaths}` | How many times the player has already died |
| `{level}` | The current level number |
| `{oldlevel}` | The previous level (before increase/decrease) |
| `{lives}` | Number of lives (LimitedLives) |

### Display settings

```yaml
display:
  use-title: true
  title: "&cYou died!"
  use-subtitle: true
  subtitle: "&eRespawning in &c{time}"
  use-actionbar: false
  actionbar: "&eYou will respawn in &c{time}"
  update-ticks: 20
```

### Time format

| Value | Example |
| --- | --- |
| `seconds` | `45 sec` |
| `short` | `1m 5s` / `25s`, from an hour — `1h 5m` |
| `mmss` | `01:05`, from an hour — `01:05:30` |

### LimitedLives integration

```yaml
limited-lives:
  enabled: true
  # Died with the last life (or fewer) — the timer is not started
  skip-if-lives-at-or-below: 1
```

If a player has **0 lives**, the plugin doesn't send them **any** message — everything is left to LimitedLives (spectator handling etc.).


## Links

- [Source code](https://github.com/byMr712/DeathCooldownTimer)
- [Report an issue](https://github.com/byMr712/DeathCooldownTimer/issues)
- Support: [Boosty](https://boosty.to/bymr712)


**License:** [Apache License 2.0](https://github.com/byMr712/DeathCooldownTimer/blob/main/LICENSE) · **Author:** [Mr712](https://modrinth.com/user/byMr712)
