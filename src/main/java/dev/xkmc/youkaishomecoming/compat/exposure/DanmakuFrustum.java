package dev.xkmc.youkaishomecoming.compat.exposure;

import net.minecraft.world.phys.Vec3;

/**
 * Simplified frustum check for determining if a point is within the camera's field of view.
 * Adapted from Exposure mod's EntitiesInFrame logic.
 */
public class DanmakuFrustum {

	private final Vec3 cameraPos;
	private final Vec3 forward;
	private final Vec3 right;
	private final Vec3 up;
	private final float halfAngleRad;

	public DanmakuFrustum(Vec3 cameraPos, Vec3 forward, float fovDegrees) {
		this.cameraPos = cameraPos;
		this.forward = forward.normalize();
		this.halfAngleRad = (float) Math.toRadians(fovDegrees / 2.0);

		// Build orthonormal basis from forward direction
		Vec3 worldUp = new Vec3(0, 1, 0);
		// Handle edge case where forward is nearly vertical
		if (Math.abs(this.forward.dot(worldUp)) > 0.99) {
			worldUp = new Vec3(0, 0, 1);
		}
		this.right = this.forward.cross(worldUp).normalize();
		this.up = this.right.cross(this.forward).normalize();
	}

	/**
	 * Check if a position is within the camera frustum.
	 */
	public boolean contains(Vec3 pos) {
		Vec3 toTarget = pos.subtract(cameraPos);
		double depth = toTarget.dot(forward);
		if (depth <= 0) return false; // Behind camera

		double horizontalOffset = toTarget.dot(right);
		double verticalOffset = toTarget.dot(up);

		double halfSize = depth * Math.tan(halfAngleRad);
		return Math.abs(horizontalOffset) <= halfSize && Math.abs(verticalOffset) <= halfSize;
	}

	public Vec3 getCameraPos() {
		return cameraPos;
	}
}
