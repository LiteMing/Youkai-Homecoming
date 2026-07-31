package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.Threat;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatFrame;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Per-tick spatial hash over predicted threat frames for broad-phase queries.
 * Cell size 4 (same as preview SpatialHash).
 */
public final class SpatioTemporalHash {

	private static final float CELL_SIZE = 4.0f;
	private static final float INV = 1.0f / CELL_SIZE;

	/** tickIndex → cellKey → list of (threatIndex) */
	private final List<Map<Long, List<Integer>>> buckets;
	private final List<Threat> threats;

	public SpatioTemporalHash(List<Threat> threats, int horizon) {
		this.threats = threats;
		this.buckets = new ArrayList<>(horizon);
		for (int t = 0; t < horizon; t++) {
			Map<Long, List<Integer>> grid = new HashMap<>();
			for (int i = 0; i < threats.size(); i++) {
				Threat threat = threats.get(i);
				ThreatFrame[] frames = threat.frames();
				if (t >= frames.length) continue;
				ThreatFrame f = frames[t];
				if (!f.active()) continue;
				long key = cellKey(f.position());
				grid.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
			}
			buckets.add(grid);
		}
	}

	public void query(int tickIndex, AABB bounds, Consumer<Integer> threatIndexConsumer) {
		if (tickIndex < 0 || tickIndex >= buckets.size()) return;
		Map<Long, List<Integer>> grid = buckets.get(tickIndex);
		int minX = cellCoord(bounds.minX);
		int maxX = cellCoord(bounds.maxX);
		int minY = cellCoord(bounds.minY);
		int maxY = cellCoord(bounds.maxY);
		int minZ = cellCoord(bounds.minZ);
		int maxZ = cellCoord(bounds.maxZ);
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cy = minY; cy <= maxY; cy++) {
				for (int cz = minZ; cz <= maxZ; cz++) {
					List<Integer> list = grid.get(pack(cx, cy, cz));
					if (list == null) continue;
					for (int idx : list) {
						threatIndexConsumer.accept(idx);
					}
				}
			}
		}
	}

	public List<Threat> threats() {
		return threats;
	}

	public int horizon() {
		return buckets.size();
	}

	private static long cellKey(Vec3 pos) {
		return pack(cellCoord(pos.x), cellCoord(pos.y), cellCoord(pos.z));
	}

	private static int cellCoord(double v) {
		return (int) Math.floor(v * INV);
	}

	private static long pack(int x, int y, int z) {
		return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
	}
}
