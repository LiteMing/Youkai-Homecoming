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

import java.util.List;
import java.util.function.Consumer;

public record LayeredRotatingProjectileType(ResourceLocation tintTex, ResourceLocation whiteTex, DisplayType display,
											double rot)
		implements RenderableDanmakuType<LayeredRotatingProjectileType, LayeredRotatingProjectileType.Ins> {

	@Override
	public void start(MultiBufferSource buffer, List<Ins> list) {
		writeLayer(buffer, tintTex, list, true);
		writeLayer(buffer, whiteTex, list, false);
	}

	private void writeLayer(MultiBufferSource buffer, ResourceLocation tex, List<Ins> list, boolean tint) {
		BulkDataWriter vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.danmaku(tex, display())),
				list.size());
		ParallelBufferFiller.fill(vc, list, 4, (buf, off, ins) -> ins.texToArray(buf, off, tint));
		vc.flush();
	}

	@Override
	public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose,
					   float pTick) {
		var m4 = pose.last().pose();
		float scale = (float) Math.cbrt(Math.abs(m4.determinant3x3()));
		float zAngle = (float) Math.toRadians((e.tickCount + pTick) * 360f / (float) rot);
		int tint = DanmakuRenderStates.fading(display, r.color(e, pTick), r, e);
		int white = (tint & 0xff000000) | 0xffffff;
		holder.accept(new Ins(m4.m30(), m4.m31(), m4.m32(), scale, zAngle, tint, white));
	}

	public record Ins(float tx, float ty, float tz, float scale, float zAngle, int tintColor, int whiteColor) {

		public void texToArray(byte[] buf, int off, boolean tint) {
			int color = tint ? tintColor : whiteColor;
			int s = BulkDataWriter.STRIDE;
			float c = (float) Math.cos(zAngle);
			float sn = (float) Math.sin(zAngle);
			vertexRotated(buf, off, 0.5f, 0.5f, 1, 0, c, sn, color);
			vertexRotated(buf, off + s, 0.5f, -0.5f, 1, 1, c, sn, color);
			vertexRotated(buf, off + s * 2, -0.5f, -0.5f, 0, 1, c, sn, color);
			vertexRotated(buf, off + s * 3, -0.5f, 0.5f, 0, 0, c, sn, color);
		}

		private void vertexRotated(byte[] buf, int off, float lx, float ly, float u, float v, float c, float sn,
								   int color) {
			float rx = (lx * c - ly * sn) * scale + tx;
			float ry = (lx * sn + ly * c) * scale + ty;
			BulkDataWriter.writeVertex(buf, off, rx, ry, tz, u, v, color);
		}

	}
}
