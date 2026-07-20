package dev.xkmc.youkaishomecoming.content.spell.pilot.debug;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Ring buffer of the last N pilot frames. On hit, dump for post-mortem tuning.
 */
public final class PilotDeathReplay {

	public static final int DEFAULT_CAPACITY = 60;

	private final PilotDebugFrame[] buf;
	private int write;
	private int size;

	public PilotDeathReplay() {
		this(DEFAULT_CAPACITY);
	}

	public PilotDeathReplay(int capacity) {
		this.buf = new PilotDebugFrame[Math.max(1, capacity)];
	}

	public void push(PilotDebugFrame frame) {
		buf[write] = frame;
		write = (write + 1) % buf.length;
		if (size < buf.length) size++;
	}

	public void clear() {
		write = 0;
		size = 0;
		for (int i = 0; i < buf.length; i++) buf[i] = null;
	}

	public int size() {
		return size;
	}

	/** Oldest → newest. */
	public List<PilotDebugFrame> snapshot() {
		List<PilotDebugFrame> out = new ArrayList<>(size);
		int start = size < buf.length ? 0 : write;
		for (int i = 0; i < size; i++) {
			PilotDebugFrame f = buf[(start + i) % buf.length];
			if (f != null) out.add(f);
		}
		return out;
	}

	public PilotDebugFrame latest() {
		if (size == 0) return null;
		int idx = (write - 1 + buf.length) % buf.length;
		return buf[idx];
	}

	/** Compact JSON for log / file dump (no Gson dependency). */
	public String toJson(String spellId, String reason) {
		StringBuilder sb = new StringBuilder(size * 120 + 128);
		sb.append("{\n");
		sb.append("  \"spell\": \"").append(escape(spellId)).append("\",\n");
		sb.append("  \"reason\": \"").append(escape(reason)).append("\",\n");
		sb.append("  \"frames\": [\n");
		List<PilotDebugFrame> frames = snapshot();
		for (int i = 0; i < frames.size(); i++) {
			PilotDebugFrame f = frames.get(i);
			sb.append("    {\"tick\":").append(f.tick())
					.append(",\"feet\":").append(vec(f.feet()))
					.append(",\"vel\":").append(vec(f.velocity()))
					.append(",\"force\":").append(vec(f.force()))
					.append(",\"clear\":").append(fmt(f.minClearance()))
					.append(",\"search\":").append(f.searchMode())
					.append(",\"nodes\":").append(f.searchNodes())
					.append(",\"threats\":").append(f.threatCount())
					.append(",\"hit\":").append(f.hardHit())
					.append(",\"ns\":").append(f.pilotNanos())
					.append('}');
			if (i + 1 < frames.size()) sb.append(',');
			sb.append('\n');
		}
		sb.append("  ]\n}\n");
		return sb.toString();
	}

	private static String vec(Vec3 v) {
		return "[" + fmt(v.x) + "," + fmt(v.y) + "," + fmt(v.z) + "]";
	}

	private static String fmt(double d) {
		if (Double.isInfinite(d)) return d > 0 ? "1e9" : "-1e9";
		if (Double.isNaN(d)) return "0";
		return String.format(java.util.Locale.ROOT, "%.4f", d);
	}

	private static String escape(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
