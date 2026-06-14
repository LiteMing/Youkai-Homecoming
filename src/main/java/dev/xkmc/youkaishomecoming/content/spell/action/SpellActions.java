package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SpellActions {

	private static final Map<String, Codec<? extends SpellAction>> REGISTRY = new HashMap<>();
	private static final Map<Class<?>, String> CLASS_TO_TYPE = new HashMap<>();

	static {
		register("set_variable", SetVariable.CODEC, SetVariable.class);
		register("add_variable", AddVariable.CODEC, AddVariable.class);
		register("clear_screen", ClearScreen.CODEC, ClearScreen.class);
		register("force_phase", ForcePhase.CODEC, ForcePhase.class);
		register("force_spell", ForceSpell.CODEC, ForceSpell.class);
		register("fire_spell", FireSpell.CODEC, FireSpell.class);
		register("play_sound", PlaySoundAction.CODEC, PlaySoundAction.class);
		register("conditional", ConditionalAction.CODEC, ConditionalAction.class);
		register("sequence", SequenceAction.CODEC, SequenceAction.class);
		register("legacy_ticker", LegacyTickerAction.CODEC, LegacyTickerAction.class);
		register("noop", NoopAction.CODEC, NoopAction.class);
		register("fire_danmaku", FireDanmakuAction.CODEC, FireDanmakuAction.class);
		register("fire_laser", FireLaserAction.CODEC, FireLaserAction.class);
		register("fire_text_danmaku", FireTextDanmakuAction.CODEC, FireTextDanmakuAction.class);
		register("repeat", RepeatAction.CODEC, RepeatAction.class);
		register("delay", DelayAction.CODEC, DelayAction.class);
		register("teleport", TeleportAction.CODEC, TeleportAction.class);
		register("spawn_shooter", SpawnShooterAction.CODEC, SpawnShooterAction.class);
		register("burst", BurstAction.CODEC, BurstAction.class);
		register("disabled", DisabledAction.CODEC, DisabledAction.class);
		register("confine_target", ConfineTargetAction.CODEC, ConfineTargetAction.class);
		register("set_entity_flag", SetEntityFlagAction.CODEC, SetEntityFlagAction.class);
		register("teleport_random", TeleportRandomAction.CODEC, TeleportRandomAction.class);
	}

	public static void register(String id, Codec<? extends SpellAction> codec) {
		REGISTRY.put(id, codec);
	}

	public static void register(String id, Codec<? extends SpellAction> codec, Class<? extends SpellAction> clazz) {
		REGISTRY.put(id, codec);
		CLASS_TO_TYPE.put(clazz, id);
	}

	private static String getType(SpellAction action) {
		String type = CLASS_TO_TYPE.get(action.getClass());
		if (type != null) return type;
		throw new IllegalStateException("Unknown action type: " + action.getClass());
	}

	/**
	 * Returns the registered type ID for the given action, or null if unknown.
	 */
	public static String getTypeId(SpellAction action) {
		return CLASS_TO_TYPE.get(action.getClass());
	}

	@SuppressWarnings("unchecked")
	static final Codec<SpellAction> DISPATCH_CODEC = Codec.STRING.fieldOf("type")
			.codec()
			.dispatch(
					SpellActions::getType,
					id -> {
						var codec = REGISTRY.get(id);
						if (codec == null) throw new IllegalStateException("Unknown action: " + id);
						return (Codec<SpellAction>) (Codec<?>) codec;
					}
			);

	// --- Action implementations ---

	public record SetVariable(String key, NumberProvider value) implements SpellAction {
		public static final Codec<SetVariable> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(SetVariable::key),
				NumberProvider.CODEC.fieldOf("value").forGetter(SetVariable::value)
		).apply(i, SetVariable::new));

		/** Convenience constructor for constant values. */
		public SetVariable(String key, double constantValue) {
			this(key, NumberProvider.constant(constantValue));
		}

		@Override
		public void execute(SpellContext ctx) {
			ctx.setVariable(key, value.get(ctx));
		}
	}

	public record AddVariable(String key, double delta) implements SpellAction {
		public static final Codec<AddVariable> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(AddVariable::key),
				Codec.DOUBLE.fieldOf("delta").forGetter(AddVariable::delta)
		).apply(i, AddVariable::new));

		@Override
		public void execute(SpellContext ctx) {
			ctx.setVariable(key, ctx.getVariable(key) + delta);
		}
	}

	public record ClearScreen() implements SpellAction {
		public static final Codec<ClearScreen> CODEC = Codec.unit(ClearScreen::new);

		@Override
		public void execute(SpellContext ctx) {
			ctx.clearDanmaku();
		}
	}

	public record ForcePhase(ResourceLocation phaseId, boolean clearScreen) implements SpellAction {
		public static final Codec<ForcePhase> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceLocation.CODEC.fieldOf("phase_id").forGetter(ForcePhase::phaseId),
				Codec.BOOL.optionalFieldOf("clear_screen", true).forGetter(ForcePhase::clearScreen)
		).apply(i, ForcePhase::new));

		@Override
		public void execute(SpellContext ctx) {
			if (ctx.holder() instanceof dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder preview
					&& preview.switchPhase(phaseId, clearScreen)) {
				return;
			}
			ctx.runtime().forceTransition(ctx, phaseId, clearScreen);
		}
	}

	public record ForceSpell(ResourceLocation spellId, boolean clearScreen) implements SpellAction {
		public static final Codec<ForceSpell> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceLocation.CODEC.fieldOf("spell_id").forGetter(ForceSpell::spellId),
				Codec.BOOL.optionalFieldOf("clear_screen", true).forGetter(ForceSpell::clearScreen)
		).apply(i, ForceSpell::new));

		@Override
		public void execute(SpellContext ctx) {
			ctx.switchSpell(spellId, clearScreen);
		}
	}

	public record FireSpell(ResourceLocation spellId, Optional<ResourceLocation> phaseId,
							NumberProvider duration) implements SpellAction {
		public static final Codec<FireSpell> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceLocation.CODEC.fieldOf("spell_id").forGetter(FireSpell::spellId),
				ResourceLocation.CODEC.optionalFieldOf("phase_id").forGetter(FireSpell::phaseId),
				NumberProvider.CODEC.optionalFieldOf("duration", NumberProvider.constant(1)).forGetter(FireSpell::duration)
		).apply(i, FireSpell::new));

		public FireSpell(ResourceLocation spellId, @Nullable ResourceLocation phaseId, int duration) {
			this(spellId, Optional.ofNullable(phaseId), NumberProvider.constant(duration));
		}

		@Override
		public void execute(SpellContext ctx) {
			var definition = SpellRegistry.get(spellId);
			if (definition == null) {
				return;
			}
			int ticks = Math.max(0, (int) duration.get(ctx));
			ctx.runtime().startChildRuntime(ctx.holder(), definition, phaseId.orElse(null), ticks);
		}
	}

	public record PlaySoundAction(ResourceLocation soundId, float volume, float pitch) implements SpellAction {
		public static final Codec<PlaySoundAction> CODEC = RecordCodecBuilder.create(i -> i.group(
				ResourceLocation.CODEC.fieldOf("sound").forGetter(PlaySoundAction::soundId),
				Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(PlaySoundAction::volume),
				Codec.FLOAT.optionalFieldOf("pitch", 1.0f).forGetter(PlaySoundAction::pitch)
		).apply(i, PlaySoundAction::new));

		@Override
		public void execute(SpellContext ctx) {
			SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
			if (sound != null) {
				var self = ctx.self();
				self.level().playSound(null, self.getX(), self.getY(), self.getZ(),
						sound, SoundSource.HOSTILE, volume, pitch);
			}
		}
	}

	public record ConditionalAction(SpellCondition condition, List<SpellAction> ifTrue,
									List<SpellAction> ifFalse) implements SpellAction {
		public static final Codec<ConditionalAction> CODEC = RecordCodecBuilder.create(i -> i.group(
				SpellCondition.CODEC.fieldOf("condition").forGetter(ConditionalAction::condition),
				SpellAction.CODEC.listOf().fieldOf("if_true").forGetter(ConditionalAction::ifTrue),
				SpellAction.CODEC.listOf().optionalFieldOf("if_false", List.of()).forGetter(ConditionalAction::ifFalse)
		).apply(i, ConditionalAction::new));

		@Override
		public void execute(SpellContext ctx) {
			var actions = condition.test(ctx) ? ifTrue : ifFalse;
			for (var action : actions) {
				action.execute(ctx);
			}
		}
	}

	public record SequenceAction(List<SpellAction> actions) implements SpellAction {
		public static final Codec<SequenceAction> CODEC = SpellAction.CODEC.listOf()
				.fieldOf("actions").codec().xmap(SequenceAction::new, SequenceAction::actions);

		@Override
		public void execute(SpellContext ctx) {
			for (var action : actions) {
				action.execute(ctx);
			}
		}
	}

	public record NoopAction() implements SpellAction {
		public static final Codec<NoopAction> CODEC = Codec.unit(NoopAction::new);

		@Override
		public void execute(SpellContext ctx) {
		}
	}

	/**
	 * Repeats body actions count times in a single tick, setting indexVariable to the current iteration index.
	 * Enables compound patterns like "outer ring 8 x inner ring 3".
	 * JSON: {"type": "repeat", "count": 8, "index_variable": "i", "body": [...]}
	 */
	public record RepeatAction(NumberProvider count, String indexVariable,
							   List<SpellAction> body) implements SpellAction {
		public static final Codec<RepeatAction> CODEC = RecordCodecBuilder.create(i -> i.group(
				NumberProvider.CODEC.fieldOf("count").forGetter(RepeatAction::count),
				Codec.STRING.optionalFieldOf("index_variable", "i").forGetter(RepeatAction::indexVariable),
				SpellAction.CODEC.listOf().fieldOf("body").forGetter(RepeatAction::body)
		).apply(i, RepeatAction::new));

		@Override
		public void execute(SpellContext ctx) {
			int n = (int) count.get(ctx);
			for (int idx = 0; idx < n; idx++) {
				ctx.setVariable(indexVariable, idx);
				for (var action : body) {
					action.execute(ctx);
				}
			}
		}
	}

	/**
	 * Wrapper that disables a child action. The inner action is preserved but never executed.
	 * Used by the editor to temporarily disable nodes without deleting them.
	 */
	public record DisabledAction(SpellAction inner) implements SpellAction {
		public static final Codec<DisabledAction> CODEC = SpellAction.CODEC
				.fieldOf("inner").codec().xmap(DisabledAction::new, DisabledAction::inner);

		@Override
		public void execute(SpellContext ctx) {
			// Intentionally empty — disabled action does nothing
		}
	}
}
