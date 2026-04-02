package dev.xkmc.youkaishomecoming.content.spell.editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record EditorNodeLayout(
		int x,
		int y
) {

	public static final Codec<EditorNodeLayout> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("x", 0).forGetter(EditorNodeLayout::x),
			Codec.INT.optionalFieldOf("y", 0).forGetter(EditorNodeLayout::y)
	).apply(i, EditorNodeLayout::new));
}
