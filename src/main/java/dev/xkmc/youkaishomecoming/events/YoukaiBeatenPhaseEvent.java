package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import net.minecraftforge.eventbus.api.Event;

/**
 * Client-side notification emitted when a synchronized youkai beaten phase changes.
 * The phase itself remains authoritative entity data; this event only starts visuals.
 */
public class YoukaiBeatenPhaseEvent extends Event {

	private final YoukaiEntity youkai;
	private final int phase;

	public YoukaiBeatenPhaseEvent(YoukaiEntity youkai, int phase) {
		this.youkai = youkai;
		this.phase = phase;
	}

	public YoukaiEntity getYoukai() {
		return youkai;
	}

	public int getBeatenPhase() {
		return phase;
	}
}
