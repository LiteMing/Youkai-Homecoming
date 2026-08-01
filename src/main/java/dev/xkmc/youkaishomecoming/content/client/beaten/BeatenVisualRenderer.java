package dev.xkmc.youkaishomecoming.content.client.beaten;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Original world-space rendering based on the timing vocabulary used by open-source
 * Touhou fangames: a short charge, radial petals, a clear wave, then a landing echo.
 */
final class BeatenVisualRenderer {

	private static final int SEGMENTS = 48;
	private static final int PETALS = 24;

	static void renderTranslucent(PoseStack pose, VertexConsumer out, BeatenVisual visual, float progress) {
		if (visual.kind != BeatenVisual.Kind.DEFEAT) return;
		Matrix4f mat = pose.last().pose();
		float travel = easeOutCubic(saturate(progress / 0.82f));
		float fade = 1f - smoothstep(0.48f, 1f, progress);

		for (int i = 0; i < PETALS; i++) {
			float variance = hash(visual.seed, i);
			float angle = visual.seed + i * Mth.TWO_PI / PETALS + (variance - 0.5f) * 0.28f;
			float distance = visual.scale * (0.16f + travel * (1.3f + variance * 1.25f));
			float length = visual.scale * (0.2f + 0.28f * hash(visual.seed + 2.3f, i));
			float width = length * (0.18f + 0.12f * hash(visual.seed + 5.1f, i));
			float spin = angle + travel * (variance - 0.5f) * 2.4f;
			float x = Mth.cos(angle) * distance;
			float y = Mth.sin(angle) * distance;
			float alpha = fade * (0.42f + 0.32f * variance);
			int palette = i % 3;
			float r = palette == 0 ? 0.22f : palette == 1 ? 0.58f : 0.76f;
			float g = palette == 0 ? 0.34f : palette == 1 ? 0.25f : 0.68f;
			float b = palette == 0 ? 0.72f : palette == 1 ? 0.72f : 0.92f;
			petal(mat, out, x, y, spin, length, width, r, g, b, alpha);
		}
	}

	static void renderAdditive(PoseStack pose, VertexConsumer out, BeatenVisual visual, float progress) {
		Matrix4f mat = pose.last().pose();
		if (visual.kind == BeatenVisual.Kind.LANDING) {
			float wave = easeOutCubic(progress);
			float alpha = Mth.sin(Mth.PI * progress) * 0.72f;
			ring(mat, out, visual.scale * (0.2f + 1.5f * wave), visual.scale * 0.08f * (1f - progress),
					0.56f, 0.82f, 1f, alpha);
			return;
		}

		float flash = 1f - smoothstep(0.05f, 0.32f, progress);
		float flashRadius = visual.scale * (0.18f + 0.95f * easeOutCubic(saturate(progress / 0.32f)));
		softDisc(mat, out, flashRadius, 0.9f, 0.96f, 1f, flash * 0.9f);

		clearWave(mat, out, visual, progress, 0.02f, 2.7f, 0.85f);
		clearWave(mat, out, visual, progress, 0.16f, 2.15f, 0.58f);

		float halo = 1f - smoothstep(0.2f, 0.85f, progress);
		ring(mat, out, visual.scale * (0.34f + 0.18f * Mth.sin(progress * Mth.TWO_PI)),
				visual.scale * 0.07f, 0.75f, 0.9f, 1f, halo * 0.72f);
	}

	private static void clearWave(Matrix4f mat, VertexConsumer out, BeatenVisual visual, float progress,
			float delay, float reach, float intensity) {
		float p = saturate((progress - delay) / (0.72f - delay));
		if (p <= 0f || p >= 1f) return;
		float radius = visual.scale * (0.2f + reach * easeOutCubic(p));
		float width = visual.scale * (0.12f * (1f - p) + 0.025f);
		float alpha = Mth.sin(Mth.PI * p) * intensity;
		ring(mat, out, radius, width, 0.66f, 0.9f, 1f, alpha);
	}

	private static void petal(Matrix4f mat, VertexConsumer out, float x, float y, float angle,
			float length, float width, float r, float g, float b, float alpha) {
		float fx = Mth.cos(angle), fy = Mth.sin(angle);
		float sx = -fy * width, sy = fx * width;
		float tx = x - fx * length * 0.45f, ty = y - fy * length * 0.45f;
		float hx = x + fx * length * 0.55f, hy = y + fy * length * 0.55f;
		vertex(out, mat, hx, hy, r, g, b, 0f);
		vertex(out, mat, x + sx, y + sy, r, g, b, alpha);
		vertex(out, mat, tx, ty, r * 0.65f, g * 0.65f, b * 0.75f, alpha * 0.35f);
		vertex(out, mat, x - sx, y - sy, r, g, b, alpha);
	}

	private static void softDisc(Matrix4f mat, VertexConsumer out, float radius,
			float r, float g, float b, float alpha) {
		for (int i = 0; i < SEGMENTS; i++) {
			float a1 = i * Mth.TWO_PI / SEGMENTS;
			float a2 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			vertex(out, mat, 0, 0, r, g, b, alpha);
			vertex(out, mat, Mth.cos(a1) * radius, Mth.sin(a1) * radius, r, g, b, 0);
			vertex(out, mat, Mth.cos(a2) * radius, Mth.sin(a2) * radius, r, g, b, 0);
			vertex(out, mat, 0, 0, r, g, b, alpha);
		}
	}

	private static void ring(Matrix4f mat, VertexConsumer out, float radius, float width,
			float r, float g, float b, float alpha) {
		if (width <= 0 || alpha <= 0) return;
		float inner = Math.max(0, radius - width);
		float outer = radius + width;
		for (int i = 0; i < SEGMENTS; i++) {
			float a1 = i * Mth.TWO_PI / SEGMENTS;
			float a2 = (i + 1) * Mth.TWO_PI / SEGMENTS;
			float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			float c2 = Mth.cos(a2), s2 = Mth.sin(a2);
			vertex(out, mat, c1 * inner, s1 * inner, r, g, b, 0);
			vertex(out, mat, c1 * radius, s1 * radius, r, g, b, alpha);
			vertex(out, mat, c2 * radius, s2 * radius, r, g, b, alpha);
			vertex(out, mat, c2 * inner, s2 * inner, r, g, b, 0);
			vertex(out, mat, c1 * radius, s1 * radius, r, g, b, alpha);
			vertex(out, mat, c1 * outer, s1 * outer, r, g, b, 0);
			vertex(out, mat, c2 * outer, s2 * outer, r, g, b, 0);
			vertex(out, mat, c2 * radius, s2 * radius, r, g, b, alpha);
		}
	}

	private static float hash(float seed, int index) {
		float value = Mth.sin(seed * 12.9898f + index * 78.233f) * 43758.547f;
		return value - Mth.floor(value);
	}

	private static float saturate(float value) {
		return Mth.clamp(value, 0f, 1f);
	}

	private static float easeOutCubic(float value) {
		float inv = 1f - saturate(value);
		return 1f - inv * inv * inv;
	}

	private static float smoothstep(float edge0, float edge1, float value) {
		float t = saturate((value - edge0) / (edge1 - edge0));
		return t * t * (3f - 2f * t);
	}

	private static void vertex(VertexConsumer out, Matrix4f mat, float x, float y,
			float r, float g, float b, float a) {
		out.vertex(mat, x, y, 0).color(r, g, b, a).endVertex();
	}

	private BeatenVisualRenderer() {
	}
}
