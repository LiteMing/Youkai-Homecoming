package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.Threat;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatFrame;
import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatProviderRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable threat set for one pilot tick. Built via provider registry;
 * snapshot does not care about mod vs vanilla origin.
 */
public final class ThreatSnapshot {

	private final List<Threat> threats;
	private final int horizon;
	private final long buildNanos;
	@Nullable
	private final SpatioTemporalHash broadphase;

	public ThreatSnapshot(List<Threat> threats, int horizon, long buildNanos, boolean buildHash) {
		this.threats = List.copyOf(threats);
		this.horizon = horizon;
		this.buildNanos = buildNanos;
		this.broadphase = buildHash && !threats.isEmpty()
				? new SpatioTemporalHash(this.threats, horizon)
				: null;
	}

	public List<Threat> threats() {
		return threats;
	}

	public int horizon() {
		return horizon;
	}

	public long buildNanos() {
		return buildNanos;
	}

	@Nullable
	public SpatioTemporalHash broadphase() {
		return broadphase;
	}

	public int size() {
		return threats.size();
	}

	/**
	 * Build from entities through the provider chain.
	 *
	 * @param topK max threats retained (by nearest approach distance at t=0); ≤0 = no truncate
	 */
	public static ThreatSnapshot capture(Iterable<? extends Entity> entities,
	                                     ThreatProviderRegistry registry,
	                                     int horizon,
	                                     int topK,
	                                     Vec3 selfPos) {
		long t0 = System.nanoTime();
		List<Threat> list = new ArrayList<>();
		for (Entity e : entities) {
			Threat t = registry.capture(e, horizon);
			if (t != null && t.frames().length > 0) {
				list.add(t);
			}
		}
		if (topK > 0 && list.size() > topK) {
			list.sort(Comparator.comparingDouble(th -> nearestDistSq(th, selfPos)));
			list = new ArrayList<>(list.subList(0, topK));
		}
		long dt = System.nanoTime() - t0;
		return new ThreatSnapshot(list, horizon, dt, true);
	}

	/** Offline builder for tests (no entities). */
	public static ThreatSnapshot of(List<Threat> threats, int horizon) {
		return new ThreatSnapshot(threats, horizon, 0, true);
	}

	public static ThreatSnapshot empty(int horizon) {
		return new ThreatSnapshot(Collections.emptyList(), horizon, 0, false);
	}

	/**
	 * Ranking key: squared <b>surface</b> distance (not center).
	 * Large balls (bubble size 4, moon/giant-yinyang size 8) have hitRadius up to ~1.6;
	 * sorting by center alone drops them in Top-K when many small bullets are closer by center.
	 */
	private static double nearestDistSq(Threat th, Vec3 selfPos) {
		ThreatFrame f = th.frames()[0];
		if (!f.active()) return Double.POSITIVE_INFINITY;
		double surface;
		if (f.isLaser() && f.orientation() != null && f.orientation().lengthSqr() > 1e-12) {
			Vec3 end = f.position().add(f.orientation().normalize().scale(f.length()));
			surface = distPointToSegment(selfPos, f.position(), end) - f.hitRadius();
		} else {
			// Point / ball: distance to sphere surface
			surface = selfPos.distanceTo(f.position()) - f.hitRadius();
		}
		// Already overlapping → highest priority
		if (surface <= 0) return 0;
		return surface * surface;
	}

	private static double distPointToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double denom = ab.lengthSqr();
		if (denom < 1e-12) return p.distanceTo(a);
		double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / denom));
		return p.distanceTo(a.add(ab.scale(t)));
	}
}
