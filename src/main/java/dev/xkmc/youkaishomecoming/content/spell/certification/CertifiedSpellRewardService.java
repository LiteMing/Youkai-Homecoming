package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.UUID;

/**
 * Certified reward issuance (design doc §16, §5.5). Saves the certificate +
 * immutable definition to world storage, spawns a glowing no-gravity
 * invulnerable item locked to the creator, and falls back to the pending
 * reward store when the item cannot be handed over.
 */
public final class CertifiedSpellRewardService {

	private CertifiedSpellRewardService() {
	}

	public static void issue(SpellCertificationEntity entity) {
		ServerLevel level = (ServerLevel) entity.level();
		SpellCertificationEntity certification = entity;
		SpellCertificate certificate = buildCertificate(certification);
		SpellDefinition definition = certification.controller().definition();
		CertifiedSpellStorage.save(level.getServer(), certificate, definition);

		ItemStack stack = DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), definition.id, true);
		CertifiedSpellValidator.tagCertified(stack, certificate);

		ServerPlayer author = certification.controller().author();
		boolean offline = author == null || !author.isAlive()
				|| !author.level().equals(level);
		if (offline) {
			PendingRewardStorage.save(level.getServer(), certification.controller().authorId(), certificate.definitionHash());
			return;
		}
		// try immediate hand-over into a free inventory slot
		if (tryAddToInventory(author, stack)) {
			dev.xkmc.youkaishomecoming.content.spell.certification.network.CertifiedSpellRewardToClient.send(author, certificate.definitionHash(), definition.display.name());
			return;
		}
		// otherwise spawn the world item locked to the creator
		ItemEntity item = new ItemEntity(level, author.getX(), author.getY() + 0.5, author.getZ(), stack);
		item.setNoGravity(true);
		item.setGlowingTag(true);
		item.setInvulnerable(true);
		if (YHModConfig.COMMON.certificationRewardNeverDespawn.get()) {
			item.setUnlimitedLifetime();
		}
		item.setPickUpDelay(YHModConfig.COMMON.certificationRewardOwnerLockTicks.get());
		item.setThrower(author.getUUID());
		level.addFreshEntity(item);
		// redundant pending marker: guarantees the reward survives chunk unload/restart
		PendingRewardStorage.save(level.getServer(), author.getUUID(), certificate.definitionHash());
	}

	private static boolean tryAddToInventory(ServerPlayer player, ItemStack stack) {
		if (player.getInventory().add(stack)) {
			return true;
		}
		return false;
	}

	private static SpellCertificate buildCertificate(SpellCertificationEntity entity) {
		var controller = entity.controller();
		SpellDefinition definition = controller.definition();
		String hash = controller.definitionHash();
		UUID authorId = controller.authorId();
		String authorName = controller.author().getGameProfile().getName();
		long cost = controller.quote().startCostUnits();
		Set<dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability> capabilities =
				controller.quote().analysis().requiredCapabilities();
		return new SpellCertificate(
				UUID.randomUUID(),
				hash,
				authorId,
				authorName,
				controller.targetTicks(),
				controller.quote().arenaHalfSize(),
				"AABB",
				cost,
				capabilities,
				SpellCertificate.CURRENT_ANALYSIS_VERSION,
				SpellCertificate.CURRENT_RULES_VERSION,
				entity.level().getGameTime()
		);
	}
}
