package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.condition.SpellCondition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpellActions {

	private static final Map<String, Codec<? extends SpellAction>> REGISTRY = new HashMap<>();

	static {
		register("set_variable", SetVariable.CODEC);
		register("add_variable", AddVariable.CODEC);
		register("clear_screen", ClearScreen.CODEC);
		register("force_phase", ForcePhase.CODEC);
		register("play_sound", PlaySoundAction.CODEC);
		register("conditional", ConditionalAction.CODEC);
		register("sequence", SequenceAction.CODEC);
		register("legacy_ticker", LegacyTickerAction.CODEC);
		register("noop", NoopAction.CODEC);
	}

	public static void register(String id, Codec<? extends SpellAction> codec) {
		REGISTRY.put(id, codec);
	}

	private static String getType(SpellAction action) {
		if (action instanceof SetVariable) return "set_variable";
		if (action instanceof AddVariable) return "add_variable";
		if (action instanceof ClearScreen) return "clear_screen";
		if (action instanceof ForcePhase) return "force_phase";
		if (action instanceof PlaySoundAction) return "play_sound";
		if (action instanceof ConditionalAction) return "conditional";
		if (action instanceof SequenceAction) return "sequence";
		if (action instanceof LegacyTickerAction) return "legacy_ticker";
		if (action instanceof NoopAction) return "noop";
		throw new IllegalStateException("Unknown action type: " + action.getClass());
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

	public record SetVariable(String key, double value) implements SpellAction {
		public static final Codec<SetVariable> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(SetVariable::key),
				Codec.DOUBLE.fieldOf("value").forGetter(SetVariable::value)
		).apply(i, SetVariable::new));

		@Override
		public void execute(SpellContext ctx) {
			ctx.setVariable(key, value);
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

	public record ForcePhase(ResourceLocation phaseId) implements SpellAction {
		public static final Codec<ForcePhase> CODEC = ResourceLocation.CODEC
				.fieldOf("phase_id").codec().xmap(ForcePhase::new, ForcePhase::phaseId);

		@Override
		public void execute(SpellContext ctx) {
			ctx.runtime().forceTransition(ctx, phaseId);
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
}
