package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

/**
 * Renders one resource channel as evenly spaced full/fractional sub-circle slots.
 * The channel's visual is a normal editable {@link SpellComponent}; this class
 * only supplies the live quantity and slot transforms.
 */
@OnlyIn(Dist.CLIENT)
public final class SpellCircleResourceRenderer {
	private static final ResourceLocation SPELL_TEXTURE =
			new ResourceLocation("youkaishomecoming", "textures/entities/spell_circle.png");

	private SpellCircleResourceRenderer() {
	}

	public static void render(PoseStack pose, MultiBufferSource buffer, int light,
			Entity entity, float pTick, float globalAlpha, int raw, int resourceUnit,
			ResourceLocation componentId, float radius, float angleOffset) {
		if (raw <= 0 || resourceUnit <= 0) {
			return;
		}
		SpellComponent component = SpellComponent.getFromConfig(componentId.toString());
		if (component == null) {
			return;
		}
		int whole = raw / resourceUnit;
		int remainder = raw % resourceUnit;
		int max = Math.max(1, YHModConfig.COMMON.spellCircleMaxResourceSubCircles.get());
		int slots = Math.min(whole + (remainder > 0 ? 1 : 0), max);
		if (slots <= 0) {
			return;
		}
		SpellComponent.RenderHandle handle = new SpellComponent.RenderHandle(
				pose, buffer, SpellRenderState.getSpell(SPELL_TEXTURE),
				entity.tickCount + pTick, light);
		for (int i = 0; i < slots; i++) {
			float slotAlpha = i < whole ? 1.0f : remainder / (float) resourceUnit;
			if (!SpellCircleLifeAlpha.shouldRender(globalAlpha * slotAlpha)) {
				continue;
			}
			// Spread the active slots around the complete ring. The configured cap only
			// limits how many icons are shown; using it as the angular denominator would
			// cluster the common one- or two-resource case into a small arc.
			float angle = angleOffset + i * 360f / slots;
			pose.pushPose();
			// SpellComponent strokes live in the local XY plane. Rotate around its
			// normal (Z), otherwise the old Y-axis transform places slots in depth
			// and they collapse/overlap once the circle is billboarded.
			pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(angle)));
			pose.translate(radius, 0, 0);
			pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-angle)));
			handle.alpha = globalAlpha * slotAlpha;
			component.render(handle);
			pose.popPose();
		}
	}
}
