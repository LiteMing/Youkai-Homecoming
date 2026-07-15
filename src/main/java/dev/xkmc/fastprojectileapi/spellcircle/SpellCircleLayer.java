package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public class SpellCircleLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

	private static final ResourceLocation SPELL = YoukaisHomecoming.loc("textures/entities/spell_circle.png");

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
		EntitySpellCircleManager.State override = EntitySpellCircleManager.getClientOverride(entity);
		if (override != null) {
			if (!override.enabled() || override.circle() == null)
				return;
			rl = override.circle();
			scale = override.size();
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
		SpellComponent.RenderHandle handle = new SpellComponent.RenderHandle(pose,
				buffer,
				buffer.getBuffer(SpellRenderState.getSpell(SPELL)),
				entity.tickCount + pTick, light);
		pose.pushPose();
		pose.translate(0, entity.getBbHeight() / 2, 0);
		pose.scale(scale / 16f, scale / 16f, scale / 16f);
		if (front != null) {
			pose.mulPose(front);
			pose.mulPose(Axis.YP.rotationDegrees(180.0F));
		}
		component.render(handle);
		pose.popPose();
	}

}
