package dev.xkmc.youkaishomecoming.content.spell.shooter;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.spellcircle.SpellCircleHolder;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.l2serial.serialization.codec.PacketCodec;
import dev.xkmc.l2serial.serialization.codec.TagCodec;
import dev.xkmc.l2serial.util.Wrappers;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverOwner;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.compat.ysm.YsmRenderOverrideTarget;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@SerialClass
public class ShooterEntity extends ProjectileHealthEntity implements LivingCardHolder, SpellCircleHolder, MoverOwner, YsmRenderOverrideTarget {

	private static final EntityDataAccessor<String> YSM_MODEL_OVERRIDE = SynchedEntityData.defineId(ShooterEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> YSM_TEXTURE_OVERRIDE = SynchedEntityData.defineId(ShooterEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> YSM_ANIMATION_OVERRIDE = SynchedEntityData.defineId(ShooterEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> YSM_MODEL_OVERRIDE_UNTIL = SynchedEntityData.defineId(ShooterEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> YSM_TEXTURE_OVERRIDE_UNTIL = SynchedEntityData.defineId(ShooterEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> YSM_ANIMATION_OVERRIDE_UNTIL = SynchedEntityData.defineId(ShooterEntity.class, EntityDataSerializers.INT);

	@SerialClass.SerialField
	private ShooterData data = ShooterData.EMPTY;
	private final float[] inheritedBulletDamage = new float[YHDanmaku.Bullet.values().length];
	private final float[] inheritedLaserDamage = new float[YHDanmaku.Laser.values().length];
	private boolean hasInheritedDamage;

	@Nullable
	@SerialClass.SerialField
	public DanmakuMover mover = null;

	@Nullable
	private LivingEntity target;

	@Nullable
	@SerialClass.SerialField
	private SpellCard spellCard;

	public ShooterEntity(EntityType<? extends LivingEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		entityData.define(YSM_MODEL_OVERRIDE, "");
		entityData.define(YSM_TEXTURE_OVERRIDE, "");
		entityData.define(YSM_ANIMATION_OVERRIDE, "");
		entityData.define(YSM_MODEL_OVERRIDE_UNTIL, 0);
		entityData.define(YSM_TEXTURE_OVERRIDE_UNTIL, 0);
		entityData.define(YSM_ANIMATION_OVERRIDE_UNTIL, 0);
	}

	public void setup(@Nullable LivingEntity owner, @Nullable LivingEntity target, ShooterData data, SpellCard card) {
		setOwner(owner);
		this.target = target;
		this.data = data;
		this.spellCard = card;
		var ins = getAttribute(Attributes.MAX_HEALTH);
		if (ins != null) ins.setBaseValue(data.health());
	}

	@Override
	public TraceableEntity asTraceable() {
		return this;
	}

	@Override
	public int lifetime() {
		return data.life();
	}

	@Override
	public void serverAiStep() {
		if (spellCard != null && isAlive()) {
			spellCard.tick(this);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			expireYsmRenderOverride();
		}
	}

	@Override
	protected ProjectileMovement updateVelocity(Vec3 vec, Vec3 pos) {
		if (mover != null) {
			return mover.move(new MoverInfo(tickCount, pos, vec, this, snapshotOwnerInfo()));
		}
		return super.updateVelocity(vec, pos);
	}

	private MoverInfo.OwnerInfo snapshotOwnerInfo() {
		Entity owner = getOwner();
		if (owner instanceof CardHolder holder) {
			return new MoverInfo.OwnerInfo(holder.center(), holder.forward());
		}
		if (owner instanceof LivingEntity living) {
			Vec3 pos = living.position().add(0, living.getBbHeight() / 2, 0);
			return new MoverInfo.OwnerInfo(pos, living.getForward());
		}
		return new MoverInfo.OwnerInfo(null, null);
	}

	// spell

	@Override
	public boolean shouldShowSpellCircle() {
		return true;
	}

	@Override
	public @Nullable ResourceLocation getSpellCircle() {
		return data.circle();
	}

	@Override
	public float getCircleSize(float pTick) {
		if (tickCount < 20) {
			return Mth.clamp((tickCount + pTick) / 20f, 0, 1);
		}
		if (deathTime > 0) {
			return Mth.clamp((20 - deathTime + pTick) / 20f, 0, 1);
		}
		return 1;
	}


	// card holder

	@Override
	public LivingEntity self() {
		return this;
	}

	@Override
	public LivingEntity shooter() {
		return getOwner() instanceof LivingEntity le ? le : this;
	}

	public void inheritDamageFrom(CardHolder holder) {
		hasInheritedDamage = true;
		for (YHDanmaku.Bullet type : YHDanmaku.Bullet.values()) {
			inheritedBulletDamage[type.ordinal()] = holder.getDamage(type);
		}
		for (YHDanmaku.Laser type : YHDanmaku.Laser.values()) {
			inheritedLaserDamage[type.ordinal()] = holder.getDamage(type);
		}
	}

	@Override
	public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DanmakuColor color) {
		return ignoreSelf(LivingCardHolder.super.prepareDanmaku(life, vec, type, color));
	}

	@Override
	public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, YHDanmaku.Laser type, DyeColor color) {
		return ignoreSelf(LivingCardHolder.super.prepareLaser(life, pos, vec, len, type, color));
	}

	@Override
	public TextDanmakuEntity prepareTextDanmaku(int life, Vec3 pos, Vec3 dir, float size, String text, int textColor) {
		return ignoreSelf(LivingCardHolder.super.prepareTextDanmaku(life, pos, dir, size, text, textColor));
	}

	private <T extends SimplifiedProjectile> T ignoreSelf(T projectile) {
		projectile.ignoreEntity(this);
		return projectile;
	}

	@Override
	public @Nullable LivingEntity targetEntity() {
		return target;
	}

	@Override
	public float getDamage(YHDanmaku.IDanmakuType type) {
		float d = data.damage();
		if (d > 0) {
			return d;
		}
		if (hasInheritedDamage) {
			if (type instanceof YHDanmaku.Bullet bullet) {
				return inheritedBulletDamage[bullet.ordinal()];
			}
			if (type instanceof YHDanmaku.Laser laser) {
				return inheritedLaserDamage[laser.ordinal()];
			}
		}
		if (getOwner() instanceof CardHolder owner) {
			return owner.getDamage(type);
		}
		return type.damage();
	}

	@Override
	public void setYsmRenderOverride(String modelId, String textureName, String animationHint, int duration, String clearTarget) {
		String model = YsmRenderOverrideTarget.normalizeYsmOverride(modelId);
		String texture = YsmRenderOverrideTarget.normalizeYsmOverride(textureName);
		String animation = YsmRenderOverrideTarget.normalizeYsmOverride(animationHint);
		if (!model.isBlank()) {
			entityData.set(YSM_MODEL_OVERRIDE, model);
		}
		if (!texture.isBlank()) {
			entityData.set(YSM_TEXTURE_OVERRIDE, texture);
		}
		if (!animation.isBlank()) {
			entityData.set(YSM_ANIMATION_OVERRIDE, animation);
		}
		int changedMask = YsmRenderOverrideTarget.changedMask(model, texture, animation);
		updateYsmFieldExpirations(changedMask, YsmRenderOverrideTarget.clearMask(clearTarget, changedMask), duration);
	}

	@Override
	public void clearYsmRenderOverride(String target) {
		clearYsmRenderOverride(YsmRenderOverrideTarget.clearMask(target, YSM_CLEAR_ALL));
	}

	private void clearYsmRenderOverride(int mask) {
		if ((mask & YSM_CLEAR_MODEL) != 0) {
			entityData.set(YSM_MODEL_OVERRIDE, "");
			entityData.set(YSM_MODEL_OVERRIDE_UNTIL, 0);
		}
		if ((mask & YSM_CLEAR_TEXTURE) != 0) {
			entityData.set(YSM_TEXTURE_OVERRIDE, "");
			entityData.set(YSM_TEXTURE_OVERRIDE_UNTIL, 0);
		}
		if ((mask & YSM_CLEAR_ANIMATION) != 0) {
			entityData.set(YSM_ANIMATION_OVERRIDE, "");
			entityData.set(YSM_ANIMATION_OVERRIDE_UNTIL, 0);
		}
	}

	@Override
	public boolean hasYsmRenderOverride() {
		return hasActiveYsmField(YSM_MODEL_OVERRIDE, YSM_MODEL_OVERRIDE_UNTIL) ||
				hasActiveYsmField(YSM_TEXTURE_OVERRIDE, YSM_TEXTURE_OVERRIDE_UNTIL) ||
				hasActiveYsmField(YSM_ANIMATION_OVERRIDE, YSM_ANIMATION_OVERRIDE_UNTIL);
	}

	@Override
	public String getYsmModelOverride() {
		return hasActiveYsmField(YSM_MODEL_OVERRIDE, YSM_MODEL_OVERRIDE_UNTIL) ? entityData.get(YSM_MODEL_OVERRIDE) : "";
	}

	@Override
	public String getYsmTextureOverride() {
		return hasActiveYsmField(YSM_TEXTURE_OVERRIDE, YSM_TEXTURE_OVERRIDE_UNTIL) ? entityData.get(YSM_TEXTURE_OVERRIDE) : "";
	}

	@Override
	public String getYsmAnimationOverride() {
		return hasActiveYsmField(YSM_ANIMATION_OVERRIDE, YSM_ANIMATION_OVERRIDE_UNTIL) ? entityData.get(YSM_ANIMATION_OVERRIDE) : "";
	}

	@Override
	public int getYsmOverrideTicksRemaining() {
		if (level().isClientSide()) {
			return 0;
		}
		int remaining = 0;
		remaining = mergeYsmRemaining(remaining, entityData.get(YSM_MODEL_OVERRIDE_UNTIL));
		remaining = mergeYsmRemaining(remaining, entityData.get(YSM_TEXTURE_OVERRIDE_UNTIL));
		remaining = mergeYsmRemaining(remaining, entityData.get(YSM_ANIMATION_OVERRIDE_UNTIL));
		return remaining;
	}

	@Override
	public String describeYsmRenderOverride() {
		if (!hasYsmRenderOverride()) {
			return "none";
		}
		return "model=" + displayYsmOverride(YSM_MODEL_OVERRIDE, YSM_MODEL_OVERRIDE_UNTIL) +
				", texture=" + displayYsmOverride(YSM_TEXTURE_OVERRIDE, YSM_TEXTURE_OVERRIDE_UNTIL) +
				", animation=" + displayYsmOverride(YSM_ANIMATION_OVERRIDE, YSM_ANIMATION_OVERRIDE_UNTIL);
	}

	private boolean hasActiveYsmField(EntityDataAccessor<String> field, EntityDataAccessor<Integer> untilField) {
		return !entityData.get(field).isBlank() && !isYsmFieldExpired(untilField);
	}

	private boolean isYsmFieldExpired(EntityDataAccessor<Integer> untilField) {
		if (level().isClientSide()) {
			return false;
		}
		int until = entityData.get(untilField);
		return until > 0 && tickCount >= until;
	}

	private int mergeYsmRemaining(int current, int until) {
		if (until <= tickCount) {
			return current;
		}
		int remaining = until - tickCount;
		return current <= 0 ? remaining : Math.min(current, remaining);
	}

	private String displayYsmOverride(EntityDataAccessor<String> field, EntityDataAccessor<Integer> untilField) {
		String value = entityData.get(field);
		if (value.isBlank()) {
			return "(keep)";
		}
		int until = entityData.get(untilField);
		if (level().isClientSide()) {
			return until > 0 ? value + " (timed)" : value;
		}
		return until > 0 && tickCount < until ? value + " (" + (until - tickCount) + "t)" : value;
	}

	private void updateYsmFieldExpirations(int changedMask, int expireMask, int duration) {
		int until = duration > 0 ? tickCount + duration : 0;
		updateYsmFieldExpiration(YSM_CLEAR_MODEL, changedMask, expireMask, until, YSM_MODEL_OVERRIDE_UNTIL);
		updateYsmFieldExpiration(YSM_CLEAR_TEXTURE, changedMask, expireMask, until, YSM_TEXTURE_OVERRIDE_UNTIL);
		updateYsmFieldExpiration(YSM_CLEAR_ANIMATION, changedMask, expireMask, until, YSM_ANIMATION_OVERRIDE_UNTIL);
	}

	private void updateYsmFieldExpiration(int bit, int changedMask, int expireMask, int until, EntityDataAccessor<Integer> untilField) {
		if ((changedMask & bit) != 0 || (expireMask & bit) != 0) {
			entityData.set(untilField, (expireMask & bit) != 0 ? until : 0);
		}
	}

	private void expireYsmRenderOverride() {
		int mask = 0;
		if (isYsmFieldExpired(YSM_MODEL_OVERRIDE_UNTIL)) {
			mask |= YSM_CLEAR_MODEL;
		}
		if (isYsmFieldExpired(YSM_TEXTURE_OVERRIDE_UNTIL)) {
			mask |= YSM_CLEAR_TEXTURE;
		}
		if (isYsmFieldExpired(YSM_ANIMATION_OVERRIDE_UNTIL)) {
			mask |= YSM_CLEAR_ANIMATION;
		}
		if (mask != 0) {
			clearYsmRenderOverride(mask);
		}
	}

	// data

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
	public void readSpawnData(FriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		PacketCodec.from(additionalData, getClass(), Wrappers.cast(this));
	}

}
