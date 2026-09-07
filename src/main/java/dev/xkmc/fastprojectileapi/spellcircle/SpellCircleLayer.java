package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public class SpellCircleLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

	private static final ResourceLocation SPELL_TEXTURE = YoukaisHomecoming.loc("textures/entities/spell_circle.png");

	public SpellCircleLayer(LivingEntityRenderer<T, M> pRenderer) {
		super(pRenderer);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffer, int light, T entity,
			float swing, float swingAmp, float pTick, float age,
			float yaw, float pitch) {
		// World-space rendering is handled by SpellCircleWorldRenderer. Rendering from
		// a living render layer inherits the model pose and can break billboarding.
	}

	public static <T extends Entity> void renderImpl(
			PoseStack pose, MultiBufferSource buffer, int light, T entity,
			float pTick, @Nullable Quaternionf front) {
		ResourceLocation rl = null;
		float scale = 0;
		GrazeCapability playerState = entity instanceof Player player ? GrazeCapability.HOLDER.get(player) : null;
		EntitySpellCircleManager.State override = EntitySpellCircleManager.getClientOverride(entity);
		if (override != null) {
			if (!override.enabled() || override.circle() == null)
				return;
			rl = override.circle();
			scale = override.size();
		} else if (playerState != null) {
			if (!playerState.shouldRenderPlayerStgCircle()) return;
			rl = PlayerStgSpellCircle.PLAYER_STG;
			scale = 1;
		} else if (entity instanceof SpellCircleHolder e) {
			if (!e.shouldShowSpellCircle())
				return;
			rl = e.getSpellCircle();
			scale = e.getCircleSize(pTick);
		}
		if (rl == null || scale <= 0)
			return;
		SpellComponent component = SpellCircleConfig.getFromConfig(rl);
		if (component == null)
			return;
		float alpha = 1;
		if (playerState != null && playerState.shouldRenderPlayerStgCircle()
				&& PlayerStgSpellCircle.PLAYER_STG.equals(rl)) {
			alpha = SpellCircleLifeAlpha.compute(playerState);
			if (!SpellCircleLifeAlpha.shouldRender(alpha)) return;
		}
		SpellComponent.RenderHandle handle = new SpellComponent.RenderHandle(pose,
				buffer,
				SpellRenderState.getSpell(SPELL_TEXTURE),
				entity.tickCount + pTick, light);
		handle.alpha = alpha;
		pose.pushPose();
		pose.translate(0, entity.getBbHeight() / 2, 0);
		pose.scale(scale / 16f, scale / 16f, scale / 16f);
		if (front != null) {
			pose.mulPose(front);
			pose.mulPose(Axis.YP.rotationDegrees(180.0F));
		}
		component.render(handle);
		if (component.player_stg_resources && playerState != null) {
			PlayerStgSpellCircle.renderResources(handle, playerState);
		}
		SpellProgressCircleRenderer.render(pose, buffer, light, entity, pTick, handle.alpha);
		pose.popPose();
	}

}
