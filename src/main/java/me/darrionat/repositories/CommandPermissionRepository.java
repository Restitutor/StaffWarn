package me.darrionat.repositories;

import java.util.HashMap;

import net.md_5.bungee.config.Configuration;

public class CommandPermissionRepository {
	private FileRepository fileRepository;

	// <Label,Permission>
	private HashMap<String, String> labelPermissionMap = new HashMap<String, String>();

	public CommandPermissionRepository(FileRepository fileRepository) {
		this.fileRepository = fileRepository;
		init();
	}

	public void init() {
		final Configuration commandConfig = fileRepository.getDataConfig(FileRepository.COMMAND_PERMISSIONS);

		for (String key : commandConfig.getKeys())
			labelPermissionMap.put(key, commandConfig.getString(key));
	}

	public HashMap<String, String> getLabelPermissionMap() {
		return labelPermissionMap;
	}

	public String getPermission(String label) {
		return labelPermissionMap.get(label);
	}

	public boolean permissionDefined(String label) {
		return labelPermissionMap.containsKey(label);
	}
}