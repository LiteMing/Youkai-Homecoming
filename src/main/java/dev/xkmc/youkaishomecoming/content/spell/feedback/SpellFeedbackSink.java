package dev.xkmc.youkaishomecoming.content.spell.feedback;

/** Presentation boundary shared by live runtime, preview, and analysis. */
public interface SpellFeedbackSink {
	void playSound(SoundCue cue);
	void cameraShake(CameraShakeCue cue);
}
