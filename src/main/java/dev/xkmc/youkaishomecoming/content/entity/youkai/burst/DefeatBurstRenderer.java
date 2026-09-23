package dev.xkmc.youkaishomecoming.content.entity.youkai.burst;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Draws the Touhou-style defeat burst as flat-colored billboard geometry,
 * mirroring the original games' untextured polygon effects: a pinwheel of dark
 * petals scattering outward, two counter-rotating spiky star flowers, a white
 * core flash and expanding shockwave rings. Everything is derived from the
 * normalized age {@code t} in [0, 1); no allocation happens per frame.
 */
@OnlyIn(Dist.CLIENT)
public class DefeatBurstRenderer {

	private static final int PETALS = 12;
	private static final int GOLD_SPIKES = 16;
	private static final int TEAL_SPIKES = 12;
	private static final int RING_SEGMENTS = 40;
	private static final int FAN_SEGMENTS = 16;

	/** Pass 1, alpha blend: dark scatter petals radiating from the center. */
	public static void renderPetals(PoseStack pose, VertexConsumer vc, DefeatBurst b, float t) {
		Matrix4f mat = pose.last().pose();
		float grow = easeOut3(t);
		float reach = 2.2f * b.scale;
		float r0 = 0.12f * reach * grow;
		float r1 = reach * grow;
		float w0 = 0.16f * reach * grow;
		float w1 = 0.35f * w0;
		float skew = 0.9f * w0;
		float fadeIn = Math.min(1, t * 8);
		float alpha = 0.45f * (float) Math.pow(1 - t, 1.4) * fadeIn;
		float spin = b.seed + t * 0.9f;
		for (int k = 0; k < PETALS; k++) {
			float phi = spin + k * Mth.TWO_PI / PETALS;
			float cos = Mth.cos(phi), sin = Mth.sin(phi);
			float tx = -sin, ty = cos;
			boolean even = (k & 1) == 0;
			float r = even ? 0.07f : 0.12f;
			float g = even ? 0.09f : 0.15f;
			float bl = even ? 0.28f : 0.38f;
			vertex(mat, vc, cos * r0 + tx * w0, sin * r0 + ty * w0, r, g, bl, alpha);
			vertex(mat, vc, cos * r0 - tx * w0, sin * r0 - ty * w0, r, g, bl, alpha);
			vertex(mat, vc, cos * r1 + tx * (skew - w1), sin * r1 + ty * (skew - w1), r, g, bl, 0);
			vertex(mat, vc, cos * r1 + tx * (skew + w1), sin * r1 + ty * (skew + w1), r, g, bl, 0);
		}
	}

	/** Pass 2, additive: shockwave rings, star flower layers, core flash. */
	public static void renderFlash(PoseStack pose, VertexConsumer vc, DefeatBurst b, float t) {
		Matrix4f mat = pose.last().pose();
		ring(mat, vc, b, t, 0.00f, 1.00f, 0.92f, 0.72f);
		ring(mat, vc, b, t, 0.12f, 0.60f, 0.95f, 0.80f);

		float goldGrow = easeOut3(Math.min(1, t / 0.65f));
		float goldFade = t < 0.55f ? 1 : 1 - (t - 0.55f) / 0.45f;
		star(mat, vc, GOLD_SPIKES, 1.45f * b.scale * goldGrow, 0.32f,
				b.seed * 1.7f - t * 1.6f, goldFade,
				1.00f, 0.78f, 0.36f, 0.75f,
				1.00f, 0.92f, 0.62f, 0.45f);

		float tealGrow = easeOut3(Math.min(1, t / 0.5f));
		float tealFade = t < 0.45f ? 1 : Math.max(0, 1 - (t - 0.45f) / 0.45f);
		star(mat, vc, TEAL_SPIKES, 0.85f * b.scale * tealGrow, 0.30f,
				-b.seed + t * 2.3f, tealFade,
				0.42f, 0.95f, 0.68f, 0.70f,
				0.75f, 1.00f, 0.90f, 0.40f);

		float tc = t / 0.28f;
		if (tc < 1) {
			float radius = 0.55f * b.scale * easeOut3(Math.min(1, tc * 2.2f));
			float alpha = 0.95f * (float) Math.pow(1 - tc, 1.6);
			fan(mat, vc, radius, b.seed, 1, 1, 1, alpha, 1, 1, 1, 0);
		}
	}

