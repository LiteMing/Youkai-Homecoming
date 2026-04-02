package dev.xkmc.youkaishomecoming.content.spell.editor;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class SpellEditorClientHelper {

	private SpellEditorClientHelper() {
	}

	public static void open(String definitionJson) {
		Minecraft minecraft = Minecraft.getInstance();
		try {
			SpellDefinition definition = SpellEditorCodec.decodeDefinitionJson(definitionJson);
			minecraft.setScreen(new SpellEditorScreen(EditorState.fromDefinition(definition)));
		} catch (Exception e) {
			if (minecraft.player != null) {
				minecraft.player.displayClientMessage(
						Component.literal("Failed to open spell editor: " + e.getMessage()),
						false
				);
			}
		}
	}
}
