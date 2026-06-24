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
 * Spiral projectile type that rotates around the flight axis based on distance
 * traveled.
 * Creates a corkscrew/spiral visual effect - consecutive bullets form a helix
 * pattern.
 */
public record SwingingProjectileType(ResourceLocation tex, DisplayType display, float rotationsPerBlock,
        float tiltAngle, float size)
        implements RenderableDanmakuType<SwingingProjectileType, SwingingProjectileType.Ins> {

    /** Convenience constructor with default size = 1.0 */
    public SwingingProjectileType(ResourceLocation tex, DisplayType display, float rotationsPerBlock, float tiltAngle) {
        this(tex, display, rotationsPerBlock, tiltAngle, 1.0f);
    }

    @Override
    public void start(MultiBufferSource buffer, List<Ins> list) {
        BulkDataWriter vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.danmaku(tex, display())),
                list.size());
        ParallelBufferFiller.fill(vc, list, 4, (buf, off, ins) -> ins.texToArray(buf, off));
        vc.flush();
    }

    @Override
    public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose,
            float pTick) {
        // Face the flight direction
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(pTick, e.yRotO, e.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pTick, e.xRotO, e.getXRot())));

        // Calculate distance traveled and rotate around flight axis (Z axis after
        // facing direction)
        double speed = e.getDeltaMovement().length();
        double distance = (e.tickCount + pTick) * speed;
        float rotation = (float) (distance * rotationsPerBlock * 360f);

        // Rotate around the flight axis (Z axis in local space)
        pose.mulPose(Axis.ZP.rotationDegrees(rotation));

        // Apply tilt to make the rotation visible (offset from flight axis)
        pose.mulPose(Axis.XP.rotationDegrees(tiltAngle));

        // Apply size scale
        pose.scale(size, size, size);

        int col = DanmakuRenderStates.fading(display, r.color(e, pTick), r, e);
        Matrix4f m4 = new Matrix4f(pose.last().pose());
        holder.accept(new Ins(m4, col));
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
            vc.addVertex(m4, x - 0.5F, 0.0F, y - 0.5F, u, v, color);
        }

        private static void vertexToArray(byte[] buf, int off, Matrix4f m4, float x, int y, int u, int v, int color) {
            BulkDataWriter.writeVertex(buf, off, m4, x - 0.5F, 0.0F, y - 0.5F, u, v, color);
        }

    }
}
