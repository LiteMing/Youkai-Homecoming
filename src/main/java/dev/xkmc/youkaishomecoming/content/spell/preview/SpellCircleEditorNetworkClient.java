package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.fastprojectileapi.spellcircle.SpellCircleEditorSyncToServer;
import dev.xkmc.fastprojectileapi.spellcircle.SpellComponent;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;

public class SpellCircleEditorNetworkClient {

	public static void save(ResourceLocation id, SpellComponent component) {
		YoukaisHomecoming.HANDLER.toServer(new SpellCircleEditorSyncToServer(id, component, false));
	}

	public static void exportGlobal(ResourceLocation id, SpellComponent component) {
		YoukaisHomecoming.HANDLER.toServer(new SpellCircleEditorSyncToServer(id, component, true));
	}

}
