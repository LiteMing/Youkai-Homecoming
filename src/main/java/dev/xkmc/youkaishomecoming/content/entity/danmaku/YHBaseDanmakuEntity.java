package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.entity.BaseProjectile;
import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.l2serial.serialization.codec.PacketCodec;
import dev.xkmc.l2serial.serialization.codec.TagCodec;
import dev.xkmc.l2serial.util.Wrappers;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.events.DanmakuHitBlockEvent;
import dev.xkmc.youkaishomecoming.events.DanmakuHitEntityEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@SerialClass
public class YHBaseDanmakuEntity extends BaseProjectile implements IYHDanmaku {

	@SerialClass.SerialField
	private int life = 0;
	@SerialClass.SerialField
	private boolean bypassWall = false, bypassEntity = false;

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
			postDanmakuHitBlock(pResult);
			// Execute onHitBlock callback before potential discard
			if (this instanceof ItemDanmakuEntity ide && ide.onHitBlockAction != null) {
				executeHitAction(ide.onHitBlockAction);
			}
			if (this instanceof ItemDanmakuEntity ide) {
				switch (ide.hitBehaviorBlock) {
					case CONTINUE -> {
						// Don't remove — let it keep flying until lifetime expires.
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
		super.onHitEntity(result);
		postDanmakuHitEntity(result);
		hurtTarget(result);
		// Execute onHitEntity callback before potential discard
		if (this instanceof ItemDanmakuEntity ide && ide.onHitEntityAction != null) {
			executeHitAction(ide.onHitEntityAction);
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

	@Nullable
	private SpellRuntime getOwningRuntime() {
		Entity owner = getOwner();
		if (owner instanceof DanmakuProxyEntity proxy) {
			return proxy.getSpellRuntime();
		}
		if (owner instanceof YoukaiEntity youkai) {
			return youkai.spellRuntime;
		}
		return null;
	}

	private void postDanmakuHitBlock(BlockHitResult result) {
		SpellRuntime runtime = getOwningRuntime();
		ResourceLocation spellId = runtime == null ? null : runtime.getDefinition().id;
		ResourceLocation phaseId = runtime == null ? null : runtime.getCurrentPhaseId();
		MinecraftForge.EVENT_BUS.post(new DanmakuHitBlockEvent(this, getOwner(), result, spellId, phaseId));
	}

	private void postDanmakuHitEntity(EntityHitResult result) {
		SpellRuntime runtime = getOwningRuntime();
		ResourceLocation spellId = runtime == null ? null : runtime.getDefinition().id;
		ResourceLocation phaseId = runtime == null ? null : runtime.getCurrentPhaseId();
		MinecraftForge.EVENT_BUS.post(new DanmakuHitEntityEvent(this, getOwner(), result.getEntity(), spellId, phaseId));
	}

	/** Helper: execute a TrailAction at the current danmaku position/direction. */
	private void executeHitAction(TrailAction action) {
		CardHolder holder = null;
		Entity e = getOwner();
		if (e instanceof CardHolder h) holder = h;
		if (holder != null) action.execute(holder, position(), getDeltaMovement());
		else action.execute(position(), getDeltaMovement());
	}

}
