package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;

/**
 * Danmaku arrangement pattern for FireDanmakuAction.
 */
public enum PatternType {
	/**
	 * Evenly distributed around a full circle when {@code spread >= 360}; for a
	 * narrower spread, emits an open arc centred on {@code angle_offset}.
	 */
	RING,
	/** Linear spread in a fan/cone */
	LINE,
	/** Random directions within the spread angle */
	RANDOM,
	/** All projectiles aimed at the same direction */
	AIMED,
	/**
	 * Outer ring × inner ring, with configurable inner ring axis via {@code tilt_angle}.
	 * <p>
	 * {@code outer_count} directions are evenly spaced on the equatorial ring. Each spawns
	 * an inner ring of {@code count} projectiles. The plane of the inner ring is controlled
	 * by {@code tilt_angle}:
	 * <ul>
	 *   <li><b>tilt_angle = 0° (default)</b>: inner ring in the <b>vertical plane</b>
	 *       containing (outerDir, normal) — classic "orange-slice" style</li>
	 *   <li><b>tilt_angle = 90°</b>: inner ring <b>perpendicular</b> to outerDir —
	 *       "stacked-hoop" style (horizontal rings forming a sphere)</li>
	 *   <li>Any intermediate value blends between the two orientations</li>
	 * </ul>
	 * Other parameters:
	 * <ul>
	 *   <li>{@code elevation} = inner ring arc range (default 360° = full circle)</li>
	 *   <li>{@code angle_offset} = outer ring rotation offset</li>
	 * </ul>
	 * Total projectiles = {@code outer_count × count}.
	 */
	NESTED_RING,
	/** 2D grid layout on a specified plane (rows × cols) */
	GRID,
	/**
	 * Uniform spherical distribution using Fibonacci (golden-angle) placement.
	 * <p>
	 * Produces approximately uniform point spacing over the sphere surface.
	 * Only {@code count} controls the total number of projectiles (no outer_count needed).
	 * <ul>
	 *   <li>{@code count} = total number of projectiles</li>
	 *   <li>{@code elevation} = latitude range in degrees (default 180 = full sphere, 90 = hemisphere)</li>
	 *   <li>{@code spread} = longitude range in degrees (default 360 = full)</li>
	 *   <li>{@code angle_offset} = longitude rotation offset</li>
	 * </ul>
	 */
	SPHERE,
	/**
	 * Random spherical distribution with uniform area density.
	 * <p>
	 * Each projectile is placed at a uniformly random point on the sphere surface
	 * (or within the specified latitude/longitude range). Uses area-correct sampling
	 * (uniform in sin(phi) space) to avoid polar clustering.
	 * <ul>
	 *   <li>{@code count} = total number of projectiles</li>
	 *   <li>{@code elevation} = latitude range in degrees (default 180 = full sphere)</li>
	 *   <li>{@code spread} = longitude range in degrees (default 360 = full)</li>
	 * </ul>
	 */
	SPHERE_RANDOM,
	/** Spiral arrangement with configurable turns and radius growth */
	SPIRAL,
	/**
	 * Cone distribution: projectiles are evenly placed on a cone surface with the
	 * <b>forward (aim) direction as the cone axis</b>.
	 * <p>
	 * Equivalent to legacy {@code getOrientation(dir).asNormal().rotateDegrees(a, coneAngle)}.
	 * <ul>
	 *   <li>{@code count} = number of projectiles around the cone</li>
	 *   <li>{@code elevation} = cone half-angle (degrees from the axis); 72° is a narrow cone
	 *       where sin(72°)≈0.95 of speed goes along the axis</li>
	 *   <li>{@code angle_offset} = rotation offset around the cone</li>
	 * </ul>
	 * Direction formula: {@code forward * sin(elevation) + (normal*cos(a) + side*sin(a)) * cos(elevation)},
	 * where a = (360/n)*i + angle_offset.
	 */
	CONE;

	public static final Codec<PatternType> CODEC = Codec.STRING.xmap(
			s -> PatternType.valueOf(s.toUpperCase()),
			e -> e.name().toLowerCase()
	);
}
