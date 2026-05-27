package dev.xkmc.fastprojectileapi.render.type;

import com.mojang.blaze3d.vertex.PoseStack;
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
import org.joml.Vector4f;

import java.util.List;
import java.util.function.Consumer;

public record CylinderLaserType(ResourceLocation inner, ResourceLocation outer, int color, int segments)
		implements RenderableProjectileType<CylinderLaserType, CylinderLaserType.Ins> {

	@Override
	public int order() {
		return 10;
	}

	@Override
	public void start(MultiBufferSource buffer, List<Ins> list) {
		boolean additive = YHModConfig.CLIENT.laserRenderAdditive.get();
		boolean invert = YHModConfig.CLIENT.laserRenderInverted.get();
		int n = 0;
		for (var e : list) {
			n += e.cache.r0.length / 2;
		}
		BulkDataWriter vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.laserCore(inner, DisplayType.TRANSPARENT)), n);
		for (var e : list) {
			e.tex(vc, false, e.core, e.cache.r0);
		}
		vc.flush();
		if (invert || !additive) {
			vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.laser(inner, DisplayType.TRANSPARENT)), n);
			for (var e : list) {
				e.tex(vc, invert, e.tran, e.cache.r1);
			}
			vc.flush();
		}
		if (additive) {
			vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.laser(outer, DisplayType.ADDITIVE)), n);
			for (var e : list) {
				e.tex(vc, false, e.add, e.cache.r1);
			}
			vc.flush();
		}
	}

	@Override
	public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose, float pTick) {
		double fade = r.fading(e);
		double tran = fade * YHModConfig.CLIENT.laserTransparency.get();
		int core = (int) (fade * 0xff) << 24 | 0xffffff;
		int outer = (int) (tran * 0xff) << 24 | (color & 0xffffff);
		int add = (int) ((color & 0xff) * tran) |
				(int) ((color >> 8 & 0xff) * tran) << 8 |
				(int) ((color >> 16 & 0xff) * tran) << 16 | 0xff000000;
		float scale = (float) Math.cbrt(Math.abs(pose.last().pose().determinant3x3()));
		int seg = adaptive(segments, scale);
		holder.accept(new Ins(Cache.vertex(pose.last().pose(), seg), core, outer, add));
	}

	private static int adaptive(int base, float scale) {
		if (!YHModConfig.CLIENT.adaptiveProjectileMesh.get()) return Mth.clamp(base, 4, 24);
		float factor = scale < 0.75f ? 0.75f : scale > 1.5f ? 1.5f : 1;
		return Mth.clamp(Math.round(base * factor), 4, 24);
	}

	public record Ins(Cache cache, int core, int tran, int add) {

		public void tex(BulkDataWriter vc, boolean invert, int color, float[][] arr) {
			int n = arr.length / 2;
			for (int i = 0; i < n; i++) {
				int next = (i + 1) % n;
				if (invert) {
					addVertex(vc, color, arr[next + n], 0, 0);
					addVertex(vc, color, arr[next], 0, 1);
					addVertex(vc, color, arr[i], 1, 1);
					addVertex(vc, color, arr[i + n], 1, 0);
				} else {
					addVertex(vc, color, arr[i + n], 1, 0);
					addVertex(vc, color, arr[i], 1, 1);
					addVertex(vc, color, arr[next], 0, 1);
					addVertex(vc, color, arr[next + n], 0, 0);
				}
			}
		}

		private void addVertex(BulkDataWriter vc, int col, float[] arr, float u, float v) {
			vc.addVertex(arr[0], arr[1], arr[2], u, v, col);
		}

	}

	public record Cache(float[][] r0, float[][] r1) {

		private static Cache vertex(Matrix4f mat, int segments) {
			var p0 = new Vector4f(0, 0, 0, 1).mul(mat);
			var px = new Vector4f(1, 0, 0, 0).mul(mat);
			var py = new Vector4f(0, 1, 0, 0).mul(mat);
			var pz = new Vector4f(0, 0, 1, 0).mul(mat);
			var ans = new Cache(new float[segments * 2][3], new float[segments * 2][3]);
			fill(ans.r0, p0, px, py, pz, segments, 0.167f);
			fill(ans.r1, p0, px, py, pz, segments, 0.5f);
			return ans;
		}

		private static void fill(float[][] arr, Vector4f p0, Vector4f px, Vector4f py, Vector4f pz, int segments, float radius) {
			for (int i = 0; i < segments; i++) {
				double a = Math.PI * 2 * i / segments;
				float sx = (float) Math.cos(a) * radius;
				float sz = (float) Math.sin(a) * radius;
				calc(arr[i], p0, px, pz, py, sx, sz, 0);
				calc(arr[i + segments], p0, px, pz, py, sx, sz, 1);
			}
		}

		private static void calc(float[] arr, Vector4f p0, Vector4f px, Vector4f pz, Vector4f py, float sx, float sz, float dy) {
			arr[0] = p0.x + px.x * sx + pz.x * sz + py.x * dy;
			arr[1] = p0.y + px.y * sx + pz.y * sz + py.y * dy;
			arr[2] = p0.z + px.z * sx + pz.z * sz + py.z * dy;
		}

	}
}
