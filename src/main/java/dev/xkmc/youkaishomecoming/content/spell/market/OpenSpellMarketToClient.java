package dev.xkmc.youkaishomecoming.content.spell.market;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

@SerialClass
public class OpenSpellMarketToClient extends SerialPacketBase {

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> SpellMarketClientHandler::open));
		context.setPacketHandled(true);
	}
}
