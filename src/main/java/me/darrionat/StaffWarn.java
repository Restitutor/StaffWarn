package me.darrionat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.darrionat.listeners.PlayerCommandListener;
import me.darrionat.services.MessageService;
import me.darrionat.services.PermissionService;
import me.darrionat.statics.Bootstrapper;
import me.darrionat.statics.Utils;

public class StaffWarn extends JavaPlugin {
	private MessageService messageService;
	private PermissionService permissionService;

	@Override
	public void onEnable() {
		initFields();

		systemLog("Registering listeners");
		Bukkit.getPluginManager().registerEvents(new PlayerCommandListener(this, messageService, permissionService), this);
	}

	public void initFields() {
		Bootstrapper bootstrapper = Bootstrapper.getBootstrapper();
		bootstrapper.init(this);
		this.messageService = bootstrapper.getMessageService();
		this.permissionService = bootstrapper.getPermissionService();
	}

	public void systemLog(String s) {
		System.out.println(Utils.chat("[" + getDescription().getName() + " " + getDescription().getVersion() + "] " + s));
	}
}