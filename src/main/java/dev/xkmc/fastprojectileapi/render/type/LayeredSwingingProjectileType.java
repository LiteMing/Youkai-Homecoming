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
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;

public record LayeredSwingingProjectileType(ResourceLocation tintTex, ResourceLocation whiteTex, DisplayType display,
											float rotationsPerBlock, float tiltAngle, float size)
		implements RenderableDanmakuType<LayeredSwingingProjectileType, LayeredSwingingProjectileType.Ins> {

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
		pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(pTick, e.yRotO, e.getYRot())));
		pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pTick, e.xRotO, e.getXRot())));

		double speed = e.getDeltaMovement().length();
		double distance = (e.tickCount + pTick) * speed;
		pose.mulPose(Axis.ZP.rotationDegrees((float) (distance * rotationsPerBlock * 360f)));
		pose.mulPose(Axis.XP.rotationDegrees(tiltAngle));
		pose.scale(size, size, size);

		int tint = DanmakuRenderStates.fading(display, r.color(e, pTick), r, e);
		int white = (tint & 0xff000000) | 0xffffff;
		holder.accept(new Ins(new Matrix4f(pose.last().pose()), tint, white));
	}

	public record Ins(Matrix4f m4, int tintColor, int whiteColor) {

		public void texToArray(byte[] buf, int off, boolean tint) {
			int color = tint ? tintColor : whiteColor;
			int s = BulkDataWriter.STRIDE;
			vertexToArray(buf, off, m4, 1, 1, 1, 0, color);
			vertexToArray(buf, off + s, m4, 1, 0, 1, 1, color);
			vertexToArray(buf, off + s * 2, m4, 0, 0, 0, 1, color);
			vertexToArray(buf, off + s * 3, m4, 0, 1, 0, 0, color);
		}

		private static void vertexToArray(byte[] buf, int off, Matrix4f m4, float x, int y, int u, int v, int color) {
			BulkDataWriter.writeVertex(buf, off, m4, x - 0.5F, 0.0F, y - 0.5F, u, v, color);
		}

	}
}
