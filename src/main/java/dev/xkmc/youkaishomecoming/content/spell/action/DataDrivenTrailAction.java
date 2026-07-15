package dev.xkmc.youkaishomecoming.content.spell.action;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * A TrailAction that executes data-driven SpellActions when a danmaku expires.
 * The actions run with a TrailCardHolder that overrides center()/forward()
 * to use the danmaku's final position and direction.
 * <p>
 * Runtime variables are <b>snapshotted</b> at danmaku creation time so that
 * per-wave variables (e.g. $lw from BurstAction) retain their correct value
 * even when the danmaku expires many ticks later.
 */
@SerialClass
public class DataDrivenTrailAction extends TrailAction {

	private List<SpellAction> actions;
	private SpellRuntime runtime;
	private SpellDefinition definition;
	/** Snapshot of runtime variables at the time the parent danmaku was created. */
	private Map<String, Double> variableSnapshot;

	/** No-arg constructor for L2Serial deserialization. Deserialized instances are non-functional (server-only logic). */
	public DataDrivenTrailAction() {
		this.actions = List.of();
		this.runtime = null;
		this.definition = null;
		this.variableSnapshot = null;
	}

	public DataDrivenTrailAction(List<SpellAction> actions, SpellRuntime runtime, SpellDefinition definition) {
		this.actions = actions;
		this.runtime = runtime;
		this.definition = definition;
		// Snapshot all current runtime variables so onExpiry sees the values at creation time.
		// Use Map.copyOf for compact immutable storage — avoids HashMap bucket allocation.
		// Typical variable count is 1-5, where copyOf uses optimized small-map implementations.
		this.variableSnapshot = Map.copyOf(runtime.getVariables());
	}

	@Override
	public void execute(CardHolder holder, Vec3 pos, Vec3 dir) {
		if (runtime == null || definition == null) return; // Deserialized stub — no-op

		// Temporarily restore snapshotted variables so child actions see creation-time values
		Map<String, Double> savedVars = null;
		if (variableSnapshot != null) {
			savedVars = Map.copyOf(runtime.getVariables());
			for (var entry : variableSnapshot.entrySet()) {
				runtime.setVariable(entry.getKey(), entry.getValue());
			}
		}

		var trailHolder = new TrailCardHolder(holder, pos, dir);
		var ctx = new SpellContext(trailHolder, definition, runtime, DifficultyModifiers.DEFAULT);
		for (var action : actions) {
			action.execute(ctx);
		}

		// Restore original variables
		if (savedVars != null) {
			// Clear any vars that were only in snapshot, restore originals
			for (var entry : variableSnapshot.entrySet()) {
				if (savedVars.containsKey(entry.getKey())) {
					runtime.setVariable(entry.getKey(), savedVars.get(entry.getKey()));
				}
			}
		}
	}

	@Override
	public void execute(Vec3 pos, Vec3 dir) {
		// Fallback when no holder is cached — can't execute without a holder
		// TrailAction.setup() should have been called to cache the holder
		super.execute(pos, dir);
	}

}
