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
	@Nullable private PaymentReceipt startReceipt;
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
		syncState();
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
		if (dev.xkmc.youkaishomecoming.compat.stg.YHStgApi.hasActiveYoukaiSession(author)) {
			// another boss battle entered mid-certification (D15)
			fail(e, CertificationFailReason.OTHER_BATTLE);
			return;
		}
		// movement + runtime loop
		movement.tick(e);
		if (e.spellRuntime == null || e.spellRuntime.isFinished()) {
			// spell naturally ended: restart it for the remaining certification time (§5.4)
			e.setSpellRuntime(new SpellRuntime(definition));
		}
		if (e.getDanmakuHolder() != null && e.getDanmakuHolder().activeProjectileCount() > 0) {
			activeThreatTicks++;
		}
		elapsedTicks++;
		if (elapsedTicks >= quote.durationTicks()) {
			success(e);
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
		refund();
		CertificationManager.INSTANCE.remove(authorId);
	}

	private void success(SpellCertificationEntity e) {
		state = CertificationState.SUCCESS;
		e.eraseAllDanmaku(null);
		e.getDanmakuHolder().clearSentQueue();
		postCertificationEvent();
		syncState();
		CertifiedSpellRewardService.issue(e);
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
		state = reason == CertificationFailReason.SYSTEM_ERROR
				|| reason == CertificationFailReason.RUNTIME_LIMIT
				? CertificationState.SYSTEM_ERROR : CertificationState.FAILED;
		refund();
		e.eraseAllDanmaku(null);
		e.getDanmakuHolder().clearSentQueue();
		postCertificationEvent();
		syncState();
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
				failReason == null ? null : failReason.id());
	}
}
