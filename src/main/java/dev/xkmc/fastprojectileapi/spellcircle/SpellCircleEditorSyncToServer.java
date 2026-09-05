package dev.xkmc.fastprojectileapi.spellcircle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;

@SerialClass
public class SpellCircleEditorSyncToServer extends SerialPacketBase {

	private static final Gson GSON = new Gson();

	public enum Action {
		SAVE,
		DELETE
	}

	@SerialClass.SerialField
	public Action action = Action.SAVE;
	@SerialClass.SerialField
	public String circleId = "";
	@SerialClass.SerialField
	public String componentJson = "";

	@Deprecated
	public SpellCircleEditorSyncToServer() {
	}

	public SpellCircleEditorSyncToServer(ResourceLocation id, SpellComponent component) {
		this.action = Action.SAVE;
		this.circleId = id.toString();
		component.invalidateCache();
		this.componentJson = GSON.toJson(component);
	}

	public SpellCircleEditorSyncToServer(ResourceLocation id, Map<ResourceLocation, SpellComponent> components) {
		this.action = Action.SAVE;
		this.circleId = id.toString();
		JsonObject map = new JsonObject();
		for (var entry : components.entrySet()) {
			entry.getValue().invalidateCache();
			map.add(entry.getKey().toString(), GSON.toJsonTree(entry.getValue()));
		}
		JsonObject root = new JsonObject();
		root.add("map", map);
		this.componentJson = GSON.toJson(root);
	}

	public static SpellCircleEditorSyncToServer delete(ResourceLocation id) {
		SpellCircleEditorSyncToServer packet = new SpellCircleEditorSyncToServer();
		packet.action = Action.DELETE;
		packet.circleId = id.toString();
		packet.componentJson = "";
		return packet;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer sender = context.getSender();
		if (sender == null) return;
		if (!sender.hasPermissions(2)) {
			sender.sendSystemMessage(Component.literal("[YH] No permission to edit spell circles on this server."));
			return;
		}
		try {
			ResourceLocation id = ResourceLocation.tryParse(circleId);
			if (id == null) {
				throw new IllegalArgumentException("Invalid spell circle id: " + circleId);
			}
			Action op = action == null ? Action.SAVE : action;
			if (op == Action.DELETE) {
				deleteCircle(sender, id);
				return;
			}
			Map<ResourceLocation, SpellComponent> components = parseComponents(id);
			if (components.isEmpty()) {
				throw new IllegalArgumentException("Spell circle parse returned no results");
			}
			for (var entry : components.entrySet()) {
				entry.getValue().invalidateCache();
				YoukaisHomecoming.SPELL.getMerged().map.put(entry.getKey().toString(), entry.getValue());
			}
			CustomSpellCircleStorage.saveCircles(sender.server, id, components);
			sender.sendSystemMessage(Component.literal("[YH] Saved spell circle " + id));
			for (ServerPlayer player : sender.server.getPlayerList().getPlayers()) {
				for (var entry : components.entrySet()) {
					YoukaisHomecoming.HANDLER.toClientPlayer(
							new SpellCircleDefinitionToClient(entry.getKey(), entry.getValue()), player);
				}
			}
		} catch (Exception e) {
			String msg = e.getMessage();
			sender.sendSystemMessage(Component.literal("[YH] Spell circle editor sync failed: " +
					(msg == null ? e.getClass().getSimpleName() : msg)));
		}
	}

	private void deleteCircle(ServerPlayer sender, ResourceLocation id) {
		if (SpellCircleConfig.isBuiltin(id)) {
			throw new IllegalArgumentException("Cannot delete built-in spell circle: " + id);
		}
		YoukaisHomecoming.SPELL.getMerged().map.remove(id.toString());
		CustomSpellCircleStorage.deleteCircle(sender.server, id);
		sender.sendSystemMessage(Component.literal("[YH] Deleted spell circle " + id));
		for (ServerPlayer player : sender.server.getPlayerList().getPlayers()) {
			YoukaisHomecoming.HANDLER.toClientPlayer(SpellCircleDefinitionToClient.delete(id), player);
		}
	}

	private Map<ResourceLocation, SpellComponent> parseComponents(ResourceLocation fallbackId) {
		JsonElement json = JsonParser.parseString(componentJson);
		Map<ResourceLocation, SpellComponent> ans = new LinkedHashMap<>();
		if (json.isJsonObject()) {
			JsonObject object = json.getAsJsonObject();
			if (object.has("map") && object.get("map").isJsonObject()) {
				JsonObject map = object.getAsJsonObject("map");
				for (var entry : map.entrySet()) {
					ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
					if (id == null) {
						throw new IllegalArgumentException("Invalid spell circle id: " + entry.getKey());
					}
					SpellComponent component = GSON.fromJson(entry.getValue(), SpellComponent.class);
					if (component == null) {
						throw new IllegalArgumentException("Spell circle parse returned no result: " + id);
					}
					component.invalidateCache();
					ans.put(id, component);
				}
				return ans;
			}
		}
		SpellComponent component = GSON.fromJson(json, SpellComponent.class);
		if (component == null) {
			throw new IllegalArgumentException("Spell circle parse returned no result");
		}
		component.invalidateCache();
		ans.put(fallbackId, component);
		return ans;
	}

}
