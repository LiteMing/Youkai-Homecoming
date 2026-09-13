package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

/** A request-time snapshot for display only. Never validates or changes a generated draft. */
record SpellAiComparison(String before, String after) {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	record Change(String path, String before, String after) {}

	List<Change> changes() {
		if (before.equals(after)) return List.of();
		try {
			var changes = new ArrayList<Change>();
			compare("$", JsonParser.parseString(before), JsonParser.parseString(after), changes);
			return List.copyOf(changes);
		} catch (RuntimeException ignored) {
			// Incomplete or non-JSON output still remains viewable and copyable for the existing importer.
			return List.of(new Change("$", before, after));
		}
	}

	private static void compare(String path, JsonElement before, JsonElement after, List<Change> changes) {
		if (java.util.Objects.equals(before, after)) return;
		if (before != null && after != null && before.isJsonObject() && after.isJsonObject()) {
			var keys = new LinkedHashSet<>(before.getAsJsonObject().keySet());
			keys.addAll(after.getAsJsonObject().keySet());
			for (String key : keys) {
				String child = key.matches("[A-Za-z_][A-Za-z_0-9]*") ? path + "." + key : path + "[" + GSON.toJson(key) + "]";
				compare(child, before.getAsJsonObject().get(key), after.getAsJsonObject().get(key), changes);
			}
		} else if (before != null && after != null && before.isJsonArray() && after.isJsonArray()) {
			compareArray(path, before.getAsJsonArray(), after.getAsJsonArray(), changes);
		} else changes.add(new Change(path, before == null ? null : GSON.toJson(before), after == null ? null : GSON.toJson(after)));
	}

	private static void compareArray(String path, JsonArray before, JsonArray after, List<Change> changes) {
		// Anchor unchanged actions so inserting a node does not mark every later node as modified.
		var positions = new HashMap<JsonElement, ArrayDeque<Integer>>();
		for (int i = 0; i < after.size(); i++) positions.computeIfAbsent(after.get(i), ignored -> new ArrayDeque<>()).add(i);
		int oldStart = 0, newStart = 0;
		for (int oldIndex = 0; oldIndex < before.size(); oldIndex++) {
			var matches = positions.get(before.get(oldIndex));
			if (matches == null) continue;
			while (!matches.isEmpty() && matches.peekFirst() < newStart) matches.removeFirst();
			if (matches.isEmpty()) continue;
			int newIndex = matches.removeFirst();
			compareGap(path, before, oldStart, oldIndex, after, newStart, newIndex, changes);
			oldStart = oldIndex + 1;
			newStart = newIndex + 1;
		}
		compareGap(path, before, oldStart, before.size(), after, newStart, after.size(), changes);
	}

	private static void compareGap(String path, JsonArray before, int oldStart, int oldEnd,
			JsonArray after, int newStart, int newEnd, List<Change> changes) {
		int shared = Math.min(oldEnd - oldStart, newEnd - newStart);
		for (int i = 0; i < shared; i++) compare(path + "[" + (newStart + i) + "]", before.get(oldStart + i), after.get(newStart + i), changes);
		for (int i = oldStart + shared; i < oldEnd; i++) compare(path + "[" + i + "]", before.get(i), null, changes);
		for (int i = newStart + shared; i < newEnd; i++) compare(path + "[" + i + "]", null, after.get(i), changes);
	}
}
