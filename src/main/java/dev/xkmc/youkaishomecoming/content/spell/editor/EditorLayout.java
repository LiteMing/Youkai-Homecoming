package dev.xkmc.youkaishomecoming.content.spell.editor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public record EditorLayout(
		Map<ResourceLocation, EditorNodeLayout> phaseLayout,
		double viewX,
		double viewY,
		double zoom
) {

	public static final EditorLayout DEFAULT = new EditorLayout(Map.of(), 0, 0, 1);

	public static final Codec<EditorLayout> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(ResourceLocation.CODEC, EditorNodeLayout.CODEC)
					.optionalFieldOf("phase_layout", Map.of())
					.forGetter(EditorLayout::phaseLayout),
			Codec.DOUBLE.optionalFieldOf("view_x", 0.0).forGetter(EditorLayout::viewX),
			Codec.DOUBLE.optionalFieldOf("view_y", 0.0).forGetter(EditorLayout::viewY),
			Codec.DOUBLE.optionalFieldOf("zoom", 1.0).forGetter(EditorLayout::zoom)
	).apply(i, EditorLayout::new));

	public EditorLayout {
		phaseLayout = new LinkedHashMap<>(phaseLayout);
	}
}
