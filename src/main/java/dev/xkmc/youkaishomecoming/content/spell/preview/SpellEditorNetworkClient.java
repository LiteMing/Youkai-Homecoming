package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;

public class SpellEditorNetworkClient {

	public static void save(SpellDefinition definition) {
		YoukaisHomecoming.HANDLER.toServer(SpellEditorSyncToServer.save(definition, false));
	}

	public static void saveAndReapply(SpellDefinition definition) {
		YoukaisHomecoming.HANDLER.toServer(SpellEditorSyncToServer.save(definition, true));
	}

	public static void delete(ResourceLocation spellId) {
		YoukaisHomecoming.HANDLER.toServer(SpellEditorSyncToServer.delete(spellId));
	}

}
