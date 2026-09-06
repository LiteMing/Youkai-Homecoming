package dev.xkmc.youkaishomecoming.compat.stg.control;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

/** Projects the server-owned classic controls flag onto the local input handler. */
@SerialClass
public class ClassicControlSyncToClient extends SerialPacketBase {
	public static final int NOTICE_NONE = 0;
	public static final int NOTICE_ENABLED = 1;
	public static final int NOTICE_DISABLED = 2;
	public static final int NOTICE_AVAILABLE = 3;

	@SerialClass.SerialField
	public boolean enabled;
	@SerialClass.SerialField
	public int notice;

	public ClassicControlSyncToClient() {
	}

	public ClassicControlSyncToClient(boolean enabled) {
		this(enabled, NOTICE_NONE);
	}

	public ClassicControlSyncToClient(boolean enabled, int notice) {
		this.enabled = enabled;
		this.notice = notice;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> ClassicControlClient.setEnabled(enabled, notice));
		context.setPacketHandled(true);
	}
}
