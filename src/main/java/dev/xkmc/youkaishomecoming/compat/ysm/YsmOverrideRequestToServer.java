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
	/** -1 preserves legacy command semantics. Editor saves must compare the synchronized revision. */
	@SerialClass.SerialField public long expectedRevision = -1;
	@SerialClass.SerialField public String requestId = "";
	/** Numeric appearance defaults for the selected UUID/type; empty on legacy commands. */
	@SerialClass.SerialField public java.util.Map<String, Float> parameters = new java.util.LinkedHashMap<>();

	@Deprecated
	public YsmOverrideRequestToServer() {
	}

	public YsmOverrideRequestToServer(String action, String entityType, String modelId, String textureName, String uuidList) {
		this.action = valueOrEmpty(action);
		this.entityType = valueOrEmpty(entityType);
		this.modelId = valueOrEmpty(modelId);
		this.textureName = valueOrEmpty(textureName);
		this.uuidList = valueOrEmpty(uuidList);
	}

	private static String valueOrEmpty(String value) {
		return value == null ? "" : value;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> YsmOverrideServerHandler.handle(context.getSender(), this));
	}

}
