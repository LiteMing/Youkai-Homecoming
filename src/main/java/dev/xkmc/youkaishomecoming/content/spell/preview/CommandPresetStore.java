package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Client-only command presets shared by worlds and servers. */
public final class CommandPresetStore {
	private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("youkaishomecoming_command_presets.json");
	private static final int MAX_PRESETS = 64;
	private static final int MAX_LENGTH = 512;

	private CommandPresetStore() {
	}

	public static synchronized List<String> list() {
		if (!Files.exists(FILE)) return List.of();
		try {
			var element = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
			if (!element.isJsonArray()) return List.of();
			List<String> result = new ArrayList<>();
			for (var value : element.getAsJsonArray()) {
				if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
					String command = value.getAsString();
					if (!command.isBlank() && command.length() <= MAX_LENGTH && !result.contains(command)) {
						result.add(command);
					}
				}
			}
			return List.copyOf(result);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	public static synchronized void save(String command) {
		if (command == null) return;
		String normalized = command.trim();
		if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) return;
		List<String> values = new ArrayList<>(list());
		values.remove(normalized);
		values.add(0, normalized);
		if (values.size() > MAX_PRESETS) values.subList(MAX_PRESETS, values.size()).clear();
		write(values);
	}

	public static synchronized void remove(String command) {
		if (command == null) return;
		List<String> values = new ArrayList<>(list());
		if (values.remove(command.trim())) write(values);
	}

	private static void write(List<String> values) {
		try {
			Files.createDirectories(FILE.getParent());
			JsonArray array = new JsonArray();
			values.forEach(array::add);
			Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(array), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}
}
