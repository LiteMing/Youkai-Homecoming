package dev.xkmc.fastprojectileapi.render.type;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.BulkDataWriter;
import dev.xkmc.fastprojectileapi.render.core.DanmakuRenderStates;
import dev.xkmc.fastprojectileapi.render.core.DisplayType;
import dev.xkmc.fastprojectileapi.render.core.ParallelBufferFiller;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;

public record SimpleProjectileType(ResourceLocation tex, DisplayType display)
		implements RenderableDanmakuType<SimpleProjectileType, SimpleProjectileType.Ins> {

	@Override
	public void start(MultiBufferSource buffer, List<Ins> list) {
		BulkDataWriter vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.danmaku(tex, display)), list.size());
		ParallelBufferFiller.fill(vc, list, 4, (buf, off, ins) -> ins.texToArray(buf, off));
		vc.flush();
	}

	@Override
	public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose, float pTick) {
		// Deferred pose calculation: extract raw translation + scale from pose,
		// defer matrix construction to parallel texToArray phase.
		var m4 = pose.last().pose();
		float scale = (float) Math.cbrt(Math.abs(m4.determinant3x3()));
		int col = DanmakuRenderStates.fading(display, -1, r, e);
		holder.accept(new Ins(m4.m30(), m4.m31(), m4.m32(), scale, col));
	}

	/**
	 * Lightweight Ins storing raw parameters instead of a pre-computed Matrix4f.
	 * Matrix construction (translate + scale) is deferred to texToArray for parallel execution.
	 */
	public record Ins(float tx, float ty, float tz, float scale, int color) {

		public void tex(BulkDataWriter vc) {
			// Reconstruct Matrix4f for legacy path
			var m4 = new Matrix4f().translation(tx, ty, tz).scale(scale);
			vertex(vc, m4, 1, 1, 1, 0, color);
			vertex(vc, m4, 1, 0, 1, 1, color);
			vertex(vc, m4, 0, 0, 0, 1, color);
			vertex(vc, m4, 0, 1, 0, 0, color);
		}

		public void texToArray(byte[] buf, int off) {
			int s = BulkDataWriter.STRIDE;
			// Inline vertex transform: pos = (localPos * scale) + translation
			// No Matrix4f construction needed — just 3 multiply-adds per vertex
			vertexDirect(buf, off, 0.5f, 0.5f, color);
			vertexDirect(buf, off + s, 0.5f, -0.5f, color);
			vertexDirect(buf, off + s * 2, -0.5f, -0.5f, color);
			vertexDirect(buf, off + s * 3, -0.5f, 0.5f, color);
		}

		private void vertexDirect(byte[] buf, int off, float lx, float ly, int color) {
			// vertex = (lx * scale + tx, ly * scale + ty, 0 * scale + tz)
			// UV: lx maps 0.5→1, -0.5→0; ly maps 0.5→0, -0.5→1
			float wx = lx * scale + tx;
			float wy = ly * scale + ty;
			float wz = tz;
			float u = lx + 0.5f;
			float v = 0.5f - ly;
			BulkDataWriter.writeVertex(buf, off, wx, wy, wz, u, v, color);
		}

		private static void vertex(BulkDataWriter vc, Matrix4f m4, float x, int y, int u, int v, int color) {
			vc.addVertex(m4, x - 0.5F, y - 0.5F, 0.0F, u, v, color);
		}

	}
}
