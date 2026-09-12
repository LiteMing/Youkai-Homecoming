package dev.xkmc.youkaishomecoming.compat.ysm;

/** Immutable, server-timed spell hint shared by boss and player presentation. */
public record YsmSpellHintState(String hint, long expiresAt, long sequence) {

	public YsmSpellHintState {
		hint = hint == null ? "" : hint.trim();
	}

	public static YsmSpellHintState create(String hint, long now, int duration, long sequence) {
		long until = duration <= 0 ? 0 : now > Long.MAX_VALUE - duration ? Long.MAX_VALUE : now + duration;
		return new YsmSpellHintState(hint, until, sequence);
	}

	public boolean active(long now) {
		return !hint.isBlank() && (expiresAt == 0 || now < expiresAt);
	}
}
