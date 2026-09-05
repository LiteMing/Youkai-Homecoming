package dev.xkmc.youkaishomecoming.compat.ysm;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import static dev.xkmc.youkaishomecoming.compat.ysm.YsmModelProfile.Trigger;

/** Pure composition: no client, renderer, AI, Molang or third-party classes. */
public final class YsmPresentationResolver {

	public record Body(String clip, String replayKey, String source, boolean explicit) { }
	public record Resolved(@Nullable Body body, Map<String, Float> parameters, boolean beaten) { }

	private YsmPresentationResolver() { }

	public static Resolved resolve(String model, @Nullable YsmModelProfile profile, YsmPresentationSignals signals,
			YsmPresentationState explicit, long now) {
		if (profile != null && !profile.model().equals(model)) profile = null;
		Map<String, Float> parameters = new LinkedHashMap<>();
		Body body = null;
		if (!signals.state().beaten()) {
			body = layer(profile, signals.state(), signals.stateAt(), signals.stateSequence(), now, parameters, body);
			if (signals.combat()) body = layer(profile, Trigger.ENTER_COMBAT, signals.combatAt(), signals.combatSequence(), now, parameters, body);
			body = layer(profile, Trigger.HURT, signals.hurtAt(), signals.hurtSequence(), now, parameters, body);
		}
		var animation = explicit.animation();
		if (animation != null && animation.active(now) && animation.matchesModel(model))
			body = new Body(animation.clip(), "explicit:" + explicit.sequence(), animation.source().name(), true);
		explicit.parameters().forEach((key, value) -> {
			if (value.active(now) && value.matchesModel(model)) parameters.put(key, value.value());
		});
		if (signals.state().beaten()) {
			// No lower-priority body can displace the existing model-independent beaten fallback.
			body = layer(profile, signals.state(), signals.stateAt(), signals.stateSequence(), now, parameters, null);
		}
		return new Resolved(body, Map.copyOf(parameters), signals.state().beaten());
	}

	private static Body layer(@Nullable YsmModelProfile profile, Trigger trigger, long at, long sequence, long now,
			Map<String, Float> parameters, @Nullable Body lower) {
		if (profile == null || at < 0 || now < at) return lower;
		String id = profile.triggers().get(trigger);
		var preset = id == null ? null : profile.presets().get(id);
		if (preset == null || trigger.event() && now - at >= preset.ticks()) return lower;
		parameters.putAll(preset.parameters());
		return preset.clip().isEmpty() ? lower : new Body(preset.clip(), trigger.id() + ":" + sequence + ":" + id + ":" + preset.clip(), trigger.id(), false);
	}
}
