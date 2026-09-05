package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Idempotent No-Hit contact gateway (design doc §8, D8).
 * <p>
 * Main entry is {@code IYHDanmaku.hurtTarget} (before hurt-time invul checks,
 * auto-bomb and damage events); {@code YHAttackListener.onHurt} is the legacy
 * fallback for any danmaku damage event that still surfaces. Both funnel here;
 * the state machine's transition to FAILED makes the gateway naturally
 * idempotent (a second contact on an already-failed trial does nothing).
 */
public final class CertificationContactGateway {

	private CertificationContactGateway() {
	}

	public static void onCertificationContact(SpellCertificationEntity entity, ServerPlayer target) {
		if (entity == null || entity.controller() == null) return;
		entity.controller().onProjectileContact(target);
	}
}
