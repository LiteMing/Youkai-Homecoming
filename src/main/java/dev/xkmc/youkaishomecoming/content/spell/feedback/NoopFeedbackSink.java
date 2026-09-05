package dev.xkmc.youkaishomecoming.content.spell.feedback;

/** Sink used by contexts that cannot present client feedback. */
public final class NoopFeedbackSink implements SpellFeedbackSink {
	public static final NoopFeedbackSink INSTANCE = new NoopFeedbackSink();
	private NoopFeedbackSink() {}
	@Override public void playSound(SoundCue cue) {}
	@Override public void cameraShake(CameraShakeCue cue) {}
}
