package me.darrionat.listeners;

import java.util.concurrent.TimeUnit;

import me.darrionat.services.MessageService;
import me.darrionat.services.PermissionService;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.luckperms.api.context.MutableContextSet;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

public class PlayerCommandListener implements Listener {
	private MessageService messageService;
	private PermissionService permissionService;

	private JavaPlugin plugin;

	public PlayerCommandListener(JavaPlugin plugin, MessageService messageService, PermissionService permissionService) {
		this.plugin = plugin;
		this.messageService = messageService;
		this.permissionService = permissionService;
	}

	@EventHandler
	public void onCommand(PlayerCommandPreprocessEvent e) {
		Player p = e.getPlayer();
		final String label = getLabel(e.getMessage().replace("/", ""));

		if (!permissionService.permissionDefined(label))
			return;

		if (!p.hasPermission(permissionService.getPermission(label)))
			return;

		// Check asynchronously
		new BukkitRunnable() {
            @Override
            public void run() {
				final String permission = permissionService.getPermission(label);
				final User user = LuckPermsProvider.get().getPlayerAdapter(Player.class).getUser(p);
				final QueryOptions query = user.getQueryOptions();
				final String world = query.context().getAnyValue("world").get();

				if (permissionService.ignoreWorld(world)) return;

				if (permissionService.defaultHasPermission(query.context(), permission)) return;

				final String origin = permissionService.getPermissionOrigin(query.context(), user, permission);
				messageService.alertPlayer(p, label, permission, origin);
            }
        }.runTaskAsynchronously(plugin);
	}

	private String getLabel(String message) {
		String label;
		final int i = message.indexOf(' ');
		try {
			label = message.substring(0, i);
		} catch (StringIndexOutOfBoundsException exe) {
			label = message;
		}
		return label;
	}
}