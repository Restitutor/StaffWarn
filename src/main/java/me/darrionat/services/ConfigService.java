package me.darrionat.services;

import java.util.HashSet;
import net.md_5.bungee.config.Configuration;

public class ConfigService {
	private Configuration config;

	public ConfigService(Configuration config) {
		this.config = config;
	}

	public HashSet<String> getDefaultGroups() {
		return new HashSet<String>(config.getStringList("defaultGroups"));
	}

	public HashSet<String> getExcludedServers() {
		return new HashSet<String>(config.getStringList("excludedServers"));
	}
}
