package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentReceipt;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentResult;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellCostContext;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellPaymentRouter;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellMovementDirective;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server-authoritative certification state machine (design doc §7).
 * <p>
 * DRAFT → QUOTED → DEPOSIT_PAID → PREPARE → ACTIVE → SUCCESS|FAILED|ABORTED|
 * SYSTEM_ERROR. Every transition validates preconditions; cleanup is unified so
 * /kill, unload, disconnect or dimension changes never leak virtual danmaku.
 */
public class CertificationController {
	private static final int DISPLAY_ITEM_SYNC_INTERVAL = 2;

	private final SpellCertificationEntity entity;
	private final ServerPlayer author;
	private final UUID authorId;
	private final SpellDefinition definition;
	private final SpellHealthPlan healthPlan;
	private final String definitionHash;
	private final CertificationQuote quote;
	private final CertificationArena arena;
	private final long movementSeed;
	private final CertificationEnemyMovement movement;
	private final boolean timeoutCompletes;
	private final net.minecraft.server.level.ServerBossEvent bossEvent = new net.minecraft.server.level.ServerBossEvent(
			Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

	private CertificationState state = CertificationState.DEPOSIT_PAID;
	private int countdown;
	private int elapsedTicks;
	private int successTicks;
	private int activeThreatTicks;
	@Nullable private CertificationFailReason failReason;
	private @Nullable PaymentReceipt startReceipt;
	private boolean combatForcedByCertification;
	/** The draft card consumed at trial start; returned (same path as the reward) when the trial fails. */
	@Nullable private ItemStack consumedDraft;
	/** Floating spell-card display following the (invisible) certification enemy. */
	@Nullable private net.minecraft.world.entity.item.ItemEntity displayItem;

	public void setStartReceipt(PaymentReceipt receipt) {
		this.startReceipt = receipt;
	}

	/** The draft card consumed at trial start; returned on failure. */
	public void setConsumedDraft(ItemStack draft) {
		this.consumedDraft = draft.copy();
	}
	@Nullable private Vec3 lastPlayerPos;
	private long lastSyncTick = -1;

	public CertificationController(SpellCertificationEntity entity, ServerPlayer author,
								   SpellDefinition definition, String definitionHash,
								   CertificationQuote quote, long movementSeed, boolean timeoutCompletes) {
		this.entity = entity;
		this.author = author;
		this.authorId = author.getUUID();
		this.definition = definition;
		this.healthPlan = quote.healthPlan();
		this.definitionHash = definitionHash;
		this.quote = quote;
		this.arena = new CertificationArena(entity.position(), quote.arenaHalfSize());
		this.movementSeed = movementSeed;
		this.movement = new CertificationEnemyMovement(arena, movementSeed);
		this.timeoutCompletes = timeoutCompletes;
	}

	// ------------------------------------------------------------ accessors

	public CertificationState state() {
		return state;
	}

	public boolean isActive() {
		return state == CertificationState.ACTIVE;
	}

	public boolean isSuccess() {
		return state == CertificationState.SUCCESS;
	}

	public int elapsedTicks() {
		return elapsedTicks;
	}

	public SpellDefinition definition() { return definition; }
	public SpellHealthPlan healthPlan() { return healthPlan; }
	public CertificationQuote quote() { return quote; }
	public int targetTicks() {
		return quote.durationTicks();
	}

	public int spellHp() {
		return quote.spellHp();
	}

	public boolean canBeBroken() {
		return !timeoutCompletes;
	}

	@Nullable
	public CertificationFailReason failReason() {
		return failReason;
	}

	public ServerPlayer author() {
		return author;
	}

	public UUID authorId() {
		return authorId;
	}

	public String definitionHash() {
		return definitionHash;
	}

	public long movementSeed() {
		return movementSeed;
	}

	/**
	 * Applies the directive selected by the just-finished spell tick to the
	 * certification entity. The submitted spell belongs to the certification
	 * enemy; it must not take ownership of the player's input movement.
	 */
	public void applySpellMovement(SpellCertificationEntity e, SpellMovementDirective directive) {
		if (state != CertificationState.ACTIVE) return;
		SpellMovementDirective.Mode mode = directive.mode();
		if (mode == SpellMovementDirective.Mode.RANDOM) {
			movement.tick(e);
			return;
		}

		if (mode == SpellMovementDirective.Mode.NONE) {
			e.setDeltaMovement(Vec3.ZERO);
			return;
		}

		Vec3 displacement = directive.displacement();
		double max = YHModConfig.COMMON.certificationMaxDisplacementPerTick.get();
		if (displacement.lengthSqr() > max * max) {
			displacement = displacement.normalize().scale(max);
		}
		Vec3 next = e.position().add(displacement);
		Vec3 bounded = arena.clamp(next, YHModConfig.COMMON.certificationEnemyBoundaryMargin.get());
		Vec3 delta = bounded.subtract(e.position());
		if (delta.lengthSqr() > 1.0e-8) {
			e.move(MoverType.SELF, delta);
		}
		e.setDeltaMovement(Vec3.ZERO);
	}

	// ------------------------------------------------------------ lifecycle

	public void beginPrepare() {
		state = CertificationState.PREPARE;
		countdown = YHModConfig.COMMON.certificationCountdownTicks.get();
		bossEvent.addPlayer(author);
		bossEvent.setVisible(true);
		// the author becomes the enemy's target so spell aim (AimMode.Target) works,
		// and enters STG combat state so the battle circle / resources display (D3/D4)
		entity.targets.add(author);
		dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.setDanmakuCombat(author, true);
		combatForcedByCertification = true;
		// the floating spell card: the consumed draft (or a blank card) hovers at
		// the enemy's spot — the only visible representation of the enemy
		if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			logState("begin prepare");
			author.displayClientMessage(dev.xkmc.youkaishomecoming.init.data.YHLangData.CERT_PREPARE.get(countdown / 20), false);
			syncState();
			return;
		}
		ItemStack shown = consumedDraft != null && !consumedDraft.isEmpty()
				? consumedDraft.copy()
				: new ItemStack(dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku.DYNAMIC_SPELL.get());
		displayItem = new dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellRewardService.SpellDisplayItem(
				level, entity.getX(), entity.getY() + 0.5, entity.getZ(), shown);
		level.addFreshEntity(displayItem);
		logState("begin prepare");
		author.displayClientMessage(dev.xkmc.youkaishomecoming.init.data.YHLangData.CERT_PREPARE.get(countdown / 20), false);
		syncState();
	}

