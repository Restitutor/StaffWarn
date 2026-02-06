# StaffWarn

Paper plugin that alerts staff when they use commands normal players can't. If a staff member runs `/vanish` and the
`default` group doesn't have `essentials.vanish`, they see a configurable reminder.

Requires **Paper 1.21+** and **LuckPerms**.

## How It Works

```
Player types /evanish
  → Paper's CommandMap resolves "evanish" → Command(name="vanish")
  → Two-tier permission resolution:
      1. Paper's command map (getPermission()) - works for most plugins
      2. fallbackPermissions.yml - for plugins like EssentialsX that return null
  → Does the player have the permission? (no → skip, server will deny anyway)
  → Is the world excluded? (yes → skip)
  → [async] Does the "default" LuckPerms group have it? (yes → skip)
  → [async] Find which group grants this permission → send alert
```

Paper's `commandMap.getCommand()` resolves aliases automatically - `getCommand("evanish")` returns the same `Command` as
`getCommand("vanish")`. This is why `fallbackPermissions.yml` only needs canonical names, not every alias.

## Files

```
StaffWarn.kt              ← plugin logic: init, listener, permission resolution
StaffWarnLoader.java      ← Paper plugin loader (pulls Kotlin stdlib via Maven at boot)
paper-plugin.yml          ← plugin descriptor
config.yml                ← defaultGroups, excludedWorlds, alert template, overrides
fallbackPermissions.yml   ← canonical-name-only permission map for misbehaving plugins
```

## Configuration

### config.yml

```yaml
defaultGroups:
  - default

excludedWorlds:
  - spawn

# MiniMessage format. Placeholders: %command%, %permission%, %origin%
alert: "<gray>Non-staff lack <yellow>%permission%</yellow> <gray>from <yellow>%origin%</yellow> <gray>so they cannot use <yellow>%command%</yellow>"

# Manual overrides - keyed by exact label the player types, highest priority
overrides: { }

debug: false
```

### fallbackPermissions.yml

Keyed by canonical command name. Only needed for plugins that null out `getPermission()` (EssentialsX, Honeypot, etc.).
Ships with EssentialsX defaults. When the plugin encounters an unmapped null-permission command, it logs a warning so
you
know to add an entry.

## Gotchas

- **EssentialsX nulls `getPermission()`** on all its commands (it does its own internal permission checks). Every
  EssentialsX command follows `essentials.<canonical_name>` but you can't get this from the Command object - hence the
  fallback map.

- **`VanillaCommandWrapper`** (vanilla/Brigadier commands) doesn't implement `PluginIdentifiableCommand`, so you can't
  get the owning plugin via cast. Use the namespace key in `knownCommands` instead (`essentials:vanish` → `essentials`).

- **Wildcard permissions** (`essentials.*`) don't show up as explicit nodes in `resolveDistinctInheritedNodes()`. The
  plugin does a second pass checking wildcard candidates to find the granting group.

- **Bukkit built-ins** (`/pl`, `/plugins`, `/version`) are in the `bukkit:` namespace with null permissions. Add them to
  `fallbackPermissions.yml` manually if you want alerts (e.g. `plugins: bukkit.command.plugins`).
