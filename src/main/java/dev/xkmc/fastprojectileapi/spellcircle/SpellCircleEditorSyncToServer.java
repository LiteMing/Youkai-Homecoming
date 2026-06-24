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

	@SerialClass.SerialField
	public String circleId = "";
	@SerialClass.SerialField
	public String componentJson = "";
	@SerialClass.SerialField
	public boolean exportGlobal = false;

	@Deprecated
	public SpellCircleEditorSyncToServer() {
	}

	public SpellCircleEditorSyncToServer(ResourceLocation id, SpellComponent component, boolean exportGlobal) {
		this.circleId = id.toString();
		component.invalidateCache();
		this.componentJson = GSON.toJson(component);
		this.exportGlobal = exportGlobal;
	}

	public SpellCircleEditorSyncToServer(ResourceLocation id, Map<ResourceLocation, SpellComponent> components, boolean exportGlobal) {
		this.circleId = id.toString();
		JsonObject map = new JsonObject();
		for (var entry : components.entrySet()) {
			entry.getValue().invalidateCache();
			map.add(entry.getKey().toString(), GSON.toJsonTree(entry.getValue()));
		}
		JsonObject root = new JsonObject();
		root.add("map", map);
		this.componentJson = GSON.toJson(root);
		this.exportGlobal = exportGlobal;
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
			Map<ResourceLocation, SpellComponent> components = parseComponents(id);
			if (components.isEmpty()) {
				throw new IllegalArgumentException("Spell circle parse returned no results");
			}
			for (var entry : components.entrySet()) {
				entry.getValue().invalidateCache();
				YoukaisHomecoming.SPELL.getMerged().map.put(entry.getKey().toString(), entry.getValue());
			}
			if (exportGlobal) {
				var file = CustomSpellCircleStorage.saveGlobalCircles(id, components);
				sender.sendSystemMessage(Component.literal("[YH] Exported global spell circle " + id + " to " + file.getPath()));
			} else {
				CustomSpellCircleStorage.saveCircles(sender.server, id, components);
				sender.sendSystemMessage(Component.literal("[YH] Saved spell circle " + id));
			}
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
