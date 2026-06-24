package dev.xkmc.fastprojectileapi.spellcircle;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

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
			SpellComponent component = GSON.fromJson(JsonParser.parseString(componentJson), SpellComponent.class);
			if (component == null) {
				throw new IllegalArgumentException("Spell circle parse returned no result");
			}
			component.invalidateCache();
			YoukaisHomecoming.SPELL.getMerged().map.put(id.toString(), component);
			if (exportGlobal) {
				var file = CustomSpellCircleStorage.saveGlobalCircle(id, component);
				sender.sendSystemMessage(Component.literal("[YH] Exported global spell circle " + id + " to " + file.getPath()));
			} else {
				CustomSpellCircleStorage.saveCircle(sender.server, id, component);
				sender.sendSystemMessage(Component.literal("[YH] Saved spell circle " + id));
			}
			var packet = new SpellCircleDefinitionToClient(id, component);
			for (ServerPlayer player : sender.server.getPlayerList().getPlayers()) {
				YoukaisHomecoming.HANDLER.toClientPlayer(packet, player);
			}
		} catch (Exception e) {
			String msg = e.getMessage();
			sender.sendSystemMessage(Component.literal("[YH] Spell circle editor sync failed: " +
					(msg == null ? e.getClass().getSimpleName() : msg)));
		}
	}

}
