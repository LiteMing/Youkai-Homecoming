package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.entity.BaseProjectile;
import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.collision.EntityInfo;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.l2serial.serialization.codec.PacketCodec;
import dev.xkmc.l2serial.serialization.codec.TagCodec;
import dev.xkmc.l2serial.util.Wrappers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.UUID;

@SerialClass
public class YHBaseDanmakuEntity extends BaseProjectile implements IYHDanmaku {
	@SerialClass.SerialField
	private int life = 0;
	@SerialClass.SerialField
	private boolean bypassWall = false, bypassEntity = false;
	@SerialClass.SerialField
	private boolean playerSpellDamageRestricted = false;
	@Nullable
	@SerialClass.SerialField
	private UUID playerSpellTargetId = null;
	@SerialClass.SerialField
	private boolean harmfulPlayerSnapshotPresent = false;
	@SerialClass.SerialField
	private final LinkedHashSet<UUID> harmfulPlayerIds = new LinkedHashSet<>();

	public void setBypassWall(boolean bypass) { this.bypassWall = bypass; }
	public void setBypassEntity(boolean bypass) { this.bypassEntity = bypass; }
	@SerialClass.SerialField
	public float damage = 0;

	protected YHBaseDanmakuEntity(EntityType<? extends YHBaseDanmakuEntity> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	protected YHBaseDanmakuEntity(EntityType<? extends YHBaseDanmakuEntity> pEntityType, double pX, double pY,
			double pZ, Level pLevel) {
		this(pEntityType, pLevel);
		this.setPos(pX, pY, pZ);
	}

	protected YHBaseDanmakuEntity(EntityType<? extends YHBaseDanmakuEntity> pEntityType, LivingEntity pShooter,
			Level pLevel) {
		this(pEntityType, pShooter.getX(), pShooter.getEyeY() - (double) 0.1F, pShooter.getZ(), pLevel);
		this.setOwner(pShooter);
	}

	public void setup(float damage, int life, boolean bypassWall, boolean bypassEntity, Vec3 initVec) {
		this.damage = damage;
		this.life = life;
		this.bypassWall = bypassWall;
		this.bypassEntity = bypassEntity;
		setDeltaMovement(initVec);
		// Directly set rotation without lerping so initial direction is correct
		Vec3 rot = ProjectileMovement.of(initVec).rot();
		float targetXRot = (float) (rot.x * Mth.RAD_TO_DEG);
		float targetYRot = (float) (rot.y * Mth.RAD_TO_DEG);
		setXRot(targetXRot);
		setYRot(targetYRot);
		xRotO = targetXRot;
		yRotO = targetYRot;
	}

	@Override
	public SimplifiedProjectile self() {
		return this;
	}

	/** Keeps a held projectile alive until its delayed release callback can run. */
	public void extendLifetimeForHold(int holdTicks) {
		long required = (long) tickCount + Math.max(1, holdTicks) + 1L;
		life = (int) Math.min(Integer.MAX_VALUE, Math.max((long) life, required));
	}

	@Override
	public void restrictPlayerSpellDamage(@Nullable LivingEntity target) {
		playerSpellDamageRestricted = true;
		playerSpellTargetId = target == null ? null : target.getUUID();
	}

	@Override
	public boolean isPlayerSpellProjectile() {
		return playerSpellDamageRestricted;
	}

	@Override
	public boolean canHitDanmakuTarget(EntityInfo target) {
		return IYHDanmaku.canPlayerSpellHit(this, target, playerSpellDamageRestricted, playerSpellTargetId);
	}

	@Override
	public void setHarmfulPlayerSnapshot(java.util.Collection<UUID> playerIds) {
		harmfulPlayerSnapshotPresent = true;
		harmfulPlayerIds.clear();
		harmfulPlayerIds.addAll(playerIds);
	}

	@Override
	public boolean hasHarmfulPlayerSnapshot() {
		return harmfulPlayerSnapshotPresent;
	}

	@Override
	public boolean isHarmfulToPlayer(UUID playerId) {
		return !harmfulPlayerSnapshotPresent || harmfulPlayerIds.contains(playerId);
	}

	@Override
	public boolean checkBlockHit() {
		return !bypassWall;
	}

	@Override
	public int lifetime() {
		// A held projectile is pinned until its callback releases it. Do not let
		// the ordinary lifetime check remove it before that release reaches the
		// client (the hold mover is synchronized separately).
		return this instanceof ItemDanmakuEntity ide && ide.isHolding()
				? Integer.MAX_VALUE : life;
	}

	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.put("auto-serial", Objects.requireNonNull(TagCodec.toTag(new CompoundTag(), this)));
	}

	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains("auto-serial")) {
			Wrappers.run(() -> TagCodec.fromTag(nbt.getCompound("auto-serial"), getClass(), this, (f) -> true));
		}
	}

	@Override
	public void writeSpawnData(FriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		PacketCodec.to(buffer, this);
	}

	@Override
	public void readSpawnData(FriendlyByteBuf data) {
		super.readSpawnData(data);
		PacketCodec.from(data, getClass(), Wrappers.cast(this));
	}

	@Override
	protected void onHitBlock(BlockHitResult pResult) {
		super.onHitBlock(pResult);
		if (!level().isClientSide) {
			var normal = pResult.getDirection().step();
			Vec3 n = new Vec3(normal.x(), normal.y(), normal.z());
			Vec3 src = tickData().moveSrc != null ? tickData().moveSrc : position();
			Vec3 movementEnd = tickData().movementEndOr(position().add(getDeltaMovement()));
			Vec3 incomingMovement = tickData().incomingMovementOr(getDeltaMovement());
			var hitCtx = new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext(
					this, dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext.HitType.BLOCK,
					src, pResult.getLocation(), movementEnd, n, incomingMovement, null);
			TrailAction blockAction = this instanceof ItemDanmakuEntity ide ? ide.onHitBlockAction : null;
			// Execute onHitBlock callback
			if (blockAction != null) {
				executeBlockHitAction(blockAction, hitCtx);
			}

			// If an action resolved disposition (e.g. BounceAction, ExpireSourceAction, DiscardSourceAction, ContinueSourceAction)
			if (hitCtx.isTerminal()) {
				applyHitDisposition(hitCtx);
				return;
			}

			// Fallback to default hitBehaviorBlock
			if (this instanceof ItemDanmakuEntity ide) {
				switch (ide.hitBehaviorBlock) {
					case CONTINUE -> {
						continueThroughHit(ide, hitCtx);
						return;
					}
					case EXPIRE -> {
						expireNow();
						return;
					}
					case DISCARD -> {
						markErased(false);
						return;
					}
				}
			}
			markErased(false);
		}
	}

	private void continueThroughHit(ItemDanmakuEntity ide, dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitCtx) {
		Vec3 resumePos = hitCtx.hitType() == dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext.HitType.BLOCK
				? hitCtx.movementEnd()
				: ide.position();
		ide.applyContinueState(resumePos, hitCtx.incomingVelocity());
		syncContinueToClient(resumePos, hitCtx.incomingVelocity());
	}

	private void resumeAfterHoldContinue(ItemDanmakuEntity ide, dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitCtx) {
		continueThroughHit(ide, hitCtx);
	}
	private void applyHitDisposition(
			dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitCtx
	) {
		switch (hitCtx.disposition()) {
			case HOLD -> {
				if (this instanceof ItemDanmakuEntity ide && hitCtx.deferredBody() != null) {
					var resume = hitCtx.holdResumeContext();
					// The callback context is authoritative. The projectile owner is commonly
					// a plain Player and is not a SpellRuntimeHost.
					if (resume == null || !resume.isUsable()) {
						ide.clearHoldState();
						markErased(false);
						return;
					}
					var runtime = resume.runtime();
					ide.extendLifetimeForHold(hitCtx.holdTicks());

					// Pin projectile on contact surface and install HitHoldMover, preserving suspended mover
					Vec3 holdPos = hitCtx.hitPosition().add(hitCtx.hitNormal().normalize().scale(0.08));
					ide.enterHoldState(holdPos, hitCtx.incomingVelocity());
					syncHoldToClient(holdPos, hitCtx.incomingVelocity());

					// Schedule resumption after holdTicks
					runtime.schedulePersistentDelayed(runtime.getTotalTick() + hitCtx.holdTicks(), java.util.List.of(
							new dev.xkmc.youkaishomecoming.content.spell.action.SpellAction() {
								@Override
								public void execute(dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext ctx) {
									if (ide.isAlive() && !ide.isRemoved()) {
										var body = hitCtx.beginResumeAndTakeBody();
										if (body != null) {
											var resumedCtx = new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext(
													resume.holder(), resume.definition(), resume.runtime(), ctx.difficulty(), hitCtx);
											resumedCtx.executeList(body);
										}
										if (hitCtx.isTerminal()) {
											applyHitDisposition(hitCtx);
										} else {
											// Fallback to default hit behavior after hold
											HitBehavior fallback = hitCtx.hitType() == dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext.HitType.BLOCK
													? ide.hitBehaviorBlock : ide.hitBehaviorEntity;
											switch (fallback) {
													case CONTINUE -> resumeAfterHoldContinue(ide, hitCtx);
													case EXPIRE -> {
														ide.clearHoldState();
														expireNow();
													}
													case DISCARD -> {
														ide.clearHoldState();
														markErased(false);
													}
											}
										}
										if (hitCtx.disposition() != dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext.HitDisposition.HOLD) {
											hitCtx.clearHoldResumeContext();
										}
									}
								}
							}
					));
				}
			}
			case CONTINUE -> {
				if (this instanceof ItemDanmakuEntity ide) {
					continueThroughHit(ide, hitCtx);
				}
			}
			case BOUNCE -> {
				if (this instanceof ItemDanmakuEntity ide) {
					var result = dev.xkmc.youkaishomecoming.content.spell.physics.DanmakuBounceResolver.resolve(
							hitCtx.hitPosition(), hitCtx.incomingVelocity(), hitCtx.hitNormal(),
							hitCtx.bounceConfig(), ide.currentBounces, resolveBounceTarget());
					if (result.erased()) {
						// Exceeded max bounces: fall back to default hitBehaviorBlock and continue through hit if CONTINUE
						ide.clearHoldState();
						switch (ide.hitBehaviorBlock) {
							case CONTINUE -> {
								continueThroughHit(ide, hitCtx);
								return;
							}
							case EXPIRE -> {
								expireNow();
								return;
							}
							case DISCARD -> {
								markErased(false);
								return;
							}
						}
						markErased(false);
						return;
					}
					ide.applyBounceState(result.newPos(), result.newVel(), result.updatedBounces());
					syncBounceToClient(result.newPos(), result.newVel(), result.updatedBounces());
				} else {
					markErased(false);
				}
			}
			case UNRESOLVED -> {
				if (this instanceof ItemDanmakuEntity ide) ide.clearHoldState();
				markErased(false);
			}
		}
	}

	@Override
	public float damage(Entity target) {
		return damage;
	}

	@Override
	public void onHitEntity(EntityHitResult result) {
		if (level().isClientSide)
			return;
		if (result.getEntity() instanceof DanmakuHostProxy)
			return;
		super.onHitEntity(result);
		hurtTarget(result);

		var hitCtx = new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext(
				this, dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext.HitType.ENTITY,
				result.getLocation(), getDeltaMovement().normalize().scale(-1), getDeltaMovement(), result.getEntity());
		TrailAction entityAction = this instanceof ItemDanmakuEntity ide ? ide.onHitEntityAction : null;
		// Execute onHitEntity callback before potential discard
		if (entityAction != null) {
			executeEntityHitAction(entityAction, hitCtx);
		}

		if (hitCtx.isTerminal()) {
			applyHitDisposition(hitCtx);
			return;
		}

		// Data-driven danmaku always collide with entities.
		// Whether they pierce or stop is controlled by hitBehaviorEntity.
		if (this instanceof ItemDanmakuEntity ide) {
			switch (ide.hitBehaviorEntity) {
				case CONTINUE -> {
					return;
				}
				case EXPIRE -> {
					expireNow();
					return;
				}
				case DISCARD -> {
					markErased(false);
					return;
				}
			}
		} else if (!bypassEntity) {
			markErased(false);
		}
	}

	private void expireNow() {
		terminate();
		markErased(false);
	}

	private void syncBounceToClient(Vec3 pos, Vec3 vel, int bounceCount) {
		if (getOwner() instanceof LivingEntity le && !level().isClientSide) {
			dev.xkmc.youkaishomecoming.init.YoukaisHomecoming.HANDLER.toTrackingPlayers(
					new dev.xkmc.fastprojectileapi.render.virtual.DanmakuBounceSyncPacket(getId(), pos, vel, bounceCount, dev.xkmc.fastprojectileapi.render.virtual.DanmakuBounceSyncPacket.ResetKind.BOUNCE), le);
		}
	}

	private void syncHoldToClient(Vec3 holdPos, Vec3 incomingVel) {
		if (getOwner() instanceof LivingEntity le && !level().isClientSide) {
			dev.xkmc.youkaishomecoming.init.YoukaisHomecoming.HANDLER.toTrackingPlayers(
					new dev.xkmc.fastprojectileapi.render.virtual.DanmakuBounceSyncPacket(getId(), holdPos, incomingVel, 0, dev.xkmc.fastprojectileapi.render.virtual.DanmakuBounceSyncPacket.ResetKind.HOLD), le);
		}
	}

	private void syncContinueToClient(Vec3 resumePos, Vec3 vel) {
		if (getOwner() instanceof LivingEntity le && !level().isClientSide) {
			dev.xkmc.youkaishomecoming.init.YoukaisHomecoming.HANDLER.toTrackingPlayers(
					new dev.xkmc.fastprojectileapi.render.virtual.DanmakuBounceSyncPacket(getId(), resumePos, vel, 0, dev.xkmc.fastprojectileapi.render.virtual.DanmakuBounceSyncPacket.ResetKind.CONTINUE), le);
		}
	}

	private void executeEntityHitAction(TrailAction action, dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitCtx) {
		CardHolder holder = null;
		Entity e = getOwner();
		if (e instanceof CardHolder h) holder = h;
		if (holder != null) {
			action.executeEntityHit(holder, hitCtx);
		} else {
			action.executeEntityHit(hitCtx);
		}
	}

	private Vec3 resolveBounceTarget() {
		if (this instanceof ItemDanmakuEntity ide) {
			Vec3 lockedTarget = ide.resolveRetargetTarget();
			if (lockedTarget != null) {
				return lockedTarget;
			}
		}
		Entity e = getOwner();
		if (e instanceof CardHolder h && h.target() != null) {
			return h.target();
		}
		if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
			return mob.getTarget().position().add(0, mob.getTarget().getEyeHeight() * 0.5, 0);
		}
		if (e instanceof dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity ye && ye.getTarget() != null) {
			return ye.getTarget().position().add(0, ye.getTarget().getEyeHeight() * 0.5, 0);
		}
		return null;
	}

	private void executeBlockHitAction(TrailAction action, dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitCtx) {
		CardHolder holder = null;
		Entity e = getOwner();
		if (e instanceof CardHolder h) holder = h;
		if (holder != null) {
			action.executeBlockHit(holder, hitCtx);
		} else {
			action.executeBlockHit(hitCtx);
		}
	}

}
