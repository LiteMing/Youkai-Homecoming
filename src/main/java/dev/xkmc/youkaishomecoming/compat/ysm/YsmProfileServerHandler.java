package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class YsmProfileServerHandler {
	private YsmProfileServerHandler() { }

	public static void handle(@Nullable ServerPlayer player, YsmProfileRequestToServer request) {
		if (player == null || player.getServer() == null) return;
		var server = player.getServer();
		var data = YsmProfileData.get(server);
		YsmProfileData.Entry entry = null;
		String id = request.requestId != null && request.requestId.length() <= 64 ? request.requestId : "";
		try {
			if (request.action == null || request.action.length() > 32 || request.target == null || request.target.length() > 64
					|| request.preset == null || request.preset.length() > 128)
				throw new IllegalArgumentException("Invalid profile request fields");
			entry = data.entry(request.model);
			if ("get".equals(request.action)) {
				reply(player, entry, id, true, "loaded");
				return;
			}
			if (!player.hasPermissions(2)) throw new IllegalArgumentException("permission");
			switch (request.action) {
				case "save" -> {
					YsmModelProfile profile = YsmModelProfile.fromJson(request.json);
					if (!profile.model().equals(request.model)) throw new IllegalArgumentException("Profile model does not match request");
					if (profile.presets().size() > YHModConfig.COMMON.modelPresentationMaxPresets.get()) throw new IllegalArgumentException("Too many presets");
					profile.presets().values().forEach(YHModel::validatePreset);
					entry = data.replace(profile, request.expectedRevision, YHModConfig.COMMON.modelPresentationMaxProfiles.get());
					for (ServerPlayer observer : server.getPlayerList().getPlayers())
						if (observer != player) reply(observer, entry, "", true, "");
					reply(player, entry, id, true, "saved");
				}
				case "apply", "clear" -> {
					var entity = YsmOverrideServerHandler.findEntity(server, UUID.fromString(request.target));
					if (!(entity instanceof YsmRenderOverrideTarget target)) throw new IllegalArgumentException("Unsupported or unloaded entity");
					if ("clear".equals(request.action)) YHModel.clear(target);
					else target.setYsmPresentation(YHModel.presetRequest(target, request.model, request.preset, request.ticks, YsmPresentationState.Source.EDITOR));
					reply(player, entry, id, true, "applied");
				}
				default -> throw new IllegalArgumentException("Unknown editor action");
			}
		} catch (IllegalArgumentException | ArithmeticException ex) {
			reply(player, entry, id, false, ex.getMessage());
		}
	}

	private static void reply(ServerPlayer player, YsmProfileData.Entry entry, String id, boolean success, String message) {
		YoukaisHomecoming.HANDLER.toClientPlayer(new YsmProfileSyncToClient(entry, id, success, message), player);
	}

	public static void syncToPlayer(ServerPlayer player) {
		if (player.getServer() == null) return;
		YsmProfileSyncToClient reset = new YsmProfileSyncToClient();
		reset.reset = true;
		YoukaisHomecoming.HANDLER.toClientPlayer(reset, player);
		YsmProfileData.get(player.getServer()).entries().values().forEach(entry -> reply(player, entry, "", true, ""));
	}
}
