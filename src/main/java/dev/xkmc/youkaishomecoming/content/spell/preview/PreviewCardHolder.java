package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.compat.ysm.YsmRenderOverrideTarget;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.init.data.YHDamageTypes;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A CardHolder implementation for the preview system.
 * Entities are stored in a local pool and never injected into the real world.
 */
public class PreviewCardHolder implements CardHolder, YsmRenderOverrideTarget {

	private final Level level;
	private final FakeCasterEntity fakeCaster;
	private final ArmorStand fakeTarget;
	private final List<Entity> localEntities = new ArrayList<>();
	private final List<Entity> pendingEntities = new ArrayList<>();
	private boolean ticking = false;

	/** Safety limit: maximum number of tracked entities. When exceeded, preview auto-pauses. */
	private static int maxEntityCount = 50_000;
	/** Whether the safety limit has been tripped. */
	private boolean safetyTripped = false;
	private final RandomSource random = RandomSource.create();
	/** Callback invoked when a danmaku hits the target AABB */
	private Runnable onTargetHit = null;
	/** Callback invoked when the preview runtime should switch to another spell definition. */
	private BiConsumer<SpellDefinition, Boolean> onSpellSwitch = null;
	/** Callback invoked when the preview runtime should switch to another phase. */
	private BiConsumer<ResourceLocation, Boolean> onPhaseSwitch = null;
	/** Set of entity IDs that have already hit the target (prevent double-counting) */
	private final java.util.Set<Integer> hitEntities = new java.util.HashSet<>();

	// Simulated target properties for preview
	private boolean targetOnGround = true;
	private float targetHealthRatio = 1.0f;
	private boolean targetFlying = false;
	private boolean targetFallFlying = false;

	/** Current action index being executed — set by SpellRuntime during tick, used to tag spawned danmaku. */
	private int currentSpawningActionIndex = -1;
	/** The action index currently selected in the editor (for highlighting). */
	private int highlightedActionIndex = -1;
	private String ysmModelOverride = "";
	private String ysmTextureOverride = "";
	private String ysmAnimationOverride = "";
	private int ysmModelOverrideUntil = 0;
	private int ysmTextureOverrideUntil = 0;
	private int ysmAnimationOverrideUntil = 0;

	public PreviewCardHolder(Level level) {
		this.level = level;
		this.fakeCaster = new FakeCasterEntity(level, this);
		this.fakeCaster.setPos(0, 0, 0);
		this.fakeCaster.setInvisible(true);
		this.fakeTarget = new ArmorStand(EntityType.ARMOR_STAND, level);
		this.fakeTarget.setPos(0, 0, -10);
		this.fakeTarget.setInvisible(true);
	}

	@Override
	public Vec3 center() {
		return fakeCaster.position().add(0, fakeCaster.getBbHeight() / 2, 0);
	}

	@Override
	public Vec3 forward() {
		Vec3 t = target();
		if (t == null) return new Vec3(0, 0, 1);
		return t.subtract(center()).normalize();
	}

	@Nullable
	@Override
	public Vec3 target() {
		return fakeTarget.position().add(0, fakeTarget.getBbHeight() / 2, 0);
	}

	@Override
	public RandomSource random() {
		return random;
	}

