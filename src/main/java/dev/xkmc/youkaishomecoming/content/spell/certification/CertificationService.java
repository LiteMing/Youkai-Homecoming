package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysis;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisLimits;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisProfile;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentReceipt;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentResult;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellCostContext;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellPaymentRouter;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Certification service: quote computation and trial startup (design doc §5.2-5.4,
 * §13 cost model, §18 protocol). Server-authoritative; every parameter is
 * re-clamped here.
 */
public final class CertificationService {

	private static final Logger LOGGER = LoggerFactory.getLogger("YoukaiHomecoming/Certification");

	private CertificationService() {
	}

	// ------------------------------------------------------------ quote

	public static CertificationQuote quote(ServerPlayer player, SpellDefinition definition,
										   int requestedDurationTicks, double requestedHalfSize) {
		// The spell's own declared duration is the certification timeout; the
		// enemy HP is derived directly from the seconds: duration seconds x 10 x
		// the regen ratio (a fixed total, not a growth over time).
		int durationTicks = clampDuration(requestedDurationTicks);
		int spellHp = (int) Math.max(1, Math.round(durationTicks / 20.0 * 10.0
				* YHModConfig.COMMON.certificationHpRegenRatio.get()));
		// the arena half size is fixed by config (UI selection is ignored)
		double halfSize = YHModConfig.COMMON.certificationFixedArenaHalfSize.get();
		// Special-node quota: EXPERIMENTAL capabilities (teleport, erase, clear,
		// on_damage) are denied by default; a boss-drop draft card may carry a
		// quota (the count of such nodes in the boss's own definition).
		// run_command and creator nodes stay operator-only.
		int specialNodeQuota = draftOpQuota(player, definition);
		SpellAnalysis analysis;
		try {
			analysis = SpellAnalyzer.analyze(definition, SpellAnalysisProfile.CERTIFICATION);
		} catch (dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisException e) {
			int count = SpecialNodeCounter.count(definition);
			if (count > 0 && count <= specialNodeQuota) {
				// the draft quota covers the special nodes: re-run with those
				// capabilities allowed (hard performance limits still apply).
				analysis = SpellAnalyzer.analyze(definition, SpellAnalysisProfile.CERTIFICATION,
						SpellAnalysisLimits.certification(), SpecialNodeCounter.EXPERIMENTAL_CAPS);
			} else {
				throw e;
			}
		}
		String hash = SpellHash.canonicalHash(definition);
		// Start fee is a fixed anti-spam toll (design §14), decoupled from spell power —
		// spam protection lives in maxTrialsPerPlayer / maxConcurrentTrials.
		long startCost = YHModConfig.COMMON.certificationStartCostUnits.get();
		// Cast/issue cost is duration-driven only (1 + 0.2/s up to 5s, +0.4/s
		// beyond); projectile volume no longer affects bomb/XP costs.
		int rewardDuration = rewardDurationTicks(durationTicks);
		long castCost = dev.xkmc.youkaishomecoming.content.spell.payment.CastCost.unitsForDuration(rewardDuration);
		long issueCost = YHModConfig.COMMON.certificationIssueFeeEnabled.get() ? castCost : 0;
		return new CertificationQuote(UUID.randomUUID().toString(), hash, durationTicks, halfSize,
				startCost, issueCost, castCost, rewardDuration,
				spellHp, specialNodeQuota, analysis, player.level().getGameTime());
	}

	/**
	 * Special-node quota carried by the spell card in the player's inventory:
	 * first a card bound to this definition, then any blank quota card.
	 */
	private static int draftOpQuota(ServerPlayer player, SpellDefinition definition) {
		int blankQuota = 0;
		for (ItemStack stack : player.getInventory().items) {
			if (stack.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
					&& !dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.isComplete(stack)) {
				ResourceLocation bound = dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getSpellId(stack);
				int quota = dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getOpQuota(stack);
				if (bound != null && definition.id != null && definition.id.equals(bound)) {
					if (quota > 0) {
						return quota;
					}
				} else if (bound == null && quota > 0 && blankQuota == 0) {
					blankQuota = quota;
				}
			}
		}
		return blankQuota;
	}

