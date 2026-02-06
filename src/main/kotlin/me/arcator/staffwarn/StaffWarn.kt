package me.arcator.staffwarn

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.minimessage.MiniMessage
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.user.User
import net.luckperms.api.node.metadata.types.InheritanceOriginMetadata
import net.luckperms.api.query.QueryOptions
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Collections

@Suppress("UnstableApiUsage")
class StaffWarn : JavaPlugin(), Listener {

    private val mm = MiniMessage.miniMessage()
    private val httpClient = HttpClient.newHttpClient()
    private val serverName = File(System.getProperty("user.dir")).name

    private lateinit var defaultGroups: List<String>
    private lateinit var excludedWorlds: Set<String>
    private lateinit var alertTemplate: String
    private lateinit var webhookUrl: String
    private lateinit var discordTemplate: String
    private var usePlayerIdentity = true
    private var debug = true

    /**
     * Permission map for commands where Paper's getPermission() returns null. Keyed by CANONICAL
     * command name. Loaded from fallbackPermissions.yml.
     */
    private lateinit var fallbackMap: Map<String, String>

    /**
     * Explicit overrides from config.yml - checked BEFORE Paper's permission. Supports subcommand
     * keys (e.g. "co i" → "coreprotect.inspect") and simple command keys. Keyed by canonical name
     * (lowercased at load).
     */
    private lateinit var overrideMap: Map<String, String>

    /** Track commands we've already warned about (null permission or unregistered permission) */
    private val warnedCommands = mutableSetOf<String>()

