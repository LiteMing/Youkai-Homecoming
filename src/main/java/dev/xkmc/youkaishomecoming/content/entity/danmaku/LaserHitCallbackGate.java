package dev.xkmc.youkaishomecoming.content.entity.danmaku;

/**
 * One-shot gate for a laser's on-hit callbacks.
 *
 * <p>A laser can overlap the same entity for many ticks, or report several
 * entities and a block during one collision pass.  The callback hook is a
 * property of the laser instance, not of each repeated collision report, so
 * the first real hit consumes this gate.</p>
 */
public final class LaserHitCallbackGate {

	private boolean consumed;

	/**
	 * Atomically claims the first callback slot.
	 *
	 * @return {@code true} only for the first call on this gate
	 */
	public boolean tryConsume() {
		if (consumed) return false;
		consumed = true;
		return true;
	}

	public boolean consumed() {
		return consumed;
	}
}
