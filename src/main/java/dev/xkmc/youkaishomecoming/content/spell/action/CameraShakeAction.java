package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.feedback.CameraShakeCue;
import dev.xkmc.youkaishomecoming.content.spell.feedback.CueFalloff;
import dev.xkmc.youkaishomecoming.content.spell.feedback.CueOrigin;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

/** Data-driven camera shake request. The server sink owns final limits. */
public record CameraShakeAction(CueOrigin origin, NumberProvider intensity, NumberProvider duration,
		NumberProvider frequency, NumberProvider radius, CueFalloff falloff, String channel) implements SpellAction {
	public static final Codec<CameraShakeAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("origin", "caster").xmap(CameraShakeAction::parseOrigin,
					CameraShakeAction::formatOrigin).forGetter(CameraShakeAction::origin),
			NumberProvider.CODEC.optionalFieldOf("intensity", NumberProvider.constant(0.5)).forGetter(CameraShakeAction::intensity),
			NumberProvider.CODEC.optionalFieldOf("duration", NumberProvider.constant(8)).forGetter(CameraShakeAction::duration),
			NumberProvider.CODEC.optionalFieldOf("frequency", NumberProvider.constant(1)).forGetter(CameraShakeAction::frequency),
			NumberProvider.CODEC.optionalFieldOf("radius", NumberProvider.constant(16)).forGetter(CameraShakeAction::radius),
			Codec.STRING.optionalFieldOf("falloff", "linear").xmap(CameraShakeAction::parseFalloff,
					CameraShakeAction::formatFalloff).forGetter(CameraShakeAction::falloff),
			Codec.STRING.optionalFieldOf("channel", "impact").forGetter(CameraShakeAction::channel)
	).apply(i, CameraShakeAction::new));

	public CameraShakeAction() {
		this(CueOrigin.CASTER, NumberProvider.constant(0.5), NumberProvider.constant(8),
				NumberProvider.constant(1), NumberProvider.constant(16), CueFalloff.LINEAR, "impact");
	}

	@Override
	public void execute(SpellContext ctx) {
		ctx.feedback().cameraShake(new CameraShakeCue(origin, null,
				intensity.get(ctx), Math.max(1, (int) duration.get(ctx)), frequency.get(ctx),
				radius.get(ctx), falloff, channel));
	}

	private static CueOrigin parseOrigin(String value) {
		try { return CueOrigin.valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
		catch (Exception ignored) { return CueOrigin.CASTER; }
	}
	private static String formatOrigin(CueOrigin value) { return value.name().toLowerCase(java.util.Locale.ROOT); }
	private static CueFalloff parseFalloff(String value) {
		try { return CueFalloff.valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
		catch (Exception ignored) { return CueFalloff.LINEAR; }
	}
	private static String formatFalloff(CueFalloff value) { return value.name().toLowerCase(java.util.Locale.ROOT); }
}
