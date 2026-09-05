package dev.xkmc.youkaishomecoming.compat.stg.control;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client input request for the temporary, server-authoritative classic controls mode. */
@SerialClass
public class ClassicControlRequestToServer extends SerialPacketBase {

	public static final int TOGGLE_MODE = 0;
	public static final int NON_SPELL_ON = 1;
	public static final int NON_SPELL_OFF = 2;
	public static final int CAST_NEXT_SPELL = 3;

	@SerialClass.SerialField
	public int action;

	public ClassicControlRequestToServer() {
	}

	public ClassicControlRequestToServer(int action) {
		this.action = action;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		ServerPlayer player = context.getSender();
		if (player != null) {
			context.enqueueWork(() -> ClassicControlService.handleInput(player, action));
		}
		context.setPacketHandled(true);
	}
}
