package dev.xkmc.youkaishomecoming.compat.ysm;

import net.minecraft.nbt.CompoundTag;

import static dev.xkmc.youkaishomecoming.compat.ysm.YsmModelProfile.Trigger;

/** Server-authored edges and persistent state, independent of the model installed on an observer. */
public record YsmPresentationSignals(Trigger state, long stateAt, long stateSequence,
		boolean combat, long combatAt, long combatSequence, long hurtAt, long hurtSequence) {

	public static final YsmPresentationSignals EMPTY = new YsmPresentationSignals(Trigger.IDLE, 0, 0, false, -1, 0, -1, 0);

	public YsmPresentationSignals {
		if (state == null || state.event() || stateAt < 0 || stateSequence < 0 || combatAt < -1 || combatSequence < 0 || hurtAt < -1 || hurtSequence < 0)
			throw new IllegalArgumentException("Invalid model presentation signals");
	}

	public YsmPresentationSignals advance(Trigger next, boolean fighting, long now) {
		if (state == next && combat == fighting) return this;
		return new YsmPresentationSignals(next, state == next ? stateAt : now,
				state == next ? stateSequence : increment(stateSequence), fighting,
				fighting && !combat ? now : combatAt, fighting && !combat ? increment(combatSequence) : combatSequence, hurtAt, hurtSequence);
	}

	/** Each accepted hit restarts HURT, including two hits in the same tick. */
	public YsmPresentationSignals hurt(long now) {
		return new YsmPresentationSignals(state, stateAt, stateSequence, combat, combatAt, combatSequence, now, increment(hurtSequence));
	}

	private static long increment(long value) { return value == Long.MAX_VALUE ? 1 : value + 1; }

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("state", state.id());
		tag.putLong("stateAt", stateAt);
		tag.putLong("stateSequence", stateSequence);
		tag.putBoolean("combat", combat);
		tag.putLong("combatAt", combatAt);
		tag.putLong("combatSequence", combatSequence);
		tag.putLong("hurtAt", hurtAt);
		tag.putLong("hurtSequence", hurtSequence);
		return tag;
	}

	public static YsmPresentationSignals fromTag(CompoundTag tag) {
		try {
			return new YsmPresentationSignals(Trigger.parse(tag.getString("state")), tag.getLong("stateAt"), tag.getLong("stateSequence"),
					tag.getBoolean("combat"), tag.getLong("combatAt"), tag.getLong("combatSequence"), tag.getLong("hurtAt"), tag.getLong("hurtSequence"));
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
