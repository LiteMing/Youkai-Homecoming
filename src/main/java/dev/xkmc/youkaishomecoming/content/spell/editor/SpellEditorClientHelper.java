package dev.xkmc.youkaishomecoming.content.spell.editor;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class SpellEditorClientHelper {

	private SpellEditorClientHelper() {
	}

	public static void open(@Nullable String definitionJson) {
		Minecraft minecraft = Minecraft.getInstance();
		try {
			if (definitionJson == null || definitionJson.isBlank()) {
				throw new IllegalStateException("editor packet payload is empty");
			}
			SpellDefinition definition = SpellEditorCodec.decodeDefinitionJson(definitionJson);
			minecraft.setScreen(new SpellEditorScreen(EditorState.fromDefinition(definition)));
		} catch (Throwable e) {
			YoukaisHomecoming.LOGGER.error(
					"Failed to open spell editor on client. payloadLength={}",
					definitionJson == null ? -1 : definitionJson.length(),
					e
			);
			if (minecraft.player != null) {
				minecraft.player.displayClientMessage(
						Component.literal("Failed to open spell editor: " + describeException(e)),
						false
				);
			}
		}
	}

	private static String describeException(Throwable e) {
		String name = e.getClass().getSimpleName();
		String message = e.getMessage();
		return message == null || message.isBlank() ? name : name + ": " + message;
	}
}
