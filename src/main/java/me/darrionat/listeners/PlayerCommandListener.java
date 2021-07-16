package me.darrionat.listeners;

import java.util.concurrent.TimeUnit;

import me.darrionat.services.MessageService;
import me.darrionat.services.PermissionService;

import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import net.luckperms.api.context.MutableContextSet;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

public class PlayerCommandListener implements Listener {
	private MessageService messageService;
	private PermissionService permissionService;

	private Plugin plugin;

	public PlayerCommandListener(Plugin plugin, MessageService messageService, PermissionService permissionService) {
		this.plugin = plugin;
		this.messageService = messageService;
		this.permissionService = permissionService;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onChatEvent(ChatEvent e) {
		if (!(e.getSender() instanceof ProxiedPlayer)) return;
		if (!(e.isCommand())) return;

		ProxiedPlayer p = (ProxiedPlayer)e.getSender();

		final String label = getLabel(e.getMessage().replace("/", ""));
		if (!permissionService.permissionDefined(label))
			return;

		if (!p.hasPermission(permissionService.getPermission(label)))
			return;

		// Check asynchronously
		plugin.getProxy().getScheduler().schedule(plugin, new Runnable() {
            @Override
            public void run() {
				final String permission = permissionService.getPermission(label);
				final User user = LuckPermsProvider.get().getPlayerAdapter(ProxiedPlayer.class).getUser(p);
				final QueryOptions query = user.getQueryOptions();
				final String server = query.context().getAnyValue("world").get();

				if (permissionService.ignoreServer(server)) return;

				final MutableContextSet context = permissionService.getQueryFromServer(server);
				if (permissionService.defaultHasPermission(context, permission)) return;

				final String origin = permissionService.getPermissionOrigin(context, user, permission);
				messageService.alertPlayer(p, label, permission, origin);
            }
        }, 0, TimeUnit.SECONDS);
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