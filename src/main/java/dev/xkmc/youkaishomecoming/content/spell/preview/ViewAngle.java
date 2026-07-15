package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

public enum ViewAngle {
	FRONT("Front (XY)", 0, 180),
	SIDE("Side (ZY)", 0, 90),
	TOP("Top (XZ)", 90, 180);

	private final String label;
	private final float xRotDeg;
	private final float yRotDeg;

	ViewAngle(String label, float xRotDeg, float yRotDeg) {
		this.label = label;
		this.xRotDeg = xRotDeg;
		this.yRotDeg = yRotDeg;
	}

	/**
	 * Apply the view rotation to a PoseStack.
	 */
	public void applyRotation(PoseStack ps) {
		applyRotation(ps, xRotDeg, yRotDeg);
	}

	public static void applyRotation(PoseStack ps, float xRot, float yRot) {
		ps.mulPose(Axis.XP.rotationDegrees(xRot));
		ps.mulPose(Axis.YP.rotationDegrees(yRot));
	}

	/**
	 * Billboard orientation quaternion matching Camera.rotation() convention.
	 */
	public Quaternionf getOrientation() {
		return computeOrientation(xRotDeg, yRotDeg);
	}

	public static Quaternionf computeOrientation(float xRot, float yRot) {
		return new Quaternionf().rotationYXZ(
				-yRot * Mth.DEG_TO_RAD,
				xRot * Mth.DEG_TO_RAD,
				0);
	}

	public float getXRot() {
		return xRotDeg;
	}

	public float getYRot() {
		return yRotDeg;
	}

	public String getLabel() {
		return label;
	}
}
