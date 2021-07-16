package me.darrionat.services;

import me.darrionat.repositories.FileRepository;
import me.darrionat.statics.Utils;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class MessageService {
	private static final String PREFIX_ENABLED = "prefix.enabled";
	private static final String PREFIX = "prefix.prefix";

	private static final String ALERT = "alert";

	private FileRepository fileRepository;
	private FileConfiguration messagesConfig;

	public MessageService(FileRepository fileRepository) {
		this.fileRepository = fileRepository;
		init();
	}

	public void init() {
		this.messagesConfig = fileRepository.getDataConfig(FileRepository.MESSAGES);
	}

	public void alertPlayer(Player p, String label, String permission, String origin) {
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
	private void sendMessage(CommandSender p, String msg) {
		if (messagesConfig.getBoolean(PREFIX_ENABLED))
			msg = messagesConfig.getString(PREFIX) + msg;
		
		p.sendMessage(Utils.chat(msg));
	}

	public String getMessage(String path) {
		return messagesConfig.getString(path);
	}
}