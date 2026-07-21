package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Opens spell preview on the client.
 * <p>
 * Draft editor uses this packet alone. Full definitions always go through
 * {@link SpellPreviewChunkToClient#sendOpenPreview} so large JSON is chunked
 * under the 32767 {@code writeUtf} limit while still syncing server content.
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

	/** Preferred: always full server definition, chunked if needed. */
	public static void sendPreview(ServerPlayer player, SpellDefinition definition) {
		SpellPreviewChunkToClient.sendOpenPreview(player, definition);
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellPreviewClientHandler.open(this);
	}
}
