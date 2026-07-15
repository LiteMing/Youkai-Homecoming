package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.Gson;
import com.mojang.serialization.JsonOps;
import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class OpenSpellPreviewToClient extends SerialPacketBase {

	private static final Gson GSON = new Gson();

	@SerialClass.SerialField
	public boolean draft;
	@SerialClass.SerialField
	public String spellId = "";
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

	public static OpenSpellPreviewToClient preview(SpellDefinition definition) {
		var json = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
				.getOrThrow(false, s -> {});
		return new OpenSpellPreviewToClient(false, definition.id.toString(), GSON.toJson(json));
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellPreviewClientHandler.open(this);
	}

}
