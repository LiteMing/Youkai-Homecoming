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
		int durationTicks = clampDuration(requestedDurationTicks);
		double halfSize = clampHalfSize(requestedHalfSize);
		// Special-node quota: EXPERIMENTAL capabilities (teleport, confine, erase,
		// clear, flags, force/fire spell, on_damage / fire / laser hooks) are
		// denied by default; a boss-drop draft card may carry a quota (the count
		// of such nodes in the boss's own definition) that allows that many.
		// run_command stays operator-only and is never unlocked here.
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
		// Cast cost = base (default 100 = 5 XP levels / 1 bomb) x logarithmic power
		// multiplier; the weakest built-in spells sit at multiplier 1 (Phase 7).
		long castCost = Math.max(1, startCost) * powerMultiplier(analysis);
		long issueCost = YHModConfig.COMMON.certificationIssueFeeEnabled.get() ? castCost : 0;
		int breakHpSeconds = breakHpSeconds(durationTicks);
		return new CertificationQuote(UUID.randomUUID().toString(), hash, durationTicks, halfSize,
				startCost, issueCost, castCost, rewardDurationTicks(durationTicks),
				breakHpSeconds, specialNodeQuota, analysis, player.level().getGameTime());
	}

	/**
	 * Special-node quota carried by the draft card bound to this definition in
	 * the player's inventory (0 when no draft card is held).
	 */
	private static int draftOpQuota(ServerPlayer player, SpellDefinition definition) {
		for (ItemStack stack : player.getInventory().items) {
			if (stack.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
					&& definition.id != null && definition.id.equals(
					dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getSpellId(stack))
					&& !dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.isComplete(stack)) {
				return dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getOpQuota(stack);
			}
		}
		return 0;
	}

	/**
	 * Break HP of the certification enemy in seconds: timeout seconds x
	 * {@code certificationBreakHpRatio} (default 1.5 — the timeout is roughly
	 * 0.66x of the HP the player must attack down; each hit removes exactly 1s).
	 */
	public static int breakHpSeconds(int durationTicks) {
		double ratio = YHModConfig.COMMON.certificationBreakHpRatio.get();
		return Math.max(10, (int) Math.ceil(durationTicks / 20.0 * ratio));
	}

	/**
	 * Logarithmic power multiplier relative to the weakest built-in spell baseline
	 * (~64M conservative projectile-ticks ≈ sunny_milk). Every 4x projectile-ticks
	 * raises the multiplier by 1, capped at 8. Costs are multipliers, never a
	 * re-derivation from absolute projectile counts.
	 */
	private static long powerMultiplier(SpellAnalysis analysis) {
		double ratio = analysis.projectileTicks() / 64_000_000.0;
		if (ratio <= 1) return 1;
		long tiers = (long) Math.ceil(Math.log(ratio) / Math.log(2) / 2.0);
		// cap at 10x base = 10 bombs / 50 XP levels (default upper bound)
		return Math.max(1, Math.min(10, tiers));
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
		if (dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.isInDanmakuSession(player)) {
			// D15: a normal boss battle is running — cannot start a certification
			LOGGER.info("[YH] start rejected: player is in danmaku combat (D15)");
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

	/** OP test path: starts without paying the start fee. */
	public static boolean startFree(ServerPlayer player, CertificationQuote quote) {
		if (!YHModConfig.COMMON.certificationEnabled.get()) return false;
		if (dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.isInDanmakuSession(player)) return false;
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
		String definitionHash = quote.definitionHash();
		long movementSeed = player.level().random.nextLong();
		SpellCertificationEntity entity = new SpellCertificationEntity(
				YHEntities.SPELL_CERTIFICATION.get(), player.level());
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
		player.level().addFreshEntity(entity);
		controller.beginPrepare();
		return true;
	}

	public static ResourceLocation startProvider() {
		return new ResourceLocation(YHModConfig.COMMON.certificationStartPaymentProvider.get());
	}

	/**
	 * Reward cast duration curve: the certified item runs for a fraction of the
	 * certification break HP — 1/3 at the shortest certification, falling linearly
	 * to 1/10 at the longest (curve endpoints configurable; the final duration is
	 * derived from the break HP, which itself scales with the timeout).
	 */
	public static int rewardDurationTicks(int certifiedDurationTicks) {
		int breakHpSeconds = breakHpSeconds(certifiedDurationTicks);
		int minD = YHModConfig.COMMON.certificationMinDurationTicks.get();
		int maxD = YHModConfig.COMMON.certificationMaxDurationTicks.get();
		double span = Math.max(1, maxD - minD);
		double t = Math.min(1, Math.max(0, (certifiedDurationTicks - minD) / span));
		double shortRatio = YHModConfig.COMMON.certificationRewardDurationShortRatio.get();
		double longRatio = YHModConfig.COMMON.certificationRewardDurationLongRatio.get();
		double ratio = shortRatio + (longRatio - shortRatio) * t;
		return Math.max(20, (int) Math.round(breakHpSeconds * 20 * ratio));
	}
}
