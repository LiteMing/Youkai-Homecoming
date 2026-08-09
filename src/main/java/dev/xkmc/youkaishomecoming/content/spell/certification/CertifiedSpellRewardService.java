package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentResult;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellCostContext;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellPaymentRouter;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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

	/**
	 * Builds the reusable certified spell item: not single-use, with a cast
	 * duration from the certified-duration curve (design §16; Phase 7 balance).
	 */
	public static ItemStack buildCertifiedStack(net.minecraft.server.MinecraftServer server,
												SpellCertificate certificate, SpellDefinition definition) {
		// The quote owns the final cast duration; do not trust the mutable source
		// definition or an item-stack duration tag here.
		int castDuration = CertificationService.rewardDurationTicks(certificate.certifiedDuration());
		ItemStack stack = DynamicSpellItem.createStackWithDuration(
				YHDanmaku.DYNAMIC_SPELL.get(), definition.id, castDuration, false);
		CertifiedSpellValidator.tagCertified(stack, certificate);
		// the certified card keeps the special-node quota granted at draft time
		// (recorded on the certificate; shown for reference)
		DynamicSpellItem.setOpQuota(stack, certificate.specialNodeQuota());
		// spell color = blended average of the danmaku colors inside the definition,
		// with a small jitter; falls back to fully random when nothing is readable
		RandomSource random = RandomSource.create();
		DanmakuColor color = SpellColorExtractor.extractWithJitter(definition, random);
		if (color == null) {
			DynamicSpellItem.withRandomColor(stack, random);
		} else {
			DynamicSpellItem.withColor(stack, color);
		}
		return stack;
	}

	public static boolean issue(SpellCertificationEntity entity) {
		ServerLevel level = (ServerLevel) entity.level();
		SpellCertificationEntity certification = entity;
		SpellCertificate certificate = buildCertificate(certification);
		SpellDefinition definition = certification.controller().definition();
		ServerPlayer author = certification.controller().author();
		long issueCost = certification.controller().quote().issueCostUnits();
		boolean offline = author == null || !author.isAlive() || author.level() != level;
		if (offline) {
			return false;
		}
		if (issueCost > 0) {
			PaymentResult payment = SpellPaymentRouter.pay(author, issueCost, SpellCostContext.CERTIFICATION_ISSUE);
			if (!payment.success()) {
				author.displayClientMessage(dev.xkmc.youkaishomecoming.init.data.YHLangData.CERT_FAIL.get("issue payment"), false);
				return false;
			}
		}
		CertifiedSpellStorage.save(level.getServer(), certificate, definition);

		ItemStack stack = buildCertifiedStack(level.getServer(), certificate, definition);

		// the reward is NEVER handed straight into the inventory: it floats in
		// place, glowing and weightless, at the certification enemy's death spot,
		// and only the creator can pick it up (immediately, no long delay)
		ItemEntity item = new CertifiedRewardItem(level, entity.getX(), entity.getY() + 0.5,
				entity.getZ(), stack, author.getUUID());
		item.setNoGravity(true);
		item.setGlowingTag(true);
		item.setInvulnerable(true);
		// ItemEntity constructor scatters a random velocity — the reward must
		// hover perfectly still at the break spot
		item.setDeltaMovement(0, 0, 0);
		if (YHModConfig.COMMON.certificationRewardNeverDespawn.get()) {
			item.setUnlimitedLifetime();
		}
		item.setPickUpDelay(0);
		level.addFreshEntity(item);
		// redundant pending marker: guarantees the reward survives chunk unload/restart
		PendingRewardStorage.save(level.getServer(), author.getUUID(), certificate.definitionHash());
		return true;
	}

	/**
	 * Owner-locked reward item: only the certification creator may pick it up;
	 * everyone else cannot touch it at all.
	 */
	public static final class CertifiedRewardItem extends ItemEntity {

		private final UUID ownerId;

		public UUID ownerId() {
			return ownerId;
		}

		public CertifiedRewardItem(Level level, double x, double y, double z,
								   ItemStack stack, UUID ownerId) {
			super(level, x, y, z, stack);
			this.ownerId = ownerId;
		}

		@Override
		public void playerTouch(Player player) {
			if (!player.getUUID().equals(ownerId)) {
				return;
			}
			super.playerTouch(player);
		}
	}

	/**
	 * Pure display item: the floating spell card shown while the certification
	 * enemy is invisible. Never pickable, weightless, motionless — the
	 * controller re-positions it every tick to follow the enemy.
	 */
	public static final class SpellDisplayItem extends ItemEntity {

		public SpellDisplayItem(Level level, double x, double y, double z, ItemStack stack) {
			super(level, x, y, z, stack);
			setNoGravity(true);
			setInvulnerable(true);
			setPickUpDelay(Integer.MAX_VALUE);
			setDeltaMovement(0, 0, 0);
		}

		@Override
		public void playerTouch(Player player) {
			// never pickable
		}
	}

	private static SpellCertificate buildCertificate(SpellCertificationEntity entity) {
		var controller = entity.controller();
		SpellDefinition definition = controller.definition();
		String hash = controller.definitionHash();
		UUID authorId = controller.authorId();
		String authorName = controller.author().getGameProfile().getName();
		long cost = controller.quote().castCostUnits();
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
				controller.quote().specialNodeQuota(),
				SpellCertificate.CURRENT_ANALYSIS_VERSION,
				SpellCertificate.CURRENT_RULES_VERSION,
				entity.level().getGameTime()
		);
	}
}
