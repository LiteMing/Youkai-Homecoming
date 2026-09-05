package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server-to-client sync of the full /yhysm override table. Sent on every
 * change and to each player on login. {@code message} carries the command
 * result feedback (empty = silent, e.g. login sync).
 */
@SerialClass
public class YsmOverrideSyncToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public CompoundTag typeOverrides = new CompoundTag();
	@SerialClass.SerialField
	public CompoundTag entityOverrides = new CompoundTag();
	@SerialClass.SerialField
	public String message = "";
	@SerialClass.SerialField public long revision;
	@SerialClass.SerialField public String requestId = "";
	@SerialClass.SerialField public boolean success;

	@Deprecated
	public YsmOverrideSyncToClient() {
	}

	public YsmOverrideSyncToClient(YsmOverrideData data, String message) {
		this.revision = data.revision();
		data.getTypeOverrides().forEach((id, binding) -> typeOverrides.put(id.toString(), YsmOverrideData.bindingToTag(binding)));
		data.getEntityOverrides().forEach((uuid, binding) -> entityOverrides.put(uuid.toString(), YsmOverrideData.bindingToTag(binding)));
		this.message = message;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> {
			YSMClientCompat.applySyncedOverrides(typeOverrides, entityOverrides, message);
			YSMClientCompat.setBindingRevision(revision);
			YsmClientProfiles.response(requestId, success, message, revision, "");
		});
	}

}
