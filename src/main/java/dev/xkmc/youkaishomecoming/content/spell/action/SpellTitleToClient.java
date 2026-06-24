package dev.xkmc.youkaishomecoming.content.spell.action;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.client.SpellTitleOverlay;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class SpellTitleToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public String name = "";

	@SerialClass.SerialField
	public String description = "";

	@SerialClass.SerialField
	public int duration = 100;

	@Deprecated
	public SpellTitleToClient() {
	}

	public SpellTitleToClient(String name, String description, int duration) {
		this.name = name;
		this.description = description;
		this.duration = duration;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellTitleOverlay.show(name, description, duration);
	}
}
