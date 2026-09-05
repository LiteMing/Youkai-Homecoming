package dev.xkmc.youkaishomecoming.compat.ysm;

import java.util.ArrayList;
import java.util.List;

/**
 * A numeric override scoped to one synchronous render. Restoring never blindly writes zero and
 * preserves a different value written by model logic during that render.
 */
public final class YsmParameterOverlay implements AutoCloseable {

	public interface Slot {
		Object get() throws ReflectiveOperationException;
		void set(Object value) throws ReflectiveOperationException;
	}

	private record Change(Slot slot, Object original, float applied) { }
	private final List<Change> changes = new ArrayList<>();

	public boolean apply(Slot slot, float value) throws ReflectiveOperationException {
		if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite model parameter");
		Object original = slot.get();
		if (original != null && !(original instanceof Number)) return false;
		changes.add(new Change(slot, original, value));
		slot.set(value);
		return true;
	}

	@Override
	public void close() throws ReflectiveOperationException {
		ReflectiveOperationException failure = null;
		for (int i = changes.size() - 1; i >= 0; i--) {
			Change change = changes.get(i);
			try {
				Object current = change.slot.get();
				if (current instanceof Number number && Double.compare(number.doubleValue(), change.applied) == 0) {
					change.slot.set(change.original);
				}
			} catch (ReflectiveOperationException ex) {
				if (failure == null) failure = ex;
				else failure.addSuppressed(ex);
			}
		}
		changes.clear();
		if (failure != null) throw failure;
	}
}
