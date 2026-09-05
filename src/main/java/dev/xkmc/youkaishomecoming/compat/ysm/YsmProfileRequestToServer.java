package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

/** Editor operations. Local preview never emits this packet. */
@SerialClass
public class YsmProfileRequestToServer extends SerialPacketBase {
	@SerialClass.SerialField public String action = "";
	@SerialClass.SerialField public String model = "";
	@SerialClass.SerialField public String json = "";
	@SerialClass.SerialField public String preset = "";
	@SerialClass.SerialField public String target = "";
	@SerialClass.SerialField public String requestId = "";
	@SerialClass.SerialField public long expectedRevision;
	@SerialClass.SerialField public int ticks = -1;

	@Override public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> YsmProfileServerHandler.handle(context.getSender(), this));
	}
}
