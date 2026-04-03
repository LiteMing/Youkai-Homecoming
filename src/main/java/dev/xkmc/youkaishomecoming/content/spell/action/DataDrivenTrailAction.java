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

/**
 * A TrailAction that executes data-driven SpellActions when a danmaku expires.
 * The actions run with a TrailCardHolder that overrides center()/forward()
 * to use the danmaku's final position and direction.
 */
@SerialClass
public class DataDrivenTrailAction extends TrailAction {

	private final List<SpellAction> actions;
	private final SpellRuntime runtime;
	private final SpellDefinition definition;

	public DataDrivenTrailAction(List<SpellAction> actions, SpellRuntime runtime, SpellDefinition definition) {
		this.actions = actions;
		this.runtime = runtime;
		this.definition = definition;
	}

	@Override
	public void execute(CardHolder holder, Vec3 pos, Vec3 dir) {
		var trailHolder = new TrailCardHolder(holder, pos, dir);
		var ctx = new SpellContext(trailHolder, definition, runtime, DifficultyModifiers.DEFAULT);
		for (var action : actions) {
			action.execute(ctx);
		}
	}

	@Override
	public void execute(Vec3 pos, Vec3 dir) {
		// Fallback when no holder is cached — can't execute without a holder
		// TrailAction.setup() should have been called to cache the holder
		super.execute(pos, dir);
	}

}