	@Override
	public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DyeColor color) {
		ItemDanmakuEntity danmaku = new ItemDanmakuEntity(YHEntities.ITEM_DANMAKU.get(), fakeCaster, level);
		danmaku.setPos(center());
		danmaku.setItem(type.get(color).asStack());
		danmaku.setup(getDamage(type), life, true, true, vec);
		return danmaku;
	}

	@Override
	public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, YHDanmaku.Laser type, DyeColor color) {
		ItemLaserEntity laser = new ItemLaserEntity(YHEntities.ITEM_LASER.get(), fakeCaster, level);
		laser.setItem(type.get(color).asStack());
		laser.setup(getDamage(type), life, len, true, vec);
		laser.setPos(pos);
		laser.setupLength = type.setupLength();
		return laser;
	}

	@Override
	public TextDanmakuEntity prepareTextDanmaku(int life, Vec3 pos, Vec3 dir, float size, String text, int textColor) {
		TextDanmakuEntity danmaku = new TextDanmakuEntity(YHEntities.TEXT_DANMAKU.get(), fakeCaster, level);
		danmaku.setPos(pos);
		danmaku.configureText(text, size, textColor);
		danmaku.setup(getDamage(YHDanmaku.Laser.PENCIL), life, danmaku.length, true, dir);
		danmaku.setupLength = YHDanmaku.Laser.PENCIL.setupLength();
		return danmaku;
	}

	@Override
	public ShooterEntity prepareShooter(ShooterData data, SpellCard spell) {
		// Anonymous subclass: override shoot() to redirect bullets to our local pool,
		// and override aiStep() to force serverAiStep() on client (needed for preview).
		ShooterEntity shooter = new ShooterEntity(YHEntities.SHOOTER.get(), level) {
			@Override
			public void shoot(Entity danmaku) {
				PreviewCardHolder.this.shoot(danmaku);
			}

			@Override
			public void aiStep() {
				super.aiStep();
				// In preview (client-side), serverAiStep() is skipped by LivingEntity.
				// Force it so the shooter's spell card ticks and spawns sub-danmaku.
				if (level().isClientSide()) {
					serverAiStep();
				}
			}
		};
		shooter.setup(fakeCaster, fakeTarget, data, spell);
		return shooter;
	}

	@Override
	public void shoot(Entity danmaku) {
		// Safety: refuse to spawn more entities if over limit
		if (safetyTripped) return;
		int total = localEntities.size() + pendingEntities.size();
		if (total >= maxEntityCount) {
			safetyTripped = true;
			return;
		}
		// Tag danmaku with source action index for highlighting
		if (danmaku instanceof ItemDanmakuEntity ide) {
			ide.sourceActionIndex = currentSpawningActionIndex;
		}
		// Setup trail action so trail danmaku work in preview
		if (danmaku instanceof ItemDanmakuEntity e && e.afterExpiry != null) {
			e.afterExpiry.setup(this);
		}
		if (danmaku instanceof ShooterEntity shooter) {
			shooter.setOldPosAndRot();
			shooter.yBodyRotO = shooter.yBodyRot;
			shooter.yHeadRotO = shooter.yHeadRot;
		}
		// Buffer during tick iteration to avoid ConcurrentModificationException
		if (ticking) {
			pendingEntities.add(danmaku);
		} else {
			localEntities.add(danmaku);
		}
	}

	/** Returns true if the safety limit has been tripped and preview should pause. */
	public boolean isSafetyTripped() {
		return safetyTripped;
	}

	/** Reset the safety flag (e.g. after user clears entities or adjusts params). */
	public void resetSafety() {
		safetyTripped = false;
	}

	/** Get the current entity count safety limit. */
	public static int getMaxEntityCount() {
		return maxEntityCount;
	}

	/** Set the entity count safety limit. */
	public static void setMaxEntityCount(int limit) {
		maxEntityCount = Math.max(100, limit);
	}

	@Override
	public LivingEntity self() {
		return fakeCaster;
	}

	@Override
	public DamageSource getDanmakuDamageSource(IYHDanmaku danmaku) {
		return YHDamageTypes.danmaku(danmaku);
	}

	@Nullable
	@Override
	public Vec3 targetVelocity() {
		return Vec3.ZERO;
	}

	@Nullable
	@Override
	public LivingEntity targetEntity() {
		return fakeTarget;
	}

	@Override
	public float getDamage(YHDanmaku.IDanmakuType type) {
		return type.damage();
	}

	/**
	 * Tick all local entities.
	 * For SimplifiedProjectile (danmaku/laser): replicate ClientDanmakuCache.tick() exactly.
	 * For ShooterEntity: manual movement + lifetime + spell tick.
	 */
	public void tick() {
		expireYsmRenderOverride();
		tickFakeCaster();
		// Auto-reset safety flag when entity count drops below limit
		if (safetyTripped && localEntities.size() + pendingEntities.size() < maxEntityCount) {
			safetyTripped = false;
		}
		ticking = true;
		var iterator = localEntities.iterator();
		while (iterator.hasNext()) {
			Entity e = iterator.next();

			if (e instanceof SimplifiedProjectile sp) {
				// Replicate ClientDanmakuCache.tick() behavior exactly
				sp.setOldPosAndRot();
				++sp.tickCount;
				sp.tick();
				if (!sp.isValid()) {
					// Manually trigger trail actions (terminate() only runs on ServerLevel)
					if (e instanceof ItemDanmakuEntity danmaku && danmaku.afterExpiry != null) {
						CardHolder trailHolder = null;
						Entity owner = danmaku.getOwner();
						if (owner instanceof CardHolder h) trailHolder = h;
						if (trailHolder == null) danmaku.afterExpiry.execute(danmaku.position(), danmaku.getDeltaMovement());
						else danmaku.afterExpiry.execute(trailHolder, danmaku.position(), danmaku.getDeltaMovement());
					}
					iterator.remove();
				}
			} else if (e instanceof ShooterEntity shooter) {
				if (!tickShooter(shooter)) {
					iterator.remove();
				}
			} else {
				e.tick();
				if (!e.isAlive() || e.isRemoved()) {
					iterator.remove();
				}
			}
		}
		// Flush entities spawned during tick (by ShooterEntity spellCard, trail actions, etc.)
		// Must flush BEFORE releasing ticking flag so new entities are visible to hit detection
		if (!pendingEntities.isEmpty()) {
			localEntities.addAll(pendingEntities);
			pendingEntities.clear();
		}
		ticking = false;
		// Check collision with target bounding box for hit counting.
		// Direct scan: spatial hash was a net negative for single-target scenarios
		// (insert overhead 4.35% vs query savings 0.03% per spark profiling).
		if (onTargetHit != null) {
			var targetBB = fakeTarget.getBoundingBox().inflate(0.3); // slightly larger for forgiving hits
			for (int i = 0, size = localEntities.size(); i < size; i++) {
				Entity e = localEntities.get(i);
				if (e instanceof SimplifiedProjectile && !hitEntities.contains(e.getId())) {
					if (targetBB.contains(e.position()) || targetBB.intersects(e.getBoundingBox())) {
						hitEntities.add(e.getId());
						onTargetHit.run();
					}
				}
			}
		}
	}

	private boolean tickShooter(ShooterEntity shooter) {
		int lifetime = Math.max(1, shooter.lifetime());
		if (shooter.tickCount >= lifetime) {
			shooter.discard();
			return false;
		}

		try {
			shooter.setOldPosAndRot();
			shooter.yBodyRotO = shooter.yBodyRot;
			shooter.yHeadRotO = shooter.yHeadRot;
			// tick() handles movement, aiStep(), and serverAiStep() (via our override)
			shooter.tick();
		} catch (Exception ignored) {
			shooter.discard();
			return false;
		}
		if (shooter.tickCount >= lifetime) {
			shooter.discard();
			return false;
		}
		return shooter.isAlive() && !shooter.isRemoved();
	}

	public List<Entity> getLocalEntities() {
		return localEntities;
	}

	public int getEntityCount() {
		return localEntities.size();
	}

	public void clear() {
		localEntities.clear();
		pendingEntities.clear();
		hitEntities.clear();
		safetyTripped = false;
	}

	public void setTargetDistance(float distance) {
		Vec3 dir = forward();
		fakeTarget.setPos(center().add(dir.scale(distance)));
	}

	public void setTargetPos(Vec3 pos) {
		fakeTarget.setPos(pos);
	}

	public Vec3 getTargetPos() {
		return fakeTarget.position();
	}

	public void setCasterHealth(float ratio) {
		float maxHealth = fakeCaster.getMaxHealth();
		fakeCaster.setHealth(maxHealth * Math.max(0.01f, ratio));
	}

	public ArmorStand getFakeCaster() {
		return fakeCaster;
	}

	public ArmorStand getFakeTarget() {
		return fakeTarget;
	}

	@Override
	public void setYsmRenderOverride(String modelId, String textureName, String animationHint, int duration, String clearTarget) {
		String model = YsmRenderOverrideTarget.normalizeYsmOverride(modelId);
		String texture = YsmRenderOverrideTarget.normalizeYsmOverride(textureName);
		String animation = YsmRenderOverrideTarget.normalizeYsmOverride(animationHint);
		if (!model.isBlank()) {
			ysmModelOverride = model;
		}
		if (!texture.isBlank()) {
			ysmTextureOverride = texture;
		}
		if (!animation.isBlank()) {
			ysmAnimationOverride = animation;
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
			ysmModelOverride = "";
			ysmModelOverrideUntil = 0;
		}
		if ((mask & YSM_CLEAR_TEXTURE) != 0) {
			ysmTextureOverride = "";
			ysmTextureOverrideUntil = 0;
		}
		if ((mask & YSM_CLEAR_ANIMATION) != 0) {
			ysmAnimationOverride = "";
			ysmAnimationOverrideUntil = 0;
		}
	}

	private void tickFakeCaster() {
		fakeCaster.setOldPosAndRot();
		fakeCaster.yBodyRotO = fakeCaster.yBodyRot;
		fakeCaster.yHeadRotO = fakeCaster.yHeadRot;
		syncFakeCasterFacing();
		fakeCaster.setOnGround(!hasYsmAnimationToken("fly"));
		fakeCaster.walkAnimation.update(hasYsmAnimationToken("walk") ? 0.8f : 0.0f, 0.4f);
		++fakeCaster.tickCount;
	}

	public void syncFakeCasterFacing() {
		Vec3 look = getCasterLookDirection();
		float yaw = getYawFromDirection(look, fakeCaster.getYRot());
		float pitch = getPitchFromDirection(look, fakeCaster.getXRot());
		if (fakeCaster.tickCount <= 0) {
			fakeCaster.yRotO = yaw;
			fakeCaster.xRotO = pitch;
			fakeCaster.yBodyRotO = yaw;
			fakeCaster.yHeadRotO = yaw;
		}
		fakeCaster.setYRot(yaw);
		fakeCaster.setXRot(pitch);
		fakeCaster.yBodyRot = yaw;
		fakeCaster.yHeadRot = yaw;
	}

	private Vec3 getCasterLookDirection() {
		Vec3 target = target();
		if (target != null) {
			Vec3 dir = target.subtract(center());
			if (dir.lengthSqr() > 1.0E-8) {
				return dir.normalize();
			}
		}
		return forward();
	}

	private static float getYawFromDirection(Vec3 dir, float fallback) {
		if (dir.horizontalDistanceSqr() < 1.0E-8) {
			return fallback;
		}
		return (float) -(Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG);
	}

	private static float getPitchFromDirection(Vec3 dir, float fallback) {
		double horizontal = dir.horizontalDistance();
		if (horizontal < 1.0E-8 && Math.abs(dir.y) < 1.0E-8) {
			return fallback;
		}
		return Mth.clamp((float) -(Mth.atan2(dir.y, horizontal) * Mth.RAD_TO_DEG), -90.0F, 90.0F);
	}

	@Override
	public boolean hasYsmRenderOverride() {
		return !getYsmModelOverride().isBlank() ||
				!getYsmTextureOverride().isBlank() ||
				!getYsmAnimationOverride().isBlank();
	}

	@Override
	public String getYsmModelOverride() {
		return hasActiveYsmField(ysmModelOverride, ysmModelOverrideUntil) ? ysmModelOverride : "";
	}

	@Override
	public String getYsmTextureOverride() {
		return hasActiveYsmField(ysmTextureOverride, ysmTextureOverrideUntil) ? ysmTextureOverride : "";
	}

	@Override
	public String getYsmAnimationOverride() {
		return hasActiveYsmField(ysmAnimationOverride, ysmAnimationOverrideUntil) ? ysmAnimationOverride : "";
	}

	@Override
	public int getYsmOverrideTicksRemaining() {
		int remaining = 0;
		remaining = mergeYsmRemaining(remaining, ysmModelOverrideUntil);
		remaining = mergeYsmRemaining(remaining, ysmTextureOverrideUntil);
		remaining = mergeYsmRemaining(remaining, ysmAnimationOverrideUntil);
		return remaining;
	}

	@Override
	public String describeYsmRenderOverride() {
		if (!hasYsmRenderOverride()) {
			return "none";
		}
		return "model=" + displayYsmOverride(ysmModelOverride, ysmModelOverrideUntil) +
				", texture=" + displayYsmOverride(ysmTextureOverride, ysmTextureOverrideUntil) +
				", animation=" + displayYsmOverride(ysmAnimationOverride, ysmAnimationOverrideUntil);
	}

	public boolean hasYsmAnimationToken(String expected) {
		for (String token : getYsmAnimationOverride().split("[,;|\\s]+")) {
			int equals = token.indexOf('=');
			String key = equals >= 0 ? token.substring(0, equals) : token;
			if (expected.equals(key.trim())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasActiveYsmField(String value, int until) {
		return !value.isBlank() && !isYsmFieldExpired(until);
	}

	private boolean isYsmFieldExpired(int until) {
		return until > 0 && fakeCaster.tickCount >= until;
	}

	private int mergeYsmRemaining(int current, int until) {
		if (until <= fakeCaster.tickCount) {
			return current;
		}
		int remaining = until - fakeCaster.tickCount;
		return current <= 0 ? remaining : Math.min(current, remaining);
	}

	private String displayYsmOverride(String value, int until) {
		if (value.isBlank()) {
			return "(keep)";
		}
		return until > 0 && fakeCaster.tickCount < until ? value + " (" + (until - fakeCaster.tickCount) + "t)" : value;
	}

	private void updateYsmFieldExpirations(int changedMask, int expireMask, int duration) {
		int until = duration > 0 ? fakeCaster.tickCount + duration : 0;
		if ((changedMask & YSM_CLEAR_MODEL) != 0 || (expireMask & YSM_CLEAR_MODEL) != 0) {
			ysmModelOverrideUntil = (expireMask & YSM_CLEAR_MODEL) != 0 ? until : 0;
		}
		if ((changedMask & YSM_CLEAR_TEXTURE) != 0 || (expireMask & YSM_CLEAR_TEXTURE) != 0) {
			ysmTextureOverrideUntil = (expireMask & YSM_CLEAR_TEXTURE) != 0 ? until : 0;
		}
		if ((changedMask & YSM_CLEAR_ANIMATION) != 0 || (expireMask & YSM_CLEAR_ANIMATION) != 0) {
			ysmAnimationOverrideUntil = (expireMask & YSM_CLEAR_ANIMATION) != 0 ? until : 0;
		}
	}

	private void expireYsmRenderOverride() {
		int mask = 0;
		if (isYsmFieldExpired(ysmModelOverrideUntil)) {
			mask |= YSM_CLEAR_MODEL;
		}
		if (isYsmFieldExpired(ysmTextureOverrideUntil)) {
			mask |= YSM_CLEAR_TEXTURE;
		}
		if (isYsmFieldExpired(ysmAnimationOverrideUntil)) {
			mask |= YSM_CLEAR_ANIMATION;
		}
		if (mask != 0) {
			clearYsmRenderOverride(mask);
		}
	}

	// --- Target property simulation ---

	public void setTargetOnGround(boolean onGround) { this.targetOnGround = onGround; }
	public boolean isTargetOnGround() { return targetOnGround; }

	public void setTargetHealthRatio(float ratio) { this.targetHealthRatio = ratio; }
	public float getTargetHealthRatio() { return targetHealthRatio; }

	public void setTargetFlying(boolean flying) { this.targetFlying = flying; }
	public boolean isTargetFlying() { return targetFlying; }

	public void setTargetFallFlying(boolean fallFlying) { this.targetFallFlying = fallFlying; }
	public boolean isTargetFallFlying() { return targetFallFlying; }

	public void setOnTargetHit(Runnable callback) { this.onTargetHit = callback; }
	public void setOnSpellSwitch(BiConsumer<SpellDefinition, Boolean> callback) { this.onSpellSwitch = callback; }
	public void setOnPhaseSwitch(BiConsumer<ResourceLocation, Boolean> callback) { this.onPhaseSwitch = callback; }

	public void setCurrentSpawningActionIndex(int index) { this.currentSpawningActionIndex = index; }
	public int getCurrentSpawningActionIndex() { return currentSpawningActionIndex; }
	public void setHighlightedActionIndex(int index) { this.highlightedActionIndex = index; }
	public int getHighlightedActionIndex() { return highlightedActionIndex; }
	public boolean switchSpell(SpellDefinition definition, boolean clearScreen) {
		if (onSpellSwitch == null) {
			return false;
		}
		onSpellSwitch.accept(definition, clearScreen);
		return true;
	}
	public boolean switchPhase(ResourceLocation phaseId, boolean clearScreen) {
		if (onPhaseSwitch == null) {
			return false;
		}
		onPhaseSwitch.accept(phaseId, clearScreen);
		return true;
	}

	/**
	 * A fake caster entity that implements CardHolder, so that danmaku
	 * using {@code getOwner() instanceof CardHolder} checks (AttachedMover,
	 * terminate trail actions, damage source) will work correctly in preview.
	 */
	static class FakeCasterEntity extends ArmorStand implements CardHolder {

		private final PreviewCardHolder holder;

		FakeCasterEntity(Level level, PreviewCardHolder holder) {
			super(EntityType.ARMOR_STAND, level);
			this.holder = holder;
		}

		@Override
		public Vec3 center() {
			return holder.center();
		}

		@Override
		public Vec3 forward() {
			return holder.forward();
		}

		@Nullable
		@Override
		public Vec3 target() {
			return holder.target();
		}

		@Override
		public RandomSource random() {
			return holder.random();
		}

		@Override
		public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DyeColor color) {
			return holder.prepareDanmaku(life, vec, type, color);
		}

		@Override
		public ItemLaserEntity prepareLaser(int life, Vec3 pos, Vec3 vec, float len, YHDanmaku.Laser type, DyeColor color) {
			return holder.prepareLaser(life, pos, vec, len, type, color);
		}

		@Override
		public TextDanmakuEntity prepareTextDanmaku(int life, Vec3 pos, Vec3 dir, float size, String text, int textColor) {
			return holder.prepareTextDanmaku(life, pos, dir, size, text, textColor);
		}

		@Override
		public ShooterEntity prepareShooter(ShooterData data, SpellCard spell) {
			return holder.prepareShooter(data, spell);
		}

		@Override
		public void shoot(Entity danmaku) {
			holder.shoot(danmaku);
		}

		@Override
		public LivingEntity self() {
			return this;
		}

		@Override
		public DamageSource getDanmakuDamageSource(IYHDanmaku danmaku) {
			return holder.getDanmakuDamageSource(danmaku);
		}

		@Nullable
		@Override
		public Vec3 targetVelocity() {
			return Vec3.ZERO;
		}

		@Nullable
		@Override
		public LivingEntity targetEntity() {
			return holder.targetEntity();
		}

		@Override
		public float getDamage(YHDanmaku.IDanmakuType type) {
			return holder.getDamage(type);
		}
	}
}
