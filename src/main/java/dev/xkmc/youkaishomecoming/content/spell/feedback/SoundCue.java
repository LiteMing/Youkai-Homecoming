package dev.xkmc.youkaishomecoming.content.spell.feedback;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Immutable world sound request emitted by a spell action. */
public record SoundCue(ResourceLocation soundId, SoundSource source, CueOrigin origin,
		@Nullable Vec3 position, float volume, float pitch, double radius, boolean attenuation)
		implements FeedbackCue {
	public SoundCue {
		source = source == null ? SoundSource.HOSTILE : source;
		origin = origin == null ? CueOrigin.CASTER : origin;
		volume = Float.isFinite(volume) ? Math.max(0, volume) : 0;
		pitch = Float.isFinite(pitch) ? Math.max(0.01f, pitch) : 1;
		radius = Double.isFinite(radius) ? Math.max(0, radius) : 0;
	}

	public SoundCue(ResourceLocation soundId, float volume, float pitch) {
		this(soundId, SoundSource.HOSTILE, CueOrigin.CASTER, null, volume, pitch, 0, true);
	}
}
