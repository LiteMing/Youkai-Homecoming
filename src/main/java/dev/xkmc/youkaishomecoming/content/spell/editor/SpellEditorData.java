package dev.xkmc.youkaishomecoming.content.spell.editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

public record SpellEditorData(
		SpellDefinition definition,
		EditorLayout editor
) {

	public static final Codec<SpellEditorData> CODEC = RecordCodecBuilder.create(i -> i.group(
			SpellDefinition.CODEC.fieldOf("definition").forGetter(SpellEditorData::definition),
			EditorLayout.CODEC.optionalFieldOf("editor", EditorLayout.DEFAULT).forGetter(SpellEditorData::editor)
	).apply(i, SpellEditorData::new));
}
