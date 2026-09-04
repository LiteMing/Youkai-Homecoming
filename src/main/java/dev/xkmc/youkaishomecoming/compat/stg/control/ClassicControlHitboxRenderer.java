package dev.xkmc.youkaishomecoming.compat.stg.control;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.fastprojectileapi.render.virtual.ClientDanmakuCache;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reuses the existing danmaku hitbox lines while the classic focus key is held. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicControlHitboxRenderer {

	private ClassicControlHitboxRenderer() {
	}

	@SubscribeEvent
	public static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
				|| !ClassicControlClient.shouldRenderFocusHitbox()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return;
		Vec3 camera = event.getCamera().getPosition();
		PoseStack pose = event.getPoseStack();
		var buffers = MultiBufferSource.immediate(new BufferBuilder(RenderType.lines().bufferSize()));
		ClientDanmakuCache.renderPlayerDanmakuHitbox(pose, buffers.getBuffer(RenderType.lines()),
				minecraft.player, camera.x, camera.y, camera.z, event.getPartialTick());
		buffers.endBatch(RenderType.lines());
	}
}
