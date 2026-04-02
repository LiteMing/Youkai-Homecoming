package dev.xkmc.youkaishomecoming.content.spell.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;

public class SpellEditorCodec {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private SpellEditorCodec() {
	}

	public static String encodeDefinitionJson(SpellDefinition definition) {
		return encode(SpellDefinition.CODEC, definition);
	}

	public static SpellDefinition decodeDefinitionJson(String json) {
		return decode(SpellDefinition.CODEC, json);
	}

	public static String encodeProjectJson(SpellEditorData data) {
		return encode(SpellEditorData.CODEC, data);
	}

	public static SpellEditorData decodeProjectJson(String json) {
		return decode(SpellEditorData.CODEC, json);
	}

	public static String encodePhaseJson(PhaseDefinition phase) {
		return encode(PhaseDefinition.CODEC, phase);
	}

	public static <T> String encode(Codec<T> codec, T value) {
		JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value)
				.resultOrPartial(error -> {
					throw new IllegalStateException(error);
				})
				.orElseThrow(() -> new IllegalStateException("Codec encode returned no result"));
		return GSON.toJson(json);
	}

	public static <T> T decode(Codec<T> codec, String json) {
		JsonElement element = JsonParser.parseString(json);
		return codec.parse(JsonOps.INSTANCE, element)
				.resultOrPartial(error -> {
					throw new IllegalStateException(error);
				})
				.orElseThrow(() -> new IllegalStateException("Codec decode returned no result"));
	}
}
