package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client-to-server request for the /yhysm manual model override commands.
 * The client keeps argument parsing (entity selectors, YSM model suggestions)
 * and sends a resolved request; the server validates permissions, persists the
 * change in {@link YsmOverrideData} and broadcasts the new table to all clients.
 */
@SerialClass
public class YsmOverrideRequestToServer extends SerialPacketBase {

	@SerialClass.SerialField
	public String action = "";
	@SerialClass.SerialField
	public String entityType = "";
	@SerialClass.SerialField
	public String modelId = "";
	@SerialClass.SerialField
	public String textureName = "";
	/** Comma-joined entity UUIDs for entity-level actions. */
	@SerialClass.SerialField
	public String uuidList = "";

	@Deprecated
	public YsmOverrideRequestToServer() {
	}

	public YsmOverrideRequestToServer(String action, String entityType, String modelId, String textureName, String uuidList) {
		this.action = action;
		this.entityType = entityType;
		this.modelId = modelId;
		this.textureName = textureName;
		this.uuidList = uuidList;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		YsmOverrideServerHandler.handle(context.getSender(), this);
	}

}
