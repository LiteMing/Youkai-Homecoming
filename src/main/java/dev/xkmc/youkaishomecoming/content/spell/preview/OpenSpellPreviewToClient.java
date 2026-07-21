package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Opens spell preview on the client.
 * <p>
 * Draft editor and legacy-ticker spells use this packet alone (id-only).
 * Data-driven definitions go through {@link SpellPreviewChunkToClient#sendOpenPreview}
 * so large JSON is chunked under the 32767 {@code writeUtf} limit.
 */
@SerialClass
public class OpenSpellPreviewToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public boolean draft;
	@SerialClass.SerialField
	public String spellId = "";
	/** Unused for open path; kept for packet schema compatibility. */
	@SerialClass.SerialField
	public String definitionJson = "";

	@Deprecated
	public OpenSpellPreviewToClient() {
	}

	private OpenSpellPreviewToClient(boolean draft, String spellId, String definitionJson) {
		this.draft = draft;
		this.spellId = spellId;
		this.definitionJson = definitionJson;
	}

	public static OpenSpellPreviewToClient draftEditor() {
		return new OpenSpellPreviewToClient(true, "", "");
	}

	/**
	 * Open preview for a spell definition.
	 * Legacy-ticker spells skip JSON encode (factory is not serializable) and open by id only.
	 * Data-driven spells still send the full definition, chunked if needed.
	 */
	public static void sendPreview(ServerPlayer player, SpellDefinition definition) {
		if (definition.hasLegacyTicker()) {
			String id = definition.id != null ? definition.id.toString() : "";
			YoukaisHomecoming.HANDLER.toClientPlayer(
					new OpenSpellPreviewToClient(false, id, ""), player);
			return;
		}
		SpellPreviewChunkToClient.sendOpenPreview(player, definition);
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellPreviewClientHandler.open(this);
	}
}
