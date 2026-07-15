package dev.xkmc.youkaishomecoming.content.spell.difficulty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DifficultyProfile(
		float speedBase,
		float speedPerHealthLost,
		float frequencyBase,
		float frequencyPerHealthLost,
		float countBase,
		float countPerHealthLost
) {

	public static final DifficultyProfile DEFAULT = new DifficultyProfile(
			1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f
	);

	public static final Codec<DifficultyProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.optionalFieldOf("speed_base", 1.0f).forGetter(DifficultyProfile::speedBase),
			Codec.FLOAT.optionalFieldOf("speed_per_health_lost", 0.0f).forGetter(DifficultyProfile::speedPerHealthLost),
			Codec.FLOAT.optionalFieldOf("frequency_base", 1.0f).forGetter(DifficultyProfile::frequencyBase),
			Codec.FLOAT.optionalFieldOf("frequency_per_health_lost", 0.0f).forGetter(DifficultyProfile::frequencyPerHealthLost),
			Codec.FLOAT.optionalFieldOf("count_base", 1.0f).forGetter(DifficultyProfile::countBase),
			Codec.FLOAT.optionalFieldOf("count_per_health_lost", 0.0f).forGetter(DifficultyProfile::countPerHealthLost)
	).apply(i, DifficultyProfile::new));

	public DifficultyModifiers resolve(float healthRatio) {
		float lost = 1.0f - healthRatio;
		return new DifficultyModifiers(
				speedBase + speedPerHealthLost * lost,
				frequencyBase + frequencyPerHealthLost * lost,
				countBase + countPerHealthLost * lost
		);
	}
}
