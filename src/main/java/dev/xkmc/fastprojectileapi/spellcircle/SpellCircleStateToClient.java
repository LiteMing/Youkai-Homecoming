package dev.xkmc.fastprojectileapi.spellcircle;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

@SerialClass
public class SpellCircleStateToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public int entityId;

	@SerialClass.SerialField
	public String entityUuid = "";

	@SerialClass.SerialField
	public boolean hasOverride;

	@SerialClass.SerialField
	public boolean enabled;

	@SerialClass.SerialField
	@Nullable
	public ResourceLocation circle;

	@SerialClass.SerialField
	public float size;

	@Deprecated
	public SpellCircleStateToClient() {
	}

	public SpellCircleStateToClient(Entity entity) {
		entityId = entity.getId();
		entityUuid = entity.getUUID().toString();
		EntitySpellCircleManager.State state = EntitySpellCircleManager.getServerOverride(entity);
		hasOverride = state != null;
		if (state != null) {
			enabled = state.enabled();
			circle = state.circle();
			size = state.size();
		}
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		EntitySpellCircleManager.clientUpdate(entityId, entityUuid, hasOverride, enabled, circle, size);
	}
}
