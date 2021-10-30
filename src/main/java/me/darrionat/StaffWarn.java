package me.darrionat;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import me.darrionat.listeners.PlayerCommandListener;
import me.darrionat.services.MessageService;
import me.darrionat.services.PermissionService;
import me.darrionat.statics.Bootstrapper;
import me.darrionat.statics.Utils;

public class StaffWarn extends Plugin {
	private MessageService messageService;
	private PermissionService permissionService;

	@Override
	public void onEnable() {
		initFields();

		systemLog("Registering listeners");
		ProxyServer.getInstance().getPluginManager().registerListener(this, new PlayerCommandListener(this, messageService, permissionService));
	}

	public void initFields() {
		Bootstrapper bootstrapper = Bootstrapper.getBootstrapper();
		bootstrapper.init(this);
		this.messageService = bootstrapper.getMessageService();
		this.permissionService = bootstrapper.getPermissionService();
	}

	public void systemLog(String s) {
		this.getLogger().info(Utils.chat("[" + getDescription().getName() + " " + getDescription().getVersion() + "] " + s));
	}
}
