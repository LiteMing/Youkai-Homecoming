package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages undo/redo history for spell editor operations.
 * Each snapshot is a JSON-serialized copy of the current PhaseDefinition,
 * which captures all action lists (onEnter, onTick, onExit, onDamage) and transitions.
 */
public class UndoManager {

	private static final int MAX_HISTORY = 100;

	private final Deque<JsonElement> undoStack = new ArrayDeque<>();
	private final Deque<JsonElement> redoStack = new ArrayDeque<>();

	/**
	 * Take a snapshot of the current state BEFORE a modification.
	 * Call this before every action that modifies the phase.
	 */
	public void pushUndo(PhaseDefinition phase) {
		var result = PhaseDefinition.CODEC.encodeStart(JsonOps.INSTANCE, phase);
		result.result().ifPresent(json -> {
			undoStack.push(json);
			if (undoStack.size() > MAX_HISTORY) {
				((ArrayDeque<JsonElement>) undoStack).removeLast();
			}
			redoStack.clear(); // New edit invalidates redo history
		});
	}

	/**
	 * Undo: restore previous state, push current state to redo stack.
	 * @return the restored PhaseDefinition, or null if nothing to undo
	 */
	public PhaseDefinition undo(PhaseDefinition current) {
		if (undoStack.isEmpty()) return null;
		// Save current to redo
		PhaseDefinition.CODEC.encodeStart(JsonOps.INSTANCE, current)
				.result().ifPresent(redoStack::push);
		// Restore from undo
		JsonElement json = undoStack.pop();
		return PhaseDefinition.CODEC.parse(JsonOps.INSTANCE, json)
				.result().orElse(null);
	}

	/**
	 * Redo: restore next state, push current state to undo stack.
	 * @return the restored PhaseDefinition, or null if nothing to redo
	 */
	public PhaseDefinition redo(PhaseDefinition current) {
		if (redoStack.isEmpty()) return null;
		// Save current to undo
		PhaseDefinition.CODEC.encodeStart(JsonOps.INSTANCE, current)
				.result().ifPresent(undoStack::push);
		// Restore from redo
		JsonElement json = redoStack.pop();
		return PhaseDefinition.CODEC.parse(JsonOps.INSTANCE, json)
				.result().orElse(null);
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
