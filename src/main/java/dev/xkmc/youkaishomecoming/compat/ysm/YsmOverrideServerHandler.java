package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.compat.ysm.YSMCompatConfig.RenderBinding;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-side authority for the /yhysm manual model override commands.
 * Validates the request (permissions, entity types, targets), persists it in
 * {@link YsmOverrideData} and broadcasts the resulting table to all clients.
 */
public class YsmOverrideServerHandler {

	private static final int REQUIRED_PERMISSION_LEVEL = 2;

	public static void handle(@Nullable ServerPlayer player, YsmOverrideRequestToServer request) {
		MinecraftServer server = player == null ? null : player.getServer();
		if (server == null) {
			return;
		}
		if (!player.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
			broadcastResult(server, null, "[YH/YSM] Requires operator permission (level 2).");
			return;
		}
		YsmOverrideData data = YsmOverrideData.get(server);
		String message;
		switch (request.action) {
			case "type_set" -> message = applyTypeSet(data, request);
			case "type_off" -> message = applyTypeOff(data, request);
			case "type_unset" -> message = applyTypeUnset(data, request);
			case "entity_set" -> message = applyEntitySet(server, data, request);
			case "entity_off" -> message = applyEntityOff(server, data, request);
			case "entity_unset" -> message = applyEntityUnset(data, request);
			case "reset" -> {
				data.clearAll();
				message = "[YH/YSM] Debug render mappings reset.";
			}
			default -> message = "[YH/YSM] Unknown override action: " + request.action;
		}
		broadcastResult(server, data, message);
	}

	/** Send the current table to a single player (on login). */
	public static void syncToPlayer(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		YoukaisHomecoming.HANDLER.toClientPlayer(new YsmOverrideSyncToClient(YsmOverrideData.get(server), ""), player);
	}

	private static void broadcastResult(MinecraftServer server, @Nullable YsmOverrideData data, @Nullable String message) {
		YsmOverrideSyncToClient packet = new YsmOverrideSyncToClient(data, message == null ? "" : message);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			YoukaisHomecoming.HANDLER.toClientPlayer(packet, player);
		}
	}

	@Nullable
	private static ResourceLocation validateType(String entityType) {
		ResourceLocation id = ResourceLocation.tryParse(entityType);
		if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)
				|| !Objects.equals(id.getNamespace(), YoukaisHomecoming.MODID)) {
			return null;
		}
		return id;
	}

	private static String applyTypeSet(YsmOverrideData data, YsmOverrideRequestToServer request) {
		ResourceLocation type = validateType(request.entityType);
		if (type == null) {
			return "[YH/YSM] Unknown or non-YH entity type: " + request.entityType;
		}
		if (request.modelId.isBlank() || request.textureName.isBlank()) {
			return "[YH/YSM] Model and texture must not be blank.";
		}
		data.setType(type, RenderBinding.enabled(request.modelId, request.textureName));
		return "[YH/YSM] type " + type + " -> " + request.modelId + " / " + request.textureName;
	}

	private static String applyTypeOff(YsmOverrideData data, YsmOverrideRequestToServer request) {
		ResourceLocation type = validateType(request.entityType);
		if (type == null) {
			return "[YH/YSM] Unknown or non-YH entity type: " + request.entityType;
		}
		data.setType(type, RenderBinding.disabled());
		return "[YH/YSM] type " + type + " YSM rendering disabled.";
	}

	private static String applyTypeUnset(YsmOverrideData data, YsmOverrideRequestToServer request) {
		ResourceLocation type = validateType(request.entityType);
		if (type == null) {
			return "[YH/YSM] Unknown or non-YH entity type: " + request.entityType;
		}
		data.removeType(type);
		return "[YH/YSM] type " + type + " uses its default mapping.";
	}

	private static String applyEntitySet(MinecraftServer server, YsmOverrideData data, YsmOverrideRequestToServer request) {
		if (request.modelId.isBlank() || request.textureName.isBlank()) {
			return "[YH/YSM] Model and texture must not be blank.";
		}
		return applyToEntities(server, data, request, RenderBinding.enabled(request.modelId, request.textureName));
	}

	private static String applyEntityOff(MinecraftServer server, YsmOverrideData data, YsmOverrideRequestToServer request) {
		return applyToEntities(server, data, request, RenderBinding.disabled());
	}

	private static String applyEntityUnset(YsmOverrideData data, YsmOverrideRequestToServer request) {
		String[] uuids = request.uuidList.split(",");
		if (uuids.length == 0) {
			return "[YH/YSM] No entity targets.";
		}
		int applied = 0;
		String firstError = null;
		for (String part : uuids) {
			if (part.isBlank()) {
				continue;
			}
			UUID uuid;
			try {
				uuid = UUID.fromString(part.trim());
			} catch (IllegalArgumentException ex) {
				firstError = "[YH/YSM] Invalid UUID: " + part.trim();
				continue;
			}
			data.removeEntity(uuid);
			applied++;
		}
		if (applied == 0) {
			return firstError != null ? firstError : "[YH/YSM] No entity targets.";
		}
		return "[YH/YSM] " + applied + " entity override(s) removed.";
	}

	private static String applyToEntities(MinecraftServer server, YsmOverrideData data, YsmOverrideRequestToServer request, RenderBinding binding) {
		String[] uuids = request.uuidList.split(",");
		if (uuids.length == 0) {
			return "[YH/YSM] No entity targets.";
		}
		int applied = 0;
		String firstError = null;
		for (String part : uuids) {
			if (part.isBlank()) {
				continue;
			}
			UUID uuid;
			try {
				uuid = UUID.fromString(part.trim());
			} catch (IllegalArgumentException ex) {
				firstError = "[YH/YSM] Invalid UUID: " + part.trim();
				continue;
			}
			if (findEntity(server, uuid) == null) {
				firstError = "[YH/YSM] Entity not found on server: " + uuid;
				continue;
			}
			data.setEntity(uuid, binding);
			applied++;
		}
		if (applied == 0) {
			return firstError != null ? firstError : "[YH/YSM] No entity targets.";
		}
		return "[YH/YSM] " + applied + " entity override(s) applied.";
	}

	@Nullable
	private static Entity findEntity(MinecraftServer server, UUID uuid) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(uuid);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

}
