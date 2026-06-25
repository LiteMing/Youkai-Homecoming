package dev.xkmc.fastprojectileapi.spellcircle;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class SpellCircleDefinitionToClient extends SerialPacketBase {

	private static final Gson GSON = new Gson();

	@SerialClass.SerialField
	public String circleId = "";
	@SerialClass.SerialField
	public String componentJson = "";
	@SerialClass.SerialField
	public boolean deleted = false;

	@Deprecated
	public SpellCircleDefinitionToClient() {
	}

	public SpellCircleDefinitionToClient(ResourceLocation id, SpellComponent component) {
		this.circleId = id.toString();
		component.invalidateCache();
		this.componentJson = GSON.toJson(component);
	}

	public static SpellCircleDefinitionToClient delete(ResourceLocation id) {
		SpellCircleDefinitionToClient packet = new SpellCircleDefinitionToClient();
		packet.circleId = id.toString();
		packet.deleted = true;
		return packet;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		Minecraft.getInstance().execute(() -> {
			try {
				ResourceLocation id = ResourceLocation.tryParse(circleId);
				if (id == null) {
					return;
				}
				if (deleted) {
					YoukaisHomecoming.SPELL.getMerged().map.remove(id.toString());
					invalidateAllCachedChildren();
					return;
				}
				SpellComponent component = GSON.fromJson(JsonParser.parseString(componentJson), SpellComponent.class);
				if (component == null) {
					return;
				}
				component.invalidateCache();
				YoukaisHomecoming.SPELL.getMerged().map.put(id.toString(), component);
				invalidateAllCachedChildren();
			} catch (Exception e) {
				YoukaisHomecoming.LOGGER.warn("Failed to sync spell circle {}", circleId, e);
			}
		});
	}

	private static void invalidateAllCachedChildren() {
		for (SpellComponent component : YoukaisHomecoming.SPELL.getMerged().map.values()) {
			if (component != null) {
				component.invalidateCache();
			}
		}
	}

}
