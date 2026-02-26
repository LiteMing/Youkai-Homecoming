package dev.xkmc.fastprojectileapi.render.type;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.BulkDataWriter;
import dev.xkmc.fastprojectileapi.render.core.DanmakuRenderStates;
import dev.xkmc.fastprojectileapi.render.core.DisplayType;
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
        for (var e : list) {
            e.tex(vc, frameCount);
        }
        vc.flush();
    }

    @Override
    public void create(Consumer<Ins> holder, ProjectileRenderer<?> r, SimplifiedProjectile e, PoseStack pose,
            float pTick) {
        var sim4 = new Matrix4f(pose.last().pose());
        sim4.set3x3(new Matrix4f().scale((float) Math.pow(sim4.determinant3x3(), 1 / 3d)));
        int col = DanmakuRenderStates.fading(display, -1, r, e);
        int frame = (e.tickCount / ticksPerFrame) % frameCount;
        holder.accept(new Ins(sim4, col, frame));
    }

    public record Ins(Matrix4f m4, int color, int frame) {

        public void tex(BulkDataWriter vc, int frameCount) {
            float v0 = (float) frame / frameCount;
            float v1 = (float) (frame + 1) / frameCount;
            vertex(vc, m4, 1, 1, 1, v0, color);
            vertex(vc, m4, 1, 0, 1, v1, color);
            vertex(vc, m4, 0, 0, 0, v1, color);
            vertex(vc, m4, 0, 1, 0, v0, color);
        }

        private static void vertex(BulkDataWriter vc, Matrix4f m4, float x, int y, float u, float v, int color) {
            vc.addVertex(m4, x - 0.5F, y - 0.5F, 0.0F, u, v, color);
        }

    }
}
