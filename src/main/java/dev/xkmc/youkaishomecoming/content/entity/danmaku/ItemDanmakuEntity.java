package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverOwner;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@SerialClass
public class ItemDanmakuEntity extends YHBaseDanmakuEntity implements ItemSupplier, MoverOwner {

	@SerialClass.SerialField
	public int controlCode = 0;
	@SerialClass.SerialField
	public DanmakuMover mover = null;
	@SerialClass.SerialField
	public TrailAction afterExpiry = null;
	/**
	 * Per-tick trail action: executed every {@link #trailInterval} ticks during flight.
	 * Used for generating sub-danmaku along the projectile's path (e.g. StarSpell shooting stars).
	 */
	public TrailAction onTrail = null;
	public int trailInterval = 1;

	/** Action executed when this danmaku hits a living entity. */
	public TrailAction onHitEntityAction = null;
	/** Action executed when this danmaku hits a block. */
	public TrailAction onHitBlockAction = null;
	/**
	 * Behavior after hitting an entity:
	 * DISCARD = remove immediately (default), CONTINUE = keep flying until lifetime expires.
	 */
	public HitBehavior hitBehaviorEntity = HitBehavior.DISCARD;
	/**
	 * Behavior after hitting a block:
	 * DISCARD = remove immediately (default), CONTINUE = keep flying until lifetime expires.
	 */
	public HitBehavior hitBehaviorBlock = HitBehavior.DISCARD;
	/**
	 * Per-danmaku damage type override. When non-null, this takes priority over
	 * the CardHolder/SpellCard damage source resolution chain.
	 * Set by data-driven {@code fire_danmaku} actions with a {@code damage_type} field.
	 */
	public dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType damageTypeOverride = null;
	@SerialClass.SerialField
	public ItemStack stack = ItemStack.EMPTY;

	private boolean isErased = false;

	@Override
	public dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType getDamageTypeOverride() {
		return damageTypeOverride;
	}

	public ItemDanmakuEntity(EntityType<? extends ItemDanmakuEntity> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	public ItemDanmakuEntity(EntityType<? extends ItemDanmakuEntity> pEntityType, double pX, double pY, double pZ, Level pLevel) {
		super(pEntityType, pX, pY, pZ, pLevel);
	}

	public ItemDanmakuEntity(EntityType<? extends ItemDanmakuEntity> pEntityType, LivingEntity pShooter, Level pLevel) {
		super(pEntityType, pShooter, pLevel);
	}

	public void setItem(ItemStack pStack) {
		stack = pStack.copyWithCount(1);
		refreshDimensions();
	}

	public void setControlCode(int code) {
		this.controlCode = code;
	}

	@Override
	public TraceableEntity asTraceable() {
		return this;
	}

	@Override
	protected void terminate() {
		if (afterExpiry == null) return;
		CardHolder holder = null;
		Entity e = getOwner();
		if (e instanceof CardHolder h) holder = h;
		if (holder == null) afterExpiry.execute(position(), getDeltaMovement());
		else afterExpiry.execute(holder, position(), getDeltaMovement());
	}

	@Override
	protected ProjectileMovement updateVelocity(Vec3 vec, Vec3 pos) {
		// Execute per-tick trail action
		if (onTrail != null && tickCount > 0 && tickCount % trailInterval == 0) {
			CardHolder holder = null;
			Entity e = getOwner();
			if (e instanceof CardHolder h) holder = h;
			if (holder != null) onTrail.execute(holder, pos, vec);
			else onTrail.execute(pos, vec);
		}

		if (mover != null) {
			return mover.move(new MoverInfo(tickCount, pos, vec, this));
		}
		if (controlCode > 0 && getOwner() instanceof DanmakuCommander commander)
			return commander.move(controlCode, tickCount, vec);
		return super.updateVelocity(vec, pos);
	}

	public ItemStack getItem() {
		return stack;
	}

	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		refreshDimensions();
	}

	@Override
	public void readSpawnData(FriendlyByteBuf data) {
		super.readSpawnData(data);
		refreshDimensions();
	}

	@Override
	public EntityDimensions getDimensions(Pose pPose) {
		return super.getDimensions(pPose).scale(scale());
	}

	public boolean fullBright() {
		return true;
	}

	public void markErased(boolean kill) {
		if (!isErased)
			super.markErased(kill);
		isErased = true;
	}

	@Override
	public boolean isValid() {
		return !isErased && super.isValid();
	}

	private Float sizeCache = null;

	public float scale() {
		if (sizeCache == null) {
			if (getItem().getItem() instanceof DanmakuItem item) {
				sizeCache = item.size;
			}
		}
		return sizeCache == null ? 1 : sizeCache;
	}

	private int lastGraze = 0;

	@Override
	public void doGraze(Player entity) {
		if (tickCount < lastGraze) return;
		lastGraze = tickCount + 20;
		GrazeHelper.graze(entity, this);
	}

	@Override
	public void poof() {
		if (!level().isClientSide()) return;
		if (!(getItem().getItem() instanceof DanmakuItem item)) return;
		int col = item.color.getTextColor();
		var pos = position().add(0, getBbHeight() / 2, 0);
		DanmakuParticleHelper.ball(level(), pos, col, getBbWidth() / 2, random);
	}

}