package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpellCircleWorldRenderer {

	@SubscribeEvent
	public static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
		Vec3 camera = event.getCamera().getPosition();
		float pTick = event.getPartialTick();
		PoseStack pose = event.getPoseStack();
		MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
		boolean rendered = false;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (usesEntityRendererPath(entity)) {
				continue;
			}
			EntitySpellCircleManager.State state = EntitySpellCircleManager.getClientOverride(entity);
			if (state == null || !state.enabled() || state.circle() == null || state.size() <= 0) {
				continue;
			}
			if (!shouldRender(dispatcher, entity, event.getFrustum(), camera)) {
				continue;
			}
			double x = Mth.lerp(pTick, entity.xOld, entity.getX()) - camera.x;
			double y = Mth.lerp(pTick, entity.yOld, entity.getY()) - camera.y;
			double z = Mth.lerp(pTick, entity.zOld, entity.getZ()) - camera.z;
			int light = LevelRenderer.getLightColor(mc.level, entity.blockPosition());
			pose.pushPose();
			pose.translate(x, y, z);
			SpellCircleLayer.renderImpl(pose, buffer, light, entity, pTick, dispatcher.cameraOrientation());
			pose.popPose();
			rendered = true;
		}
		if (rendered) {
			buffer.endLastBatch();
		}
	}

	private static boolean usesEntityRendererPath(Entity entity) {
		return entity instanceof LivingEntity || entity instanceof SpellCircleHolder;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static boolean shouldRender(EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum, Vec3 camera) {
		EntityRenderer renderer = dispatcher.getRenderer(entity);
		return renderer.shouldRender(entity, frustum, camera.x, camera.y, camera.z);
	}

}
