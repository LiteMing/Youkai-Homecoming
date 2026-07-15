package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/**
 * Composite (segmented) mover: chains multiple movers sequentially in time.
 * Each segment has a duration; when the current segment's time expires, the next one starts.
 *
 * Now extends TargetPosMover so it works correctly when nested inside LayeredMover.
 * For sub-movers that are TargetPosMover, delegates to their pos() directly.
 * For non-TargetPosMover sub-movers (rare), falls back to velocity-based delta computation.
 */
@SerialClass
public class CompositeMover extends TargetPosMover {

	@SerialClass.SerialField
	private final ArrayList<Entry> list = new ArrayList<>();

	@SerialClass.SerialField
	private int total = 0;

	public CompositeMover() {
	}

	public CompositeMover add(int duration, DanmakuMover mover) {
		list.add(new Entry(total, duration, mover));
		total += duration;
		return this;
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		if (list.isEmpty()) return info.prevPos();

		int tick = info.tick();
		// Find the active segment
		int segIdx = 0;
		for (int i = 1; i < list.size(); i++) {
			if (list.get(i).startTick <= tick) {
				segIdx = i;
			} else {
				break;
			}
		}

		var entry = list.get(segIdx);
		int localTick = tick - entry.startTick;
		MoverInfo localInfo = new MoverInfo(localTick, info.prevPos(), info.prevVel(), info.self(), info.ownerInfo());

		if (entry.mover instanceof TargetPosMover tpm) {
			return tpm.pos(localInfo);
		}

		// Fallback for non-TargetPosMover: use move() delta from prevPos
		// This is less accurate but handles edge cases like RotateMover
		ProjectileMovement pm = entry.mover.move(localInfo);
		return info.prevPos().add(pm.vec());
	}

	@Override
	public ProjectileMovement move(MoverInfo info) {
		// Use the TargetPosMover default: pos(tick) - prevPos
		return super.move(info);
	}

	public void addEnd() {
		if (!list.isEmpty()) {
			var lastEntry = list.get(list.size() - 1);
			if (lastEntry.mover instanceof RectMover rect) {
				int lastDuration = lastEntry.duration;
				add(20, rect.toStatic(lastDuration));
			}
		}
	}

	@SerialClass
	public static class Entry {
		@SerialClass.SerialField
		public int startTick;
		@SerialClass.SerialField
		public int duration;
		@SerialClass.SerialField
		public DanmakuMover mover;

		@Deprecated
		public Entry() {
		}

		public Entry(int startTick, int duration, DanmakuMover mover) {
			this.startTick = startTick;
			this.duration = duration;
			this.mover = mover;
		}
	}
}
