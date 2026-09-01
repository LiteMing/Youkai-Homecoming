package dev.xkmc.youkaishomecoming.content.spell.feedback;

/** Marker for serializable presentation requests queued by the server dispatcher. */
public sealed interface FeedbackCue permits SoundCue, CameraShakeCue {
}
