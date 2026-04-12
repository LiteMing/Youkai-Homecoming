package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.collision.UserCacheHolder;
import dev.xkmc.fastprojectileapi.entity.EntityCachingUser;
import dev.xkmc.fastprojectileapi.entity.ParallelTicker;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.virtual.DanmakuManager;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
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

import java.util.ArrayList;
import java.util.LinkedList;
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
		implements LivingCardHolder, EntityCachingUser {

	// ==================== Virtual danmaku infrastructure (copied from YoukaiEntity) ====================

	private final LinkedList<SimplifiedProjectile> allDanmakus = new LinkedList<>();
	private ArrayList<SimplifiedProjectile> temp;
	private final ArrayList<SimplifiedProjectile> toBeSent = new ArrayList<>();
	private boolean removeDanmaku = false;
	private final UserCacheHolder cache = new UserCacheHolder();

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
		this.ownerPlayerId = player.getUUID();
		this.ownerPlayer = player;
		this.maxDuration = duration;
		this.runtime = new SpellRuntime(definition);
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

	// ==================== Virtual danmaku methods (from YoukaiEntity) ====================

	@Override
	public void shoot(Entity danmaku) {
		if (danmaku instanceof ItemDanmakuEntity e) {
			if (e.afterExpiry != null) {
				e.afterExpiry.setup(this);
			}
		}
		if (danmaku instanceof SimplifiedProjectile proj) {
			if (temp != null) temp.add(proj);
			else allDanmakus.add(proj);
			toBeSent.add(proj);
		} else {
			LivingCardHolder.super.shoot(danmaku);
		}
	}

	private void tickDanmaku() {
		removeDanmaku = false;
		temp = new ArrayList<>();
		var preheatCache = level() instanceof ServerLevel sl ? cache.get(sl, shooter()) : null;
		ParallelTicker.tickAll(allDanmakus, () -> removeDanmaku, preheatCache);
		if (!removeDanmaku) {
			var itr = allDanmakus.iterator();
			while (itr.hasNext()) {
				var e = itr.next();
				if (e.isAddedToWorld() && !e.isRemoved()) continue;
				if (!e.isValid()) {
					itr.remove();
				}
			}
			allDanmakus.addAll(temp);
			DanmakuManager.send(this, toBeSent);
		}
		temp = null;
		toBeSent.clear();
		DanmakuManager.flushErases();
	}

	public void eraseAllDanmaku(@Nullable Player player) {
		for (var e : allDanmakus) {
			if (player == null) e.markErased(true);
			else e.erase(player);
		}
		allDanmakus.clear();
		removeDanmaku = true;
		DanmakuManager.flushErases();
	}

	@Override
	public UserCacheHolder entityCache() {
		return cache;
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
		if (!allDanmakus.isEmpty()) {
			eraseAllDanmaku(null);
		}
		super.remove(reason);
	}

	/**
	 * @return true if this proxy has finished its spell and all danmaku have expired
	 */
	public boolean isFinished() {
		return isRemoved() || (spellTickCount >= maxDuration && allDanmakus.isEmpty());
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
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isNoGravity() {
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
