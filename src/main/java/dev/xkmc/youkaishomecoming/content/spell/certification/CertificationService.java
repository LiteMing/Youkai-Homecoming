package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysis;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisLimits;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisProfile;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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

	public static CertificationQuote quote(ServerPlayer player, SpellDefinition definition) {
		return quote(player, definition, false);
	}

	/** OP-only /yhdev quote: keeps certification limits but permits operator actions. */
	public static CertificationQuote quoteOperatorTest(ServerPlayer player, SpellDefinition definition) {
		return quote(player, definition, true);
	}

	private static CertificationQuote quote(ServerPlayer player, SpellDefinition definition,
									 boolean operatorTest) {
		// Health and timeout are declaration data. The break chain is the only
		// successful certification path; timeout targets remain legal boss behavior
		// but any timeout fails the trial.
		SpellHealthPlan healthPlan = SpellHealthPlan.analyze(definition,
				dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry::get);
		int durationTicks = healthPlan.totalDurationTicks();
		int spellHp = healthPlan.totalHealth();
		// the arena half size is fixed by config (UI selection is ignored)
		double halfSize = YHModConfig.COMMON.certificationFixedArenaHalfSize.get();
		// Special-node quota: EXPERIMENTAL capabilities (teleport, erase, clear,
		// on_damage) are denied by default; a boss-drop draft card may carry a
		// quota (the count of such nodes in the boss's own definition).
		// run_command and unrestricted control nodes stay operator-only.
		int specialNodeQuota = draftOpQuota(player, definition);
		SpellAnalysis analysis;
		try {
			analysis = analyzePlan(healthPlan, operatorTest, java.util.Set.of());
		} catch (dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisException e) {
			if (operatorTest) throw e;
			int count = healthPlan.definitions().values().stream()
					.mapToInt(SpecialNodeCounter::count).sum();
			if (count > 0 && count <= specialNodeQuota) {
				// the draft quota covers the special nodes: re-run with those
				// capabilities allowed (hard performance limits still apply).
				analysis = analyzePlan(healthPlan, false, SpecialNodeCounter.EXPERIMENTAL_CAPS);
			} else {
				throw e;
			}
		}
		String hash = SpellHash.canonicalBundleHash(healthPlan.definitions());
		// Start fee is a fixed anti-spam toll (design §14), decoupled from spell power —
		// spam protection lives in maxConcurrentTrials and quote expiration.
		long startCost = YHModConfig.COMMON.certificationStartCostUnits.get();
		// Cast/issue cost is duration-driven: 100-tick minimum, then
		// +0.2 BOMB / +1 XP per additional 20 ticks.
		int rewardDuration = rewardDurationTicks(durationTicks);
		long castCost = dev.xkmc.youkaishomecoming.content.spell.payment.CastCost.unitsForDuration(rewardDuration);
		long issueCost = YHModConfig.COMMON.certificationIssueFeeEnabled.get() ? castCost : 0;
		return new CertificationQuote(UUID.randomUUID().toString(), hash, durationTicks, halfSize,
				startCost, issueCost, castCost, rewardDuration,
				spellHp, specialNodeQuota, operatorTest, healthPlan, analysis, player.level().getGameTime());
	}

	/**
	 * Special-node quota carried by the spell card in the player's inventory:
	 * first a card bound to this definition, then any blank quota card.
	 */
	private static int draftOpQuota(ServerPlayer player, SpellDefinition definition) {
		int blankQuota = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
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

	public static boolean hasUnfinishedDraft(ServerPlayer player, @Nullable ResourceLocation definitionId) {
		if (definitionId == null) return false;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
					&& definitionId.equals(
					dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getSpellId(stack))
					&& !dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.isComplete(stack)
					&& !dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.isCertified(stack)) {
				return true;
			}
		}
		return false;
	}

	public static int clampDuration(int requested) {
		return Math.max(0, Math.min(1200, requested));
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
		if (!hasUnfinishedDraft(player, quote.healthPlan().rootDefinition().id)) {
			LOGGER.info("[YH] start rejected: matching unfinished draft is no longer present");
			return false;
		}
		PaymentResult payment = SpellPaymentRouter.pay(player, quote.startCostUnits(),
				SpellCostContext.CERTIFICATION_START);
		if (!payment.success()) {
			LOGGER.info("[YH] start rejected: payment failed: {}", payment);
			return false;
		}
		boolean started = spawnTrial(player, quote, payment.receipt(), quote.spellHp(), false, true);
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
		return startFree(player, quote, (float) quote.spellHp());
	}

	/** OP test path with optional break health; null means no-hit survival completion. */
	public static boolean startFree(ServerPlayer player, CertificationQuote quote, @Nullable Float breakHealth) {
		if (!YHModConfig.COMMON.certificationEnabled.get()) return false;
		if (inRealBattle(player)) return false;
		if (CertificationManager.INSTANCE.hasActiveTrial(player)) return false;
		int hp = breakHealth == null ? quote.spellHp() : Math.max(1, Math.round(breakHealth));
		return spawnTrial(player, quote, null, hp, breakHealth == null, false);
	}

	private static boolean spawnTrial(ServerPlayer player, CertificationQuote quote,
									  @Nullable PaymentReceipt startReceipt, int breakHealth,
									  boolean timeoutCompletes, boolean requireDraft) {
		SpellDefinition definition = CertificationManager.INSTANCE.getQuoteDefinition(quote.quoteId());
		if (definition == null) {
			if (startReceipt != null) {
				SpellPaymentRouter.refund(player, startReceipt);
			}
			return false;
		}
		// Re-check the cached definition and current bounds at the payment boundary;
		// registry reloads or config changes must invalidate an old quote.
		if (!quote.definitionHash().equals(SpellHash.canonicalBundleHash(quote.healthPlan().definitions()))
				|| quote.durationTicks() != clampDuration(quote.durationTicks())
				|| quote.arenaHalfSize() != YHModConfig.COMMON.certificationFixedArenaHalfSize.get()
				|| !quoteCostsMatchCurrentConfig(quote)) {
			if (startReceipt != null) {
				SpellPaymentRouter.refund(player, startReceipt);
			}
			return false;
		}
		ItemStack consumed = requireDraft ? consumeDraft(player, definition.id) : null;
		if (requireDraft && consumed == null) {
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
			maxHealth.setBaseValue(Math.max(1, breakHealth));
		}
		entity.setHealth(entity.getMaxHealth());
		// the certification enemy glows so the player can always find their target
		entity.setGlowingTag(true);
		entity.setPos(player.position());
		entity.initCertification(player, definition, definitionHash, quote, movementSeed, timeoutCompletes);
		CertificationController controller = entity.controller();
		if (!CertificationManager.INSTANCE.register(player, controller)) {
			if (startReceipt != null) {
				SpellPaymentRouter.refund(player, startReceipt);
			}
			returnConsumedDraft(player, consumed);
			return false;
		}
		if (!player.level().addFreshEntity(entity)) {
			CertificationManager.INSTANCE.remove(player.getUUID());
			if (startReceipt != null) SpellPaymentRouter.refund(player, startReceipt);
			returnConsumedDraft(player, consumed);
			entity.discard();
			return false;
		}
		if (startReceipt != null) {
			controller.setStartReceipt(startReceipt);
		}
		// Starting a survival certification consumes the player's draft card; a
		// failed trial returns it along the same path as the reward.
		if (consumed != null) {
			controller.setConsumedDraft(consumed);
		}
		controller.beginPrepare();
		return true;
	}

	private static boolean quoteCostsMatchCurrentConfig(CertificationQuote quote) {
		long castCost = dev.xkmc.youkaishomecoming.content.spell.payment.CastCost.unitsForDuration(
				rewardDurationTicks(quote.durationTicks()));
		long issueCost = YHModConfig.COMMON.certificationIssueFeeEnabled.get() ? castCost : 0;
		return quote.rewardDurationTicks() == rewardDurationTicks(quote.durationTicks())
				&& quote.castCostUnits() == castCost && quote.issueCostUnits() == issueCost;
	}

	/**
	 * Removes one draft card (unfinished dynamic spell, not certified/complete)
	 * bound to this definition from the player's inventory; returns a copy for
	 * the fail-return. Null when no draft card is held (e.g. operator give cards).
	 */
	@Nullable
	private static ItemStack consumeDraft(ServerPlayer player, ResourceLocation definitionId) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
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

	private static void returnConsumedDraft(ServerPlayer player, @Nullable ItemStack stack) {
		if (stack != null && !stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack);
		}
	}

	public static ResourceLocation startProvider() {
		return new ResourceLocation(YHModConfig.COMMON.certificationStartPaymentProvider.get());
	}

	/**
	 * Certified reward duration is the exact health-plan timeout preview.
	 */
	public static int rewardDurationTicks(int certifiedDurationTicks) {
		return Math.max(0, Math.min(1200, certifiedDurationTicks));
	}

	private static SpellAnalysis analyzePlan(SpellHealthPlan plan, boolean operatorTest,
			java.util.Set<dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability> extraAllowed) {
		List<SpellAnalysis> analyses = new ArrayList<>();
		for (SpellDefinition definition : plan.definitions().values()) {
			analyses.add(operatorTest
					? SpellAnalyzer.analyzeOperatorTest(definition, SpellAnalysisLimits.certification())
					: SpellAnalyzer.analyze(definition, SpellAnalysisProfile.CERTIFICATION,
						SpellAnalysisLimits.certification(), extraAllowed));
		}
		return mergeAnalyses(analyses);
	}

	private static SpellAnalysis mergeAnalyses(List<SpellAnalysis> analyses) {
		long spawns = 0;
		long projectileTicks = 0;
		int peakAlive = 0;
		int maxSpawn = 0;
		long hooks = 0;
		long expressionOps = 0;
		double serverWork = 0;
		double renderWork = 0;
		double gameplayPower = 0;
		var capabilities = EnumSet.noneOf(dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability.class);
		var diagnostics = new ArrayList<dev.xkmc.youkaishomecoming.content.spell.analysis.SpellDiagnostic>();
		for (SpellAnalysis analysis : analyses) {
			spawns = saturatedAdd(spawns, analysis.totalSpawnUpperBound());
			projectileTicks = saturatedAdd(projectileTicks, analysis.projectileTicks());
			peakAlive = Math.max(peakAlive, analysis.peakAliveUpperBound());
			maxSpawn = Math.max(maxSpawn, analysis.maxSpawnPerTick());
			hooks = saturatedAdd(hooks, analysis.hookExecutionUpperBound());
			expressionOps = saturatedAdd(expressionOps, analysis.expressionOps());
			serverWork += analysis.serverWork();
			renderWork += analysis.clientRenderWork();
			gameplayPower += analysis.gameplayPower();
			capabilities.addAll(analysis.requiredCapabilities());
			diagnostics.addAll(analysis.diagnostics());
		}
		return new SpellAnalysis(spawns, projectileTicks, peakAlive, maxSpawn, hooks,
				expressionOps, serverWork, renderWork, gameplayPower,
				java.util.Set.copyOf(capabilities), List.copyOf(diagnostics));
	}

	private static long saturatedAdd(long a, long b) {
		if (b <= 0) return a;
		return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
	}
}
