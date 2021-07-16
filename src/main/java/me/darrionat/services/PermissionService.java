package me.darrionat.services;

import me.darrionat.repositories.CommandPermissionRepository;

import java.util.HashSet;

import net.luckperms.api.context.MutableContextSet;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.metadata.types.InheritanceOriginMetadata;
import net.luckperms.api.query.QueryOptions;


public class PermissionService {
	private CommandPermissionRepository commandPermissionRepository;
	private HashSet<String> defaultGroups;
	private HashSet<String> excludedServers;

	public PermissionService(ConfigService configService, CommandPermissionRepository commandPermissionRepository) {
		this.commandPermissionRepository = commandPermissionRepository;
		this.defaultGroups = configService.getDefaultGroups();
		this.excludedServers = configService.getExcludedServers();
	}

	public String getPermission(String label) {
		return commandPermissionRepository.getPermission(label);
	}

	public boolean permissionDefined(String label) {
		return commandPermissionRepository.permissionDefined(label);
	}

	public boolean ignoreServer(String server) {
		return excludedServers.contains(server);
	}

	public MutableContextSet getQueryFromServer(String server) {
		MutableContextSet context = MutableContextSet.create();
		context.add("server", server);
		return context;
	}

	public MutableContextSet getQuery(String server, String world) {
		MutableContextSet context = getQueryFromServer(server);
		context.add("world", world);
		return context;
	}

	public String getPermissionOrigin(MutableContextSet context, PermissionHolder pHolder, String permission) {
		for (Node node : pHolder.resolveDistinctInheritedNodes(QueryOptions.contextual(context))) {
			if (node.getKey().equals(permission)) {
				return node.metadata(InheritanceOriginMetadata.KEY).getOrigin().getName();
			}
		}
		return "";
	}

	private String getPermissionOriginGroup(MutableContextSet context, String group, String permission) {
		final Group g = LuckPermsProvider.get().getGroupManager().getGroup(group);
		return getPermissionOrigin(context, g, permission);
	}

	public String getPermissionOriginByDefault(MutableContextSet context, String permission) {
		/**
		 * We could optimize this by traversing the child groups of defaultGroups.
		 * To find and remove groups contributing to duplicate searches.
		 */
		for (String group : defaultGroups) {
			final String origin = getPermissionOriginGroup(context, group, permission);
			if (!origin.isEmpty()) return origin;
		}
		return "";
	}

	public boolean defaultHasPermission(MutableContextSet context, String permission) {
		return !getPermissionOriginByDefault(context, permission).isEmpty();
	}
}