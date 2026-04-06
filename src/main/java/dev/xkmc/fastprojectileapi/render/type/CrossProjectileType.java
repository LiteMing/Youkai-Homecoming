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

/**
 * Cross-shaped projectile type similar to Minecraft saplings.
 * Renders two perpendicular planes intersecting along the flight axis.
 * Good for scale and kunai bullets.
 */
public record CrossProjectileType(ResourceLocation tex, DisplayType display)
        implements RenderableDanmakuType<CrossProjectileType, CrossProjectileType.Ins> {

    @Override
    public void start(MultiBufferSource buffer, List<Ins> list) {
        // Each instance renders 2 quads (cross shape) = 8 vertices
        BulkDataWriter vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.danmaku(tex, display())),
                list.size() * 2);
        ParallelBufferFiller.fill(vc, list, 8, (buf, off, ins) -> ins.texToArray(buf, off));
        vc.flush();
    }

    @Override
    public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose,
            float pTick) {
        // Face the flight direction
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(pTick, e.yRotO, e.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pTick, e.xRotO, e.getXRot())));

        int col = DanmakuRenderStates.fading(display, -1, r, e);

        // First plane (vertical)
        Matrix4f m4a = new Matrix4f(pose.last().pose());

        // Second plane (horizontal, rotated 90 degrees around flight axis)
        pose.mulPose(Axis.ZP.rotationDegrees(90f));
        Matrix4f m4b = new Matrix4f(pose.last().pose());

        holder.accept(new Ins(m4a, m4b, col));
    }

    public record Ins(Matrix4f m4a, Matrix4f m4b, int color) {

        public void tex(BulkDataWriter vc) {
            // Vertical plane
            vertex(vc, m4a, 1, 1, 1, 0, color);
            vertex(vc, m4a, 1, 0, 1, 1, color);
            vertex(vc, m4a, 0, 0, 0, 1, color);
            vertex(vc, m4a, 0, 1, 0, 0, color);

            // Horizontal plane (rotated 90 degrees)
            vertex(vc, m4b, 1, 1, 1, 0, color);
            vertex(vc, m4b, 1, 0, 1, 1, color);
            vertex(vc, m4b, 0, 0, 0, 1, color);
            vertex(vc, m4b, 0, 1, 0, 0, color);
        }

        public void texToArray(byte[] buf, int off) {
            int s = BulkDataWriter.STRIDE;
            // Vertical plane
            vertexToArray(buf, off, m4a, 1, 1, 1, 0, color);
            vertexToArray(buf, off + s, m4a, 1, 0, 1, 1, color);
            vertexToArray(buf, off + s * 2, m4a, 0, 0, 0, 1, color);
            vertexToArray(buf, off + s * 3, m4a, 0, 1, 0, 0, color);
            // Horizontal plane (rotated 90 degrees)
            vertexToArray(buf, off + s * 4, m4b, 1, 1, 1, 0, color);
            vertexToArray(buf, off + s * 5, m4b, 1, 0, 1, 1, color);
            vertexToArray(buf, off + s * 6, m4b, 0, 0, 0, 1, color);
            vertexToArray(buf, off + s * 7, m4b, 0, 1, 0, 0, color);
        }

        private static void vertex(BulkDataWriter vc, Matrix4f m4, float x, int y, int u, int v, int color) {
            vc.addVertex(m4, x - 0.5F, 0.0F, y - 0.5F, u, v, color);
        }

        private static void vertexToArray(byte[] buf, int off, Matrix4f m4, float x, int y, int u, int v, int color) {
            BulkDataWriter.writeVertex(buf, off, m4, x - 0.5F, 0.0F, y - 0.5F, u, v, color);
        }

    }
}
