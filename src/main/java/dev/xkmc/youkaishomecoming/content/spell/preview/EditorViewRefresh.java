package dev.xkmc.youkaishomecoming.content.spell.preview;

/** Coalesces changes until the next safe render/input boundary, outside widget event dispatch. */
final class EditorViewRefresh {

	private final Runnable rebuild;
	private int interactionDepth;
	private boolean pending;

	EditorViewRefresh(Runnable rebuild) {
		this.rebuild = rebuild;
	}

	void request() {
		pending = true;
	}

	/** Buttons and committed selections refresh even when their value callback does not request it. */
	void interact(Runnable change) {
		interactionDepth++;
		try {
			change.run();
		} finally {
			pending = true;
			interactionDepth--;
		}
	}

	void flush() {
		if (interactionDepth > 0 || !pending) return;
		pending = false;
		rebuild.run();
	}
}
