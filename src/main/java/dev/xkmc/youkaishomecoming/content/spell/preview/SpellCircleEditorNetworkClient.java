package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.fastprojectileapi.spellcircle.SpellCircleEditorSyncToServer;
import dev.xkmc.fastprojectileapi.spellcircle.SpellComponent;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class SpellCircleEditorNetworkClient {

	public static void save(ResourceLocation id, SpellComponent component) {
		YoukaisHomecoming.HANDLER.toServer(new SpellCircleEditorSyncToServer(id, component));
	}

	public static void save(ResourceLocation id, Map<ResourceLocation, SpellComponent> components) {
		YoukaisHomecoming.HANDLER.toServer(new SpellCircleEditorSyncToServer(id, components));
	}

	public static void delete(ResourceLocation id) {
		YoukaisHomecoming.HANDLER.toServer(SpellCircleEditorSyncToServer.delete(id));
	}

}
