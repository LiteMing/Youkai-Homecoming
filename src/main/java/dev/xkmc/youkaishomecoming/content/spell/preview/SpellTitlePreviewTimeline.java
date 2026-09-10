package dev.xkmc.youkaishomecoming.content.spell.preview;

/** Local preview playback. Editing duration preserves the frame being inspected. */
public final class SpellTitlePreviewTimeline {

	private int duration = 20;
	private double elapsed;
	private boolean playing;

	public void setDuration(int ticks) {
		int next = Math.max(20, ticks);
		if (next == duration) return;
		elapsed = elapsed / duration * next;
		duration = next;
	}

	public int duration() { return duration; }
	public boolean isPlaying() { return playing; }

	public void restart() {
		elapsed = 0;
		playing = true;
	}

	public void togglePlaying() {
		if (playing) playing = false;
		else if (elapsed >= duration) restart();
		else playing = true;
	}

	public void seek(float progress) {
		elapsed = (Float.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0) * duration;
		playing = false;
	}

	public void tick() {
		if (!playing) return;
		elapsed = Math.min(duration, elapsed + 1);
		if (elapsed >= duration) playing = false;
	}

	public float progress(float partialTick) {
		double fraction = playing ? Math.max(0, Math.min(1, partialTick)) : 0;
		return (float) Math.min(1, (elapsed + fraction) / duration);
	}
}
