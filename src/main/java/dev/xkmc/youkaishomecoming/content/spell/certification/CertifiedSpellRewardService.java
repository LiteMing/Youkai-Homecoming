package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
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
		// The immutable definition remains authoritative at cast time, while the
		// projection tags keep inventory selection, borders and tooltips correct
		// without consulting mutable live registry data.
		DynamicSpellItem.setCardType(stack, definition.itemForm.cardType());
		DynamicSpellItem.setExSpell(stack, definition.itemForm.exSpell());
		if (certificate.draftBudget() != null) {
			DynamicSpellItem.setDraftBudget(stack, certificate.draftBudget());
			dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank rank =
					dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.fromBudget(certificate.draftBudget());
			DynamicSpellItem.setRank(stack, rank);
		} else {
			DynamicSpellItem.setOpQuota(stack, certificate.specialNodeQuota());
			DynamicSpellItem.setRank(stack, dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank.GREATER_VIRTUE);
		}
		// spell color = blended average of the danmaku colors inside the definition,
		// with a small jitter; falls back to fully random when nothing is readable
		RandomSource random = RandomSource.create();
		return SpellColorExtractor.applyToStack(stack, definition, random);
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
				author.displayClientMessage(
						dev.xkmc.youkaishomecoming.init.data.YHLangData.CERT_ISSUE_PAYMENT_FAIL.get(), false);
				return false;
			}
		}
		CertifiedSpellStorage.save(level.getServer(), certificate, certification.controller().healthPlan());

		ItemStack stack = buildCertifiedStack(level.getServer(), certificate, definition);

		// The reward floats at the certification enemy's death spot. It remains
		// owner-locked for the configured grace period, then becomes public.
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
	 * Reward item: creator-only during the configured lock window, public after it.
	 */
	public static final class CertifiedRewardItem extends ItemEntity {

		private final UUID ownerId;
		private int ownerLockTicks;

		public UUID ownerId() {
			return ownerId;
		}

		public CertifiedRewardItem(Level level, double x, double y, double z,
								   ItemStack stack, UUID ownerId) {
			this(level, x, y, z, stack, ownerId,
					YHModConfig.COMMON.certificationRewardOwnerLockTicks.get());
		}

		public CertifiedRewardItem(Level level, double x, double y, double z,
								   ItemStack stack, UUID ownerId, int ownerLockTicks) {
			super(level, x, y, z, stack);
			this.ownerId = ownerId;
			this.ownerLockTicks = Math.max(0, ownerLockTicks);
		}

		public boolean isOwnerLocked() {
			return ownerLockTicks > 0;
		}

		@Override
		public void tick() {
			super.tick();
			if (!level().isClientSide && ownerLockTicks > 0) {
				ownerLockTicks--;
			}
		}

		@Override
		public void playerTouch(Player player) {
			if (isOwnerLocked() && !player.getUUID().equals(ownerId)) {
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
				controller.quote().draftBudget(),
				controller.quote().operatorTest(),
				SpellCertificate.CURRENT_HEALTH_PLAN_VERSION,
				SpellCertificate.CURRENT_ANALYSIS_VERSION,
				SpellCertificate.CURRENT_RULES_VERSION,
				entity.level().getGameTime()
		);
	}
}
