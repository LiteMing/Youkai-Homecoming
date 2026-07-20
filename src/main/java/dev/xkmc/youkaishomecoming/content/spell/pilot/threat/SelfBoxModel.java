package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatSemantic;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Self hit volume as local offsets from feet position.
 * Core only holds numbers — consumers build via factories below.
 * <p>
 * Matches game hit semantics (plan §2.5b):
 * <ul>
 *   <li>Preview: inflate(0.3) on armor-stand box</li>
 *   <li>Player vs DANMAKU: asymmetric shrink from HITBOX attr</li>
 *   <li>Youkai/Boss vs DANMAKU: inflate(GRAZE_RANGE=1.5)</li>
 *   <li>Any vs VANILLA: full collision box</li>
 * </ul>
 */
public final class SelfBoxModel {

	/** Matches {@code IYHDanmaku.GRAZE_RANGE}. */
	public static final float GRAZE_RANGE = 1.5f;

	/** Local hard-hit box relative to feet (min corner → max corner). */
	private final double minX, minY, minZ, maxX, maxY, maxZ;
	private final float grazeExtra;

	public SelfBoxModel(double minX, double minY, double minZ,
	                    double maxX, double maxY, double maxZ,
	                    float grazeExtra) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxY = maxY;
		this.maxZ = maxZ;
		this.grazeExtra = grazeExtra;
	}

	public AABB hardAt(Vec3 feet) {
		return new AABB(
				feet.x + minX, feet.y + minY, feet.z + minZ,
				feet.x + maxX, feet.y + maxY, feet.z + maxZ
		);
	}

	public AABB grazeAt(Vec3 feet) {
		return hardAt(feet).inflate(grazeExtra);
	}

	/**
	 * Hit box for a threat semantic. VANILLA uses full hard box (no danmaku shrink).
	 * DANMAKU uses the model as constructed (already shrink/inflate applied).
	 */
	public AABB hitBoxAt(Vec3 feet, ThreatSemantic semantic) {
		return hardAt(feet);
	}

	public float grazeExtra() {
		return grazeExtra;
	}

	public double width() {
		return maxX - minX;
	}

	public double height() {
		return maxY - minY;
	}

	// --- Factories (pure geometry; no Entity imports) ---

	/**
	 * Preview target: armor-stand-like 0.5×1.975×0.5 then inflate(0.3)
	 * matching {@code PreviewCardHolder} hit path.
	 */
	public static SelfBoxModel previewTarget() {
		double hw = 0.25 + 0.3;
		double h = 1.975 + 0.6;
		return new SelfBoxModel(-hw, -0.3, -hw, hw, h - 0.3, hw, GRAZE_RANGE);
	}

	/**
	 * Player vs DANMAKU: base player box 0.6×1.8×0.6 with asymmetric HITBOX shrink.
	 * {@code hitBoxDelta} from {@code GrazeHelper.getHitBoxDelta} (live each tick).
	 * shrink = -hitBoxDelta: negative attr enlarges shrink → smaller box.
	 */
	public static SelfBoxModel playerDanmaku(float hitBoxDelta) {
		double shrink = -hitBoxDelta;
		// Base player: feet at origin, width 0.6, height 1.8
		double minX = -0.3 + shrink;
		double maxX = 0.3 - shrink;
		double minY = 0 + shrink * 2;
		double maxY = 1.8; // top not shrunk
		double minZ = -0.3 + shrink;
		double maxZ = 0.3 - shrink;
		return new SelfBoxModel(minX, minY, minZ, maxX, maxY, maxZ, GRAZE_RANGE);
	}

	/** Full collision box (vanilla projectiles / any vs VANILLA). */
	public static SelfBoxModel vanillaPlayer() {
		return new SelfBoxModel(-0.3, 0, -0.3, 0.3, 1.8, 0.3, 0.3f);
	}

	/** Youkai/Boss vs DANMAKU: full box inflated by GRAZE_RANGE. */
	public static SelfBoxModel youkaiDanmaku(double width, double height) {
		double hw = width * 0.5 + GRAZE_RANGE;
		return new SelfBoxModel(-hw, -GRAZE_RANGE, -hw, hw, height + GRAZE_RANGE, hw, GRAZE_RANGE);
	}

	/** Axis-aligned box from world AABB relative to feet. */
	public static SelfBoxModel fromWorldBox(AABB world, Vec3 feet, float grazeExtra) {
		return new SelfBoxModel(
				world.minX - feet.x, world.minY - feet.y, world.minZ - feet.z,
				world.maxX - feet.x, world.maxY - feet.y, world.maxZ - feet.z,
				grazeExtra
		);
	}
}
