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

/**
 * Animated projectile type that plays a sequence of frames.
 * Texture should be vertically stacked frames (e.g., 32x128 for 4 frames of
 * 32x32).
 */
public record AnimatedProjectileType(ResourceLocation tex, DisplayType display, int frameCount, int ticksPerFrame)
        implements RenderableDanmakuType<AnimatedProjectileType, AnimatedProjectileType.Ins> {

    @Override
    public void start(MultiBufferSource buffer, List<Ins> list) {
        BulkDataWriter vc = new BulkDataWriter(buffer.getBuffer(DanmakuRenderStates.danmaku(tex, display())),
                list.size());
        int fc = frameCount;
        ParallelBufferFiller.fill(vc, list, 4, (buf, off, ins) -> ins.texToArray(buf, off, fc));
        vc.flush();
    }

    @Override
    public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose,
            float pTick) {
        var m4 = pose.last().pose();
        float scale = (float) Math.cbrt(Math.abs(m4.determinant3x3()));
        int col = DanmakuRenderStates.fading(display, -1, r, e);
        int frame = (e.tickCount / ticksPerFrame) % frameCount;
        holder.accept(new Ins(m4.m30(), m4.m31(), m4.m32(), scale, col, frame));
    }

    public record Ins(float tx, float ty, float tz, float scale, int color, int frame) {

        public void tex(BulkDataWriter vc, int frameCount) {
            var m4 = new Matrix4f().translation(tx, ty, tz).scale(scale);
            float v0 = (float) frame / frameCount;
            float v1 = (float) (frame + 1) / frameCount;
            vc.addVertex(m4, 0.5F, 0.5F, 0.0F, 1, v0, color);
            vc.addVertex(m4, 0.5F, -0.5F, 0.0F, 1, v1, color);
            vc.addVertex(m4, -0.5F, -0.5F, 0.0F, 0, v1, color);
            vc.addVertex(m4, -0.5F, 0.5F, 0.0F, 0, v0, color);
        }

        public void texToArray(byte[] buf, int off, int frameCount) {
            int s = BulkDataWriter.STRIDE;
            float v0 = (float) frame / frameCount;
            float v1 = (float) (frame + 1) / frameCount;
            vertexDirect(buf, off, 0.5f, 0.5f, 1, v0);
            vertexDirect(buf, off + s, 0.5f, -0.5f, 1, v1);
            vertexDirect(buf, off + s * 2, -0.5f, -0.5f, 0, v1);
            vertexDirect(buf, off + s * 3, -0.5f, 0.5f, 0, v0);
        }

        private void vertexDirect(byte[] buf, int off, float lx, float ly, float u, float v) {
            BulkDataWriter.writeVertex(buf, off, lx * scale + tx, ly * scale + ty, tz, u, v, color);
        }

    }
}
