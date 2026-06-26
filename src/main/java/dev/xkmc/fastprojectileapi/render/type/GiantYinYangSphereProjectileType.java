package dev.xkmc.fastprojectileapi.render.type;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.DanmakuRenderStates;
import dev.xkmc.fastprojectileapi.render.core.DisplayType;
import dev.xkmc.fastprojectileapi.render.core.GiantDanmakuScreenOverlay;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;

public record GiantYinYangSphereProjectileType(ResourceLocation overlay, DisplayType display,
											   int segments, int rings, float rotationTicks)
		implements RenderableDanmakuType<GiantYinYangSphereProjectileType, GiantYinYangSphereProjectileType.Ins> {

	@Override
	public boolean hasDepthPrepass() {
		return true;
	}

	@Override
	public void startDepth(MultiBufferSource buffer, List<Ins> list) {
		VertexConsumer vc = buffer.getBuffer(DanmakuRenderStates.danmakuColorSphereDepth());
		for (var e : list) {
			e.render(vc);
		}
	}

	@Override
	public void start(MultiBufferSource buffer, List<Ins> list) {
		VertexConsumer vc = buffer.getBuffer(DanmakuRenderStates.danmakuColorSphere(display()));
		for (var e : list) {
			GiantDanmakuScreenOverlay.accept(overlay, e.color, e.insideScore());
			e.render(vc);
		}
	}

	@Override
	public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose, float pTick) {
		float rot = rotationTicks <= 0 ? 0 : (e.tickCount + pTick) * 360f / rotationTicks;
		pose.mulPose(Axis.YP.rotationDegrees(rot * 0.35f));
		pose.mulPose(Axis.ZP.rotationDegrees(rot));
		int col = DanmakuRenderStates.fading(display, r.color(e, pTick), r, e);
		var m4 = new Matrix4f(pose.last().pose());
		float scale = (float) Math.cbrt(Math.abs(m4.determinant3x3()));
		int seg = segments(scale);
		int ring = rings(scale);
		holder.accept(new Ins(m4, col, seg, ring, scale));
	}

	private int segments(float scale) {
		if (!YHModConfig.CLIENT.adaptiveProjectileMesh.get()) {
			return Mth.clamp(YHModConfig.CLIENT.giantSphereBaseSegments.get(), 16, 64);
		}
		return adaptive(segments, scale, 16, 64);
	}

	private int rings(float scale) {
		if (!YHModConfig.CLIENT.adaptiveProjectileMesh.get()) {
			return Mth.clamp(YHModConfig.CLIENT.giantSphereBaseRings.get(), 8, 32);
		}
		return adaptive(rings, scale, 8, 32);
	}

	private static int adaptive(int base, float scale, int min, int max) {
		float factor = scale < 0.75f ? 0.75f : scale > 1.5f ? 1.5f : 1;
		return Mth.clamp(Math.round(base * factor), min, max);
	}

	public record Ins(Matrix4f m4, int color, int segments, int rings, float scale) {

		private float insideScore() {
			float radius = Math.max(0.0001f, scale * 0.5f);
			float dist = (float) Math.sqrt(m4.m30() * m4.m30() + m4.m31() * m4.m31() + m4.m32() * m4.m32());
			return (radius - dist) / radius;
		}

		public void render(VertexConsumer vc) {
			for (int y = 0; y < rings; y++) {
				float v0 = (float) y / rings;
				float v1 = (float) (y + 1) / rings;
				float theta0 = (float) (Math.PI * v0);
				float theta1 = (float) (Math.PI * v1);
				for (int x = 0; x < segments; x++) {
					float u0 = (float) x / segments;
					float u1 = (float) (x + 1) / segments;
					float phi0 = (float) (Math.PI * 2 * u0);
					float phi1 = (float) (Math.PI * 2 * u1);
					int col = faceColor((theta0 + theta1) * 0.5f, (phi0 + phi1) * 0.5f);
					vertex(vc, theta0, phi0, col);
					vertex(vc, theta0, phi1, col);
					vertex(vc, theta1, phi1, col);
					vertex(vc, theta1, phi0, col);
				}
			}
		}

		private int faceColor(float theta, float phi) {
			float sin = (float) Math.sin(theta);
			float x = sin * (float) Math.cos(phi);
			float y = (float) Math.cos(theta);
			float z = sin * (float) Math.sin(phi);
			return materialColor(isBlack(x, y), x, y, z);
		}

		private static boolean isBlack(float x, float y) {
			boolean black = x < 0;
			float top = x * x + (y - 0.5f) * (y - 0.5f);
			float bottom = x * x + (y + 0.5f) * (y + 0.5f);
			if (top <= 0.25f) black = false;
			if (bottom <= 0.25f) black = true;
			if (top <= 0.015f) black = true;
			if (bottom <= 0.015f) black = false;
			return black;
		}

		private int materialColor(boolean black, float x, float y, float z) {
			int alpha = Math.round(FastColor.ARGB32.alpha(color) * 0.92f);
			float light = Mth.clamp(0.86f + y * 0.12f + z * 0.07f - x * 0.04f, 0.68f, 1.04f);
			float rim = Mth.clamp((float) Math.sqrt(x * x + y * y), 0, 1);
			light *= 1 - rim * 0.10f;
			if (black) {
				int r = Math.round((FastColor.ARGB32.red(color) * 0.055f + 18) * light);
				int g = Math.round((FastColor.ARGB32.green(color) * 0.055f + 18) * light);
				int b = Math.round((FastColor.ARGB32.blue(color) * 0.055f + 22) * light);
				return alpha << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
			}
			int r = Math.round((FastColor.ARGB32.red(color) * 0.88f + 26) * light);
			int g = Math.round((FastColor.ARGB32.green(color) * 0.88f + 26) * light);
			int b = Math.round((FastColor.ARGB32.blue(color) * 0.84f + 34) * light);
			return alpha << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
		}

		private static int clamp(int val) {
			return Mth.clamp(val, 0, 255);
		}

		private void vertex(VertexConsumer vc, float theta, float phi, int col) {
			float sin = (float) Math.sin(theta);
			float x = sin * (float) Math.cos(phi) * 0.5f;
			float y = (float) Math.cos(theta) * 0.5f;
			float z = sin * (float) Math.sin(phi) * 0.5f;
			vc.vertex(m4, x, y, z).color(col).endVertex();
		}

	}
}
