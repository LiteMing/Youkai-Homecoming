package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.entity.youkai.SpellCertificationEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentReceipt;
import dev.xkmc.youkaishomecoming.content.spell.payment.PaymentResult;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellCostContext;
import dev.xkmc.youkaishomecoming.content.spell.payment.SpellPaymentRouter;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.server.level.ServerPlayer;
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

	private final SpellCertificationEntity entity;
	private final ServerPlayer author;
	private final UUID authorId;
	private final SpellDefinition definition;
	private final String definitionHash;
	private final CertificationQuote quote;
	private final CertificationArena arena;
	private final long movementSeed;
	private final CertificationEnemyMovement movement;

	private CertificationState state = CertificationState.DEPOSIT_PAID;
	private int countdown;
	private int elapsedTicks;
	private int activeThreatTicks;
	private int illegalMoveCooldown;
	@Nullable private CertificationFailReason failReason;
	private @Nullable PaymentReceipt startReceipt;
	private boolean combatForcedByCertification;

	public void setStartReceipt(PaymentReceipt receipt) {
		this.startReceipt = receipt;
	}
	@Nullable private Vec3 lastPlayerPos;
	private long lastSyncTick = -1;

	public CertificationController(SpellCertificationEntity entity, ServerPlayer author,
								   SpellDefinition definition, String definitionHash,
								   CertificationQuote quote, long movementSeed) {
		this.entity = entity;
		this.author = author;
		this.authorId = author.getUUID();
		this.definition = definition;
		this.definitionHash = definitionHash;
		this.quote = quote;
		this.arena = new CertificationArena(entity.position(), quote.arenaHalfSize());
		this.movementSeed = movementSeed;
		this.movement = new CertificationEnemyMovement(arena, movementSeed);
	}

	// ------------------------------------------------------------ accessors

	public CertificationState state() {
		return state;
	}

	public boolean isActive() {
		return state == CertificationState.ACTIVE;
	}

	public int elapsedTicks() {
		return elapsedTicks;
	}

	public SpellDefinition definition() { return definition; }
	public CertificationQuote quote() { return quote; }
	public int targetTicks() {
		return quote.durationTicks();
	}

	public int spellHp() {
		return quote.spellHp();
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

	// ------------------------------------------------------------ lifecycle

	public void beginPrepare() {
		state = CertificationState.PREPARE;
		countdown = YHModConfig.COMMON.certificationCountdownTicks.get();
		// the author becomes the enemy's target so spell aim (AimMode.Target) works,
		// and enters STG combat state so the battle circle / resources display (D3/D4)
		entity.targets.add(author);
		dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.setDanmakuCombat(author, true);
		combatForcedByCertification = true;
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
			e.setSpellRuntime(new SpellRuntime(definition));
			logState("active start");
			author.displayClientMessage(dev.xkmc.youkaishomecoming.init.data.YHLangData.CERT_ACTIVE.get(quote.durationTicks() / 20), false);
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
		if (illegalMoveCooldown <= 0 && lastPlayerPos != null) {
			double moved = author.position().distanceToSqr(lastPlayerPos);
			double max = YHModConfig.COMMON.certificationMaxDisplacementPerTick.get();
			if (moved > max * max) {
				fail(e, CertificationFailReason.ILLEGAL_MOVE);
				return;
			}
		}
		illegalMoveCooldown = 10;
		lastPlayerPos = author.position();
		// During the spell release the player may not move at all: there is no
		// spell-declared player motion yet, so every tick zeroes their velocity
		// (the certification enemy moves only when the spell declares
		// caster_moves — the spell's specified motion).
		author.setDeltaMovement(0, 0, 0);
		if (dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.hasActiveYoukaiSession(author)) {
			// another boss battle entered mid-certification (D15)
			fail(e, CertificationFailReason.OTHER_BATTLE);
			return;
		}
		// movement + runtime loop — the certification enemy stands still by
		// default so the player can attack it down; it only moves when the spell
		// declares caster_moves.
		if (definition.itemForm.casterMoves()) {
			movement.tick(e);
		}
		if (e.spellRuntime == null || e.spellRuntime.isFinished()) {
			// spell naturally ended: restart it for the remaining certification time (§5.4)
			e.setSpellRuntime(new SpellRuntime(definition));
		}
		if (e.getDanmakuHolder() != null && e.getDanmakuHolder().activeProjectileCount() > 0) {
			activeThreatTicks++;
		}
		elapsedTicks++;
		// timeout countdown: failing to break the spell before the timeout is a loss
		if (elapsedTicks >= quote.durationTicks()) {
			fail(e, CertificationFailReason.TIMEOUT);
		} else if ((elapsedTicks & 63) == 0) {
			logState("tick");
		}
	}

	private void tickSuccess(SpellCertificationEntity e) {
		// Phase 4 wires reward issuance here; cleanup happens after the reward flow.
		cleanup(e, true);
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
		// Must restore forced danmaku combat here too: the player would otherwise
		// be stuck in D15 (certification start always rejected) after the enemy
		// entity is killed or unloaded mid-trial.
		restoreCombatState();
		refund();
		CertificationManager.INSTANCE.remove(authorId);
	}

	private void success(SpellCertificationEntity e) {
		state = CertificationState.SUCCESS;
		e.eraseAllDanmaku(null);
		e.getDanmakuHolder().clearSentQueue();
		restoreCombatState();
		logState("success");
		postCertificationEvent();
		author.displayClientMessage(dev.xkmc.youkaishomecoming.init.data.YHLangData.CERT_SUCCESS.get(), false);
		syncState();
		try {
			CertifiedSpellRewardService.issue(e);
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
		}
		logState("fail " + reason.id());
		state = reason == CertificationFailReason.SYSTEM_ERROR
				|| reason == CertificationFailReason.RUNTIME_LIMIT
				? CertificationState.SYSTEM_ERROR : CertificationState.FAILED;
		refund();
		e.eraseAllDanmaku(null);
		e.getDanmakuHolder().clearSentQueue();
		postCertificationEvent();
		author.displayClientMessage(dev.xkmc.youkaishomecoming.init.data.YHLangData.CERT_FAIL.get(reason.id()), false);
		syncState();
	}

	/** HIT failure: full danmaku battle defeat flow (design doc §5.6). */
	private void defeatPlayer() {
		combatForcedByCertification = false;
		entity.targets.remove(author.getUUID());
		dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.defeat(author);
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
		restoreCombatState();
		logState("cleanup");
		if (!success && startReceipt != null && failReason != null && failReason.fullRefund()) {
			// refund already handled in fail(); guard against double refund
			startReceipt = null;
		}
		e.eraseAllDanmaku(null);
		if (e.getDanmakuHolder() != null) e.getDanmakuHolder().clearSentQueue();
		CertificationManager.INSTANCE.remove(authorId);
		e.discard();
	}

	private void syncState() {
		dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationStateToClient.send(
				entity, state, elapsedTicks, quote.durationTicks(),
				(int) entity.getMaxHealth(), (int) Math.max(0, entity.getHealth()),
				failReason == null ? null : failReason.id());
	}
}
