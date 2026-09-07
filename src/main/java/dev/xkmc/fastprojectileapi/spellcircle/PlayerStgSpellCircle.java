package dev.xkmc.fastprojectileapi.spellcircle;

import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Editable player STG resource projection shared by automatic circles, overrides and preview.
 * <ul>
 *   <li>main ring from the {@code youkaishomecoming:player_stg} circle definition;</li>
 *   <li>resource sub-circles are opt-in via {@link SpellComponent#player_stg_resources};</li>
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

	public static final ResourceLocation PLAYER_STG = new ResourceLocation("youkaishomecoming", "player_stg");
	public static final ResourceLocation PLAYER_STG_BOMB = new ResourceLocation("youkaishomecoming", "player_stg_bomb");
	public static final ResourceLocation PLAYER_STG_POWER = new ResourceLocation("youkaishomecoming", "player_stg_power");
	public static final ResourceLocation PLAYER_STG_POINTS = new ResourceLocation("youkaishomecoming", "player_stg_points");
	public static final List<ResourceLocation> COMPONENT_IDS = List.of(
			PLAYER_STG, PLAYER_STG_BOMB, PLAYER_STG_POWER, PLAYER_STG_POINTS);
	public static final int RESOURCE_UNIT = 5;
	public static final int POWER_UNIT = 100;
	public static final int POINTS_UNIT = 100;
	private static final float BOMB_RADIUS = 44;
	// Keep resource rings outside the HP/time progress rings (HP is centered at 52).
	private static final float POWER_RADIUS = 60;
	private static final float POINTS_RADIUS = 68;

	private PlayerStgSpellCircle() {
	}

	public static void renderResources(SpellComponent.RenderHandle handle, GrazeCapability cap) {
		renderResources(handle, cap.getBomb(), cap.getPower(), cap.getPoints());
	}

	/** Raw resource values; preview supplies samples without changing the player's capability. */
	public static void renderResources(SpellComponent.RenderHandle handle, int bomb, int power, int points) {
		SpellCircleResourceRenderer.render(handle, bomb, RESOURCE_UNIT, PLAYER_STG_BOMB, BOMB_RADIUS, 0);
		SpellCircleResourceRenderer.render(handle, power, POWER_UNIT, PLAYER_STG_POWER, POWER_RADIUS, 180);
		SpellCircleResourceRenderer.render(handle, points, POINTS_UNIT, PLAYER_STG_POINTS, POINTS_RADIUS, 0);
	}

}
