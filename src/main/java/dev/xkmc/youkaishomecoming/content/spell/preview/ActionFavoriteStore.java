package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Local action snapshots, shared across spells, worlds and editor sessions. */
public final class ActionFavoriteStore {

	/** Names use paths relative to the saved root; the empty key names the root itself. */
	public record Favorite(String name, JsonObject action, Map<String, String> customNames) {
		public Favorite {
			if (name == null || name.isBlank()) throw new IllegalArgumentException("Empty favorite name");
			action = action.deepCopy();
			customNames = Map.copyOf(customNames);
		}

		@Override
		public JsonObject action() {
			return action.deepCopy();
		}
	}

	private final Path file;

	public ActionFavoriteStore(Path file) {
		this.file = file;
	}

	public synchronized List<Favorite> list() throws IOException {
		if (Files.notExists(file)) return List.of();
		try {
			JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!json.isJsonArray()) throw new IllegalArgumentException("Expected a favorites array");
			List<Favorite> result = new ArrayList<>();
			for (JsonElement element : json.getAsJsonArray()) {
				JsonObject entry = element.getAsJsonObject();
				Map<String, String> names = new TreeMap<>();
				if (entry.has("custom_names")) {
					for (var name : entry.getAsJsonObject("custom_names").entrySet()) {
						names.put(name.getKey(), stringValue(name.getValue()));
					}
				}
				result.add(new Favorite(stringValue(entry.get("name")), entry.getAsJsonObject("action"), names));
			}
			return List.copyOf(result);
		} catch (RuntimeException e) {
			// A damaged file must remain intact; saving must not treat it as an empty collection.
			throw new IOException("Invalid action favorites: " + file, e);
		}
	}

	public synchronized void save(Favorite favorite) throws IOException {
		List<Favorite> values = new ArrayList<>(list());
		values.removeIf(favorite::equals);
		values.add(0, favorite);
		write(values);
	}

	public synchronized void remove(Favorite favorite) throws IOException {
		List<Favorite> values = new ArrayList<>(list());
		if (values.removeIf(favorite::equals)) write(values);
	}

	private static String stringValue(JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("Expected a string");
		}
		return element.getAsString();
	}

	private void write(List<Favorite> values) throws IOException {
		JsonArray array = new JsonArray();
		for (Favorite favorite : values) {
			JsonObject entry = new JsonObject();
			entry.addProperty("name", favorite.name());
			entry.add("action", favorite.action());
			JsonObject names = new JsonObject();
			new TreeMap<>(favorite.customNames()).forEach(names::addProperty);
			entry.add("custom_names", names);
			array.add(entry);
		}
		Path target = file.toAbsolutePath();
		Files.createDirectories(target.getParent());
		Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(array), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
