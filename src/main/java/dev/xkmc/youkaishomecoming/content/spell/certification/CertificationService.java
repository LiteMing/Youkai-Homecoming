package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysis;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisProfile;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentResult;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellCostContext;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellPaymentRouter;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Certification service: quote computation and trial startup (design doc §5.2-5.4,
 * §13 cost model, §18 protocol). Server-authoritative; every parameter is
 * re-clamped here.
 */
public final class CertificationService {

	private CertificationService() {
	}

	// ------------------------------------------------------------ quote

	public static CertificationQuote quote(ServerPlayer player, SpellDefinition definition,
										   int requestedDurationTicks, double requestedHalfSize) {
		int durationTicks = clampDuration(requestedDurationTicks);
		double halfSize = clampHalfSize(requestedHalfSize);
		SpellAnalysis analysis = SpellAnalyzer.analyze(definition, SpellAnalysisProfile.CERTIFICATION);
		String hash = SpellHash.canonicalHash(definition);
		// Start fee is a fixed anti-spam toll (design §14), decoupled from spell power —
		// spam protection lives in maxTrialsPerPlayer / maxConcurrentTrials.
		long startCost = YHModConfig.COMMON.certificationStartCostUnits.get();
		// Issue fee scales mildly with spell power so certified casts cost a few
		// bombs/XP levels, not tens of thousands (Phase 7 balance).
		long issueCost = YHModConfig.COMMON.certificationIssueFeeEnabled.get()
				? scaledCastCost(analysis) : 0;
		return new CertificationQuote(UUID.randomUUID().toString(), hash, durationTicks, halfSize,
				startCost, issueCost, analysis, player.level().getGameTime());
	}

	/**
	 * Mildly scaled cast fee: projectile-ticks and per-tick spawns map to a few
	 * provider units after rate conversion (e.g. 100 units = 1 bomb/1 XP level).
	 */
	private static long scaledCastCost(SpellAnalysis analysis) {
		long ticks = Math.max(1, analysis.projectileTicks());
		long perTick = Math.max(1, analysis.maxSpawnPerTick());
		long units = (long) Math.ceil(ticks / 1_000_000.0) * 10
				+ (long) Math.ceil(perTick / 100.0) * 2;
		long discounted = (long) Math.ceil(units * proofMultiplier(
				YHModConfig.COMMON.certificationMaxDurationTicks.get(),
				YHModConfig.COMMON.certificationMaxArenaHalfSize.get()));
		return Math.max(1, discounted);
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
		if (!YHModConfig.COMMON.certificationEnabled.get()) return false;
		if (dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.isInDanmakuSession(player)) {
			// D15: a normal boss battle is running — cannot start a certification
			return false;
		}
		if (CertificationManager.INSTANCE.hasActiveTrial(player)) return false;
		PaymentResult payment = SpellPaymentRouter.pay(player, quote.startCostUnits(),
				SpellCostContext.CERTIFICATION_START);
		if (!payment.success()) return false;
		return spawnTrial(player, quote, payment.receipt());
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
}
