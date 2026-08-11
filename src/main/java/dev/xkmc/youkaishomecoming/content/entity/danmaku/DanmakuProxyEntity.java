package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.collision.UserCacheHolder;
import dev.xkmc.fastprojectileapi.entity.EntityCachingUser;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.analysis.SpellHealthPlan;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeHost;
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
		this.ownerPlayerId = player.getUUID();
		this.ownerPlayer = player;
		this.maxDuration = duration;
		this.runtime = healthPlan == null ? new SpellRuntime(definition)
				: new SpellRuntime(definition, healthPlan::resolve, healthPlan);
		this.runtime.reset();
		this.spellTickCount = 0;

		if (target != null) {
			this.targetId = target.getUUID();
			this.targetCache = target;
			this.targetPos = target.position().add(0, target.getBbHeight() / 2, 0);
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
				cleanup();
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

		// Drive the spell runtime
		if (runtime != null) {
			runtime.tick(this);
			applySpellMovement();
			tickDanmaku();
		}

		// Check for completion
		spellTickCount++;
		boolean naturalEnd = maxDuration < 0 && runtime != null && runtime.isFinished();
		boolean timedOut = maxDuration >= 0 && spellTickCount >= maxDuration;
		if (naturalEnd || timedOut) {
			cleanup();
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
				targetPos = targetCache.position().add(0, targetCache.getBbHeight() / 2, 0);
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
			targetPos = le.position().add(0, le.getBbHeight() / 2, 0);
		} else {
			targetId = null;
			updateAimTarget(ownerPlayer);
		}
	}

	private void updateAimTarget(ServerPlayer player) {
		targetPos = GrazeHelper.getAimTarget(player, center());
	}

	// ==================== Virtual danmaku methods (from YoukaiEntity) ====================

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
		danmakuHolder.countDanmakuInFrustum(frustum, limit, result);
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
	public LivingEntity shooter() {
		return ownerPlayer != null ? ownerPlayer : this;
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
		eraseAllDanmaku(null);
		this.discard();
	}

	/**
	 * Safety net: ensure virtual danmaku are erased no matter how this entity is removed.
	 * Covers: /kill, void fall (checkBelowWorld → discard), chunk unload, or any
	 * unexpected removal path that bypasses {@link #cleanup()}.
	 */
	@Override
	public void remove(RemovalReason reason) {
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
		return isRemoved() || (spellTickCount >= maxDuration && danmakuHolder.isEmpty());
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
