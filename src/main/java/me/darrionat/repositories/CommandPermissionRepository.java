package me.darrionat.repositories;

import java.util.HashMap;

import org.bukkit.configuration.file.FileConfiguration;

public class CommandPermissionRepository {
	private FileRepository fileRepository;

	// <Label,Permission>
	private HashMap<String, String> labelPermissionMap = new HashMap<String, String>();

	public CommandPermissionRepository(FileRepository fileRepository) {
		this.fileRepository = fileRepository;
		init();
	}

	public void init() {
		final FileConfiguration commandConfig = fileRepository.getDataConfig(FileRepository.COMMAND_PERMISSIONS);

		for (String key : commandConfig.getKeys(false))
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