package me.darrionat.services;

import java.util.HashSet;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigService {
	private FileConfiguration config;

	public ConfigService(FileConfiguration config) {
		this.config = config;
	}

	public HashSet<String> getDefaultGroups() {
		return new HashSet<String>(config.getStringList("defaultGroups"));
	}

	public HashSet<String> getExcludedWorlds() {
		return new HashSet<String>(config.getStringList("excludedWorlds"));
	}
}
