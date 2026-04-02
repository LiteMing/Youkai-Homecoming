package dev.xkmc.youkaishomecoming.content.spell.editor;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public final class SpellPreviewRenderState {

	private static final ThreadLocal<Quaternionf> ORIENTATION = new ThreadLocal<>();

	private SpellPreviewRenderState() {
	}

	public static boolean isActive() {
		return ORIENTATION.get() != null;
	}

	@Nullable
	public static Quaternionf orientation() {
		Quaternionf value = ORIENTATION.get();
		return value == null ? null : new Quaternionf(value);
	}

	public static Token push(Quaternionf orientation) {
		Quaternionf previous = ORIENTATION.get();
		ORIENTATION.set(new Quaternionf(orientation));
		return new Token(previous);
	}

	public static final class Token implements AutoCloseable {

		@Nullable
		private final Quaternionf previous;

		private Token(@Nullable Quaternionf previous) {
			this.previous = previous;
		}

		@Override
		public void close() {
			if (previous == null) {
				ORIENTATION.remove();
			} else {
				ORIENTATION.set(previous);
			}
		}
	}
}
