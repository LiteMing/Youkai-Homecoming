package dev.xkmc.youkaishomecoming.content.spell.pilot.threat;

import dev.xkmc.youkaishomecoming.content.spell.pilot.predict.ThreatSemantic;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Self hit volume as local offsets from feet.
 * Holds <b>both</b> DANMAKU and VANILLA boxes so scoring can switch per threat
 * (plan §2.5b — shrink only applies to mod danmaku, not vanilla arrows).
 */
public final class SelfBoxModel {

	/** Matches {@code IYHDanmaku.GRAZE_RANGE}. */
	public static final float GRAZE_RANGE = 1.5f;

	/** Local hard-hit box for DANMAKU threats (may be shrunk / inflated). */
	private final double dMinX, dMinY, dMinZ, dMaxX, dMaxY, dMaxZ;
	/** Local hard-hit box for VANILLA threats (full collision). */
	private final double vMinX, vMinY, vMinZ, vMaxX, vMaxY, vMaxZ;
	private final float grazeExtra;

	public SelfBoxModel(double dMinX, double dMinY, double dMinZ,
	                    double dMaxX, double dMaxY, double dMaxZ,
	                    double vMinX, double vMinY, double vMinZ,
	                    double vMaxX, double vMaxY, double vMaxZ,
	                    float grazeExtra) {
		this.dMinX = dMinX;
		this.dMinY = dMinY;
		this.dMinZ = dMinZ;
		this.dMaxX = dMaxX;
		this.dMaxY = dMaxY;
		this.dMaxZ = dMaxZ;
		this.vMinX = vMinX;
		this.vMinY = vMinY;
		this.vMinZ = vMinZ;
		this.vMaxX = vMaxX;
		this.vMaxY = vMaxY;
		this.vMaxZ = vMaxZ;
		this.grazeExtra = grazeExtra;
	}

	/** Convenience: same box for both semantics. */
	public SelfBoxModel(double minX, double minY, double minZ,
	                    double maxX, double maxY, double maxZ,
	                    float grazeExtra) {
		this(minX, minY, minZ, maxX, maxY, maxZ,
				minX, minY, minZ, maxX, maxY, maxZ, grazeExtra);
	}

	/** Default hard box = DANMAKU semantics (most common consumer path). */
	public AABB hardAt(Vec3 feet) {
		return hitBoxAt(feet, ThreatSemantic.DANMAKU);
	}

	public AABB grazeAt(Vec3 feet) {
		return hardAt(feet).inflate(grazeExtra);
	}

	/**
	 * Hit box for a threat semantic.
	 * DANMAKU → possibly shrunk/inflated game hitbox; VANILLA → full collision box.
	 */
	public AABB hitBoxAt(Vec3 feet, ThreatSemantic semantic) {
		if (semantic == ThreatSemantic.VANILLA) {
			return new AABB(
					feet.x + vMinX, feet.y + vMinY, feet.z + vMinZ,
					feet.x + vMaxX, feet.y + vMaxY, feet.z + vMaxZ
			);
		}
		return new AABB(
				feet.x + dMinX, feet.y + dMinY, feet.z + dMinZ,
				feet.x + dMaxX, feet.y + dMaxY, feet.z + dMaxZ
		);
	}

	/** Full body box for wall probes / terrain (always vanilla footprint). */
	public AABB bodyAt(Vec3 feet) {
		return hitBoxAt(feet, ThreatSemantic.VANILLA);
	}

	public float grazeExtra() {
		return grazeExtra;
	}

	public double width() {
		return dMaxX - dMinX;
	}

	public double height() {
		return dMaxY - dMinY;
	}

	// --- Factories ---

	/**
	 * Preview target: armor-stand-like 0.5×1.975×0.5 then inflate(0.3)
	 * for both semantics (preview uses one hit path).
	 */
	public static SelfBoxModel previewTarget() {
		double hw = 0.25 + 0.3;
		double h = 1.975 + 0.6;
		return new SelfBoxModel(-hw, -0.3, -hw, hw, h - 0.3, hw, GRAZE_RANGE);
	}

	/**
	 * Player: DANMAKU = asymmetric HITBOX shrink; VANILLA = full 0.6×1.8×0.6.
	 * {@code hitBoxDelta} from {@code GrazeHelper.getHitBoxDelta} (live each tick).
	 */
	public static SelfBoxModel playerDanmaku(float hitBoxDelta) {
		double shrink = -hitBoxDelta;
		double dMinX = -0.3 + shrink;
		double dMaxX = 0.3 - shrink;
		double dMinY = 0 + shrink * 2;
		double dMaxY = 1.8;
		double dMinZ = -0.3 + shrink;
		double dMaxZ = 0.3 - shrink;
		// Vanilla full player box
		double vMinX = -0.3, vMaxX = 0.3;
		double vMinY = 0, vMaxY = 1.8;
		double vMinZ = -0.3, vMaxZ = 0.3;
		return new SelfBoxModel(
				dMinX, dMinY, dMinZ, dMaxX, dMaxY, dMaxZ,
				vMinX, vMinY, vMinZ, vMaxX, vMaxY, vMaxZ,
				GRAZE_RANGE
		);
	}

	/** Full collision box only (both semantics identical). */
	public static SelfBoxModel vanillaPlayer() {
		return new SelfBoxModel(-0.3, 0, -0.3, 0.3, 1.8, 0.3, 0.3f);
	}

	/** Youkai/Boss: DANMAKU = inflate(GRAZE_RANGE); VANILLA = raw width×height. */
	public static SelfBoxModel youkaiDanmaku(double width, double height) {
		double hw = width * 0.5;
		double dHw = hw + GRAZE_RANGE;
		return new SelfBoxModel(
				-dHw, -GRAZE_RANGE, -dHw, dHw, height + GRAZE_RANGE, dHw,
				-hw, 0, -hw, hw, height, hw,
				GRAZE_RANGE
		);
	}

	public static SelfBoxModel fromWorldBox(AABB world, Vec3 feet, float grazeExtra) {
		double minX = world.minX - feet.x, minY = world.minY - feet.y, minZ = world.minZ - feet.z;
		double maxX = world.maxX - feet.x, maxY = world.maxY - feet.y, maxZ = world.maxZ - feet.z;
		return new SelfBoxModel(minX, minY, minZ, maxX, maxY, maxZ, grazeExtra);
	}
}
