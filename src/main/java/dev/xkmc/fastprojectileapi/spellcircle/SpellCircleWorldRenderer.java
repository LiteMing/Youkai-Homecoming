package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.youkaishomecoming.content.entity.fairy.CirnoRenderer;
import dev.xkmc.youkaishomecoming.content.entity.reimu.ReimuRenderer;
import dev.xkmc.youkaishomecoming.content.entity.rumia.RumiaRenderer;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiRenderer;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterRenderer;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
			if (isFirstPersonCameraEntity(mc, entity)) {
				continue;
			}
			EntityRenderer<?> renderer = dispatcher.getRenderer(entity);
			if (usesDirectSpellCircleRenderer(renderer)) {
				continue;
			}
			if (!hasRenderableSpellCircle(entity, pTick)) {
				continue;
			}
			if (!shouldRender(renderer, entity, event.getFrustum(), camera)) {
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

	private static boolean isFirstPersonCameraEntity(Minecraft mc, Entity entity) {
		return mc.options.getCameraType().isFirstPerson() && entity == mc.getCameraEntity();
	}

	private static boolean usesDirectSpellCircleRenderer(EntityRenderer<?> renderer) {
		return renderer instanceof ShooterRenderer<?> ||
				renderer instanceof GeneralYoukaiRenderer<?> ||
				renderer instanceof RumiaRenderer ||
				renderer instanceof ReimuRenderer ||
				renderer instanceof CirnoRenderer;
	}

	private static boolean hasRenderableSpellCircle(Entity entity, float pTick) {
		EntitySpellCircleManager.State state = EntitySpellCircleManager.getClientOverride(entity);
		if (state != null) {
			return state.enabled() && state.circle() != null && state.size() > 0;
		}
		if (entity instanceof net.minecraft.world.entity.player.Player player) {
			GrazeCapability cap = GrazeCapability.HOLDER.get(player);
			return cap != null && (cap.isInDanmakuCombat() || cap.isPlayerSpellActive());
		}
		if (entity instanceof SpellCircleHolder holder) {
			return holder.shouldShowSpellCircle() && holder.getSpellCircle() != null && holder.getCircleSize(pTick) > 0;
		}
		return false;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static boolean shouldRender(EntityRenderer renderer, Entity entity, Frustum frustum, Vec3 camera) {
		return renderer.shouldRender(entity, frustum, camera.x, camera.y, camera.z);
	}

}
