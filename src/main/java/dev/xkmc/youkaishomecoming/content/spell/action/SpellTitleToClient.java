package dev.xkmc.youkaishomecoming.content.spell.action;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.client.SpellTitleOverlay;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class SpellTitleToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public String titleId = "";

	@SerialClass.SerialField
	public CompoundTag presentation = new CompoundTag();

	@SerialClass.SerialField
	public String name = "";

	@SerialClass.SerialField
	public String description = "";

	@SerialClass.SerialField
	public int duration = 100;

	@Deprecated
	public SpellTitleToClient() {
	}

	public SpellTitleToClient(String titleId, String name, String description, int duration, CompoundTag presentation) {
		this.titleId = titleId;
		this.name = name;
		this.description = description;
		this.duration = duration;
		this.presentation = presentation.copy();
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellTitleOverlay.show(titleId, name, description, duration, presentation);
	}
}
