package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpecialNodeCounter;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysis;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisLimits;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisProfile;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalyzer;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisException;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapability;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCapabilityPolicies;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellDraftBudget;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHash;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.content.item.danmaku.SpellItemCost;
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
		if (definition.itemForm.cardType() == SpellCardType.NON_SPELL) {
			throw new SpellAnalysisException("Non-spells do not enter certification");
		}
		// Health and timeout are declaration data. The break chain is the only
		// successful certification path; timeout targets remain legal boss behavior
		// but any timeout fails the trial.
		SpellHealthPlan healthPlan = SpellHealthPlan.analyze(definition,
				dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry::get);
		int durationTicks = healthPlan.totalDurationTicks();
		int spellHp = healthPlan.totalHealth();
		// the arena half size is fixed by config (UI selection is ignored)
		double halfSize = YHModConfig.COMMON.certificationFixedArenaHalfSize.get();
		SpecialNodeCounter.Summary nodes = SpecialNodeCounter.summarize(healthPlan.definitions().values());
		SpellDraftBudget budget;
		SpellAnalysis analysis;
		if (operatorTest) {
			analysis = analyzePlan(healthPlan, true, java.util.Set.of());
			budget = operatorBudget(nodes);
		} else {
			budget = draftBudget(player, definition);
			if (budget == null) throw new SpellAnalysisException("Certification rejected: matching draft budget missing");
			analysis = analyzeSurvivalPlan(healthPlan, budget, nodes);
		}
		budget.validatePerformance(analysis, SpellAnalysisLimits.certification());
		String hash = SpellHash.canonicalBundleHash(healthPlan.definitions());
		// Start fee is a fixed anti-spam toll (design §14), decoupled from spell power —
		// spam protection lives in maxConcurrentTrials and quote expiration.
		long startCost = YHModConfig.COMMON.certificationStartCostUnits.get();
		// Cast/issue cost is duration-driven: 100-tick minimum, then
		// +0.2 BOMB / +1 XP per additional 20 ticks.
		int rewardDuration = rewardDurationTicks(durationTicks);
		long durationCost = dev.xkmc.youkaishomecoming.content.spell.payment.CastCost.unitsForDuration(rewardDuration);
		long castCost = saturatedAdd(durationCost, budget.nodeCostUnits(nodes));
		castCost = SpellItemCost.scaleTypeCost(castCost, definition.itemForm.cardType(),
				SpellCostContext.SPELL_CAST_NON_STG);
		long issueCost = YHModConfig.COMMON.certificationIssueFeeEnabled.get() ? castCost : 0;
		return new CertificationQuote(UUID.randomUUID().toString(), hash, durationTicks, halfSize,
				startCost, issueCost, castCost, rewardDuration,
				spellHp, budget.legacyExperimentalQuota(), budget, nodes,
				operatorTest, healthPlan, analysis, player.level().getGameTime());
	}

	@Nullable
	private static SpellDraftBudget draftBudget(ServerPlayer player, SpellDefinition definition) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
					&& !dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.isComplete(stack)
					&& !CertifiedSpellValidator.isCertified(stack)) {
				ResourceLocation bound = dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getSpellId(stack);
				if (bound != null && definition.id != null && definition.id.equals(bound)) {
					return dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getDraftBudget(stack);
				}
			}
		}
		return null;
	}

	private static SpellDraftBudget operatorBudget(SpecialNodeCounter.Summary nodes) {
		SpellAnalysisLimits limits = SpellAnalysisLimits.certification();
		return new SpellDraftBudget(nodes.ordinaryNodes(), limits.maxSpawnPerTick(),
				limits.maxPeakAlive(), limits.maxProjectileTicks(), limits.maxHookExecutions(),
				nodes.experimentalCount(SpellCapability.TELEPORT),
				nodes.experimentalCount(SpellCapability.ERASE_ENEMY_DANMAKU),
				nodes.experimentalCount(SpellCapability.CLEAR_SCREEN),
				nodes.experimentalCount(SpellCapability.BOSS_ON_DAMAGE),
				nodes.experimentalCount(SpellCapability.CONFINED_TARGET), 0);
	}

	public static boolean hasUnfinishedDraft(ServerPlayer player, @Nullable ResourceLocation definitionId) {
		return !findUnfinishedDraft(player, definitionId).isEmpty();
	}

	public static ItemStack findUnfinishedDraft(ServerPlayer player, @Nullable ResourceLocation definitionId) {
		if (definitionId == null) return ItemStack.EMPTY;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
					&& definitionId.equals(
					dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getSpellId(stack))
					&& !dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.isComplete(stack)
					&& !dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.isCertified(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
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
		boolean started = spawnTrial(player, quote, payment.receipt(), quote.spellHp(),
				quote.healthPlan().rootDefinition().itemForm.cardType() == SpellCardType.TIMEOUT_SPELL, true);
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
				|| !quoteStillValid(player, quote, requireDraft)) {
			if (startReceipt != null) {
				SpellPaymentRouter.refund(player, startReceipt);
			}
			return false;
		}
		ItemStack consumed = requireDraft ? consumeDraft(player, definition.id, quote.draftBudget()) : null;
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
		long durationCost = dev.xkmc.youkaishomecoming.content.spell.payment.CastCost.unitsForDuration(
				rewardDurationTicks(quote.durationTicks()));
		long castCost = saturatedAdd(durationCost, quote.draftBudget().nodeCostUnits(quote.nodeSummary()));
		castCost = SpellItemCost.scaleTypeCost(castCost,
				quote.healthPlan().rootDefinition().itemForm.cardType(), SpellCostContext.SPELL_CAST_NON_STG);
		long issueCost = YHModConfig.COMMON.certificationIssueFeeEnabled.get() ? castCost : 0;
		return quote.rewardDurationTicks() == rewardDurationTicks(quote.durationTicks())
				&& quote.castCostUnits() == castCost && quote.issueCostUnits() == issueCost;
	}

	private static boolean quoteStillValid(ServerPlayer player, CertificationQuote quote, boolean requireDraft) {
		if (quote.draftBudget() == null || quote.nodeSummary() == null || !quoteCostsMatchCurrentConfig(quote)) {
			return false;
		}
		if (requireDraft) {
			SpellDraftBudget current = draftBudget(player, quote.healthPlan().rootDefinition());
			if (!quote.draftBudget().equals(current)) return false;
		}
		try {
			SpecialNodeCounter.Summary currentNodes = SpecialNodeCounter.summarize(
					quote.healthPlan().definitions().values());
			if (!quote.nodeSummary().equals(currentNodes)) return false;
			SpellAnalysis currentAnalysis = quote.operatorTest()
					? analyzePlan(quote.healthPlan(), true, java.util.Set.of())
					: analyzeSurvivalPlan(quote.healthPlan(), quote.draftBudget(), currentNodes);
			quote.draftBudget().validatePerformance(currentAnalysis, SpellAnalysisLimits.certification());
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Removes one draft card (unfinished dynamic spell, not certified/complete)
	 * bound to this definition from the player's inventory; returns a copy for
	 * the fail-return. Null when no draft card is held (e.g. operator give cards).
	 */
	@Nullable
	private static ItemStack consumeDraft(ServerPlayer player, ResourceLocation definitionId,
			SpellDraftBudget expectedBudget) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() instanceof dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem
					&& definitionId != null && definitionId.equals(
					dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getSpellId(stack))
					&& !dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.isComplete(stack)
					&& !dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellValidator.isCertified(stack)
					&& expectedBudget.equals(dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem.getDraftBudget(stack))) {
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
		SpellAnalysisLimits configured = SpellAnalysisLimits.certification();
		// A health-plan quote is a finite trial. Project recurring work over the
		// break-chain duration, while keeping the configured window for explicit
		// no-timeout plans and legacy definitions without a health declaration.
		long projectionWindow = plan.totalDurationTicks() > 0
				? Math.min(configured.certificationWindowTicks(), plan.totalDurationTicks())
				: configured.certificationWindowTicks();
		SpellAnalysisLimits limits = configured.withCertificationWindow(projectionWindow);
		List<SpellAnalysis> analyses = new ArrayList<>();
		for (SpellDefinition definition : plan.definitions().values()) {
			analyses.add(operatorTest
					? SpellAnalyzer.analyzeOperatorTest(definition, limits)
					: SpellAnalyzer.analyze(definition, SpellAnalysisProfile.CERTIFICATION,
						limits, extraAllowed));
		}
		return SpellAnalysis.combine(analyses);
	}

	private static SpellAnalysis analyzeSurvivalPlan(SpellHealthPlan plan, SpellDraftBudget budget,
			SpecialNodeCounter.Summary nodes) {
		if (nodes.operatorOnlyNodes() > 0) {
			throw new SpellAnalysisException("Certification rejected: OP-only nodes " + nodes.operatorOnlyNodes());
		}
		if (nodes.deniedNodes() > 0) {
			throw new SpellAnalysisException("Certification rejected: denied nodes " + nodes.deniedNodes());
		}
		if (!budget.permitsExperimental(nodes)) {
			throw new SpellAnalysisException("Certification rejected: experimental capability grants exceeded");
		}
		java.util.Set<SpellCapability> granted = nodes.experimentalNodes() > 0
				? SpellCapabilityPolicies.experimentalCapabilities() : java.util.Set.of();
		SpellAnalysis analysis = analyzePlan(plan, false, granted);
		budget.validatePerformance(analysis, SpellAnalysisLimits.certification());
		return analysis;
	}

	static SpellAnalysis validateCertifiedPlan(SpellHealthPlan plan, SpellDraftBudget budget) {
		return analyzeSurvivalPlan(plan, budget,
				SpecialNodeCounter.summarize(plan.definitions().values()));
	}

	private static long saturatedAdd(long a, long b) {
		if (b <= 0) return a;
		return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
	}
}
