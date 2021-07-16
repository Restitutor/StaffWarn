package me.darrionat.services;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.api.chat.TextComponent;

import me.darrionat.repositories.FileRepository;
import me.darrionat.statics.Utils;

public class MessageService {
	private static final String PREFIX_ENABLED = "prefix.enabled";
	private static final String PREFIX = "prefix.prefix";

	private static final String ALERT = "alert";

	private FileRepository fileRepository;
	private Configuration messagesConfig;

	public MessageService(FileRepository fileRepository) {
		this.fileRepository = fileRepository;
		init();
	}

	public void init() {
		this.messagesConfig = fileRepository.getDataConfig(FileRepository.MESSAGES);
	}

	public void alertPlayer(ProxiedPlayer p, String label, String permission, String origin) {
		String msg = getMessage(ALERT);
		msg = msg.replace("%command%", label);
		msg = msg.replace("%permission%", permission);
		msg = msg.replace("%origin%", origin);
		sendMessage(p, msg);
	}

	/**
	 * Sends a message with the prefix, if enabled
	 * 
	 * @param sender the {@code CommandSender} to send to
	 * @param msg    the message to send
	 */
	private void sendMessage(ProxiedPlayer p, String msg) {
		if (messagesConfig.getBoolean(PREFIX_ENABLED))
			msg = messagesConfig.getString(PREFIX) + msg;
		
		final TextComponent text = new TextComponent(Utils.chat(msg));
		p.sendMessage(text);
	}

	public String getMessage(String path) {
		return messagesConfig.getString(path);
	}
}