package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum TransitionMode {
	IMMEDIATE,
	CLEAR_SCREEN,
	DELAYED;

	public static final Codec<TransitionMode> CODEC = Codec.STRING.xmap(
			s -> TransitionMode.valueOf(s.toUpperCase(Locale.ROOT)),
			t -> t.name().toLowerCase(Locale.ROOT)
	);
}
