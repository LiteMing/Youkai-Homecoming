package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the server when a youkai's combat progress is depleted,
 * i.e. the moment it is defeated in a danmaku battle (满身疮痍).
 * Fires once per depletion; a youkai whose progress is later restored
 * (e.g. {@link YoukaiEntity#resetTarget}) may fire it again on the next defeat.
 * Not cancelable; this is a record of the state transition, not a hook to prevent it.
 */
public class YoukaiDefeatedEvent extends Event {

	public final YoukaiEntity youkai;
	public final DamageSource source;
	public final float previousProgress;

	public YoukaiDefeatedEvent(YoukaiEntity youkai, DamageSource source, float previousProgress) {
		this.youkai = youkai;
		this.source = source;
		this.previousProgress = previousProgress;
	}

}
