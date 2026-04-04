package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum TransitionMode {
	/** Switch phase immediately, keep existing danmaku on screen */
	IMMEDIATE,
	/** Clear all danmaku on screen before switching phase */
	CLEAR_SCREEN,
	/** Clear danmaku and reset all runtime variables */
	CLEAR_AND_RESET;

	public static final Codec<TransitionMode> CODEC = Codec.STRING.xmap(
			s -> TransitionMode.valueOf(s.toUpperCase(Locale.ROOT)),
			t -> t.name().toLowerCase(Locale.ROOT)
	);
}
