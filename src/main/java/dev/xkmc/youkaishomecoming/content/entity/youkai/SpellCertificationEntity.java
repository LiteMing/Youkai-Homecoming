package dev.xkmc.youkaishomecoming.content.entity.youkai;

import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationContactGateway;
import dev.xkmc.youkaishomecoming.content.spell.certification.CertificationController;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Dedicated certification enemy (design doc §6). Inherits YoukaiEntity so the
 * OYSM/TLM render chain, spell runtime lifecycle and spell circles work, but
 * disables all ordinary youkai/boss semantics:
 * <ul>
 *   <li>cannot be hurt or killed (actuallyHurt/die are no-ops);</li>
 *   <li>no AI goals, no loot, no boss bar, no persistence (shouldBeSaved false);</li>
 *   <li>danmaku only hits the creator (shouldHurt by author id, D13);</li>
 *   <li>No-Hit contacts funnel through {@link CertificationContactGateway} (D8).</li>
 * </ul>
 */
public class SpellCertificationEntity extends GeneralYoukaiEntity {

	@Nullable
	private UUID authorId;
	@Nullable
	private CertificationController controller;

	public SpellCertificationEntity(EntityType<? extends GeneralYoukaiEntity> type, Level level) {
		super(type, level);
		// Certification target hovers in place (no gravity) so player shots can
		// reliably land; knockback is disabled for the same reason (INV-3: gravity
		// handling is explicit — floating is the intended behaviour for the target).
		setNoGravity(true);
	}

	@Override
	public void knockback(double strength, double dx, double dz) {
		// the certification target never gets knocked back by player attacks
	}

	/**
	 * Must be called before addFreshEntity. movementSeed is locked at payment time
	 * and drives the dedicated waypoint RandomSource (D5).
	 */
	public void initCertification(ServerPlayer author, SpellDefinition definition, String definitionHash,
								  dev.xkmc.youkaishomecoming.content.spell.certification.CertificationQuote quote,
								  long movementSeed) {
		this.authorId = author.getUUID();
		this.controller = new CertificationController(this, author, definition, definitionHash, quote, movementSeed);
	}

	@Nullable
	public CertificationController controller() {
		return controller;
	}

	@Nullable
	public UUID authorId() {
		return authorId;
	}

	// ------------------------------------------------------------ disabled semantics

	@Override
	protected void registerGoals() {
		// no AI goals: movement is server-authoritative waypoints
	}

	@Override
	public boolean shouldTickSpell() {
		return controller != null && controller.isActive();
	}

	@Override
	public boolean shouldHurt(LivingEntity target) {
		// certification danmaku may only hit the creator (D13)
		return target instanceof ServerPlayer p && p.getUUID().equals(authorId);
	}

	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		// The certification enemy cannot be killed, but it CAN be attacked: each
		// DANMAKU hit removes exactly 1 second of break HP (the player breaks the
		// spell with their own danmaku — melee/other damage sources are ignored).
		if (!level().isClientSide && controller != null && isDanmakuDamage(source)) {
			controller.onEntityHit();
		}
	}

	private static boolean isDanmakuDamage(DamageSource source) {
		if (source.getDirectEntity() instanceof IYHDanmaku) {
			return true;
		}
		return source.is(dev.xkmc.youkaishomecoming.init.data.YHDamageTypes.DANMAKU_TYPE);
	}

	@Override
	public void die(DamageSource source) {
		// only the state machine may end a certification
	}

	@Override
	public void danmakuHitTarget(IYHDanmaku self, DamageSource source, LivingEntity target) {
		// primary contact entry is IYHDanmaku.hurtTarget (D8); this is the fallback
		// for any path that still reaches the entity gateway
		if (controller != null && target instanceof ServerPlayer p) {
			CertificationContactGateway.onCertificationContact(this, p);
		}
	}

	@Override
	public void aiStep() {
		if (!level().isClientSide() && controller != null) {
			controller.tick(this);
		}
		if (controller == null || controller.isActive()) {
			super.aiStep();
		}
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPersistenceRequired() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide && controller != null && !isRemoved()) {
			// unified cleanup path (design doc §7): never leak virtual danmaku
			controller.abortIfUnfinished();
		}
		super.remove(reason);
	}
}
