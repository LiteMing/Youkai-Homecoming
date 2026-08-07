package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.item.danmaku.LaserItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.EntityNumberProviderEvaluator;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.mover.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@SerialClass
public class ItemLaserEntity extends YHBaseLaserEntity implements ItemSupplier, MoverOwner {

	@SerialClass.SerialField
	public DanmakuMover mover;
	@SerialClass.SerialField
	public ItemStack stack = ItemStack.EMPTY;
	@SerialClass.SerialField
	public float visualScale = 1;
	public NumberProvider visualScaleFunction = null;
	private static final float VISUAL_SCALE_EPSILON = 1.0E-4f;
	private float currentVisualScale = 1;
	/**
	 * Per-laser damage type override. When non-null, this takes priority over
	 * the CardHolder/SpellCard damage source resolution chain.
	 * Set by data-driven {@code fire_laser} actions with a {@code damage_type} field.
	 */
	public dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType damageTypeOverride = null;

	@Override
	public dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType getDamageTypeOverride() {
		return damageTypeOverride;
	}

	public ItemLaserEntity(EntityType<? extends ItemLaserEntity> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	public ItemLaserEntity(EntityType<? extends ItemLaserEntity> pEntityType, double pX, double pY, double pZ, Level pLevel) {
		super(pEntityType, pX, pY, pZ, pLevel);
	}

	public ItemLaserEntity(EntityType<? extends ItemLaserEntity> pEntityType, LivingEntity pShooter, Level pLevel) {
		super(pEntityType, pShooter, pLevel);
	}

	public void setDelayedMover(float v0, float v1, int prepare, int setup) {
		var dir = getForward();
		var pos = position;
		var m = new CompositeMover();
		m.add(prepare, new ZeroMover(dir, dir, prepare));
		m.add(setup, new RectMover(pos, dir.scale(v0), Vec3.ZERO));
		m.add(life, new RectMover(pos.add(dir.scale(v0 * setup)), dir.scale(v1), Vec3.ZERO));
		this.mover = m;
	}

	/**
	 * Target-position movers define an absolute trajectory: {@code pos(0)} may
	 * differ from the spawn anchor (e.g. PolarMover starts at center + radius
	 * offset). The entity must be placed at {@code pos(0)} and seeded with the
	 * real first path segment {@code pos(1) - pos(0)}, otherwise the first tick
	 * moves radially from the anchor instead of tangentially along the path.
	 * Must be called after {@code mover} is assigned and before
	 * {@code holder.shoot()} so spawn data carries the corrected state.
	 */
	public void initializeMoverAtSpawn() {
		if (!(mover instanceof TargetPosMover targetMover)) {
			return;
		}
		MoverInfo.OwnerInfo ownerInfo = snapshotOwnerInfo(getOwner());
		Vec3 originalVelocity = getDeltaMovement();

		MoverInfo zeroInfo = new MoverInfo(0, position(), originalVelocity, this, ownerInfo);
		Vec3 startPos = targetMover.pos(zeroInfo);
		setPos(startPos);

		MoverInfo firstInfo = new MoverInfo(1, startPos, originalVelocity, this, ownerInfo);
		ProjectileMovement firstMove = targetMover.move(firstInfo);
		setDeltaMovement(firstMove.vec());
		setInitialRotation(firstMove.rot());
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
	protected void prepareMoveState() {
		if (mover != null) mover.prepare(this);
	}

	protected ProjectileMovement updateVelocity(Vec3 vec, Vec3 pos) {
		if (mover != null) {
			return mover.move(new MoverInfo(tickCount, pos, vec, this, tickData().ownerInfo));
		}
		return new ProjectileMovement(vec, rot());
	}

	public void setItem(ItemStack pStack) {
		stack = pStack.copyWithCount(1);
		sizeCache = null;
		refreshDimensions();
	}

	public void configureVisualScale(float scale, NumberProvider function) {
		visualScale = Math.max(0.05f, scale);
		visualScaleFunction = function;
		updateVisualScaleDimensions(true);
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

	private Float sizeCache = null;

	public float scale() {
		if (sizeCache == null) {
			if (getItem().getItem() instanceof LaserItem item) {
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

	private boolean isErased = false;

	public void markErased(boolean kill) {
		if (!isErased)
			super.markErased(kill);
		isErased = true;
	}

	@Override
	public boolean isValid() {
		return !isErased && super.isValid();
	}

	private int lastGraze = 0;

	@Override
	public void doGraze(Player entity) {
		if (tickCount < lastGraze) return;
		lastGraze = tickCount + 5;
		GrazeHelper.graze(entity, this);
	}

	@Override
	public void poof() {
		if (!level().isClientSide()) return;
		if (!(getItem().getItem() instanceof LaserItem item)) return;
		int col = item.color.getTextColor();
		var pos = position().add(0, getBbHeight() / 2, 0);
		DanmakuParticleHelper.line(level(), pos, getForward(), col, length, getBbWidth() / 2, random);
	}

}
