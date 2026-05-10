package dev.xkmc.youkaishomecoming.content.spell.mover;

import dev.xkmc.l2serial.serialization.SerialClass;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-segment cubic Bezier mover. Each segment is a cubic Bezier curve
 * with its own control points and duration. Segments are chained end-to-end:
 * the end point of segment N becomes the start point of segment N+1.
 * After all segments complete, the projectile continues in a straight line.
 */
@SerialClass
public final class MultiBezierMover extends TargetPosMover {

	@SerialClass.SerialField
	private final ArrayList<Segment> segments = new ArrayList<>();

	@SerialClass.SerialField
	private int totalDuration = 0;

	@Deprecated
	public MultiBezierMover() {
	}

	public MultiBezierMover(List<Segment> segments) {
		this.segments.addAll(segments);
		for (var seg : segments) {
			totalDuration += seg.duration;
		}
	}

	@Override
	public Vec3 pos(MoverInfo info) {
		return pos(info.tick());
	}

	public Vec3 pos(double tick) {
		if (segments.isEmpty()) return Vec3.ZERO;

		// Find which segment we're in
		double elapsed = 0;
		for (var seg : segments) {
			if (tick < elapsed + seg.duration) {
				double localT = (tick - elapsed) / seg.duration;
				return seg.bezier(localT);
			}
			elapsed += seg.duration;
		}

		// Past all segments: continue linearly from last segment's end
		var lastSeg = segments.get(segments.size() - 1);
		Vec3 endPos = lastSeg.bezier(1.0);
		Vec3 endVel = lastSeg.bezierDerivative(1.0);
		return endPos.add(endVel.scale(tick - totalDuration));
	}

	@SerialClass
	public static class Segment {
		@SerialClass.SerialField
		public Vec3 p0 = Vec3.ZERO;
		@SerialClass.SerialField
		public Vec3 p1 = Vec3.ZERO;
		@SerialClass.SerialField
		public Vec3 p2 = Vec3.ZERO;
		@SerialClass.SerialField
		public Vec3 p3 = Vec3.ZERO;
		@SerialClass.SerialField
		public int duration = 40;

		@Deprecated
		public Segment() {
		}

		public Segment(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int duration) {
			this.p0 = p0;
			this.p1 = p1;
			this.p2 = p2;
			this.p3 = p3;
			this.duration = Math.max(1, duration);
		}

		public Vec3 bezier(double t) {
			double u = 1 - t;
			double u2 = u * u;
			double u3 = u2 * u;
			double t2 = t * t;
			double t3 = t2 * t;
			return p0.scale(u3)
					.add(p1.scale(3 * u2 * t))
					.add(p2.scale(3 * u * t2))
					.add(p3.scale(t3));
		}

		public Vec3 bezierDerivative(double t) {
			double u = 1 - t;
			Vec3 d = p1.subtract(p0).scale(3 * u * u)
					.add(p2.subtract(p1).scale(6 * u * t))
					.add(p3.subtract(p2).scale(3 * t * t));
			return d.scale(1.0 / duration);
		}
	}
}
