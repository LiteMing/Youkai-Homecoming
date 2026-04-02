package dev.xkmc.youkaishomecoming.content.spell.editor;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class OpenSpellEditorToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public String definitionJson;

	public OpenSpellEditorToClient() {
	}

	public OpenSpellEditorToClient(String definitionJson) {
		this.definitionJson = definitionJson;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellEditorClientHelper.open(definitionJson);
	}
}
