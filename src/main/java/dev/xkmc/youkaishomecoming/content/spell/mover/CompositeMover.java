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
	public void prepare(MoverOwner owner) {
		for (Entry entry : list) {
			entry.mover.prepare(owner);
		}
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		if (list.isEmpty()) return info.prevPos();

		int tick = info.tick();
		Vec3 currentBase = null;

		for (int i = 0; i < list.size(); i++) {
			Entry entry = list.get(i);
			int nextStart = (i + 1 < list.size()) ? list.get(i + 1).startTick : Integer.MAX_VALUE;

			if (tick < nextStart) {
				// 当前活跃分段
				int localTick = tick - entry.startTick;
				MoverInfo localInfo = new MoverInfo(localTick, info.prevPos(), info.prevVel(), info.self(), info.ownerInfo());

				Vec3 segPos;
				if (entry.mover instanceof TargetPosMover tpm) {
					segPos = tpm.pos(localInfo);
				} else {
					ProjectileMovement pm = entry.mover.move(localInfo);
					segPos = info.prevPos().add(pm.vec());
				}

				if (currentBase != null && entry.mover instanceof TargetPosMover tpm) {
					// 继承前置分段累积位移
					Vec3 initialPos = tpm.pos(new MoverInfo(0, info.prevPos(), info.prevVel(), info.self(), info.ownerInfo()));
					return currentBase.add(segPos.subtract(initialPos));
				}
				return segPos;
			} else {
				// 累积前置分段的终点绝对位置
				MoverInfo endInfo = new MoverInfo(entry.duration, info.prevPos(), info.prevVel(), info.self(), info.ownerInfo());
				Vec3 segEndPos;
				if (entry.mover instanceof TargetPosMover tpm) {
					segEndPos = tpm.pos(endInfo);
					if (currentBase != null) {
						Vec3 initialPos = tpm.pos(new MoverInfo(0, info.prevPos(), info.prevVel(), info.self(), info.ownerInfo()));
						currentBase = currentBase.add(segEndPos.subtract(initialPos));
					} else {
						currentBase = segEndPos;
					}
				} else {
					// 非 TargetPosMover 保持原有相对基准
				}
			}
		}

		return info.prevPos();
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
