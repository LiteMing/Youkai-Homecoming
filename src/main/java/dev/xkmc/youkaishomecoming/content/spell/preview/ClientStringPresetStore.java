package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small client-side store for ordered, de-duplicated string presets. */
public final class ClientStringPresetStore {

	private final Path file;
	private final int maxPresets;
	private final int maxLength;

	public ClientStringPresetStore(Path file, int maxPresets, int maxLength) {
		this.file = file;
		this.maxPresets = maxPresets;
		this.maxLength = maxLength;
	}

	public synchronized List<String> list() {
		if (!Files.exists(file)) return List.of();
		try {
			var element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!element.isJsonArray()) return List.of();
			List<String> result = new ArrayList<>();
			for (var value : element.getAsJsonArray()) {
				if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
					String preset = value.getAsString();
					if (!preset.isBlank() && preset.length() <= maxLength && !result.contains(preset)) {
						result.add(preset);
					}
				}
			}
			return List.copyOf(result);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	public synchronized void save(String value) {
		if (value == null) return;
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > maxLength) return;
		List<String> values = new ArrayList<>(list());
		values.remove(normalized);
		values.add(0, normalized);
		if (values.size() > maxPresets) values.subList(maxPresets, values.size()).clear();
		write(values);
	}

	public synchronized void remove(String value) {
		if (value == null) return;
		List<String> values = new ArrayList<>(list());
		if (values.remove(value.trim())) write(values);
	}

	private void write(List<String> values) {
		try {
			Path parent = file.getParent();
			if (parent != null) Files.createDirectories(parent);
			JsonArray array = new JsonArray();
			values.forEach(array::add);
			Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(array),
					StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}
}