    /** LRU cache to prevent duplicate alerts within 1 minute. Max 50 entries. */
    private val alertCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(51, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 50
            }
        }
    )

    override fun onEnable() {
        saveDefaultConfig()
        saveResource("fallbackPermissions.yml", true) // overwrite

        server.pluginManager.registerEvents(this, this)

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar()
                .register(
                    "staffwarn",
                    "StaffWarn admin commands",
                    listOf("sw"),
                    object : BasicCommand {
                        override fun execute(
                            stack: CommandSourceStack,
                            args: Array<String>
                        ) {
                            if (args.firstOrNull()?.lowercase() == "reload") {
                                loadConfig()
                                stack.sender.sendMessage(
                                    mm.deserialize("<green>StaffWarn config reloaded.")
                                )
                            } else {
                                stack.sender.sendMessage(
                                    mm.deserialize("<yellow>Usage: /staffwarn reload")
                                )
                            }
                        }

                        override fun suggest(
                            stack: CommandSourceStack,
                            args: Array<String>
                        ): Collection<String> {
                            return if (args.size <= 1) listOf("reload") else emptyList()
                        }

                        override fun permission(): String = "staffwarn.reload"
                    }
                )
        }

        loadConfig()
    }

    private fun loadConfig() {
        reloadConfig()

        defaultGroups = config.getStringList("defaultGroups")
        excludedWorlds = config.getStringList("excludedWorlds").map { it.lowercase() }.toSet()
        alertTemplate =
            config.getString("alert")
                ?: "<gray>Non-staff lack <yellow>%permission%</yellow> <gray>so they cannot use <yellow>%command%</yellow>"
        debug = config.getBoolean("debug", true)
        webhookUrl = config.getString("discord.webhookUrl") ?: ""
        discordTemplate =
            config.getString("discord.message")
                ?: "**%player%** used **%command%** (requires **%permission%** from **%origin%**) [Server: **%server%**, World: **%world%**]"
        usePlayerIdentity = config.getBoolean("discord.usePlayerIdentity", true)

        // Load fallback map from file (for commands where Paper returns null permission)
        val fallbackConfig =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                File(dataFolder, "fallbackPermissions.yml")
            )
        fallbackMap =
            fallbackConfig.getKeys(false).associate {
                it.lowercase() to fallbackConfig.getString(it)!!
            }

        // Load overrides - checked BEFORE Paper's permission, supports subcommand keys
        val overrideSection = config.getConfigurationSection("overrides")
        overrideMap =
            overrideSection?.getKeys(false)?.associate {
                it.lowercase() to overrideSection.getString(it)!!
            }
                ?: emptyMap()

        // Clear warned commands so null-permission warnings re-fire after fallback changes
        warnedCommands.clear()
        alertCache.clear()

        logger.info("[INIT] StaffWarn enabled")
        logger.info("[INIT] defaultGroups=$defaultGroups")
        logger.info("[INIT] excludedWorlds=$excludedWorlds")
        logger.info(
            "[INIT] fallbackMap=${fallbackMap.size} entries, overrideMap=${overrideMap.size} entries"
        )
        logger.info("[INIT] debug=$debug")
        logger.info(
            "[INIT] Discord webhook ${if (webhookUrl.isNotBlank()) "enabled" else "disabled"}"
        )
    }

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val raw = event.message.removePrefix("/")
        val parts = raw.split(' ', limit = 3)
        val label = parts[0].lowercase()
        val firstArg = parts.getOrNull(1)?.lowercase()

        dbg("[CMD-ENTER] player=${player.name} raw=\"${event.message}\"")
        dbg("[CMD-LABEL] parsed=\"$label\" firstArg=\"$firstArg\"")

        val resolution = resolvePermission(label, firstArg)

        if (resolution == null) {
            dbg("[CMD-PERM] no permission found for \"$label\" - skipping")
            return
        }

        val (permission, canonicalName) = resolution
        dbg("[CMD-PERM] permission=\"$permission\"")

        if (!player.hasPermission(permission)) {
            dbg("[CMD-HAS] player=${player.name} lacks \"$permission\" - skipping")
            return
        }
        dbg("[CMD-HAS] player=${player.name} has \"$permission\" ✓")

        val world = player.world.name.lowercase()
        if (world in excludedWorlds) {
            dbg("[WORLD] world=\"$world\" is excluded - skipping")
            return
        }
        dbg("[WORLD] world=\"$world\" not excluded ✓")

        val playerName = player.name
        server.asyncScheduler.runNow(this) {
            try {
                asyncCheck(player, playerName, canonicalName, permission, world)
            } catch (e: Exception) {
                logger.warning("[ASYNC-ERROR] ${e.message}")
                if (debug) e.printStackTrace()
            }
        }
    }

    private fun asyncCheck(
        player: Player,
        playerName: String,
        label: String,
        permission: String,
        world: String
    ) {
        // Rate limiting: max once per minute per user+permission
        val cacheKey = "${player.uniqueId}:$permission"
        val now = System.currentTimeMillis()
        val lastAlert = alertCache[cacheKey]

        if (lastAlert != null && (now - lastAlert) < 60000) {
            dbg("[RATE-LIMIT] skipping duplicate alert for $playerName + $permission (${(now - lastAlert) / 1000}s ago)")
            return
        }

        dbg("[ASYNC-START] checking defaults for \"$permission\"")

        val lp = LuckPermsProvider.get()
        val user = lp.getPlayerAdapter(Player::class.java).getUser(player)

        val context = user.queryOptions.context()
        dbg("[LP-CONTEXT] player context=$context")

        val contextualQuery = QueryOptions.contextual(context)

        for (groupName in defaultGroups) {
            val group = lp.groupManager.getGroup(groupName)
            if (group == null) {
                dbg("[LP-DEFAULT] group=\"$groupName\" NOT FOUND in LuckPerms - skipping it")
                continue
            }

            val has =
                group.cachedData
                    .getPermissionData(contextualQuery)
                    .checkPermission(permission)
                    .asBoolean()

            dbg("[LP-DEFAULT] group=\"$groupName\" has \"$permission\"? → $has")

            if (has) {
                dbg("[LP-DEFAULT] default group has permission - no alert needed")
                return
            }
        }

        val origin = findPermissionOrigin(user, contextualQuery, permission)
        dbg("[LP-ORIGIN] origin for \"$permission\" → \"$origin\"")

        val message =
            alertTemplate
                .replace("%command%", label)
                .replace("%permission%", permission)
                .replace("%origin%", origin)

        player.scheduler.run(this, { player.sendMessage(mm.deserialize(message)) }, null)
        dbg("[ALERT] sent to $playerName")

        logger.info("$playerName used /$label (requires $permission from $origin)")

        if (webhookUrl.isNotBlank()) {
            val discordMsg =
                discordTemplate
                    .replace("%player%", playerName)
                    .replace("%command%", label)
                    .replace("%permission%", permission)
                    .replace("%origin%", origin)
                    .replace("%server%", serverName)
                    .replace("%world%", world)
            sendWebhook(discordMsg, playerName, player.uniqueId.toString())
        }

        // Update cache to prevent duplicates
        alertCache[cacheKey] = now
    }

    private fun sendWebhook(
        content: String,
        playerName: String? = null,
        playerUuid: String? = null
    ) {
        val fields = mutableListOf<String>()

        val escapedContent = content.replace("\\", "\\\\").replace("\"", "\\\"")
        fields.add(""""content":"$escapedContent"""")

        if (playerName != null && playerUuid != null && usePlayerIdentity) {
            val escapedName = playerName.replace("\\", "\\\\").replace("\"", "\\\"")
            fields.add(""""username":"$escapedName"""")

            val uuid = playerUuid.replace("-", "")
            fields.add(""""avatar_url":"https://mc-heads.net/avatar/$uuid/128"""")
        }

        val json = "{${fields.joinToString(",")}}"

        val request =
            HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()
        httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() !in 200..299) {
                    logger.warning(
                        "Discord webhook returned ${response.statusCode()}: ${response.body()}"
                    )
                }
            }
            .exceptionally { ex ->
                logger.warning("Discord webhook failed: ${ex.message}")
                null
            }
    }

    /**
     * Four-tier resolution:
     * 1. Override map with subcommand key ("co i") - for subcommand-specific permissions
     * 2. Override map with command key ("co") - general override for the command
     * 3. Paper command map's getPermission() - works for well-behaved plugins
     * 4. Fallback map - keyed by canonical name, for EssentialsX etc.
     *
     * Returns (permission, canonicalName) or null if no permission can be determined.
     */
    private fun resolvePermission(label: String, firstArg: String?): Pair<String, String>? {
        val command = server.commandMap.getCommand(label)
        if (command == null) {
            dbg("[CMD-RESOLVE] commandMap.getCommand(\"$label\") → null (not a registered command)")
            return null
        }

        val canonicalName = command.name.lowercase()
        dbg(
            "[CMD-RESOLVE] commandMap.getCommand(\"$label\") → ${command::class.simpleName}" +
                    "(name=\"$canonicalName\", label=\"${command.label}\")"
        )
        dbg("[CMD-RESOLVE]   .permission=\"${command.permission}\"  .aliases=${command.aliases}")

        // Tier 1: Override map - subcommand key (e.g. "co i" → "coreprotect.inspect")
        if (firstArg != null) {
            val subKey = "$canonicalName $firstArg"
            val subOverride = overrideMap[subKey]
            if (subOverride != null) {
                dbg("[CMD-RESOLVE] override (subcommand): \"$subKey\" → \"$subOverride\"")
                return subOverride to canonicalName
            }
        }

        // Tier 2: Override map - command key (e.g. "co" → "coreprotect.co")
        val cmdOverride = overrideMap[canonicalName]
        if (cmdOverride != null) {
            dbg("[CMD-RESOLVE] override (command): \"$canonicalName\" → \"$cmdOverride\"")
            return cmdOverride to canonicalName
        }

        // Tier 3: Paper's command map has a permission
        val paperPerm = command.permission
        if (!paperPerm.isNullOrBlank()) {
            val perm =
                if (';' in paperPerm) {
                    dbg(
                        "[CMD-RESOLVE] WARNING: semicolon-separated perms: \"$paperPerm\" - using first"
                    )
                    paperPerm.substringBefore(';').trim()
                } else {
                    paperPerm
                }
            warnUnregisteredPermission(canonicalName, perm)
            return perm to canonicalName
        }

        // Tier 4: Fallback map - keyed by canonical name
        val fallbackPerm = fallbackMap[canonicalName]
        if (fallbackPerm != null) {
            dbg("[CMD-RESOLVE] fallback: canonical=\"$canonicalName\" → \"$fallbackPerm\"")
            return fallbackPerm to canonicalName
        }

        logNullPermissionPlugin(canonicalName)
        return null
    }

    /**
     * Resolve the plugin namespace for a canonical command name from the command map. e.g. "co" →
     * "coreprotect" (from the "coreprotect:co" entry in knownCommands).
     */
    private fun pluginNameFor(canonicalName: String): String? =
        server.commandMap
            .knownCommands
            .keys
            .firstOrNull { it.contains(':') && it.endsWith(":$canonicalName") }
            ?.substringBefore(':')

    /** Log a warning (once per command) when we encounter a command with no permission. */
    private fun logNullPermissionPlugin(canonicalName: String) {
        if (canonicalName in warnedCommands) return
        warnedCommands.add(canonicalName)

        val pluginName = pluginNameFor(canonicalName) ?: "unknown"

        dbg("[CMD-RESOLVE] null permission - plugin=\"$pluginName\" canonical=\"$canonicalName\"")
        logger.warning(
            "Plugin \"$pluginName\" registers commands without permissions. " +
                    "Add entries to fallbackPermissions.yml if you want alerts for its commands. " +
                    "(first seen: /$canonicalName)"
        )
    }

    /**
     * Log a warning (once per command) when Paper reports a permission that looks like an
     * auto-inferred base-command node (e.g. "coreprotect.co" for /co) and is NOT registered in the
     * Bukkit permission registry.
     *
     * Suppressed once the admin adds an override (Tier 1/2 catches it before Tier 3).
     */
    private fun warnUnregisteredPermission(canonicalName: String, permission: String) {
        if (canonicalName in warnedCommands) return

        // Only flag permissions that look like <namespace>.<commandname>
        val pluginName = pluginNameFor(canonicalName) ?: return
        if (permission.lowercase() != "${pluginName.lowercase()}.$canonicalName") return

        // If the permission IS registered in Bukkit, it's likely intentional
        if (server.pluginManager.getPermission(permission) != null) return

        warnedCommands.add(canonicalName)

        dbg(
            "[CMD-RESOLVE] unregistered permission - plugin=\"$pluginName\" canonical=\"$canonicalName\" perm=\"$permission\""
        )
        logger.warning(
            "Command \"$canonicalName\" (plugin \"$pluginName\") has permission " +
                    "\"$permission\" which is not registered - this may be a base-command permission, " +
                    "not a subcommand permission. If \"$pluginName\" uses subcommands, add overrides " +
                    "in config.yml (e.g., \"$canonicalName <subcommand>\": $pluginName.<action>)"
        )
    }

    private fun findPermissionOrigin(
        user: User,
        queryOptions: QueryOptions,
        permission: String,
    ): String {
        val nodes = user.resolveDistinctInheritedNodes(queryOptions)

        val exactMatch = nodes.firstOrNull { it.key == permission }
        if (exactMatch != null) {
            val origin = exactMatch.metadata(InheritanceOriginMetadata.KEY)?.origin?.name
            dbg("[LP-ORIGIN] exact node match: key=\"${exactMatch.key}\" origin=\"$origin\"")
            return origin ?: "unknown"
        }

        val parts = permission.split('.')
        val wildcardCandidates =
            (parts.size - 1 downTo 1).map { i -> parts.take(i).joinToString(".") + ".*" } +
                    listOf("*")

        dbg("[LP-ORIGIN] trying wildcard candidates: $wildcardCandidates")

        for (candidate in wildcardCandidates) {
            val wildcardMatch = nodes.firstOrNull { it.key == candidate }
            if (wildcardMatch != null) {
                val origin = wildcardMatch.metadata(InheritanceOriginMetadata.KEY)?.origin?.name
                dbg("[LP-ORIGIN] wildcard match: key=\"${wildcardMatch.key}\" origin=\"$origin\"")
                return origin ?: "unknown"
            }
        }

        dbg("[LP-ORIGIN] no exact node for \"$permission\" - possible wildcard grant")
        dbg("[LP-ORIGIN] inherited nodes (first 20):")
        nodes.take(20).forEach { node ->
            val nodeOrigin = node.metadata(InheritanceOriginMetadata.KEY)?.origin?.name
            dbg("[LP-ORIGIN]   key=\"${node.key}\" origin=\"$nodeOrigin\"")
        }

        return "unknown (wildcard?)"
    }

    private fun dbg(msg: String) {
        if (debug) logger.info(msg)
    }
}
