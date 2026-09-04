package dev.xkmc.youkaishomecoming.compat.stg.control;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

/** Projects the server-owned classic controls flag onto the local input handler. */
@SerialClass
public class ClassicControlSyncToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public boolean enabled;

	public ClassicControlSyncToClient() {
	}

	public ClassicControlSyncToClient(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> ClassicControlClient.setEnabled(enabled));
		context.setPacketHandled(true);
	}
}
