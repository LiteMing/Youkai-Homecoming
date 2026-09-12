package dev.xkmc.youkaishomecoming.compat.ysm;

import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable snapshots can also be read by OpenYSM's animation worker. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID)
public final class YsmSpellHintClient {

	private record Entry(int entityId, String dimension, YsmSpellHintState state) { }
	private static final Map<String, Entry> HINTS = new ConcurrentHashMap<>();

	private YsmSpellHintClient() { }

	public static void receive(YsmSpellHintToClient packet) {
		var level = Minecraft.getInstance().level;
		if (level == null || !level.dimension().location().toString().equals(packet.dimension)) return;
		var next = new Entry(packet.entityId, packet.dimension,
				new YsmSpellHintState(packet.hint, packet.expiresAt, packet.sequence));
		HINTS.compute(packet.entityUuid, (id, previous) -> {
			if (previous != null && previous.state().sequence() > next.state().sequence()) return previous;
			return next.state().hint().isBlank() ? null : next;
		});
	}

	public static String animationOverride(LivingEntity entity) {
		Entry entry = HINTS.get(entity.getStringUUID());
		if (entry != null && entry.entityId() == entity.getId()
				&& entry.dimension().equals(entity.level().dimension().location().toString())
				&& entry.state().active(entity.level().getGameTime())) return entry.state().hint();
		return entity instanceof YsmRenderOverrideTarget target ? target.getYsmAnimationOverride() : "";
	}

	@SubscribeEvent
	public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
		HINTS.clear();
	}

	@SubscribeEvent
	public static void unload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide()) HINTS.clear();
	}

	@SubscribeEvent
	public static void untrack(EntityLeaveLevelEvent event) {
		if (event.getLevel().isClientSide()) HINTS.remove(event.getEntity().getStringUUID());
	}
}
