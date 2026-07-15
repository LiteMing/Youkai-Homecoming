package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class SpellPreviewClientHandler {

	public static void open(OpenSpellPreviewToClient packet) {
		Minecraft.getInstance().execute(() -> openOnClient(packet));
	}

	private static void openOnClient(OpenSpellPreviewToClient packet) {
		Minecraft mc = Minecraft.getInstance();
		if (packet.draft) {
			mc.setScreen(SpellPreviewScreen.createDraftEditor());
			return;
		}
		SpellDefinition definition = readDefinition(packet);
		if (definition == null) {
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.literal("[YH] Unknown spell: " + packet.spellId), false);
			}
			return;
		}
		SpellRegistry.register(definition);
		mc.setScreen(new SpellPreviewScreen(definition));
	}

	private static SpellDefinition readDefinition(OpenSpellPreviewToClient packet) {
		if (packet.definitionJson != null && !packet.definitionJson.isBlank()) {
			try {
				var json = JsonParser.parseString(packet.definitionJson);
				return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
						.resultOrPartial(msg -> YoukaisHomecoming.LOGGER.warn("Failed to open spell preview: {}", msg))
						.orElse(null);
			} catch (Exception e) {
				YoukaisHomecoming.LOGGER.warn("Failed to decode spell preview {}", packet.spellId, e);
			}
		}
		if (packet.spellId == null || packet.spellId.isBlank()) return null;
		try {
			return SpellRegistry.get(new ResourceLocation(packet.spellId));
		} catch (Exception e) {
			return null;
		}
	}

}
