package dev.xkmc.youkaishomecoming.content.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * Flag effect for client-side auto-dodge pilot.
 * Amplifier maps to linearly stronger basic, enhanced and advanced profiles.
 * Decision/execution run on local client player (see AutoDodgeClientHandlers).
 */
public class AutoDodgeEffect extends EmptyEffect {

	public AutoDodgeEffect(MobEffectCategory category, int color) {
		super(category, color);
	}
}
