#!/bin/bash

# ─── Fallback Permission Map ─────────────────────────────────────────
# For plugins that return null from Command.getPermission() (e.g. EssentialsX).
#
# KEY: canonical command name (what Paper's commandMap reports as command.name)
#      NOT aliases — Paper resolves aliases before we look here.
#
# VALUE: the actual permission node the plugin checks internally
#
# You only need entries here for commands where Paper's getPermission() is null.
# Well-behaved plugins (Vanilla, CoreProtect, etc.) are handled automatically.
#
# To find canonical names, enable debug mode and check the log:
#   [CMD-RESOLVE] commandMap.getCommand("evanish") → PluginCommand(name="vanish", ...)
#                                                            ^^^^^^^^ this is the canonical name

set -euxo pipefail
wget -N https://raw.githubusercontent.com/Xeyame/essinfo.xeya.me/master/data/commands.json
wget -N https://raw.githubusercontent.com/Xeyame/essinfo.xeya.me/master/data/permissions.json
python ./getPermissions.py > commandPermissions.yml