	private void logState(String event) {
		dev.xkmc.youkaishomecoming.init.YoukaisHomecoming.LOGGER.info(
				"[Certification] {} state={} elapsed={}/{} threat={}",
				event, state, elapsedTicks, quote.durationTicks(), activeThreatTicks);
	}

	public void tick(SpellCertificationEntity e) {
		if (e.level().isClientSide) return;
		// lock-on: the certification enemy always faces the player while the
		// trial prepares or runs (death animation takes over afterwards)
		if (state == CertificationState.PREPARE || state == CertificationState.ACTIVE) {
			e.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
					author.getEyePosition());
		}
		// the floating spell card follows the enemy exactly
		if (displayItem != null && !displayItem.isRemoved()) {
			displayItem.setPos(e.getX(), e.getY() + 0.5, e.getZ());
			if (e.level() instanceof net.minecraft.server.level.ServerLevel level
					&& e.tickCount % DISPLAY_ITEM_SYNC_INTERVAL == 0) {
				level.getChunkSource().broadcastAndSend(displayItem,
						new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(displayItem));
			}
		}
		long gameTime = e.level().getGameTime();
		if (gameTime - lastSyncTick >= 20) {
			lastSyncTick = gameTime;
			syncState();
		}
		switch (state) {
			case PREPARE -> tickPrepare(e);
			case ACTIVE -> tickActive(e);
			case SUCCESS -> tickSuccess(e);
			case FAILED, ABORTED, SYSTEM_ERROR -> tickTerminal(e);
			default -> {
			}
		}
	}

	private void tickPrepare(SpellCertificationEntity e) {
		if (!checkAliveAndConnected()) {
			fail(e, CertificationFailReason.EXITED);
			return;
		}
		if (countdown-- <= 0) {
			state = CertificationState.ACTIVE;
			elapsedTicks = 0;
			activeThreatTicks = 0;
			lastPlayerPos = author.position();
			// fresh runtime: spell loops are restarted by the controller (D6/D5)
			e.setSpellRuntime(new SpellRuntime(definition,
					healthPlan::resolve,
					healthPlan));
			logState("active start");
			author.displayClientMessage(quote.durationTicks() > 0
					? YHLangData.CERT_ACTIVE.get((quote.durationTicks() + 19) / 20)
					: YHLangData.CERT_ACTIVE_INFINITE.get(), false);
			syncState();
		}
	}

	private void tickActive(SpellCertificationEntity e) {
		if (!checkAliveAndConnected()) {
			fail(e, CertificationFailReason.EXITED);
			return;
		}
		if (!arena.contains(author.position())) {
			fail(e, CertificationFailReason.OUT_OF_ARENA);
			return;
		}
		if (lastPlayerPos != null) {
			double moved = author.position().distanceToSqr(lastPlayerPos);
			double max = YHModConfig.COMMON.certificationMaxDisplacementPerTick.get();
			if (moved > max * max) {
				fail(e, CertificationFailReason.ILLEGAL_MOVE);
				return;
			}
		}
		lastPlayerPos = author.position();
		if (dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.hasActiveYoukaiSession(author)) {
			// another boss battle entered mid-certification (D15)
			fail(e, CertificationFailReason.OTHER_BATTLE);
			return;
		}
		if (e.spellRuntime == null || e.spellRuntime.isFinished()) {
			// spell naturally ended: restart it for the remaining certification time (§5.4)
			e.setSpellRuntime(new SpellRuntime(definition));
		}
		if (e.getDanmakuHolder() != null && e.getDanmakuHolder().activeProjectileCount() > 0) {
			activeThreatTicks++;
		}
		elapsedTicks++;
		// Operator no-hit tests complete by surviving the whole duration. Normal
		// certification and tests with break health still require a break in time.
		if (quote.durationTicks() > 0 && elapsedTicks >= quote.durationTicks()) {
			if (timeoutCompletes) success(e);
			else fail(e, CertificationFailReason.TIMEOUT);
		} else if ((elapsedTicks & 63) == 0) {
			logState("tick");
		}
	}

	private void tickSuccess(SpellCertificationEntity e) {
		// the defeat animation (beaten, exactly 1 second) plays before cleanup
		successTicks++;
		if (successTicks >= 20) {
			cleanup(e, true);
		}
	}

	private void tickTerminal(SpellCertificationEntity e) {
		cleanup(e, false);
	}

	// ------------------------------------------------------------ transitions

	public void onProjectileContact(ServerPlayer target) {
		if (state != CertificationState.ACTIVE) return;
		if (!target.getUUID().equals(authorId)) return;
		fail(entity, CertificationFailReason.HIT);
	}

	/**
	 * The certification enemy's plain health reached zero: the spell is broken
	 * (requires the whole run to be no-hit/no-bomb — any hit or bomb already
	 * failed the trial).
	 */
	public void onSpellBroken() {
		if (state != CertificationState.ACTIVE) return;
		success(entity);
	}

	/** Any declared segment timeout fails certification, regardless of its boss transition target. */
	public void onSpellTimeout() {
		if (state != CertificationState.ACTIVE) return;
		fail(entity, CertificationFailReason.TIMEOUT);
	}

	/** Player cast a different spell card mid-trial: no-bomb/no-hit forbids it. */
	public void onPlayerCastsOtherSpell() {
		if (state != CertificationState.ACTIVE) return;
		fail(entity, CertificationFailReason.OTHER_SPELL);
	}

	/** Player used a bomb mid-trial: no-bomb/no-hit forbids it. */
	public void onPlayerBomb() {
		if (state != CertificationState.ACTIVE) return;
		fail(entity, CertificationFailReason.BOMB);
	}

	public void abort() {
		if (state == CertificationState.SUCCESS || state == CertificationState.FAILED
				|| state == CertificationState.ABORTED || state == CertificationState.SYSTEM_ERROR) {
			return;
		}
		failReason = CertificationFailReason.ABORTED;
		state = CertificationState.ABORTED;
		syncState();
	}

	/** Entity removal (e.g. /kill, chunk unload, server stop) mid-flight: refund and
	 * clean up without leaking danmaku (design doc §7 unified cleanup). */
	public void abortIfUnfinished() {
		if (state == CertificationState.SUCCESS || state == CertificationState.FAILED
				|| state == CertificationState.ABORTED || state == CertificationState.SYSTEM_ERROR) {
			return;
		}
		failReason = CertificationFailReason.SYSTEM_ERROR;
		state = CertificationState.SYSTEM_ERROR;
		hideBossBar();
		// Removal can happen before the normal terminal tick. Run the same
		// resource/entity cleanup here so /kill and unload cannot leak projectiles,
		// the display item, or the consumed draft card.
		entity.eraseAllDanmaku(null);
		if (entity.getDanmakuHolder() != null) entity.getDanmakuHolder().clearSentQueue();
		removeDisplayItem();
		returnDraft(entity);
		// Must restore forced danmaku combat here too: the player would otherwise
		// be stuck in D15 (certification start always rejected) after the enemy
		// entity is killed or unloaded mid-trial.
		restoreCombatState();
		refund();
		CertificationManager.INSTANCE.remove(authorId);
	}

	private void success(SpellCertificationEntity e) {
		state = CertificationState.SUCCESS;
		successTicks = 0;
		e.eraseAllDanmaku(null);
		e.getDanmakuHolder().clearSentQueue();
		// the floating draft display is replaced by the actual reward item
		removeDisplayItem();
		// the broken spell card plays the defeat animation: beaten for exactly
		// 1 second (defeat -> falling -> prone) before the entity is cleaned up
		e.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				dev.xkmc.youkaishomecoming.init.registrate.YHEffects.BEATEN.get(), 20, 0));
		e.beginDanmakuDefeat();
		restoreCombatState();
		logState("success");
		postCertificationEvent();
		author.displayClientMessage(dev.xkmc.youkaishomecoming.init.data.YHLangData.CERT_SUCCESS.get(), false);
		syncState();
		try {
			if (!CertifiedSpellRewardService.issue(e)) {
				// Issuance fees are paid at the success boundary. If the player
				// disconnected or cannot pay, return the consumed draft instead of
				// creating an unbacked pending reward.
				returnDraft(e);
			}
		} catch (Exception ex) {
			dev.xkmc.youkaishomecoming.init.YoukaisHomecoming.LOGGER.error("Failed to issue certified reward", ex);
		}
	}

	private void postCertificationEvent() {
		if (!net.minecraftforge.fml.ModList.get().isLoaded("kubejs")) return;
		if (!dev.xkmc.youkaishomecoming.compat.kubejs.spell.YHSpellKubeJSEvents.CERTIFICATION.hasListeners()) return;
		dev.xkmc.youkaishomecoming.compat.kubejs.spell.YHSpellKubeJSEvents.CERTIFICATION.post(
				new dev.xkmc.youkaishomecoming.compat.kubejs.spell.CertificationEventJS(
						author, state, definitionHash,
						failReason == null ? "" : failReason.id()));
	}

	private void fail(SpellCertificationEntity e, CertificationFailReason reason) {
		if (state == CertificationState.SUCCESS || state == CertificationState.FAILED
				|| state == CertificationState.ABORTED || state == CertificationState.SYSTEM_ERROR) {
			return;
		}
		failReason = reason;
		// A No-Hit contact is a lost battle: run the full danmaku defeat flow
		// (resource reset, weak, beaten animation, Defeat event) instead of a
		// quiet combat-state restore.
		if (reason == CertificationFailReason.HIT) {
			defeatPlayer();
		} else {
			restoreCombatState();
			broadcastDanmakuDefeat();
		}
		logState("fail " + reason.id());
		state = reason == CertificationFailReason.SYSTEM_ERROR
				|| reason == CertificationFailReason.RUNTIME_LIMIT
				? CertificationState.SYSTEM_ERROR : CertificationState.FAILED;
		refund();
		e.eraseAllDanmaku(null);
		e.getDanmakuHolder().clearSentQueue();
		// a failed trial returns the consumed draft card along the same path as
		// the reward (glowing, weightless, owner-locked at the enemy spot)
		removeDisplayItem();
		returnDraft(e);
		postCertificationEvent();
		author.displayClientMessage(dev.xkmc.youkaishomecoming.init.data.YHLangData.CERT_FAIL.get(reason.displayName()), false);
		syncState();
	}

	/** HIT failure: full danmaku battle defeat flow (design doc §5.6). */
	private void defeatPlayer() {
		combatForcedByCertification = false;
		entity.targets.remove(author.getUUID());
		dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.defeat(author);
	}

	/**
	 * Return the consumed draft card as a floating, glowing, owner-locked item
	 * at the certification enemy's spot (same path as the certified reward).
	 */
	private void returnDraft(SpellCertificationEntity e) {
		if (consumedDraft == null || consumedDraft.isEmpty()) {
			return;
		}
		ItemStack stack = consumedDraft;
		consumedDraft = null;
		if (!(e.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		var item = new dev.xkmc.youkaishomecoming.content.spell.certification.CertifiedSpellRewardService.CertifiedRewardItem(
				level, e.getX(), e.getY() + 0.5, e.getZ(), stack, authorId);
		item.setNoGravity(true);
		item.setGlowingTag(true);
		item.setInvulnerable(true);
		item.setDeltaMovement(0, 0, 0);
		item.setPickUpDelay(0);
		level.addFreshEntity(item);
	}

	private void restoreCombatState() {
		if (combatForcedByCertification) {
			combatForcedByCertification = false;
			dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.setDanmakuCombat(author, false);
		}
		entity.targets.remove(author.getUUID());
	}

	private void refund() {
		if (startReceipt == null) return;
		long amount = startReceipt.amount();
		if (!failReason.fullRefund()) {
			double ratio = YHModConfig.COMMON.certificationRefundOnFailure.get();
			amount = (long) (amount * ratio);
		}
		if (amount > 0) {
			SpellPaymentRouter.refund(author, new PaymentReceipt(startReceipt.provider(),
					SpellCostContext.CERTIFICATION_START, amount));
		}
	}

	// ------------------------------------------------------------ helpers

	private boolean checkAliveAndConnected() {
		if (!author.level().players().contains(author)) return false;
		if (!author.isAlive()) return false;
		return author.level() == entity.level();
	}

	private void cleanup(SpellCertificationEntity e, boolean success) {
		if (e.isRemoved()) return;
		hideBossBar();
		restoreCombatState();
		logState("cleanup");
		removeDisplayItem();
		if (!success && startReceipt != null && failReason != null && failReason.fullRefund()) {
			// refund already handled in fail(); guard against double refund
			startReceipt = null;
		}
		e.eraseAllDanmaku(null);
		if (e.getDanmakuHolder() != null) e.getDanmakuHolder().clearSentQueue();
		CertificationManager.INSTANCE.remove(authorId);
		e.discard();
	}

	private void removeDisplayItem() {
		if (displayItem != null && !displayItem.isRemoved()) {
			displayItem.discard();
		}
		displayItem = null;
	}

	private void syncState() {
		int currentMaxHealth = entity.spellRuntime == null || entity.spellRuntime.getSpellMaxHealth() <= 0
				? (int) entity.getMaxHealth() : entity.spellRuntime.getSpellMaxHealth();
		syncBossBar(currentMaxHealth, (int) Math.max(0, entity.getHealth()));
		dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationStateToClient.send(
				entity, state, elapsedTicks, quote.durationTicks(),
				currentMaxHealth, (int) Math.max(0, entity.getHealth()),
				failReason == null ? null : failReason.id());
	}

	private void syncBossBar(int maxHealth, int health) {
		if (state != CertificationState.PREPARE && state != CertificationState.ACTIVE) {
			hideBossBar();
			return;
		}
		int remainingTicks = Math.max(0, quote.durationTicks() - elapsedTicks);
		bossEvent.setProgress(maxHealth <= 0 ? 0 : Math.max(0, Math.min(1, health / (float) maxHealth)));
		bossEvent.setName(quote.durationTicks() > 0
				? YHLangData.CERT_BOSSBAR.get(definition.display.displayName(), health, maxHealth,
				(remainingTicks + 19) / 20)
				: YHLangData.CERT_BOSSBAR_INFINITE.get(definition.display.displayName(), health, maxHealth));
		bossEvent.setVisible(true);
	}

	private void hideBossBar() {
		bossEvent.setVisible(false);
		bossEvent.removeAllPlayers();
	}

	private void broadcastDanmakuDefeat() {
		if (!author.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)
				|| !author.level().players().contains(author)) return;
		author.server.getPlayerList().broadcastSystemMessage(YHLangData.STG_DEFEAT.get(author.getDisplayName()), false);
	}
}
