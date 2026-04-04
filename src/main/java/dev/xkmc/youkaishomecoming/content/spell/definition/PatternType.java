package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;

/**
 * Danmaku arrangement pattern for FireDanmakuAction.
 */
public enum PatternType {
	/** Evenly distributed around a full circle */
	RING,
	/** Linear spread in a fan/cone */
	LINE,
	/** Random directions within the spread angle */
	RANDOM,
	/** All projectiles aimed at the same direction */
	AIMED,
	/** Outer ring of groups, each group is a ring perpendicular to its outer direction */
	NESTED_RING,
	/** 2D grid layout on a specified plane (rows × cols) */
	GRID,
	/** Uniform spherical distribution (latitude × longitude) */
	SPHERE,
	/** Spiral arrangement with configurable turns and radius growth */
	SPIRAL;

	public static final Codec<PatternType> CODEC = Codec.STRING.xmap(
			s -> PatternType.valueOf(s.toUpperCase()),
			e -> e.name().toLowerCase()
	);
}