	/** One expanding shockwave ring, soft on both edges. */
	private static void ring(Matrix4f mat, VertexConsumer vc, DefeatBurst b, float t,
			float delay, float r, float g, float bl) {
		float tw = (t - delay) / 0.55f;
		if (tw <= 0 || tw >= 1) return;
		float radius = 2.6f * b.scale * easeOut3(tw);
		float half = b.scale * (0.05f + 0.12f * tw);
		float peak = 0.5f * (1 - tw) * (1 - tw);
		for (int i = 0; i < RING_SEGMENTS; i++) {
			float a1 = b.seed + i * Mth.TWO_PI / RING_SEGMENTS;
			float a2 = b.seed + (i + 1) * Mth.TWO_PI / RING_SEGMENTS;
			ringRow(mat, vc, a1, a2, radius - half, radius, 0, peak, r, g, bl);
			ringRow(mat, vc, a1, a2, radius, radius + half, peak, 0, r, g, bl);
		}
	}

	private static void ringRow(Matrix4f mat, VertexConsumer vc, float a1, float a2,
			float rIn, float rOut, float alphaIn, float alphaOut, float r, float g, float bl) {
		float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
		float c2 = Mth.cos(a2), s2 = Mth.sin(a2);
		vertex(mat, vc, c1 * rIn, s1 * rIn, r, g, bl, alphaIn);
		vertex(mat, vc, c2 * rIn, s2 * rIn, r, g, bl, alphaIn);
		vertex(mat, vc, c2 * rOut, s2 * rOut, r, g, bl, alphaOut);
		vertex(mat, vc, c1 * rOut, s1 * rOut, r, g, bl, alphaOut);
	}

	/** Spiky star flower: alternating long/short spikes around an inner glow disc. */
	private static void star(Matrix4f mat, VertexConsumer vc, int spikes, float radius, float innerRatio,
			float rot, float fade,
			float br, float bg, float bb, float baseAlpha,
			float tr, float tg, float tb, float tipAlpha) {
		if (radius <= 0 || fade <= 0) return;
		float ri = innerRatio * radius;
		float delta = Mth.PI / spikes * 0.9f;
		float baseA = baseAlpha * fade;
		float tipA = tipAlpha * fade;
		for (int k = 0; k < spikes; k++) {
			float phi = rot + k * Mth.TWO_PI / spikes;
			float ro = radius * ((k & 1) == 0 ? 1 : 0.68f);
			float a1 = phi - delta, a2 = phi + delta;
			float e1 = phi - delta * 0.1f, e2 = phi + delta * 0.1f;
			vertex(mat, vc, Mth.cos(a1) * ri, Mth.sin(a1) * ri, br, bg, bb, baseA);
			vertex(mat, vc, Mth.cos(a2) * ri, Mth.sin(a2) * ri, br, bg, bb, baseA);
			vertex(mat, vc, Mth.cos(e2) * ro, Mth.sin(e2) * ro, tr, tg, tb, tipA);
			vertex(mat, vc, Mth.cos(e1) * ro, Mth.sin(e1) * ro, tr, tg, tb, tipA);
		}
		fan(mat, vc, ri, rot, br, bg, bb, 0.7f * baseA, tr, tg, tb, 0.35f * baseA);
	}

	/** Filled disc as a degenerate-quad triangle fan, soft toward the edge. */
	private static void fan(Matrix4f mat, VertexConsumer vc, float radius, float rot,
			float cr, float cg, float cb, float ca,
			float er, float eg, float eb, float ea) {
		if (radius <= 0 || ca <= 0) return;
		for (int i = 0; i < FAN_SEGMENTS; i++) {
			float a1 = rot + i * Mth.TWO_PI / FAN_SEGMENTS;
			float a2 = rot + (i + 1) * Mth.TWO_PI / FAN_SEGMENTS;
			vertex(mat, vc, 0, 0, cr, cg, cb, ca);
			vertex(mat, vc, 0, 0, cr, cg, cb, ca);
			vertex(mat, vc, Mth.cos(a2) * radius, Mth.sin(a2) * radius, er, eg, eb, ea);
			vertex(mat, vc, Mth.cos(a1) * radius, Mth.sin(a1) * radius, er, eg, eb, ea);
		}
	}

	private static void vertex(Matrix4f mat, VertexConsumer vc, float x, float y,
			float r, float g, float b, float a) {
		vc.vertex(mat, x, y, 0).color(r, g, b, a).endVertex();
	}

	private static float easeOut3(float x) {
		float inv = 1 - x;
		return 1 - inv * inv * inv;
	}

}