	public static int clampDuration(int requested) {
		return Math.max(YHModConfig.COMMON.certificationMinDurationTicks.get(),
				Math.min(YHModConfig.COMMON.certificationMaxDurationTicks.get(), requested));
	}

	public static double clampHalfSize(double requested) {
		return Math.max(YHModConfig.COMMON.certificationMinArenaHalfSize.get(),
				Math.min(YHModConfig.COMMON.certificationMaxArenaHalfSize.get(), requested));
	}

	/**
	 * Design doc §13: proof discount with diminishing returns and a floor.
	 * durationDiscount and arenaDiscount use sqrt curves; the floor is
	 * certificationMinProofMultiplier (default 0.45).
	 */
	private static double proofMultiplier(int durationTicks, double halfSize) {
		int minDuration = YHModConfig.COMMON.certificationMinDurationTicks.get();
		double minHalf = YHModConfig.COMMON.certificationMinArenaHalfSize.get();
		double durationDiscount = Math.sqrt((double) minDuration / Math.max(minDuration, durationTicks));
		double arenaDiscount = Math.sqrt(minHalf / Math.max(minHalf, halfSize));
		double minProof = YHModConfig.COMMON.certificationMinProofMultiplier.get();
		return Math.max(minProof, Math.min(1.0, durationDiscount * arenaDiscount));
	}

	// ------------------------------------------------------------ start

	/**
	 * Pays the start fee and spawns the certification enemy. The definition is
	 * resolved from the quote cache — the start request only carries the quoteId,
	 * so the client can never swap the definition (design doc §18).
	 */
	public static boolean start(ServerPlayer player, CertificationQuote quote) {
		if (!YHModConfig.COMMON.certificationEnabled.get()) {
			LOGGER.info("[YH] start rejected: certification disabled in config");
			return false;
		}
		if (inRealBattle(player)) {
			// D15: a real boss battle or PvP duel is running — cannot start a
			// certification. A plain forced combat (player-declared or leftover)
			// is taken over by the certification instead.
			LOGGER.info("[YH] start rejected: player is in a real battle (D15)");
			return false;
		}
		if (CertificationManager.INSTANCE.hasActiveTrial(player)) {
			LOGGER.info("[YH] start rejected: player already has an active trial");
			return false;
		}
		PaymentResult payment = SpellPaymentRouter.pay(player, quote.startCostUnits(),
				SpellCostContext.CERTIFICATION_START);
		if (!payment.success()) {
			LOGGER.info("[YH] start rejected: payment failed: {}", payment);
			return false;
		}
		boolean started = spawnTrial(player, quote, payment.receipt());
		LOGGER.info("[YH] start result: started={} cost={} hash={}",
				started, quote.startCostUnits(), quote.definitionHash().substring(0, Math.min(8, quote.definitionHash().length())));
		return started;
	}

	/** A real opponent battle (Youkai session or PvP duel); forced combat alone is fine. */
	private static boolean inRealBattle(ServerPlayer player) {
		var cap = dev.xkmc.youkaishomecoming.content.capability.GrazeCapability.HOLDER.get(player);
		return cap.isInSession() || !cap.snapshotOpponents().ids().isEmpty();
	}

	/** OP test path: starts without paying the start fee. */
	public static boolean startFree(ServerPlayer player, CertificationQuote quote) {
		if (!YHModConfig.COMMON.certificationEnabled.get()) return false;
		if (inRealBattle(player)) return false;
		if (CertificationManager.INSTANCE.hasActiveTrial(player)) return false;
		return spawnTrial(player, quote, null);
	}

