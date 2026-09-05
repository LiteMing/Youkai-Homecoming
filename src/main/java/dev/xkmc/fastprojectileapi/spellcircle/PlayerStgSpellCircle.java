package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Player STG battle spell circle (design doc §17.2-17.3, D4).
 * <ul>
 *   <li>main ring from the {@code youkaishomecoming:player_stg} circle definition;</li>
 *   <li>bomb sub-circles rendered dynamically around the ring from the editable
 *   {@code player_stg_bomb} component: one full sub-circle per whole bomb
 *   (RESOURCE_UNIT = 5 raw units), a partial sub-circle for the fractional
 *   remainder, capped at spellCircleMaxResourceSubCircles;</li>
 *   <li>power and point progress use the same resource-slot projection with
 *   editable {@code player_stg_power} and {@code player_stg_points} components
 *   (100 raw units per displayed level/progress unit);</li>
 *   <li>global alpha fades continuously below spellCirclePlayerFadeStartLife
 *   (smoothstep), pinned to 1.0 while the player's own certification trial is
 *   active (D4);</li>
 *   <li>first-person rendering is intentionally not implemented (design §17.5:
 *   "若需显示" — third-person and other players see it).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerStgSpellCircle {

	private static final ResourceLocation PLAYER_STG = new ResourceLocation("youkaishomecoming", "player_stg");
	private static final ResourceLocation PLAYER_STG_BOMB = new ResourceLocation("youkaishomecoming", "player_stg_bomb");
	private static final ResourceLocation PLAYER_STG_POWER = new ResourceLocation("youkaishomecoming", "player_stg_power");
	private static final ResourceLocation PLAYER_STG_POINTS = new ResourceLocation("youkaishomecoming", "player_stg_points");
	private static final ResourceLocation SPELL_TEX = new ResourceLocation("youkaishomecoming", "textures/entities/spell_circle.png");
	private static final int RESOURCE_UNIT = 5;
	private static final int POWER_UNIT = 100;
	private static final int POINTS_UNIT = 100;
	private static final float BOMB_RADIUS = 44;
	// Keep resource rings outside the HP/time progress rings (HP is centered at 52).
	private static final float POWER_RADIUS = 60;
	private static final float POINTS_RADIUS = 68;

	private PlayerStgSpellCircle() {
	}

	public static void render(PoseStack pose, MultiBufferSource buffer, int light,
							  Player player, float pTick, @Nullable Quaternionf front) {
		GrazeCapability cap = GrazeCapability.HOLDER.get(player);
		if (cap == null || !cap.shouldRenderPlayerStgCircle()) return;
		SpellComponent component = SpellComponent.getFromConfig(PLAYER_STG.toString());
		if (component == null) return;

		float alpha = computeAlpha(cap);
		if (!SpellCircleLifeAlpha.shouldRender(alpha)) return;

		pose.pushPose();
		pose.translate(0, player.getBbHeight() * 0.5f, 0);
		pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
		if (front != null) {
			pose.mulPose(front);
			pose.mulPose(new Quaternionf().rotationY((float) Math.PI));
		}
		SpellComponent.RenderHandle handle = new SpellComponent.RenderHandle(
				pose, buffer, SpellRenderState.getSpell(SPELL_TEX), player.tickCount + pTick, light);
		handle.alpha = alpha;
		component.render(handle);
		SpellProgressCircleRenderer.render(pose, buffer, light, player, pTick, alpha);
		SpellCircleResourceRenderer.render(pose, buffer, light, player, pTick, alpha,
				cap.getBomb(), RESOURCE_UNIT, PLAYER_STG_BOMB, BOMB_RADIUS, 0);
		SpellCircleResourceRenderer.render(pose, buffer, light, player, pTick, alpha,
				cap.getPower(), POWER_UNIT, PLAYER_STG_POWER, POWER_RADIUS, 180);
		SpellCircleResourceRenderer.render(pose, buffer, light, player, pTick, alpha,
				cap.getPoints(), POINTS_UNIT, PLAYER_STG_POINTS, POINTS_RADIUS, 0);
		pose.popPose();
	}

	private static float computeAlpha(GrazeCapability cap) {
		return SpellCircleLifeAlpha.compute(cap);
	}

}
