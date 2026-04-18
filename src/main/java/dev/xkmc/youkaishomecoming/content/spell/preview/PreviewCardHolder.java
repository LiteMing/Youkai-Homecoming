package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterData;
import dev.xkmc.youkaishomecoming.content.spell.shooter.ShooterEntity;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.SpellCard;
import dev.xkmc.youkaishomecoming.init.data.YHDamageTypes;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import dev.xkmc.youkaishomecoming.init.registrate.YHEntities;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A CardHolder implementation for the preview system.
 * Entities are stored in a local pool and never injected into the real world.
 */
public class PreviewCardHolder implements CardHolder {

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
	/** Set of entity IDs that have already hit the target (prevent double-counting) */
	private final java.util.Set<Integer> hitEntities = new java.util.HashSet<>();

	// Simulated target properties for preview
	private boolean targetOnGround = true;
	private float targetHealthRatio = 1.0f;
	private boolean targetFlying = false;
	private boolean targetFallFlying = false;

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
		// Setup trail action so trail danmaku work in preview
		if (danmaku instanceof ItemDanmakuEntity e && e.afterExpiry != null) {
			e.afterExpiry.setup(this);
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
				tickShooter(shooter);
				if (!shooter.isAlive() || shooter.isRemoved()) {
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

	private void tickShooter(ShooterEntity shooter) {
		// Lifetime check before tick
		if (shooter.tickCount >= shooter.lifetime()) {
			shooter.discard();
			return;
		}

		try {
			// tick() handles movement, aiStep(), and serverAiStep() (via our override)
			shooter.tick();
		} catch (Exception ignored) {
			// Safety net for any server-only code paths
		}
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
