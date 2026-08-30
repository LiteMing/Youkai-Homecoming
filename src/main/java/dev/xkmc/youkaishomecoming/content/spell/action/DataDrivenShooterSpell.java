package dev.xkmc.youkaishomecoming.content.spell.action;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.definition.*;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyModifiers;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * A SpellCard implementation that executes data-driven SpellActions each tick.
 * Used by SpawnShooterAction to give a ShooterEntity a data-driven behavior.
 * <p>
 * Internally creates a minimal single-phase SpellDefinition + SpellRuntime
 * so that actions have access to phaseTick, variables, etc.
 */
@SerialClass
public class DataDrivenShooterSpell extends SpellCard {

	private static final ResourceLocation SHOOTER_PHASE =
			new ResourceLocation("youkaishomecoming", "shooter/main");
	private static final ResourceLocation SHOOTER_ID =
			new ResourceLocation("youkaishomecoming", "shooter_spell");

	private final List<SpellAction> tickActions;
	private final SpellRuntime runtime;
	private final SpellDefinition definition;
	private final Map<String, Double> inheritedVariables;

	@SerialClass.SerialField
	private int tick;

	public DataDrivenShooterSpell(List<SpellAction> tickActions, Map<String, Double> inheritedVariables) {
		this.tickActions = tickActions;
		this.inheritedVariables = inheritedVariables;
		var phase = new PhaseDefinition(SHOOTER_PHASE,
				List.of(), tickActions, List.of(), List.of(), List.of());
		var display = new SpellDisplay("", "", java.util.Optional.empty(), java.util.Optional.empty());
		this.definition = new SpellDefinition(SHOOTER_ID, display, SpellItemForm.NONE,
				SHOOTER_PHASE, Map.of(SHOOTER_PHASE, phase),
				dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile.DEFAULT);
		this.runtime = new SpellRuntime(definition);
		if (inheritedVariables != null && !inheritedVariables.isEmpty()) {
			this.runtime.getVariables().putAll(inheritedVariables);
		}
	}

	public DataDrivenShooterSpell(List<SpellAction> tickActions) {
		this(tickActions, Map.of());
	}

	/** No-arg constructor for serialization. Will have null tickActions until deserialized. */
	public DataDrivenShooterSpell() {
		this(List.of());
	}

	@Override
	public void tick(CardHolder holder) {
		// Delegate to runtime which handles phaseTick/totalTick increment
		// and executes the onTick actions from the single-phase definition
		runtime.tick(holder);
		tick++;
	}

	@Override
	public void reset() {
		tick = 0;
		runtime.reset();
	}
}
