package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.collision.UserCacheHolder;
import dev.xkmc.fastprojectileapi.entity.EntityCachingUser;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.analysis.NonSpellValidator;
import dev.xkmc.youkaishomecoming.content.spell.analysis.NonSpellLimiterBypass;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellAnalysisException;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellCardRank;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellPermissionService;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.item.SpellContainer;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeHost;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHLangData;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A lightweight invisible proxy entity that acts as a danmaku emitter on behalf of a player.
 * <p>
 * When a player uses a {@link dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem},
 * instead of adding each danmaku as a real world entity, a single DanmakuProxyEntity is spawned.
 * It follows the player's position and view angle, drives the SpellRuntime, and manages all
 * danmaku in a virtual list (identical to YoukaiEntity's virtualization infrastructure).
 * <p>
 * Danmaku are rendered on clients via {@link DanmakuManager#send} batch packets,
 * the same path used by boss youkai entities.
 */
public class DanmakuProxyEntity extends PathfinderMob
		implements SpellRuntimeHost, EntityCachingUser, DanmakuHostProxy {

	// ==================== Virtual danmaku infrastructure ====================

	private final VirtualDanmakuHolder danmakuHolder = new VirtualDanmakuHolder();

	// ==================== Owner binding ====================

	@Nullable
	private UUID ownerPlayerId;
	@Nullable
	private ServerPlayer ownerPlayer;

	// ==================== Target tracking ====================

	@Nullable
	private UUID targetId;
	@Nullable
	private LivingEntity targetCache;
	@Nullable
	private Vec3 targetPos;

	// ==================== Spell driving ====================

	@Nullable
	private SpellRuntime runtime;
	private int spellTickCount = 0;
	private int maxDuration;
	private SpellCardType cardType = SpellCardType.NORMAL;
	private boolean certifiedCard;
	private boolean ending;
	/** True after a non-spell is toggled off; existing danmaku still drain. */
	private boolean generationStopped;
	/** Bound only by normal item casts; /yhspell proxy keeps its force-test behavior. */
	@Nullable
	private SpellCardRank nonSpellRank;
	private double validatedNonSpellPower = Double.NaN;
	private int validatedNonSpellPermission = -1;
	private int nonSpellSpawnLimit;
	private long nonSpellSpawnTick = Long.MIN_VALUE;
	private int nonSpellSpawnsThisTick;
	private boolean nonSpellLimiterBypassActive;
	@Nullable
	private String cardKey;

	public enum EndReason {
		TIMEOUT,
		SPELL_BREAK,
		PLAYER_CANCEL,
		EXTERNAL_ABORT
	}

	// ==================== Constructor ====================

	public DanmakuProxyEntity(EntityType<? extends DanmakuProxyEntity> type, Level level) {
		super(type, level);
		this.setInvisible(true);
		this.setSilent(true);
		this.setNoGravity(true);
		this.noPhysics = true;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0);
	}

	// ==================== Initialization ====================

	/**
	 * Set up this proxy for a player casting a dynamic spell.
	 * Must be called before adding the entity to the world.
	 */
	public void init(ServerPlayer player, SpellDefinition definition, int duration,
					 @Nullable LivingEntity target) {
		init(player, definition, duration, target, null);
	}

	public void init(ServerPlayer player, SpellDefinition definition, int duration,
						 @Nullable LivingEntity target, @Nullable SpellHealthPlan healthPlan) {
		init(player, definition, duration, target, healthPlan, null);
	}

	public void init(ServerPlayer player, SpellDefinition definition, int duration,
						 @Nullable LivingEntity target, @Nullable SpellHealthPlan healthPlan,
						 @Nullable Integer durationOverride) {
		init(player, definition, duration, target, healthPlan, durationOverride, false);
	}

	public void init(ServerPlayer player, SpellDefinition definition, int duration,
						 @Nullable LivingEntity target, @Nullable SpellHealthPlan healthPlan,
						 @Nullable Integer durationOverride, boolean certifiedCard) {
		init(player, definition, duration, target, healthPlan, durationOverride, certifiedCard, 4);
	}

	public void init(ServerPlayer player, SpellDefinition definition, int duration,
						 @Nullable LivingEntity target, @Nullable SpellHealthPlan healthPlan,
						 @Nullable Integer durationOverride, boolean certifiedCard, int permissionLevel) {
		this.ownerPlayerId = player.getUUID();
		this.ownerPlayer = player;
		this.maxDuration = duration;
		this.runtime = healthPlan == null ? new SpellRuntime(definition)
				: new SpellRuntime(definition, healthPlan::resolve, healthPlan);
		this.runtime.setPermissionLevel(permissionLevel);
		this.runtime.reset();
		this.runtime.setDurationOverride(durationOverride);
		this.spellTickCount = 0;
		this.cardType = definition.itemForm.cardType();
		this.certifiedCard = certifiedCard;
		this.ending = false;
		this.generationStopped = false;
		this.nonSpellRank = null;
		this.validatedNonSpellPower = Double.NaN;
		this.validatedNonSpellPermission = -1;
		this.nonSpellSpawnLimit = 0;
		this.nonSpellSpawnTick = Long.MIN_VALUE;
		this.nonSpellSpawnsThisTick = 0;
		this.nonSpellLimiterBypassActive = false;

		if (target != null) {
			this.targetId = target.getUUID();
			this.targetCache = target;
			this.targetPos = target.getEyePosition();
		}

		// Position at the player
		this.moveTo(player.position());
		this.setYRot(player.getYRot());
		this.setXRot(player.getXRot());
		this.setYHeadRot(player.getYHeadRot());
		if (target == null) {
			updateAimTarget(player);
		}
	}

	// ==================== Core tick ====================

	@Override
	public void tick() {
		// Minimal super.tick() — we skip most mob logic
		this.baseTick();

		if (level().isClientSide()) return;

		// Validate owner
		if (ownerPlayer == null || ownerPlayer.isRemoved() || !ownerPlayer.isAlive()) {
			resolveOwner();
			if (ownerPlayer == null) {
				cleanup(EndReason.EXTERNAL_ABORT);
				return;
			}
		}

		// Follow player position and orientation
		this.moveTo(ownerPlayer.position());
		this.setYRot(ownerPlayer.getYRot());
		this.setXRot(ownerPlayer.getXRot());
		this.setYHeadRot(ownerPlayer.getYHeadRot());

		// Refresh target tracking
		refreshTarget();

		// Drive the spell runtime while generation is enabled.  A stopped non-spell
		// keeps its runtime available to callbacks owned by already emitted
		// projectiles, but must not execute its cast loop or create new output.
		if (runtime != null && !generationStopped && refreshNonSpellBudget()) {
			// A fixed player-card duration ends the normal on_tick cast loop, but
			// held projectiles may still own persistent release callbacks. Keep the
			// proxy alive and advance only that callback queue until it drains.
			if (SpellProxyLifecycle.castLoopActive(maxDuration, spellTickCount, runtime.isFinished())) {
				runtime.tick(this);
			} else {
				runtime.tickDelayed(this);
			}
			applySpellMovement();
		}
		// Existing virtual projectiles continue to move, collide, expire, and run
		// their own callbacks after a non-spell key-up/toggle-off.
		tickDanmaku();

		if (SpellProxyLifecycle.shouldFinishStoppedGeneration(generationStopped,
				danmakuHolder.isEmpty())) {
			finishStoppedGeneration();
			return;
		}

		// Check for completion
		spellTickCount++;
		boolean runtimeFinished = runtime != null && runtime.isFinished();
		boolean pendingHold = runtime != null && runtime.hasPendingHoldActions();
		if (SpellProxyLifecycle.shouldCleanup(maxDuration, spellTickCount, runtimeFinished, pendingHold)) {
			cleanup(EndReason.TIMEOUT);
		} else if ((spellTickCount & 3) == 0 && ownerPlayer != null) {
			// The proxy owns the authoritative elapsed tick; refresh the Bossbar
			// after advancing the runtime rather than waiting for capability order.
			SpellContainer.refreshSpellBossBar(ownerPlayer, this);
		}
	}

	private void resolveOwner() {
		if (ownerPlayerId == null) return;
		if (!(level() instanceof ServerLevel sl)) return;
		var entity = sl.getEntity(ownerPlayerId);
		if (entity instanceof ServerPlayer sp && sp.isAlive()) {
			ownerPlayer = sp;
		} else {
			ownerPlayer = null;
		}
	}

	private void refreshTarget() {
		if (targetCache != null) {
			if (targetCache.isAlive()) {
				targetPos = targetCache.getEyePosition();
				return;
			} else {
				targetId = null;
				targetCache = null;
			}
		}
		if (targetId == null) {
			updateAimTarget(ownerPlayer);
			return;
		}
		if (!(level() instanceof ServerLevel sl)) return;
		var entity = sl.getEntity(targetId);
		if (entity instanceof LivingEntity le && le.isAlive()) {
			targetCache = le;
			targetPos = le.getEyePosition();
		} else {
			targetId = null;
			updateAimTarget(ownerPlayer);
		}
	}

	private void updateAimTarget(ServerPlayer player) {
		targetPos = GrazeHelper.getAimTarget(player, center());
	}

	/** Bind the rank at the authoritative item boundary, before adding the proxy. */
	public void bindNonSpellBudget(SpellCardRank rank) {
		nonSpellRank = rank;
		validatedNonSpellPower = Double.NaN;
		validatedNonSpellPermission = -1;
		nonSpellLimiterBypassActive = NonSpellLimiterBypass.isEnabled(ownerPlayer);
	}

	private boolean refreshNonSpellBudget() {
		if (nonSpellRank == null) return true;
		if (runtime == null || ownerPlayer == null) return false;
		boolean bypass = NonSpellLimiterBypass.isEnabled(ownerPlayer);
		if (bypass != nonSpellLimiterBypassActive) {
			nonSpellLimiterBypassActive = bypass;
			// Force a fresh validation when an administrator turns the switch off.
			validatedNonSpellPower = Double.NaN;
		}
		double power = GrazeHelper.getEffectivePowerLevel(ownerPlayer);
		if (bypass) {
			nonSpellSpawnLimit = Integer.MAX_VALUE;
			return true;
		}
		int permission = SpellPermissionService.effectiveLevel(ownerPlayer);
		if (Double.compare(power, validatedNonSpellPower) != 0 || permission != validatedNonSpellPermission) {
			try {
				// Recheck before executing count-dependent loops at the new Power.
				NonSpellValidator.validateForPlayer(runtime.getDefinition(), nonSpellRank, power, permission);
				validatedNonSpellPower = power;
				validatedNonSpellPermission = permission;
			} catch (SpellAnalysisException rejected) {
				ownerPlayer.displayClientMessage(DynamicSpellItem.nonSpellRejectedMessage(rejected), false);
				SpellContainer.clearActiveNonSpell(ownerPlayer);
				return false;
			} catch (RuntimeException unexpected) {
				YoukaisHomecoming.LOGGER.warn("Unexpected live non-spell validation failure for {}",
						runtime.getDefinition().id, unexpected);
				ownerPlayer.displayClientMessage(YHLangData.NON_SPELL_REJECTED_UNKNOWN.get(), false);
				SpellContainer.clearActiveNonSpell(ownerPlayer);
				return false;
			}
		}
		nonSpellSpawnLimit = nonSpellRank.danmakuPerTick(power);
		return true;
	}

	/** Root actions, delayed callbacks and child shooters share one world-tick allowance. */
	boolean reserveNonSpellSpawn(long gameTime, int limit) {
		if (nonSpellSpawnTick != gameTime) {
			nonSpellSpawnTick = gameTime;
			nonSpellSpawnsThisTick = 0;
		}
		if (nonSpellSpawnsThisTick >= limit) return false;
		nonSpellSpawnsThisTick++;
		return true;
	}

	@Override
	public ShooterEntity prepareShooter(ShooterData data, SpellCard spell) {
		ShooterEntity shooter = SpellRuntimeHost.super.prepareShooter(data, spell);
		if (nonSpellRank != null) shooter.bindNonSpellHost(this);
		return shooter;
	}

	// ==================== Virtual danmaku methods (from YoukaiEntity) ====================

	@Override
	public void shoot(Entity danmaku) {
		if (isRemoved() || generationStopped || nonSpellRank != null
				&& (!refreshNonSpellBudget() || !nonSpellLimiterBypassActive
				&& !reserveNonSpellSpawn(level().getGameTime(), nonSpellSpawnLimit))) {
			if (danmaku instanceof SimplifiedProjectile projectile) {
				projectile.markErased(true);
			} else {
				danmaku.discard();
			}
			return;
		}
		if (danmaku instanceof ItemDanmakuEntity e) {
			if (e.afterExpiry != null) {
				e.afterExpiry.setup(this);
			}
		}
		if (danmaku instanceof ItemLaserEntity e) {
			if (e.afterExpiry != null) {
				e.afterExpiry.setup(this);
			}
		}
		if (!danmakuHolder.shoot(danmaku)) {
			SpellRuntimeHost.super.shoot(danmaku);
		}
	}

	private void tickDanmaku() {
		danmakuHolder.tickDanmaku(this, shooter());
	}

	@Override
	public int activeDanmakuCount() {
		return danmakuHolder.activeProjectileCount();
	}

	public void eraseAllDanmaku(@Nullable Player player) {
		eraseAllDanmakuAndCount(player);
	}

	public int eraseAllDanmakuAndCount(@Nullable Player player) {
		return danmakuHolder.eraseAllDanmakuAndCount(this, player);
	}

	public int eraseDanmakuInRadius(Vec3 center, double radius, @Nullable Player player) {
		return danmakuHolder.eraseDanmakuInRadius(this, center, radius, player);
	}

	public void countDanmakuInFrustum(dev.xkmc.youkaishomecoming.compat.exposure.DanmakuFrustum frustum, int limit, dev.xkmc.youkaishomecoming.compat.exposure.EraseResult result) {
		danmakuHolder.countDanmakuInFrustum(this, frustum, limit, result, getSpellDefinitionId());
	}

	public void eraseDanmakuInFrustum(dev.xkmc.youkaishomecoming.compat.exposure.DanmakuFrustum frustum, @Nullable Player player, int limit) {
		danmakuHolder.eraseDanmakuInFrustum(this, frustum, player, limit);
	}

	public void switchSpellDefinition(SpellDefinition definition, boolean clearScreen) {
		if (clearScreen) {
			eraseAllDanmaku(null);
		}
		setSpellRuntime(new SpellRuntime(definition));
	}

	@Nullable
	@Override
	public ResourceLocation getSpellDefinitionId() {
		return runtime == null ? null : runtime.getDefinition().id;
	}

	@Override
	public UserCacheHolder entityCache() {
		return danmakuHolder.entityCache();
	}

	// ==================== LivingCardHolder implementation ====================

	@Override
	public LivingEntity self() {
		return this;
	}

	@Override
	public Vec3 center() {
		// The tiny proxy follows the player's feet. Fire along the player's sightline
		// instead, matching the eye anchor used by DanmakuHitBox for living targets.
		// Explicit offsets still apply relative to this pose-dependent firing origin.
		return ownerPlayer == null ? SpellRuntimeHost.super.center()
				: ownerPlayer.getEyePosition();
	}

	@Override
	public LivingEntity shooter() {
		return ownerPlayer != null ? ownerPlayer : this;
	}

	@Override
	public double casterPower() {
		resolveOwner();
		return ownerPlayer == null ? 0 : GrazeHelper.getEffectivePowerLevel(ownerPlayer);
	}

	@Nullable
	@Override
	public LivingEntity owner() {
		resolveOwner();
		return ownerPlayer;
	}

	@Nullable
	@Override
	public SpellRuntime getSpellRuntime() {
		return runtime;
	}

	@Override
	public void setSpellRuntime(@Nullable SpellRuntime runtime) {
		this.runtime = runtime;
		if (runtime != null && ownerPlayer != null) {
			runtime.setPermissionLevel(SpellPermissionService.effectiveLevel(ownerPlayer));
		}
		validatedNonSpellPower = Double.NaN;
		validatedNonSpellPermission = -1;
		nonSpellLimiterBypassActive = false;
	}

	@Override
	public boolean restrictsManualMovement() {
		return !generationStopped && SpellRuntimeHost.super.restrictsManualMovement();
	}

	@Override
	public void eraseDanmaku(@Nullable Player player) {
		eraseAllDanmaku(player);
	}

	@Override
	public void syncSpellState() {
		if (ownerPlayer != null && runtime != null) {
			SpellContainer.syncRuntimeSpellBar(ownerPlayer, this);
		}
	}

	@Override
	public boolean isBossHost() {
		return false;
	}

	@Override
	public boolean isOwnedBy(@Nullable Player player) {
		return player != null && ownerPlayerId != null && ownerPlayerId.equals(player.getUUID());
	}

	@Override
	public @Nullable LivingEntity targetEntity() {
		return targetCache;
	}

	@Override
	public @Nullable Vec3 target() {
		return targetPos;
	}

	@Override
	public float getDamage(YHDanmaku.IDanmakuType type) {
		return type.damage();
	}

	// ==================== Lifecycle ====================

	/**
	 * Clean up all virtual danmaku and remove this proxy entity from the world.
	 */
	public void cleanup() {
		cleanup(EndReason.EXTERNAL_ABORT);
	}

	public void cleanup(EndReason reason) {
		if (ending || isRemoved()) return;
		ending = true;
		ServerPlayer owner = ownerPlayer;
		eraseAllDanmaku(null);
		this.discard();
		if (owner != null) {
			SpellContainer.onProxyEnded(owner, this,
					reason == null ? EndReason.EXTERNAL_ABORT : reason);
		}
	}

	/**
	 * Disable future cast-loop output without erasing projectiles already in
	 * flight.  Global combat cleanup must continue to use {@link #cleanup}.
	 */
	public void stopGenerationPreserveDanmaku() {
		if (ending || isRemoved()) return;
		generationStopped = true;
	}

	public boolean isGenerationStopped() {
		return generationStopped;
	}

	public boolean isGenerating() {
		return !generationStopped && !isRemoved();
	}

	private void finishStoppedGeneration() {
		if (ending || isRemoved()) return;
		ending = true;
		// The holder is already empty, so discard cannot trigger the safety-net
		// erase path in remove(). onProxyRemoved drops the active-card marker.
		discard();
	}

	/**
	 * Safety net: ensure virtual danmaku are erased no matter how this entity is removed.
	 * Covers: /kill, void fall (checkBelowWorld → discard), chunk unload, or any
	 * unexpected removal path that bypasses {@link #cleanup()}.
	 */
	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && ownerPlayer != null) {
			SpellContainer.onProxyRemoved(ownerPlayer, this);
		}
		clearTemporarySpellCircle();
		if (!danmakuHolder.isEmpty()) {
			eraseAllDanmaku(null);
		}
		super.remove(reason);
	}

	/**
	 * @return true if this proxy has finished its spell and all danmaku have expired
	 */
	public boolean isFinished() {
		if (isRemoved()) return true;
		if (generationStopped) return danmakuHolder.isEmpty();
		boolean runtimeFinished = runtime != null && runtime.isFinished();
		boolean pendingHold = runtime != null && runtime.hasPendingHoldActions();
		return SpellProxyLifecycle.isFinished(maxDuration, spellTickCount,
				runtimeFinished, pendingHold, danmakuHolder.isEmpty());
	}

	public int spellElapsedTicks() {
		if (runtime != null && runtime.getSpellHealthTotal() > 0) {
			return runtime.getSpellElapsedTicks();
		}
		return Math.max(0, spellTickCount);
	}

	/** Zero means natural end / no fixed countdown. */
	public int spellDurationTicks() {
		if (runtime != null && runtime.getSpellHealthTotal() > 0) {
			return Math.max(0, runtime.getSpellDurationTicks());
		}
		return Math.max(0, maxDuration);
	}

	public SpellCardType cardType() {
		return cardType;
	}

	public boolean isCertifiedCard() {
		return certifiedCard;
	}

	public boolean isExSpell() {
		return runtime != null && runtime.getDefinition().itemForm.exSpell();
	}

	public void bindCardKey(@Nullable String cardKey) {
		this.cardKey = cardKey;
	}

	@Nullable
	public String cardKey() {
		return cardKey;
	}

	// ==================== Entity properties: invisible, invulnerable, no AI ====================

	@Override
	protected void registerGoals() {
		// No AI goals
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean isInvisible() {
		return true;
	}

	@Override
	public boolean isInvisibleTo(Player player) {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	// ==================== Serialization (minimal — entity is transient) ====================

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (ownerPlayerId != null) {
			tag.putUUID("OwnerPlayer", ownerPlayerId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.hasUUID("OwnerPlayer")) {
			ownerPlayerId = tag.getUUID("OwnerPlayer");
		}
		// Runtime is not persisted — proxy will be cleaned up after server restart
		// since shouldBeSaved() returns false, this is just a safety fallback
	}
}
