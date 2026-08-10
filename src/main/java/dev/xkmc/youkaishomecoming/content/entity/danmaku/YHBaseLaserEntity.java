package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.entity.BaseLaser;
import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.collision.EntityInfo;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.l2serial.serialization.codec.PacketCodec;
import dev.xkmc.l2serial.serialization.codec.TagCodec;
import dev.xkmc.l2serial.util.Wrappers;
import dev.xkmc.youkaishomecoming.content.spell.mover.CompositeMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.RectMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.ZeroMover;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

@SerialClass
public class YHBaseLaserEntity extends BaseLaser implements IEntityAdditionalSpawnData, IYHDanmaku {

	@SerialClass.SerialField
	protected int life = 0, prepare, start, end;
	@SerialClass.SerialField
	private boolean bypassWall = false;
	@SerialClass.SerialField
	private boolean playerSpellDamageRestricted = false;
	@Nullable
	@SerialClass.SerialField
	private UUID playerSpellTargetId = null;
	@SerialClass.SerialField
	public float damage = 0, length = 0;
	@SerialClass.SerialField
	public boolean setupLength;

	@SerialClass.SerialField
	public dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction afterExpiry = null;

	/** Per-tick trail action: executed every {@link #trailInterval} ticks during flight. */
	public dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction onTrail = null;
	public int trailInterval = 1;

	/** Action executed when this laser hits a living entity. */
	public dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction onHitEntityAction = null;
	/** Action executed when this laser hits a block. */
	public dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction onHitBlockAction = null;
	/**
	 * Behavior after hitting an entity:
	 * DISCARD = remove immediately, EXPIRE = trigger expiry immediately, CONTINUE = keep flying.
	 */
	public HitBehavior hitBehaviorEntity = HitBehavior.CONTINUE;
	/**
	 * Behavior after hitting a block:
	 * All modes clip the beam at the wall without removing its visible prefix.
	 * DISCARD suppresses expiry, EXPIRE triggers expiry once, and CONTINUE defers expiry to lifetime end.
	 */
	public HitBehavior hitBehaviorBlock = HitBehavior.CONTINUE;

	public double earlyTerminate = -1;
	private boolean expiryActionConsumed = false;
	private BlockPos activeBlockHit = null;

