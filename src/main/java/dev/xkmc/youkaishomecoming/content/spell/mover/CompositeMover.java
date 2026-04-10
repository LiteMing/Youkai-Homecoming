package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;

import java.util.ArrayList;

@SerialClass
public class CompositeMover extends DanmakuMover {

	@SerialClass.SerialField
	private final ArrayList<Entry> list = new ArrayList<>();

	@SerialClass.SerialField
	private int total = 0, index = 0;

	public CompositeMover() {

	}

	public CompositeMover add(int duration, DanmakuMover mover) {
		list.add(new Entry(total, mover));
		total += duration;
		return this;
	}

	@Override
	public ProjectileMovement move(MoverInfo info) {
		var ent = entryForTick(info.tick());
		return ent.mover.move(info.offsetTime(-ent.subtract));
	}

	@Override
	public boolean allowNextTickStep1Prefetch() {
		if (list.isEmpty()) {
			return false;
		}
		for (Entry entry : list) {
			if (entry.mover() == null || !entry.mover().allowNextTickStep1Prefetch()) {
				return false;
			}
		}
		return true;
	}

	public void addEnd() {
		var mover = list.get(list.size() - 1);
		if (mover.mover instanceof RectMover rect) {
			add(20, rect.toStatic(total - mover.subtract()));
		}
	}

	private Entry entryForTick(int tick) {
		if (list.isEmpty()) {
			throw new IllegalStateException("CompositeMover has no segments");
		}
		for (int i = list.size() - 1; i > 0; i--) {
			Entry entry = list.get(i);
			if (entry.subtract <= tick) {
				return entry;
			}
		}
		return list.get(0);
	}

	public record Entry(int subtract, DanmakuMover mover) {

	}

}