	private static boolean spawnTrial(ServerPlayer player, CertificationQuote quote,
									  @Nullable PaymentReceipt startReceipt) {
		SpellDefinition definition = CertificationManager.INSTANCE.getQuoteDefinition(quote.quoteId());
		if (definition == null) {
			if (startReceipt != null) {
				SpellPaymentRouter.refund(player, startReceipt);
			}
			return false;
		}
		// Re-check the cached definition and current bounds at the payment boundary;
		// registry reloads or config changes must invalidate an old quote.
		if (!quote.definitionHash().equals(SpellHash.canonicalHash(definition))
				|| quote.durationTicks() != clampDuration(quote.durationTicks())
				|| quote.arenaHalfSize() != YHModConfig.COMMON.certificationFixedArenaHalfSize.get()) {
			if (startReceipt != null) {
				SpellPaymentRouter.refund(player, startReceipt);
			}
			return false;
		}
		// entering the certification ends and clears any spell card the player
		// is currently releasing (their danmaku would pollute the trial)
		dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer.clear(player);
		String definitionHash = quote.definitionHash();
		long movementSeed = player.level().random.nextLong();
		SpellCertificationEntity entity = new SpellCertificationEntity(
				YHEntities.SPELL_CERTIFICATION.get(), player.level());
		// plain spell HP: the enemy's max health comes from the spell definition
		var maxHealth = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(Math.max(1, quote.spellHp()));
		}
		entity.setHealth(entity.getMaxHealth());
		// the certification enemy glows so the player can always find their target
		entity.setGlowingTag(true);
		entity.setPos(player.position());
		entity.initCertification(player, definition, definitionHash, quote, movementSeed);
		CertificationController controller = entity.controller();
		if (!CertificationManager.INSTANCE.register(player, controller)) {
			if (startReceipt != null) {
				SpellPaymentRouter.refund(player, startReceipt);
			}
			return false;
		}
		if (startReceipt != null) {
			controller.setStartReceipt(startReceipt);
		}
		// starting a certification consumes the player's draft card (survival);
		// a failed trial returns it along the same path as the reward
		ItemStack consumed = consumeDraft(player, definition.id);
		if (consumed != null) {
			controller.setConsumedDraft(consumed);
		}
		player.level().addFreshEntity(entity);
		controller.beginPrepare();
		return true;
	}

	/**
	 * Removes one draft card (unfinished dynamic spell, not certified/complete)
	 * bound to this definition from the player's inventory; returns a copy for
	 * the fail-return. Null when no draft card is held (e.g. operator give cards).
	 */
	@Nullable
	private static ItemStack consumeDraft(ServerPlayer player, ResourceLocation definitionId) {
		for (ItemStack stack : player.getInventory().items) {
			if (stack.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
					&& definitionId != null && definitionId.equals(
					dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getSpellId(stack))
					&& !dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.isComplete(stack)
					&& !dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.isCertified(stack)) {
				ItemStack copy = stack.copy();
				stack.shrink(1);
				return copy;
			}
		}
		return null;
	}

	public static ResourceLocation startProvider() {
		return new ResourceLocation(YHModConfig.COMMON.certificationStartPaymentProvider.get());
	}

	/**
	 * Reward cast duration curve: the certified item runs for a fraction of the
	 * certified timeout — 1/3 at the shortest certification, falling linearly
	 * to 1/10 at the longest (curve endpoints configurable).
	 */
	public static int rewardDurationTicks(int certifiedDurationTicks) {
		int minD = YHModConfig.COMMON.certificationMinDurationTicks.get();
		int maxD = YHModConfig.COMMON.certificationMaxDurationTicks.get();
		double span = Math.max(1, maxD - minD);
		double t = Math.min(1, Math.max(0, (certifiedDurationTicks - minD) / span));
		double shortRatio = YHModConfig.COMMON.certificationRewardDurationShortRatio.get();
		double longRatio = YHModConfig.COMMON.certificationRewardDurationLongRatio.get();
		double ratio = shortRatio + (longRatio - shortRatio) * t;
		return Math.max(20, (int) Math.round(certifiedDurationTicks * ratio));
	}
}
