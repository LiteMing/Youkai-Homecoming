package dev.xkmc.fastprojectileapi.render.type;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

public record RotatingProjectileType(ResourceLocation tex, DisplayType display, double rot)
		implements RenderableDanmakuType<RotatingProjectileType, RotatingProjectileType.Ins> {

	@Override
	public void start(MultiBufferSource buffer, List<Ins> list) {
		BulkDataWriter vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.danmaku(tex, display())), list.size());
		ParallelBufferFiller.fill(vc, list, 4, (buf, off, ins) -> ins.texToArray(buf, off));
		vc.flush();
	}

	@Override
	public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose, float pTick) {
		var m4 = pose.last().pose();
		float scale = (float) Math.cbrt(Math.abs(m4.determinant3x3()));
		float zAngle = (float) Math.toRadians((e.tickCount + pTick) * 360f / (float) rot);
		int col = DanmakuRenderStates.fading(display, -1, r, e);
		holder.accept(new Ins(m4.m30(), m4.m31(), m4.m32(), scale, zAngle, col));
	}

	public record Ins(float tx, float ty, float tz, float scale, float zAngle, int color) {

		public void tex(BulkDataWriter vc) {
			var m4 = new Matrix4f().translation(tx, ty, tz).scale(scale)
					.rotate(new org.joml.Quaternionf().rotateZ(zAngle));
			vertex(vc, m4, 1, 1, 1, 0, color);
			vertex(vc, m4, 1, 0, 1, 1, color);
			vertex(vc, m4, 0, 0, 0, 1, color);
			vertex(vc, m4, 0, 1, 0, 0, color);
		}

		public void texToArray(byte[] buf, int off) {
			int s = BulkDataWriter.STRIDE;
			// Inline: translate + scale + rotateZ
			float c = (float) Math.cos(zAngle);
			float sn = (float) Math.sin(zAngle);
			vertexRotated(buf, off, 0.5f, 0.5f, 1, 0, c, sn);
			vertexRotated(buf, off + s, 0.5f, -0.5f, 1, 1, c, sn);
			vertexRotated(buf, off + s * 2, -0.5f, -0.5f, 0, 1, c, sn);
			vertexRotated(buf, off + s * 3, -0.5f, 0.5f, 0, 0, c, sn);
		}

		private void vertexRotated(byte[] buf, int off, float lx, float ly, float u, float v, float c, float sn) {
			float rx = (lx * c - ly * sn) * scale + tx;
			float ry = (lx * sn + ly * c) * scale + ty;
			float rz = tz;
			BulkDataWriter.writeVertex(buf, off, rx, ry, rz, u, v, color);
		}

		private static void vertex(BulkDataWriter vc, Matrix4f m4, float x, int y, int u, int v, int color) {
			vc.addVertex(m4, x - 0.5F, y - 0.5F, 0.0F, u, v, color);
		}

	}
}
