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

	@Override
	public void restrictPlayerSpellDamage(@Nullable LivingEntity target) {
		playerSpellDamageRestricted = true;
		playerSpellTargetId = target == null ? null : target.getUUID();
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
		return life;
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
			// Execute onHitBlock callback before potential discard
			if (this instanceof ItemDanmakuEntity ide && ide.onHitBlockAction != null) {
				executeBlockHitAction(ide.onHitBlockAction, pResult);
			}
			if (this instanceof ItemDanmakuEntity ide) {
				switch (ide.hitBehaviorBlock) {
					case CONTINUE -> {
						// Don't remove — let it keep flying until lifetime expires.
						return;
					}
					case BOUNCE -> {
						var bounceCfg = ide.bounceConfig;
						int maxBounces = bounceCfg != null ? bounceCfg.maxBounces() : 1;
						double decay = bounceCfg != null ? bounceCfg.decay() : 1.0;
						boolean retarget = bounceCfg != null && bounceCfg.retarget();
						var mode = bounceCfg != null ? bounceCfg.mode() : dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig.BounceMode.SPECULAR;

						ide.currentBounces++;
						if (ide.currentBounces > maxBounces) {
							markErased(false);
							return;
						}

						var normal = pResult.getDirection().step();
						Vec3 n = new Vec3(normal.x(), normal.y(), normal.z());
						Vec3 v = getDeltaMovement();
						double speed = v.length() * decay;

						if (mode == dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuBounceConfig.BounceMode.GROUND_GLIDE && (n.y > 0.5 || pResult.getDirection() == net.minecraft.core.Direction.UP)) {
							// 转为贴地飞行
							ide.isGroundGliding = true;
							Vec3 newPos = position().add(0, bounceCfg.groundOffset(), 0);
							ide.setPos(newPos);
							Vec3 flatDir = new Vec3(v.x, 0, v.z).normalize();
							if (retarget) {
								LivingEntity target = getOwnerTarget();
								if (target != null) {
									Vec3 toTarget = target.position().subtract(position());
									flatDir = new Vec3(toTarget.x, 0, toTarget.z).normalize();
								}
							}
							Vec3 newVel = flatDir.scale(Math.max(1e-4, speed));
							setDeltaMovement(newVel);
							syncBounceToClient(newPos, newVel);
							return;
						}

						double dot = v.dot(n);
						if (dot < 0) {
							Vec3 bounced = v.subtract(n.scale(2 * dot)).normalize().scale(speed);
							if (retarget) {
								LivingEntity target = getOwnerTarget();
								if (target != null) {
									Vec3 toTarget = target.position().add(0, target.getEyeHeight() * 0.5, 0).subtract(position());
									if (toTarget.lengthSqr() > 1e-4) {
										bounced = toTarget.normalize().scale(speed);
									}
								}
							}
							setDeltaMovement(bounced);
							Vec3 newPos = position().add(n.scale(0.05));
							setPos(newPos);
							syncBounceToClient(newPos, bounced);
						}
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
		// Execute onHitEntity callback before potential discard
		if (this instanceof ItemDanmakuEntity ide && ide.onHitEntityAction != null) {
			executeEntityHitAction(ide.onHitEntityAction, result);
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

	private void syncBounceToClient(Vec3 pos, Vec3 vel) {
		if (getOwner() instanceof LivingEntity le && !level().isClientSide) {
			dev.xkmc.youkaishomecoming.init.YoukaisHomecoming.HANDLER.toTrackingPlayers(
					new dev.xkmc.fastprojectileapi.render.virtual.DanmakuBounceSyncPacket(getId(), pos, vel), le);
		}
	}

	private void executeEntityHitAction(TrailAction action, EntityHitResult result) {
		CardHolder holder = null;
		Entity e = getOwner();
		if (e instanceof CardHolder h) holder = h;
		if (holder != null) {
			action.executeEntityHit(holder, result.getLocation(), getDeltaMovement(), result.getEntity());
		} else {
			action.executeEntityHit(result.getLocation(), getDeltaMovement(), result.getEntity());
		}
	}

	private LivingEntity getOwnerTarget() {
		Entity e = getOwner();
		if (e instanceof net.minecraft.world.entity.Mob mob) {
			return mob.getTarget();
		}
		if (e instanceof dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity ye) {
			return ye.getTarget();
		}
		return null;
	}

	private void executeBlockHitAction(TrailAction action, BlockHitResult result) {
		CardHolder holder = null;
		Entity e = getOwner();
		if (e instanceof CardHolder h) holder = h;
		if (holder != null) {
			action.executeBlockHit(holder, result.getLocation(), getDeltaMovement());
		} else {
			action.executeBlockHit(result.getLocation(), getDeltaMovement());
		}
	}

}
