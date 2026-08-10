package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages undo/redo history for spell editor operations.
 * Each snapshot is a JSON-serialized copy of the current PhaseDefinition,
 * which captures all action lists (onEnter, onTick, onExit, onDamage) and transitions.
 */
public class UndoManager {

	private static final int MAX_HISTORY = 100;
	public record Restored(PhaseDefinition phase, Map<String, String> customNames) {}
	private record Snapshot(JsonElement phase, Map<String, String> customNames) {}

	private final Deque<Snapshot> undoStack = new ArrayDeque<>();
	private final Deque<Snapshot> redoStack = new ArrayDeque<>();

	/**
	 * Take a snapshot of the current state BEFORE a modification.
	 * Call this before every action that modifies the phase.
	 */
	public void pushUndo(PhaseDefinition phase, Map<String, String> customNames) {
		var result = PhaseDefinition.CODEC.encodeStart(JsonOps.INSTANCE, phase);
		result.result().ifPresent(json -> {
			undoStack.push(new Snapshot(json, new HashMap<>(customNames)));
			if (undoStack.size() > MAX_HISTORY) {
				undoStack.removeLast();
			}
			redoStack.clear(); // New edit invalidates redo history
		});
	}

	/**
	 * Undo: restore previous state, push current state to redo stack.
	 * @return the restored phase and custom names, or null if nothing to undo
	 */
	public Restored undo(PhaseDefinition current, Map<String, String> customNames) {
		if (undoStack.isEmpty()) return null;
		// Save current to redo
		PhaseDefinition.CODEC.encodeStart(JsonOps.INSTANCE, current)
				.result().ifPresent(json -> redoStack.push(
						new Snapshot(json, new HashMap<>(customNames))));
		// Restore from undo
		return restore(undoStack.pop());
	}

	/**
	 * Redo: restore next state, push current state to undo stack.
	 * @return the restored phase and custom names, or null if nothing to redo
	 */
	public Restored redo(PhaseDefinition current, Map<String, String> customNames) {
		if (redoStack.isEmpty()) return null;
		// Save current to undo
		PhaseDefinition.CODEC.encodeStart(JsonOps.INSTANCE, current)
				.result().ifPresent(json -> undoStack.push(
						new Snapshot(json, new HashMap<>(customNames))));
		// Restore from redo
		return restore(redoStack.pop());
	}

	private Restored restore(Snapshot snapshot) {
		PhaseDefinition phase = PhaseDefinition.CODEC.parse(JsonOps.INSTANCE, snapshot.phase())
				.result().orElse(null);
		return phase == null ? null : new Restored(phase, new HashMap<>(snapshot.customNames()));
	}

	public boolean canUndo() {
		return !undoStack.isEmpty();
	}

	public boolean canRedo() {
		return !redoStack.isEmpty();
	}

	public void clear() {
		undoStack.clear();
		redoStack.clear();
	}
}
