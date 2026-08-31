package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.collision.UserCacheHolder;
import dev.xkmc.fastprojectileapi.entity.EntityCachingUser;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeHost;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A general-purpose spell proxy entity that can attach to any entity and drive a
 * {@link SpellRuntime}, emitting virtual danmaku on behalf of that entity.
 * <p>
 * Use cases:
 * <ul>
 *   <li>"Ender Dragon that casts spell cards" - attach to an EnderDragon</li>
 *   <li>Spell trap blocks - spawn without a host at a fixed position</li>
 *   <li>Any custom entity that needs danmaku abilities without implementing SpellRuntimeHost</li>
 * </ul>
 * <p>
 * Unlike {@link DanmakuProxyEntity} (which is player-bound), this proxy:
 * <ul>
 *   <li>Follows any entity's position (or stays fixed for block traps)</li>
 *   <li>Sends danmaku packets to players tracking the <em>host</em> entity</li>
 *   <li>Selects targets automatically (nearest hostile player) or from a fixed reference</li>
 * </ul>
 */
public class EntitySpellProxyEntity extends PathfinderMob
		implements SpellRuntimeHost, EntityCachingUser, DanmakuHostProxy {

	// ==================== Virtual danmaku infrastructure ====================

	private final VirtualDanmakuHolder danmakuHolder = new VirtualDanmakuHolder();

	// ==================== Host binding ====================

	@Nullable
	private UUID hostId;
	@Nullable
	private Entity hostEntity;
	private boolean destroyWhenHostDies = true;

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
	private int maxDuration = -1;

	// ==================== Constructor ====================

	public EntitySpellProxyEntity(EntityType<? extends EntitySpellProxyEntity> type, Level level) {
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
	 * Attach this proxy to a host entity. The proxy will follow the host's position.
	 */
	public void attachTo(Entity host, SpellDefinition definition, int duration,
						  @Nullable LivingEntity target) {
		this.hostId = host.getUUID();
		this.hostEntity = host;
		this.maxDuration = duration;
		this.runtime = new SpellRuntime(definition);
		this.runtime.reset();
		this.spellTickCount = 0;
		setTarget(target);

		copyHostTransform(host);
	}

	/**
	 * Spawn this proxy at a fixed position for block traps.
	 * No host entity: the proxy stays at the given position.
	 */
	public void spawnAtPosition(Vec3 position, float yRot, SpellDefinition definition,
								 int duration, @Nullable LivingEntity target) {
		spawnAtPosition(position, yRot, 0, definition, duration, target);
	}

	/**
	 * Spawn this proxy at a fixed position with an explicit rotation.
	 */
	public void spawnAtPosition(Vec3 position, float yRot, float xRot, SpellDefinition definition,
								 int duration, @Nullable LivingEntity target) {
		this.hostId = null;
		this.hostEntity = null;
		this.maxDuration = duration;
		this.runtime = new SpellRuntime(definition);
		this.runtime.reset();
		this.spellTickCount = 0;
		setTarget(target);

		this.moveTo(position);
		this.setYRot(yRot);
		this.setXRot(xRot);
		this.setYHeadRot(yRot);
	}

	public void setDestroyWhenHostDies(boolean value) {
		this.destroyWhenHostDies = value;
	}

	@Nullable
	public Entity attachedHost() {
		if (hostEntity == null && hostId != null) {
			resolveHost();
		}
		return hostEntity;
	}

	public boolean isFixedPositionProxy() {
		return hostId == null && hostEntity == null;
	}

	public void setTarget(@Nullable LivingEntity target) {
		if (target == null) {
			targetId = null;
			targetCache = null;
			targetPos = null;
			return;
		}
		targetId = target.getUUID();
		targetCache = target;
		targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
	}

	// ==================== Core tick ====================

	@Override
	public void tick() {
		this.baseTick();
		if (level().isClientSide()) return;

		if (!validateHost()) {
			return;
		}

		// Follow host position
		if (hostEntity != null) {
			copyHostTransform(hostEntity);
		}

		// Refresh target tracking
		refreshTarget();

		// Auto-select target if none set
		if (targetCache == null || !targetCache.isAlive()) {
			autoSelectTarget();
		}

		// Drive the spell runtime
		if (runtime != null) {
			// Stop ordinary phase actions at the fixed duration while retaining the
			// proxy for persistent hold-release callbacks.
			if (SpellProxyLifecycle.castLoopActive(maxDuration, spellTickCount, runtime.isFinished())) {
				runtime.tick(this);
			} else {
				runtime.tickDelayed(this);
			}
			applySpellMovement();
			tickDanmaku();
		}

		// Check for completion
		spellTickCount++;
		boolean runtimeFinished = runtime != null && runtime.isFinished();
		boolean pendingHold = runtime != null && runtime.hasPendingHoldActions();
		if (SpellProxyLifecycle.shouldCleanup(maxDuration, spellTickCount, runtimeFinished, pendingHold)) {
			cleanup();
		}
	}

	private boolean validateHost() {
		if (hostEntity == null && hostId != null) {
			resolveHost();
		}
		if (hostEntity == null) {
			if (hostId == null) {
				return true;
			}
			if (destroyWhenHostDies) {
				cleanup();
				return false;
			}
			hostId = null;
			return true;
		}
		boolean dying = hostEntity instanceof LivingEntity living && living.deathTime > 0;
		if (!hostEntity.isRemoved() && hostEntity.isAlive() && !dying) {
			return true;
		}
		if (destroyWhenHostDies) {
			cleanup();
			return false;
		}
		hostEntity = null;
		hostId = null;
		return true;
	}

	private void resolveHost() {
		if (hostId == null || !(level() instanceof ServerLevel sl)) return;
		Entity entity = sl.getEntity(hostId);
		if (entity != null && entity.isAlive() && !entity.isRemoved()) {
			hostEntity = entity;
		}
	}

	private void copyHostTransform(Entity host) {
		this.moveTo(host.position());
		this.setYRot(host.getYRot());
		this.setXRot(host.getXRot());
		this.setYHeadRot(host instanceof LivingEntity le ? le.getYHeadRot() : host.getYRot());
	}

	private void autoSelectTarget() {
		if (!(level() instanceof ServerLevel)) return;
		Vec3 searchCenter = hostEntity != null ? hostEntity.position() : position();
		AABB searchBox = AABB.ofSize(searchCenter, 64, 64, 64);
		List<Player> players = level().getEntitiesOfClass(Player.class, searchBox,
				this::canAutoTarget);
		if (!players.isEmpty()) {
			Player nearest = null;
			double bestDistSq = Double.MAX_VALUE;
			for (Player p : players) {
				double distSq = p.distanceToSqr(searchCenter);
				if (distSq < bestDistSq) {
					bestDistSq = distSq;
					nearest = p;
				}
			}
			if (nearest != null) {
				setTarget(nearest);
			}
		}
	}

	private boolean canAutoTarget(Player player) {
		if (!player.isAlive() || !player.isAddedToWorld() || player.isSpectator()) return false;
		if (player == hostEntity) return false;
		return !(hostEntity instanceof LivingEntity owner) || !owner.isAlliedTo(player);
	}

	private void refreshTarget() {
		if (targetCache != null) {
			if (targetCache.isAlive()) {
				targetPos = targetCache.position().add(0, targetCache.getBbHeight() / 2, 0);
				return;
			} else {
				targetId = null;
				targetCache = null;
			}
		}
		if (targetId == null) {
			targetPos = null;
			return;
		}
		if (!(level() instanceof ServerLevel sl)) return;
		var entity = sl.getEntity(targetId);
		if (entity instanceof LivingEntity le && le.isAlive()) {
			targetCache = le;
			targetPos = le.position().add(0, le.getBbHeight() / 2, 0);
		} else {
			targetId = null;
			targetPos = null;
		}
	}

	// ==================== Virtual danmaku methods ====================

	@Override
	public void shoot(Entity danmaku) {
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
		// Use host as tracking target so packets go to players watching the host
		danmakuHolder.tickDanmaku(trackingHost(), shooter());
	}

	public void eraseAllDanmaku(@Nullable Player player) {
		danmakuHolder.eraseAllDanmaku(trackingHost(), player);
	}

	public int eraseAllDanmakuAndCount(@Nullable Player player) {
		return danmakuHolder.eraseAllDanmakuAndCount(trackingHost(), player);
	}

	public int eraseDanmakuInRadius(Vec3 center, double radius, @Nullable Player player) {
		return danmakuHolder.eraseDanmakuInRadius(trackingHost(), center, radius, player);
	}

	public void countDanmakuInFrustum(dev.xkmc.youkaishomecoming.compat.exposure.DanmakuFrustum frustum, int limit, dev.xkmc.youkaishomecoming.compat.exposure.EraseResult result) {
		danmakuHolder.countDanmakuInFrustum(trackingHost(), frustum, limit, result, getSpellDefinitionId());
	}

	public void eraseDanmakuInFrustum(dev.xkmc.youkaishomecoming.compat.exposure.DanmakuFrustum frustum, @Nullable Player player, int limit) {
		danmakuHolder.eraseDanmakuInFrustum(trackingHost(), frustum, player, limit);
	}

	private LivingEntity trackingHost() {
		return hostEntity instanceof LivingEntity le && !le.isRemoved() ? le : this;
	}

	@Override
	public int activeDanmakuCount() {
		return danmakuHolder.activeProjectileCount();
	}

	// ==================== LivingCardHolder implementation ====================

	@Override
	public LivingEntity self() {
		return trackingHost();
	}

	@Override
	public LivingEntity shooter() {
		return trackingHost();
	}

	@Override
	public double casterPower() {
		Entity host = attachedHost();
		return host instanceof Player player ? GrazeHelper.getEffectivePowerLevel(player) : 0;
	}

	@Nullable
	@Override
	public LivingEntity owner() {
		return trackingHost();
	}

	@Nullable
	@Override
	public SpellRuntime getSpellRuntime() {
		return runtime;
	}

	@Override
	public void setSpellRuntime(@Nullable SpellRuntime runtime) {
		this.runtime = runtime;
	}

	@Override
	public void eraseDanmaku(@Nullable Player player) {
		eraseAllDanmaku(player);
	}

	@Override
	public void syncSpellState() {
	}

	@Override
	public boolean isBossHost() {
		return false;
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

	@Override
	public UserCacheHolder entityCache() {
		return danmakuHolder.entityCache();
	}

	// ==================== Lifecycle ====================

	public void cleanup() {
		eraseAllDanmaku(null);
		this.discard();
	}

	@Override
	public void remove(RemovalReason reason) {
		clearTemporarySpellCircle();
		if (!danmakuHolder.isEmpty()) {
			eraseAllDanmaku(null);
		}
		super.remove(reason);
	}

	public boolean isFinished() {
		if (isRemoved()) return true;
		boolean runtimeFinished = runtime != null && runtime.isFinished();
		boolean pendingHold = runtime != null && runtime.hasPendingHoldActions();
		return SpellProxyLifecycle.isFinished(maxDuration, spellTickCount,
				runtimeFinished, pendingHold, danmakuHolder.isEmpty());
	}

	// ==================== Entity properties ====================

	@Override
	protected void registerGoals() {
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

	// ==================== Serialization ====================

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (hostId != null) {
			tag.putUUID("Host", hostId);
		}
		if (targetId != null) {
			tag.putUUID("Target", targetId);
		}
		tag.putBoolean("DestroyWhenHostDies", destroyWhenHostDies);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.hasUUID("Host")) {
			hostId = tag.getUUID("Host");
		}
		if (tag.hasUUID("Target")) {
			targetId = tag.getUUID("Target");
		}
		destroyWhenHostDies = !tag.contains("DestroyWhenHostDies") || tag.getBoolean("DestroyWhenHostDies");
	}
}
