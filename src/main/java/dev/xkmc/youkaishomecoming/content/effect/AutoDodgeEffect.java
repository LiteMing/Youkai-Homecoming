package dev.xkmc.youkaishomecoming.content.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * Flag effect for client-side auto-dodge pilot.
 * Amplifier maps to tier: 0 = rescue, 1 = assist, 2+ = takeover.
 * Decision/execution run on local client player (see AutoDodgeClientHandlers).
 */
public class AutoDodgeEffect extends EmptyEffect {

	public AutoDodgeEffect(MobEffectCategory category, int color) {
		super(category, color);
	}
}
