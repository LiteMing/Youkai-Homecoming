package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColorAnimation;
import dev.xkmc.youkaishomecoming.content.spell.definition.EntityNumberProviderEvaluator;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverOwner;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@SerialClass
public class ItemDanmakuEntity extends YHBaseDanmakuEntity implements ItemSupplier, MoverOwner {

	@SerialClass.SerialField
	public DanmakuMover mover = null;
	@SerialClass.SerialField
	public TrailAction afterExpiry = null;

	/** Preview-only: index of the source action that spawned this danmaku (-1 = unknown). */
	public transient int sourceActionIndex = -1;
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
	 * DISCARD = remove immediately, EXPIRE = trigger expiry immediately, CONTINUE = keep flying.
	 */
	public HitBehavior hitBehaviorEntity = HitBehavior.DISCARD;
	/**
	 * Behavior after hitting a block:
	 * DISCARD = remove immediately, EXPIRE = trigger expiry immediately, CONTINUE = keep flying.
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
	@SerialClass.SerialField
	public float visualScale = 1;
	@SerialClass.SerialField
	private int tint = 0xffffffff;
	@SerialClass.SerialField
	private int colorAnimationMode = DanmakuColorAnimation.Resolved.NONE;
	@SerialClass.SerialField
	private float colorAnimationPeriod = 120;
	@SerialClass.SerialField
	private float colorAnimationHueOffset = 0;
	@SerialClass.SerialField
	private float colorAnimationSaturation = 1;
	@SerialClass.SerialField
	private float colorAnimationBrightness = 1;
	@SerialClass.SerialField
	private float colorAnimationAlpha = 1;
	public NumberProvider visualScaleFunction = null;

	private static final float VISUAL_SCALE_EPSILON = 1.0E-4f;
	private float currentVisualScale = 1;
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
		if (stack.getItem() instanceof DanmakuItem) {
			tint = DanmakuItem.getColor(stack).argb();
		}
		sizeCache = null;
		refreshDimensions();
	}

	public void setTint(int tint) {
		this.tint = tint;
	}

	public int getTint() {
		return tint;
	}

	public void configureColorAnimation(DanmakuColorAnimation.Resolved animation) {
		colorAnimationMode = animation.mode();
		colorAnimationPeriod = Math.max(1.0e-3f, animation.period());
		colorAnimationHueOffset = animation.hueOffset();
		colorAnimationSaturation = Mth.clamp(animation.saturation(), 0, 1);
		colorAnimationBrightness = Mth.clamp(animation.brightness(), 0, 1);
		colorAnimationAlpha = Mth.clamp(animation.alpha(), 0, 1);
	}

	public int getRenderTint(float pTick) {
		if (colorAnimationMode == DanmakuColorAnimation.Resolved.HUE_CYCLE) {
			float hue = colorAnimationHueOffset + (tickCount + pTick) / colorAnimationPeriod;
			hue -= Mth.floor(hue);
			int rgb = Mth.hsvToRgb(hue, colorAnimationSaturation, colorAnimationBrightness) & 0xffffff;
			int alpha = Mth.clamp((int) (colorAnimationAlpha * 255), 0, 255);
			return alpha << 24 | rgb;
		}
		return tint;
	}

	public void configureVisualScale(float scale, NumberProvider function) {
		visualScale = Math.max(0.05f, scale);
		visualScaleFunction = function;
		updateVisualScaleDimensions(true);
	}

	@Override
	public void tick() {
		if (visualScaleFunction != null) {
			updateVisualScaleDimensions(false);
		}
		super.tick();
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
		if (mover != null) {
			return mover.move(new MoverInfo(tickCount, pos, vec, this, tickData().ownerInfo));
		}
		return super.updateVelocity(vec, pos);
	}

	@Override
	protected void commitPreMoveEffects(TickData data) {
		if (onTrail == null || tickCount <= 0 || tickCount % trailInterval != 0) {
			return;
		}
		CardHolder holder = null;
		Entity e = getOwner();
		if (e instanceof CardHolder h) holder = h;
		Vec3 pos = data.moveSrc == null ? position() : data.moveSrc;
		Vec3 vec = data.inputVelocity == null ? getDeltaMovement() : data.inputVelocity;
		if (holder != null) onTrail.execute(holder, pos, vec);
		else onTrail.execute(pos, vec);
	}

	public ItemStack getItem() {
		return stack;
	}

	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		readScaleFunction(nbt);
		updateVisualScaleDimensions(true);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		writeScaleFunction(nbt);
	}

	@Override
	public void writeSpawnData(FriendlyByteBuf data) {
		super.writeSpawnData(data);
		CompoundTag tag = new CompoundTag();
		writeScaleFunction(tag);
		data.writeNbt(tag);
	}

	@Override
	public void readSpawnData(FriendlyByteBuf data) {
		super.readSpawnData(data);
		CompoundTag tag = data.readNbt();
		if (tag != null) {
			readScaleFunction(tag);
		}
		updateVisualScaleDimensions(true);
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
		tickData().removed = true;
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
		return (sizeCache == null ? 1 : sizeCache) * currentVisualScale;
	}

	private void updateVisualScaleDimensions(boolean force) {
		float next = visualScaleFunction == null ? visualScale : evaluateVisualScaleFunction();
		if (force || Math.abs(next - currentVisualScale) > VISUAL_SCALE_EPSILON) {
			currentVisualScale = next;
			refreshDimensions();
		}
	}

	private float evaluateVisualScaleFunction() {
		if (visualScaleFunction == null) return visualScale;
		return Math.max(0.05f, (float) EntityNumberProviderEvaluator.get(visualScaleFunction, tickCount, visualScale, random));
	}

	private void writeScaleFunction(CompoundTag nbt) {
		if (visualScaleFunction == null) return;
		NumberProvider.CODEC.encodeStart(NbtOps.INSTANCE, visualScaleFunction)
				.result().ifPresent(tag -> nbt.put("visual_scale_function", tag));
	}

	private void readScaleFunction(CompoundTag nbt) {
		if (!nbt.contains("visual_scale_function")) return;
		NumberProvider.CODEC.parse(NbtOps.INSTANCE, nbt.get("visual_scale_function"))
				.result().ifPresent(provider -> visualScaleFunction = provider);
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
		int col = getRenderTint(0) & 0xffffff;
		var pos = position().add(0, getBbHeight() / 2, 0);
		DanmakuParticleHelper.ball(level(), pos, col, getBbWidth() / 2, random);
	}

}
