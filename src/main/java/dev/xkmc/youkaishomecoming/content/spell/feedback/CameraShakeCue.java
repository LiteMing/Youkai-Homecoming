package dev.xkmc.youkaishomecoming.content.spell.feedback;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Immutable request for a purely visual camera shake. */
public record CameraShakeCue(CueOrigin origin, @Nullable Vec3 position, double intensity,
		int duration, double frequency, double radius, CueFalloff falloff, String channel)
		implements FeedbackCue {
	public CameraShakeCue {
		origin = origin == null ? CueOrigin.CASTER : origin;
		intensity = Double.isFinite(intensity) ? Math.max(0, intensity) : 0;
		duration = Math.max(1, duration);
		frequency = Double.isFinite(frequency) ? Math.max(0.01, frequency) : 1;
		radius = Double.isFinite(radius) ? Math.max(0, radius) : 0;
		falloff = falloff == null ? CueFalloff.LINEAR : falloff;
		channel = channel == null || channel.isBlank() ? "impact" : channel;
	}

	public CameraShakeCue(CueOrigin origin, double intensity, int duration, double frequency,
			double radius, CueFalloff falloff, String channel) {
		this(origin, null, intensity, duration, frequency, radius, falloff, channel);
	}
}
