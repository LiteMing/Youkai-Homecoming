package dev.xkmc.youkaishomecoming.compat.ysm;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

import static dev.xkmc.youkaishomecoming.compat.ysm.YsmModelProfile.Trigger;

/** Server-authored edges and persistent state, independent of the model installed on an observer. */
public record YsmPresentationSignals(Trigger state, long stateAt, long stateSequence,
		boolean combat, Map<Trigger, Event> events) {

	public record Event(long at, long sequence) {
		public Event {
			if (at < -1 || sequence < 0) throw new IllegalArgumentException("Invalid presentation event");
		}
	}
	private static final Event NONE = new Event(-1, 0);
	public static final YsmPresentationSignals EMPTY = new YsmPresentationSignals(Trigger.IDLE, 0, 0, false, Map.of());

	public YsmPresentationSignals {
		if (state == null || state.event() || stateAt < 0 || stateSequence < 0 || events.keySet().stream().anyMatch(t -> !t.event()))
			throw new IllegalArgumentException("Invalid model presentation signals");
		events = Map.copyOf(events);
	}

	public Event event(Trigger trigger) { return events.getOrDefault(trigger, NONE); }

	public YsmPresentationSignals advance(Trigger next, boolean fighting, long now) {
		if (state == next && combat == fighting) return this;
		var value = new YsmPresentationSignals(next, state == next ? stateAt : now,
				state == next ? stateSequence : increment(stateSequence), fighting, events);
		return fighting && !combat ? value.fire(Trigger.ENTER_COMBAT, now) : value;
	}

	/** Each occurrence restarts its event, including two accepted hits in the same tick. */
	public YsmPresentationSignals fire(Trigger trigger, long now) {
		if (!trigger.event()) throw new IllegalArgumentException("Expected an event trigger");
		var copy = new EnumMap<Trigger, Event>(Trigger.class);
		copy.putAll(events);
		copy.put(trigger, new Event(now, increment(event(trigger).sequence())));
		return new YsmPresentationSignals(state, stateAt, stateSequence, combat, copy);
	}

	public YsmPresentationSignals hurt(long now) { return fire(Trigger.HURT, now); }

	private static long increment(long value) { return value == Long.MAX_VALUE ? 1 : value + 1; }

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("state", state.id());
		tag.putLong("stateAt", stateAt);
		tag.putLong("stateSequence", stateSequence);
		tag.putBoolean("combat", combat);
		CompoundTag edges = new CompoundTag();
		events.forEach((trigger, event) -> {
			CompoundTag edge = new CompoundTag();
			edge.putLong("at", event.at());
			edge.putLong("sequence", event.sequence());
			edges.put(trigger.id(), edge);
		});
		tag.put("events", edges);
		return tag;
	}

	public static YsmPresentationSignals fromTag(CompoundTag tag) {
		try {
			var events = new EnumMap<Trigger, Event>(Trigger.class);
			var edges = tag.getCompound("events");
			for (String key : edges.getAllKeys()) {
				var edge = edges.getCompound(key);
				events.put(Trigger.parse(key), new Event(edge.getLong("at"), edge.getLong("sequence")));
			}
			return new YsmPresentationSignals(Trigger.parse(tag.getString("state")), tag.getLong("stateAt"),
					tag.getLong("stateSequence"), tag.getBoolean("combat"), events);
		} catch (IllegalArgumentException ex) { return EMPTY; }
	}

	public static final class Cache {
		private CompoundTag previous;
		private YsmPresentationSignals value = EMPTY;
		public YsmPresentationSignals read(CompoundTag tag) {
			if (tag != previous) { value = fromTag(tag); previous = tag; }
			return value;
		}
	}
}
