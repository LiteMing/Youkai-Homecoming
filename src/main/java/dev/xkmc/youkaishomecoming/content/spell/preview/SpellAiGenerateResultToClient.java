package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class SpellAiGenerateResultToClient extends SerialPacketBase {
	@SerialClass.SerialField public boolean success;
	@SerialClass.SerialField public String json = "";
	@SerialClass.SerialField public String message = "";

	@Deprecated public SpellAiGenerateResultToClient() {}

	public SpellAiGenerateResultToClient(boolean success, String json, String message) {
		this.success = success;
		this.json = json == null ? "" : json;
		this.message = message == null ? "" : message;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellPreviewClientHandler.onAiResult(this);
	}
}
