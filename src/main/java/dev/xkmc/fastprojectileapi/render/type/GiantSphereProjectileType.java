package dev.xkmc.fastprojectileapi.render.type;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.BulkDataWriter;
import dev.xkmc.fastprojectileapi.render.core.DanmakuRenderStates;
import dev.xkmc.fastprojectileapi.render.core.DisplayType;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;

public record GiantSphereProjectileType(ResourceLocation tex, DisplayType display, int segments, int rings, float rotationTicks)
		implements RenderableDanmakuType<GiantSphereProjectileType, GiantSphereProjectileType.Ins> {

	@Override
	public void start(MultiBufferSource buffer, List<Ins> list) {
		int quads = 0;
		for (var e : list) {
			quads += e.segments * e.rings;
		}
		BulkDataWriter vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.danmaku(tex, display())), quads);
		for (var e : list) {
			e.tex(vc);
		}
		vc.flush();
	}

	@Override
	public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose, float pTick) {
		float rot = rotationTicks <= 0 ? 0 : (e.tickCount + pTick) * 360f / rotationTicks;
		pose.mulPose(Axis.YP.rotationDegrees(rot));
		pose.mulPose(Axis.XP.rotationDegrees(rot * 0.35f));
		int col = DanmakuRenderStates.fading(display, r.color(e, pTick), r, e);
		var m4 = new Matrix4f(pose.last().pose());
		float scale = (float) Math.cbrt(Math.abs(m4.determinant3x3()));
		int seg = segments(scale);
		int ring = rings(scale);
		holder.accept(new Ins(m4, col, seg, ring));
	}

	private int segments(float scale) {
		if (!YHModConfig.CLIENT.adaptiveProjectileMesh.get()) {
			return Mth.clamp(YHModConfig.CLIENT.giantSphereBaseSegments.get(), 8, 32);
		}
		return adaptive(segments, scale, 8, 32);
	}

	private int rings(float scale) {
		if (!YHModConfig.CLIENT.adaptiveProjectileMesh.get()) {
			return Mth.clamp(YHModConfig.CLIENT.giantSphereBaseRings.get(), 4, 16);
		}
		return adaptive(rings, scale, 4, 16);
	}

	private static int adaptive(int base, float scale, int min, int max) {
		float factor = scale < 0.75f ? 0.75f : scale > 1.5f ? 1.5f : 1;
		return Mth.clamp(Math.round(base * factor), min, max);
	}

	public record Ins(Matrix4f m4, int color, int segments, int rings) {

		public void tex(BulkDataWriter vc) {
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
					vertex(vc, theta0, phi1, u1, v0);
					vertex(vc, theta0, phi0, u0, v0);
					vertex(vc, theta1, phi0, u0, v1);
					vertex(vc, theta1, phi1, u1, v1);
				}
			}
		}

		private void vertex(BulkDataWriter vc, float theta, float phi, float u, float v) {
			float sin = (float) Math.sin(theta);
			float x = sin * (float) Math.cos(phi) * 0.5f;
			float y = (float) Math.cos(theta) * 0.5f;
			float z = sin * (float) Math.sin(phi) * 0.5f;
			vc.addVertex(m4, x, y, z, u, v, color);
		}

	}
}
