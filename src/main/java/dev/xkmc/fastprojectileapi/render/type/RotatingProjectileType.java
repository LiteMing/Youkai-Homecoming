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
		var sim4 = new Matrix4f(pose.last().pose());
		sim4.set3x3(new Matrix4f().scale((float) Math.cbrt(Math.abs(sim4.determinant3x3()))));
		var q4 = Axis.ZP.rotationDegrees((e.tickCount + pTick) * 360f / (float) rot);
		sim4.rotate(q4);
		int col = DanmakuRenderStates.fading(display, -1, r, e);
		holder.accept(new Ins(sim4, col));
	}

	public record Ins(Matrix4f m4, int color) {

		public void tex(BulkDataWriter vc) {
			vertex(vc, m4, 1, 1, 1, 0, color);
			vertex(vc, m4, 1, 0, 1, 1, color);
			vertex(vc, m4, 0, 0, 0, 1, color);
			vertex(vc, m4, 0, 1, 0, 0, color);
		}

		public void texToArray(byte[] buf, int off) {
			int s = BulkDataWriter.STRIDE;
			vertexToArray(buf, off, m4, 1, 1, 1, 0, color);
			vertexToArray(buf, off + s, m4, 1, 0, 1, 1, color);
			vertexToArray(buf, off + s * 2, m4, 0, 0, 0, 1, color);
			vertexToArray(buf, off + s * 3, m4, 0, 1, 0, 0, color);
		}

		private static void vertex(BulkDataWriter vc, Matrix4f m4, float x, int y, int u, int v, int color) {
			vc.addVertex(m4, x - 0.5F, y - 0.5F, 0.0F, u, v, color);
		}

		private static void vertexToArray(byte[] buf, int off, Matrix4f m4, float x, int y, int u, int v, int color) {
			BulkDataWriter.writeVertex(buf, off, m4, x - 0.5F, y - 0.5F, 0.0F, u, v, color);
		}

	}
}
