package me.darrionat.statics;

import me.darrionat.StaffWarn;
import me.darrionat.repositories.CommandPermissionRepository;
import me.darrionat.repositories.FileRepository;
import me.darrionat.services.ConfigService;
import me.darrionat.services.MessageService;
import me.darrionat.services.PermissionService;

public class Bootstrapper {

	private static Bootstrapper instance;

	private CommandPermissionRepository commandPermissionRepository;
	private FileRepository fileRepository;

	private ConfigService configService;
	private MessageService messageService;
	private PermissionService permissionService;

	public static Bootstrapper getBootstrapper() {
		if (instance == null)
			instance = new Bootstrapper();
		return instance;
	}

	public void init(StaffWarn plugin) {
		this.fileRepository = new FileRepository(plugin);
		this.commandPermissionRepository = new CommandPermissionRepository(fileRepository);

		this.configService = new ConfigService(fileRepository.getDataConfigWithDefault(FileRepository.CONFIG));
		this.messageService = new MessageService(fileRepository);
		this.permissionService = new PermissionService(configService, commandPermissionRepository);
	}

	public CommandPermissionRepository getCommandPermissionRepository() {
		return commandPermissionRepository;
	}

	public FileRepository getFileRepository() {
		return fileRepository;
	}

	public ConfigService getConfigService() {
		return configService;
	}

	public MessageService getMessageService() {
		return messageService;
	}

	public PermissionService getPermissionService() {
		return permissionService;
	}
}