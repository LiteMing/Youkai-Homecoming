package dev.xkmc.youkaishomecoming.compat.ysm;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import static dev.xkmc.youkaishomecoming.compat.ysm.YsmPresentationSignals.Trigger;

/** Pure composition: no client, renderer, AI, Molang or third-party classes. */
public final class YsmPresentationResolver {

	public record Body(String clip, String replayKey, String source, boolean explicit) { }
	public record Resolved(@Nullable Body body, Map<String, Float> parameters, boolean beaten,
			boolean combatExpressionRouted) { }
	private static final Trigger[] EVENT_LAYERS = {Trigger.ENTER_COMBAT, Trigger.SPELL_SWITCH, Trigger.MELEE_ATTACK, Trigger.HURT};

	private YsmPresentationResolver() { }

	public static Resolved resolve(String model, @Nullable Map<String, YsmPresetBridge.Preset> presets, YsmPresentationSignals signals,
			YsmPresentationState explicit, long now) {
		return resolve(model, presets, signals, explicit, now, Map.of());
	}

	public static Resolved resolve(String model, @Nullable Map<String, YsmPresetBridge.Preset> presets, YsmPresentationSignals signals,
			YsmPresentationState explicit, long now, Map<String, Float> bindingParameters) {
		Map<String, Float> parameters = new LinkedHashMap<>(bindingParameters);
		Body body = null;
		boolean combatExpressionRouted = false;
		if (!signals.state().beaten()) {
			body = layer(model, presets, signals.state(), signals.stateAt(), signals.stateSequence(), now, parameters, body);
			Trigger combatTrigger = signals.combatMode().trigger();
			if (combatTrigger != null && activePreset(model, presets, combatTrigger, signals.combatModeAt(), now) != null) {
				body = layer(model, presets, combatTrigger, signals.combatModeAt(), signals.combatModeSequence(), now, parameters, body);
				combatExpressionRouted = true;
			}
			for (Trigger trigger : EVENT_LAYERS) {
				if ((trigger == Trigger.ENTER_COMBAT || trigger == Trigger.SPELL_SWITCH) && !signals.combat()) continue;
				var event = signals.event(trigger);
				body = layer(model, presets, trigger, event.at(), event.sequence(), now, parameters, body);
			}
		}
		var animation = explicit.animation();
		if (animation != null && animation.active(now) && animation.matchesModel(model))
			body = new Body(animation.clip(), "explicit:" + explicit.sequence(), animation.source().name(), true);
		explicit.parameters().forEach((key, value) -> {
			if (value.active(now) && value.matchesModel(model)) parameters.put(key, value.value());
		});
		var victory = signals.event(Trigger.BOSS_VICTORY);
		body = layer(model, presets, Trigger.BOSS_VICTORY, victory.at(), victory.sequence(), now, parameters, body);
		if (signals.state().beaten()) {
			// No lower-priority body can displace the existing model-independent beaten fallback.
			body = layer(model, presets, signals.state(), signals.stateAt(), signals.stateSequence(), now, parameters, null);
		}
		return new Resolved(body, Map.copyOf(parameters), signals.state().beaten(), combatExpressionRouted);
	}

	@Nullable
	private static YsmPresetBridge.Preset activePreset(String model, @Nullable Map<String, YsmPresetBridge.Preset> presets, Trigger trigger, long at, long now) {
		if (presets == null || at < 0 || now < at) return null;
		String presetId = YsmPresetBridge.binding(model, trigger.id());
		var preset = presetId.isEmpty() ? null : presets.get(presetId);
		return preset == null || trigger.event() && now - at >= preset.ticks() ? null : preset;
	}

	private static Body layer(String model, @Nullable Map<String, YsmPresetBridge.Preset> presets, Trigger trigger, long at, long sequence, long now,
			Map<String, Float> parameters, @Nullable Body lower) {
		var preset = activePreset(model, presets, trigger, at, now);
		if (preset == null) return lower;
		parameters.putAll(preset.parameters());
		return preset.clip().isEmpty() ? lower : new Body(preset.clip(), trigger.id() + ":" + sequence + ":" + preset.id() + ":" + preset.clip(), trigger.id(), false);
	}
}
