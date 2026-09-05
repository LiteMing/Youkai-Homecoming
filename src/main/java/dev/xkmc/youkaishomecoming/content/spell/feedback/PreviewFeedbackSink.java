package dev.xkmc.youkaishomecoming.content.spell.feedback;

import java.util.Objects;
import java.util.function.Consumer;

/** Local preview sink; consumers are owned by the editor viewport. */
public final class PreviewFeedbackSink implements SpellFeedbackSink {
	private final Consumer<SoundCue> soundConsumer;
	private final Consumer<CameraShakeCue> shakeConsumer;
	public PreviewFeedbackSink() { this(cue -> {}, cue -> {}); }
	public PreviewFeedbackSink(Consumer<SoundCue> soundConsumer, Consumer<CameraShakeCue> shakeConsumer) {
		this.soundConsumer = Objects.requireNonNull(soundConsumer);
		this.shakeConsumer = Objects.requireNonNull(shakeConsumer);
	}
	@Override public void playSound(SoundCue cue) { if (cue != null) soundConsumer.accept(cue); }
	@Override public void cameraShake(CameraShakeCue cue) { if (cue != null) shakeConsumer.accept(cue); }
}
