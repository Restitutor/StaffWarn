package me.darrionat.repositories;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;


import me.darrionat.StaffWarn;

public class FileRepository {

	public static final String COMMAND_PERMISSIONS = "commandPermissions";
	public static final String MESSAGES = "messages";

	private StaffWarn plugin;

	public FileRepository(StaffWarn plugin) {
		this.plugin = plugin;
		setupFiles();
	}

	public static final String CONFIG = "config";

	private static final String[] CONFIG_NAMES = { CONFIG, COMMAND_PERMISSIONS, MESSAGES };
	private static final String[] DATA_CONFIG_NAMES = {};
	private static final String[] DIRECTORIES = {};

	private void setupFiles() {
		plugin.systemLog("Setting up files");
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdir();
		}

		for (String fileName : CONFIG_NAMES) {
			if (!fileExists(fileName)) {
				plugin.systemLog("Saving " + fileName + ".yml");
				saveResource(fileName);
				continue;
			}
			plugin.systemLog("Updating " + fileName + ".yml");
			matchConfig(fileName);
		}

		for (String fileName : DATA_CONFIG_NAMES) {
			if (!fileExists(fileName)) {
				setupFile(fileName);
			}
		}

		for (String dir : DIRECTORIES) {
			if (!dirExists(dir))
				mkDir(dir);
		}
	}

	private Configuration getJarConfig(String fileName) {
		final InputStream is = plugin.getResourceAsStream(fileName + ".yml");
		return ConfigurationProvider.getProvider(YamlConfiguration.class).load(is);
	}

	private void saveResource(String fileName) {
		final Configuration jarConfig = getJarConfig(fileName);
		saveConfigFile(fileName, jarConfig);
	}

	public void setupFile(String fileName) {
		File file = new File(plugin.getDataFolder(), fileName + ".yml");
		setupFile(file);
	}

	public void setupPlayerDataFile(UUID uuid) {
		File file = new File(plugin.getDataFolder() + "\\data", uuid.toString() + ".yml");
		setupFile(file);
	}

	private void setupFile(File file) {
		if (!file.exists()) {
			try {
				file.createNewFile();
				plugin.systemLog("Saving " + file.getName());
			} catch (IOException exe) {
				plugin.systemLog("Failed to create " + file.getName());
				exe.printStackTrace();
			}
		}
	}

	public boolean fileExists(String fileName) {
		return new File(plugin.getDataFolder(), fileName + ".yml").exists();
	}

	public void deleteFile(String fileName) {
		new File(plugin.getDataFolder(), fileName + ".yml").delete();
	}

	public Configuration getDataConfigWithDefault(String fileName) {
		final Configuration jarConfig = getJarConfig(fileName);
		File file = getDataFile(fileName);

		try {
			return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file, jarConfig);
		} catch (IOException e) {
			e.printStackTrace();
			setupFile(file);
			return getDataConfigWithDefault(fileName);
		}
	}

	public Configuration getDataConfig(String fileName) {
		final File file = getDataFile(fileName);

		try {
			return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
		} catch (IOException e) {
			e.printStackTrace();
			setupFile(file);
			return getDataConfig(fileName);
		}
	}

	public File getDataFile(String fileName) {
		return new File(plugin.getDataFolder(), fileName + ".yml");
	}

	public void saveConfigFile(String fileName, Configuration dataConfig) {
		try {
			File file = getDataFile(fileName);
			ConfigurationProvider.getProvider(YamlConfiguration.class).save(dataConfig, file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean dirExists(String dir) {
		return new File(plugin.getDataFolder(), "\\" + dir).exists();
	}

	public void mkDir(String dir) {
		new File(plugin.getDataFolder(), dir).mkdir();
	}

	public void matchConfig(String fileName) {
		// Add new keys from jar
		Configuration config = getDataConfigWithDefault(fileName);

		// Regression
		// This is a shallow search but the Spigot version uses a deep search
		final Configuration jarConfig = getJarConfig(fileName);
		for (String key : config.getKeys())
			if (!jarConfig.contains(key))
				config.set(key, null);

		config.set("version", plugin.getDescription().getVersion());
		saveConfigFile(fileName, config);
	}
}