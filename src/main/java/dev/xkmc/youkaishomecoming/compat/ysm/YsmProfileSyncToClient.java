package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

/** One bounded model per packet; never a multi-megabyte all-model NBT snapshot. */
@SerialClass
public class YsmProfileSyncToClient extends SerialPacketBase {
	@SerialClass.SerialField public String json = "";
	@SerialClass.SerialField public long revision;
	@SerialClass.SerialField public boolean reset;
	@SerialClass.SerialField public String requestId = "";
	@SerialClass.SerialField public boolean success;
	@SerialClass.SerialField public String message = "";

	public YsmProfileSyncToClient() { }
	public YsmProfileSyncToClient(YsmProfileData.Entry entry, String requestId, boolean success, String message) {
		if (entry != null) { json = entry.profile().toJson(); revision = entry.revision(); }
		this.requestId = requestId;
		this.success = success;
		this.message = message;
	}
	@Override public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> YsmClientProfiles.receive(this));
	}
}
