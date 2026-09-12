package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

/** The same presentation snapshot for a player, a boss, or an attached spell's owner. */
@SerialClass
public class YsmSpellHintToClient extends SerialPacketBase {

	@SerialClass.SerialField public int entityId;
	@SerialClass.SerialField public String entityUuid = "";
	@SerialClass.SerialField public String dimension = "";
	@SerialClass.SerialField public String hint = "";
	@SerialClass.SerialField public long expiresAt;
	@SerialClass.SerialField public long sequence;

	public YsmSpellHintToClient() { }

	public YsmSpellHintToClient(LivingEntity entity, YsmSpellHintState state) {
		entityId = entity.getId();
		entityUuid = entity.getStringUUID();
		dimension = entity.level().dimension().location().toString();
		hint = state.hint();
		expiresAt = state.expiresAt();
		sequence = state.sequence();
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> YsmSpellHintClient.receive(this));
		context.setPacketHandled(true);
	}
}