	protected YHBaseLaserEntity(EntityType<? extends YHBaseLaserEntity> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	protected YHBaseLaserEntity(EntityType<? extends YHBaseLaserEntity> pEntityType, double pX, double pY, double pZ, Level pLevel) {
		this(pEntityType, pLevel);
		this.setPos(pX, pY, pZ);
	}

	protected YHBaseLaserEntity(EntityType<? extends YHBaseLaserEntity> pEntityType, LivingEntity pShooter, Level pLevel) {
		this(pEntityType, pShooter.getX(), pShooter.getEyeY() - (double) 0.1F, pShooter.getZ(), pLevel);
		this.setOwner(pShooter);
	}

	public void setup(float damage, int life, float length, boolean bypassWall, Vec3 vec3) {
		double d0 = vec3.horizontalDistance();
		setup(damage, life, length, bypassWall,
				(float) (-Mth.atan2(vec3.x, vec3.z) * Mth.RAD_TO_DEG),
				(float) (-Mth.atan2(vec3.y, d0) * Mth.RAD_TO_DEG));
	}


	public void setupTime(int prepare, int start, int life, int end) {
		this.prepare = prepare;
		this.start = this.prepare + start;
		this.end = this.start + life;
		this.life = this.end + end;
	}

	public void setup(float damage, int life, float length, boolean bypassWall, float rY, float rX) {
		this.damage = damage;
		this.bypassWall = bypassWall;
		this.length = length;
		setupTime(20, 20, life, 20);
		setYRot(rY);
		setXRot(rX);
		// Initialize previous rotation to the same direction (mirror YHBaseDanmakuEntity.setup).
		// Prevents tick-0 interpolation/angle unwrapping from starting at the default 0°.
		yRotO = rY;
		xRotO = rX;
	}

	@Override
	public SimplifiedProjectile self() {
		return this;
	}

	@Override
	public void restrictPlayerSpellDamage(@Nullable LivingEntity target) {
		playerSpellDamageRestricted = true;
		playerSpellTargetId = target == null ? null : target.getUUID();
	}

	@Override
	public boolean canHitDanmakuTarget(EntityInfo target) {
		return IYHDanmaku.canPlayerSpellHit(target, playerSpellDamageRestricted, playerSpellTargetId);
	}

	@Override
	public double getLength() {
		return length;
	}

	@Override
	public boolean checkBlockHit() {
		return !bypassWall;
	}

	public void setBypassWall(boolean bypassWall) {
		this.bypassWall = bypassWall;
	}

	/** Set rotation and old rotation without lerp (used at spawn to seed the real first path segment). */
	public void setInitialRotation(Vec3 rot) {
		float pitch = (float) (rot.x * Mth.RAD_TO_DEG);
		float yaw = (float) (rot.y * Mth.RAD_TO_DEG);
		setXRot(pitch);
		setYRot(yaw);
		xRotO = pitch;
		yRotO = yaw;
	}

	@Override
	public float getEffectiveHitRadius() {
		return getBbWidth() / 4f;
	}

	@Override
	public boolean checkEntityHit() {
		return isHitWindowOpen(tickCount);
	}

	/** Absolute-tick hit window for Pilot prediction (warn/fade are inactive). */
	public boolean isHitWindowOpen(int absTick) {
		return absTick > start && absTick < end;
	}

	@Override
	public float damage(Entity target) {
		return damage;
	}

	public float percentOpen(float pTick) {
		return setupLength ? 1 : percentLoad(pTick);
	}

	public float percentLoad(float pTick) {
		pTick += tickCount;
		if (pTick < prepare) return 0.1f;
		else if (pTick < start)
			return (pTick - prepare) / (start - prepare) * 0.9f + 0.1f;
		else if (pTick < end) return 1;
		else if (pTick < life)
			return (pTick - end) / (life - end) * -0.9f + 1f;
		else return 0;
	}

	public float effectiveLength(float pTick) {
		float visualLength = setupLength ? percentLoad(pTick) * length : length;
		return LaserBlockHitEffect.clipLength(visualLength, earlyTerminate);
	}

	@Override
	protected void planMove(TickData data) {
		data.moveSrc = position();
		data.inputVelocity = getDeltaMovement();
		data.plannedMovement = computeMove(data.inputVelocity, data.moveSrc);
		data.moveDst = data.moveSrc.add(data.plannedMovement.vec());
	}

	@Override
	protected void applyMoveTick(TickData data) {
		if (data.plannedMovement != null) {
			applyMove(data.plannedMovement);
		}
	}

	@Override
	protected void finishTick(TickData data) {
		super.finishTick(data);
		if (level().isClientSide()) {
			Vec3 src = (data.moveDst == null ? position() : data.moveDst).add(0, getBbHeight() / 2f, 0);
			earlyTerminate = data.blockHit == null ? -1 : src.distanceTo(data.blockHit.getLocation());
		} else if (data.blockHit == null) {
			activeBlockHit = null;
		}
		// Per-tick trail action (mirror ItemDanmakuEntity.commitPreMoveEffects).
		// Like danmaku, the hook is transient (server-only); the client copy has no onTrail.
		if (onTrail != null && tickCount > 0 && tickCount % trailInterval == 0) {
			CardHolder holder = getOwner() instanceof CardHolder h ? h : null;
			Vec3 pos = data.moveSrc == null ? position() : data.moveSrc;
			Vec3 vec = data.inputVelocity == null ? getDeltaMovement() : data.inputVelocity;
			if (holder != null) onTrail.execute(holder, pos, vec);
			else onTrail.execute(pos, vec);
		}
		if (!level().isClientSide() && tickCount > life) {
			runExpiryActionOnce(null, position(), getDeltaMovement());
			markErased(false);
		}
	}

	protected ProjectileMovement computeMove() {
		return computeMove(getDeltaMovement(), position());
	}

	/**
	 * Lasers must follow the mover's true direction exactly. The base class
	 * smooths rotation with lerpRotation (0.2/tick), which makes the beam lag
	 * the mover by ~4x its angular rate and swing the long way around when the
	 * Euler yaw/pitch flips near the vertical. Hit detection already uses the
	 * exact target rot (plannedMovement.rot()), so rendering with the exact
	 * rot keeps the rendered beam and the actual hit line consistent.
	 * <p>
	 * The new Euler angles are unwrapped to stay within ±180° of the previous
	 * tick's rotation, so the renderer's partial-tick interpolation
	 * (getViewYRot/getViewXRot, a plain lerp between yRotO/xRotO and the new
	 * value) never crosses the ±180° seam: +179° → -179° becomes
	 * +179° → +181°, a real 2° turn instead of a 358° wrong-way detour.
	 */
	@Override
	protected void updateRotation(Vec3 rot) {
		float targetX = (float) (rot.x * Mth.RAD_TO_DEG);
		float targetY = (float) (rot.y * Mth.RAD_TO_DEG);
		targetX = xRotO + Mth.wrapDegrees(targetX - xRotO);
		targetY = yRotO + Mth.wrapDegrees(targetY - yRotO);
		setXRot(targetX);
		setYRot(targetY);
	}

	protected ProjectileMovement computeMove(Vec3 vec, Vec3 pos) {
		return updateVelocity(vec, pos);
	}

	protected void applyMove(ProjectileMovement movement) {
		setDeltaMovement(movement.vec());
		updateRotation(movement.rot());
		double d2 = getX() + movement.vec().x;
		double d0 = getY() + movement.vec().y;
		double d1 = getZ() + movement.vec().z;
		setPos(d2, d0, d1);
	}

	protected ProjectileMovement updateVelocity(Vec3 vec, Vec3 pos) {
		return ProjectileMovement.of(vec);
	}

	@Override
	protected void onHit(BlockHitResult blockHit, Iterable<Entity> hitEntities) {
		boolean hitEntity = false;
		for (var e : hitEntities) {
			hurtTarget(new EntityHitResult(e));
			hitEntity = true;
		}
		if (level().isClientSide()) return;
		if (hitEntity) {
			// Execute onHitEntity callback before potential discard
			if (onHitEntityAction != null) executeHitAction(onHitEntityAction);
			switch (hitBehaviorEntity) {
				case CONTINUE -> {
				}
				case EXPIRE -> {
					expireLaserNow();
					return;
				}
				case DISCARD -> {
					markErased(false);
					return;
				}
			}
		}
		if (blockHit != null) {
			Vec3 hitPos = blockHit.getLocation();
			Vec3 hitDirection = getForward();
			BlockPos blockPos = blockHit.getBlockPos();
			if (!blockPos.equals(activeBlockHit) && onHitBlockAction != null) {
				executeHitAction(onHitBlockAction, hitPos, hitDirection);
			}
			activeBlockHit = blockPos;
			switch (LaserBlockHitEffect.from(hitBehaviorBlock)) {
				case CLIP_ONLY -> {
				}
				case CLIP_AND_RUN_EXPIRY -> runExpiryActionOnce(null, hitPos, hitDirection);
				case CLIP_AND_SUPPRESS_EXPIRY -> suppressExpiryAction();
			}
		}
	}

	private void expireLaserNow() {
		expireLaserNow(position(), getDeltaMovement());
	}

	private void expireLaserNow(Vec3 pos, Vec3 direction) {
		runExpiryActionOnce(null, pos, direction);
		markErased(false);
	}

	/** Runs the expiry hook at most once, using an explicit holder for local preview entities. */
	public void runExpiryActionOnce(CardHolder fallbackHolder, Vec3 pos, Vec3 direction) {
		if (expiryActionConsumed) return;
		expiryActionConsumed = true;
		if (afterExpiry == null) return;
		CardHolder holder = getOwner() instanceof CardHolder h ? h : fallbackHolder;
		if (holder != null) afterExpiry.execute(holder, pos, direction);
		else afterExpiry.execute(pos, direction);
	}

	/** DISCARD at a wall removes only the blocked suffix and must not trigger on_expiry later. */
	public void suppressExpiryAction() {
		expiryActionConsumed = true;
	}

	/** Execute a TrailAction at the supplied impact position and direction. */
	private void executeHitAction(dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction action) {
		executeHitAction(action, position(), getDeltaMovement());
	}

	private void executeHitAction(dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction action,
			Vec3 pos, Vec3 direction) {
		CardHolder holder = getOwner() instanceof CardHolder h ? h : null;
		if (holder != null) action.execute(holder, pos, direction);
		else action.execute(pos, direction);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		var src = position().add(0, getBbHeight() / 2f, 0);
		return new AABB(src, src.add(getForward().scale(length))).inflate(getBbWidth() / 2f);
	}

	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.put("auto-serial", Objects.requireNonNull(TagCodec.toTag(new CompoundTag(), this)));
		nbt.putBoolean("ExpiryActionConsumed", expiryActionConsumed);
	}

	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains("auto-serial")) {
			Wrappers.run(() -> TagCodec.fromTag(nbt.getCompound("auto-serial"), getClass(), this, (f) -> true));
		}
		expiryActionConsumed = nbt.getBoolean("ExpiryActionConsumed");
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
		// Spawn packets carry only the current rotation, not the previous render
		// tick's. Sync the old rotation so partial-tick interpolation and angle
		// unwrapping start from the actual spawn direction instead of 0°.
		xRotO = getXRot();
		yRotO = getYRot();
	}

	@Override
	public boolean isValid() {
		return tickCount < life;
	}

}
