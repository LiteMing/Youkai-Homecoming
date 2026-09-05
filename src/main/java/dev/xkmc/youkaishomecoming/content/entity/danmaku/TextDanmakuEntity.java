package dev.xkmc.youkaishomecoming.content.entity.danmaku;

import dev.xkmc.fastprojectileapi.entity.ProjectileMovement;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType;
import dev.xkmc.youkaishomecoming.content.spell.mover.DanmakuMover;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverInfo;
import dev.xkmc.youkaishomecoming.content.spell.mover.MoverOwner;
import dev.xkmc.youkaishomecoming.util.GlyphRuns;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@SerialClass
public class TextDanmakuEntity extends YHBaseLaserEntity implements MoverOwner {

	public static final float DEFAULT_SIZE = 0.4f;
	private static final float MIN_SIZE = 0.05f;

	@SerialClass.SerialField
	public DanmakuMover mover;
	@SerialClass.SerialField
	public String text = "";
	@SerialClass.SerialField
	public String backText = "";
	@SerialClass.SerialField
	public int textColor = 0xFFFFFFFF;
	@SerialClass.SerialField
	public boolean perChar = false;
	@SerialClass.SerialField
	public float roll = 0;
	@SerialClass.SerialField
	public float size = DEFAULT_SIZE;

	public DanmakuDamageType damageTypeOverride = null;

	@Override
	public DanmakuDamageType getDamageTypeOverride() {
		return damageTypeOverride;
	}

	public TextDanmakuEntity(EntityType<? extends TextDanmakuEntity> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	public TextDanmakuEntity(EntityType<? extends TextDanmakuEntity> pEntityType, double pX, double pY, double pZ, Level pLevel) {
		super(pEntityType, pX, pY, pZ, pLevel);
	}

	public TextDanmakuEntity(EntityType<? extends TextDanmakuEntity> pEntityType, LivingEntity pShooter, Level pLevel) {
		super(pEntityType, pShooter, pLevel);
	}

	public void configureText(String text, float size, int textColor) {
		this.text = text == null ? "" : text;
		this.textColor = textColor;
		this.size = clampSize(size);
		this.length = computeAutoLength(this.text, this.size);
		refreshDimensions();
	}

	public static float computeAutoLength(String text, float size) {
		// Count decoded code points: "\\uE001" is one glyph slot, not six.
		int count = GlyphRuns.count(text);
		if (count <= 0) {
			return clampSize(size);
		}
		return count * clampSize(size);
	}

	private static float clampSize(float size) {
		return Math.max(MIN_SIZE, size);
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return super.getDimensions(pose).scale(size / DEFAULT_SIZE);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		size = clampSize(size);
		length = computeAutoLength(text, size);
		refreshDimensions();
	}

	@Override
	public void readSpawnData(FriendlyByteBuf data) {
		super.readSpawnData(data);
		size = clampSize(size);
		length = computeAutoLength(text, size);
		refreshDimensions();
	}

	@Override
	public TraceableEntity asTraceable() {
		return this;
	}

	@Override
	protected void prepareMoveState() {
		if (mover != null) mover.prepare(this);
	}

	@Override
	protected ProjectileMovement updateVelocity(Vec3 vec, Vec3 pos) {
		if (mover != null) {
			return mover.move(new MoverInfo(tickCount, pos, vec, this, tickData().ownerInfo));
		}
		return new ProjectileMovement(vec, rot());
	}

	public boolean fullBright() {
		return true;
	}

	private boolean isErased = false;

	@Override
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
		int col = textColor & 0x00FFFFFF;
		var pos = position().add(0, getBbHeight() / 2, 0);
		DanmakuParticleHelper.line(level(), pos, getForward(), col, length, getBbWidth() / 2, random);
	}

}
