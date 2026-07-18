package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.l2serial.network.SerialPacketBase;
import net.minecraftforge.network.NetworkEvent;

public class OpenSpellMarketToClient extends SerialPacketBase {

	@Override
	public void handle(NetworkEvent.Context context) {
		SpellMarketClientHandler.open();
	}
}
