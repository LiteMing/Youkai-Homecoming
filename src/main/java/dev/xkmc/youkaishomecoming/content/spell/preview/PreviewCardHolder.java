package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.spellcircle.SpellCircleHolder;
import dev.xkmc.youkaishomecoming.compat.ysm.YsmRenderOverrideTarget;
import dev.xkmc.youkaishomecoming.content.capability.GrazeHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemLaserEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.LaserBlockHitEffect;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor;
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
import net.minecraft.world.phys.AABB;
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
	/** Callback invoked when a danmaku uses entity-hit semantics on the preview target. */
	private Runnable onTargetHit = null;
	/** Callback invoked when the preview runtime should switch to another spell definition. */
	private BiConsumer<SpellDefinition, Boolean> onSpellSwitch = null;
	/** Callback invoked when the preview runtime should switch to another phase. */
	private BiConsumer<ResourceLocation, Boolean> onPhaseSwitch = null;
	@Nullable
	private java.util.function.Supplier<dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime> runtimeSupplier = null;

	public void setRuntimeSupplier(java.util.function.Supplier<dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime> supplier) {
		this.runtimeSupplier = supplier;
	}
	private void forgetProjectile(SimplifiedProjectile p) {
		entityHitProjectiles.remove(p);
		blockContactStates.remove(p);
	}
	private final java.util.Set<SimplifiedProjectile> entityHitProjectiles = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	private record BlockContactState(int lastHitTick, Vec3 lastNormal) {}
	private final java.util.Map<SimplifiedProjectile, BlockContactState> blockContactStates = new java.util.IdentityHashMap<>();

	// Simulated target properties for preview
	private boolean targetOnGround = true;
	private float targetHealthRatio = 1.0f;
	private boolean targetFlying = false;
	private boolean targetFallFlying = false;
	/** Preview-only player power override; never written back to the real player. */
	private double casterPower = 0;
	private Vec3 blockTargetPos = PreviewTarget.getRememberedBoxPos();
	private Vec3 targetBoxSize = PreviewTarget.getRememberedBoxSize();
	/** Real target velocity for aim-lead spells (updated by setTargetPos diff or pilot). */
	private Vec3 targetVelocity = Vec3.ZERO;

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
	private boolean previewSpellCircleOverride = false;
	private boolean previewSpellCircleVisible = false;
	@Nullable
	private ResourceLocation previewSpellCircle = null;
	private float previewSpellCircleSize = 1.0f;

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
		if (t == null) return new Vec3(0, 0, -1);
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
	public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DanmakuColor color) {
		ItemDanmakuEntity danmaku = new ItemDanmakuEntity(YHEntities.ITEM_DANMAKU.get(), fakeCaster, level);
		danmaku.setPos(center());
		// For DYE_TEXTURES mode: use the specific colored item (has correct texture baked in)
		// For TINTED/FIXED modes: use BASE_DANMAKU with NBT color and runtime tint
		if (type.usesDyeTextures()) {
			net.minecraft.world.item.DyeColor dyeColor = color.toDyeColor();
			danmaku.setItem(type.get(dyeColor).asStack());
		} else {
			danmaku.setItem(type.stack(color));
			danmaku.setTint(color.argb());
		}
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
		shooter.setup(fakeCaster, targetEntity(), data, spell);
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
		if (danmaku instanceof ItemLaserEntity e && e.afterExpiry != null) {
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
		return targetVelocity;
	}

	public void setTargetVelocity(Vec3 velocity) {
		this.targetVelocity = velocity == null ? Vec3.ZERO : velocity;
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
					if (e instanceof ItemLaserEntity laser && laser.afterExpiry != null) {
						laser.runExpiryActionOnce(this, laser.position(), laser.getDeltaMovement());
					}
					forgetProjectile(sp);
					iterator.remove();
				}
			} else if (e instanceof ShooterEntity shooter) {
				if (!tickShooter(shooter)) {
					iterator.remove();
				}
			} else {
				e.tick();
				if (!e.isAlive() || e.isRemoved()) {
					if (e instanceof SimplifiedProjectile sp) forgetProjectile(sp);
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
		detectTargetHits();
	}

	private void detectTargetHits() {
		for (int i = 0, size = localEntities.size(); i < size; i++) {
			Entity entity = localEntities.get(i);
			if (!(entity instanceof SimplifiedProjectile projectile) || projectile.tickCount <= 0) continue;
			List<PreviewHit> hits = new ArrayList<>(2);
			java.util.Optional<PreviewHit> blockHit = findTargetHit(
					projectile, getBlockTargetCollisionBox(), PreviewTarget.HitType.BLOCK);
			if (projectile instanceof YHBaseLaserEntity laser) {
				laser.earlyTerminate = blockHit.map(hit -> Math.sqrt(hit.distanceSqr())).orElse(-1.0);
			}
			if (!entityHitProjectiles.contains(projectile)) {
				findTargetHit(projectile, getEntityTargetCollisionBox(), PreviewTarget.HitType.ENTITY)
						.ifPresent(hits::add);
			}
			if (blockHit.isPresent()) {
				PreviewHit bh = blockHit.get();
				BlockContactState contact = blockContactStates.get(projectile);
				// Debounce block hit on the same contact surface within the same tick / recent contact
				boolean isSameContact = contact != null && contact.lastHitTick == projectile.tickCount && contact.lastNormal.dot(bh.normal()) > 0.9;
				if (!isSameContact) {
					hits.add(bh);
				}
			}
			hits.sort(java.util.Comparator.comparingDouble(PreviewHit::distanceSqr));
			for (PreviewHit hit : hits) {
				if (!projectile.isValid()) break;
				if (hit.type() == PreviewTarget.HitType.ENTITY) {
					entityHitProjectiles.add(projectile);
				} else {
					blockContactStates.put(projectile, new BlockContactState(projectile.tickCount, hit.normal()));
				}
				handlePreviewTargetHit(projectile, hit);
			}
		}
		localEntities.removeIf(entity -> {
			if (entity instanceof SimplifiedProjectile projectile && !projectile.isValid()) {
				forgetProjectile(projectile);
				return true;
			}
			return false;
		});
	}

	private java.util.Optional<PreviewHit> findTargetHit(SimplifiedProjectile projectile, AABB targetBox,
												 PreviewTarget.HitType type) {
		Vec3 from;
		Vec3 to;
		java.util.Optional<Vec3> hit;
		Vec3 hitNormal = Vec3.ZERO;
		if (projectile instanceof YHBaseLaserEntity laser) {
			if (type == PreviewTarget.HitType.ENTITY && !laser.checkEntityHit()) {
				return java.util.Optional.empty();
			}
			if (type == PreviewTarget.HitType.ENTITY) {
				targetBox = targetBox.inflate(laser.getEffectiveHitRadius());
			}
			from = laser.position().add(0, laser.getBbHeight() / 2, 0);
			float length = type == PreviewTarget.HitType.BLOCK
					? (float) laser.getLength() : laser.effectiveLength(0);
			to = from.add(laser.getForward().scale(length));
			if (type == PreviewTarget.HitType.BLOCK) {
				var surf = PreviewTarget.firstSurfaceIntersectionWithNormal(targetBox, from, to);
				hit = surf.map(PreviewTarget.SurfaceHit::pos);
				hitNormal = surf.map(PreviewTarget.SurfaceHit::normal).orElse(Vec3.ZERO);
			} else {
				hit = PreviewTarget.firstVolumeIntersection(targetBox, from, to);
			}
		} else {
			if (type == PreviewTarget.HitType.ENTITY) {
				targetBox = targetBox.inflate(projectile.getBbWidth() * 0.5);
			}
			// BaseProjectile sweeps from position(), while lasers use their vertical center line.
			from = new Vec3(projectile.xOld, projectile.yOld, projectile.zOld);
			to = projectile.position();
			if (type == PreviewTarget.HitType.BLOCK) {
				var surf = PreviewTarget.firstSurfaceIntersectionWithNormal(targetBox, from, to);
				hit = surf.map(PreviewTarget.SurfaceHit::pos);
				hitNormal = surf.map(PreviewTarget.SurfaceHit::normal).orElse(Vec3.ZERO);
			} else {
				hit = PreviewTarget.firstVolumeIntersection(targetBox, from, to);
			}
		}
		final Vec3 finalNormal = hitNormal;
		return hit.map(pos -> new PreviewHit(type, pos, from.distanceToSqr(pos), finalNormal));
	}

	private void handlePreviewTargetHit(SimplifiedProjectile projectile, PreviewHit hit) {
		if (hit.type() == PreviewTarget.HitType.ENTITY && onTargetHit != null) {
			onTargetHit.run();
		}
		if (projectile instanceof ItemDanmakuEntity danmaku) {
			handlePreviewProjectileHook(projectile, hit,
					hit.type() == PreviewTarget.HitType.ENTITY ? danmaku.onHitEntityAction : danmaku.onHitBlockAction,
					hit.type() == PreviewTarget.HitType.ENTITY ? danmaku.hitBehaviorEntity : danmaku.hitBehaviorBlock,
					danmaku.afterExpiry);
		} else if (projectile instanceof ItemLaserEntity laser) {
			handlePreviewLaserHook(laser, hit,
					hit.type() == PreviewTarget.HitType.ENTITY ? laser.onHitEntityAction : laser.onHitBlockAction,
					hit.type() == PreviewTarget.HitType.ENTITY ? laser.hitBehaviorEntity : laser.hitBehaviorBlock,
					laser.afterExpiry);
		}
	}

	private void handlePreviewLaserHook(ItemLaserEntity laser, PreviewHit hit,
			dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction hitAction,
			dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior behavior,
			dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction afterExpiry) {
		if (hit.type() != PreviewTarget.HitType.BLOCK) {
			handlePreviewProjectileHook(laser, hit, hitAction, behavior, afterExpiry);
			return;
		}
		if (hitAction != null) hitAction.executeBlockHit(this, hit.position(), laser.getForward());
		switch (LaserBlockHitEffect.from(behavior)) {
			case CLIP_ONLY -> {
			}
			case CLIP_AND_SUPPRESS_EXPIRY -> laser.suppressExpiryAction();
			case CLIP_AND_RUN_EXPIRY -> laser.runExpiryActionOnce(this, hit.position(), laser.getForward());
		}
	}

	private void handlePreviewProjectileHook(SimplifiedProjectile projectile, PreviewHit hit,
											 dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction hitAction,
											 dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior behavior,
											 dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction afterExpiry) {
		Vec3 from = new Vec3(projectile.xOld, projectile.yOld, projectile.zOld);
		Vec3 to = projectile.position();
		var hitCtx = new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext(
				projectile,
				hit.type() == PreviewTarget.HitType.BLOCK ? dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext.HitType.BLOCK : dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext.HitType.ENTITY,
				from,
				hit.position(),
				to,
				hit.normal(),
				projectile.getDeltaMovement(),
				null
		);

		if (hitAction != null) {
			if (hit.type() == PreviewTarget.HitType.BLOCK) {
				hitAction.executeBlockHit(this, hitCtx);
			} else {
				hitAction.executeEntityHit(this, hitCtx);
			}
		}

		if (hitCtx.isTerminal()) {
			applyPreviewHitDisposition(hitCtx, afterExpiry);
			return;
		}

		switch (behavior) {
			case CONTINUE -> {
				if (hit.type() == PreviewTarget.HitType.BLOCK && projectile instanceof ItemDanmakuEntity ide) {
					ide.applyContinueState(hitCtx.movementEnd(), hitCtx.incomingVelocity());
				}
			}
			case DISCARD -> projectile.markErased(false);
			case EXPIRE -> {
				if (afterExpiry != null) {
					afterExpiry.execute(this, hit.position(), projectile.getDeltaMovement());
				}
				projectile.markErased(false);
			}
		}
	}

	private void applyPreviewHitDisposition(
			dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext hitCtx,
			@Nullable dev.xkmc.youkaishomecoming.content.spell.spellcard.TrailAction afterExpiry
	) {
		SimplifiedProjectile projectile = hitCtx.source();
		switch (hitCtx.disposition()) {
			case CONTINUE -> {
				if (projectile instanceof ItemDanmakuEntity ide
						&& hitCtx.hitType() == dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext.HitType.BLOCK) {
					ide.applyContinueState(hitCtx.movementEnd(), hitCtx.incomingVelocity());
				}
			}
			case EXPIRE -> {
				if (afterExpiry != null) {
					afterExpiry.execute(this, hitCtx.hitPosition(), hitCtx.incomingVelocity());
				}
				projectile.markErased(false);
			}
			case DISCARD -> projectile.markErased(false);
			case HOLD -> {
				if (projectile instanceof ItemDanmakuEntity ide && hitCtx.deferredBody() != null) {
					Vec3 holdPos = hitCtx.hitPosition().add(hitCtx.hitNormal().normalize().scale(0.08));
					ide.enterHoldState(holdPos, hitCtx.incomingVelocity());
					if (runtimeSupplier != null && runtimeSupplier.get() != null) {
						var runtime = runtimeSupplier.get();
						runtime.scheduleDelayed(runtime.getTotalTick() + hitCtx.holdTicks(), List.of(
								new dev.xkmc.youkaishomecoming.content.spell.action.SpellAction() {
									@Override
									public void execute(dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext ctx) {
										if (ide.isAlive() && !ide.isRemoved()) {
											var releaseBody = hitCtx.beginResumeAndTakeBody();
											if (releaseBody != null) {
												var resumedCtx = new dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext(
														ctx.holder(), ctx.definition(), ctx.runtime(), ctx.difficulty(), hitCtx);
												resumedCtx.executeList(releaseBody);
											}
											if (hitCtx.isTerminal()) {
												applyPreviewHitDisposition(hitCtx, afterExpiry);
											} else {
												dev.xkmc.youkaishomecoming.content.entity.danmaku.HitBehavior fallback = hitCtx.hitType() == dev.xkmc.youkaishomecoming.content.spell.runtime.SpellHitContext.HitType.BLOCK
														? ide.hitBehaviorBlock : ide.hitBehaviorEntity;
												switch (fallback) {
													case CONTINUE -> {
														ide.applyContinueState(hitCtx.movementEnd(), hitCtx.incomingVelocity());
													}
													case EXPIRE -> {
														ide.clearHoldState();
														if (afterExpiry != null) afterExpiry.execute(PreviewCardHolder.this, hitCtx.hitPosition(), hitCtx.incomingVelocity());
														projectile.markErased(false);
													}
													case DISCARD -> {
														ide.clearHoldState();
														projectile.markErased(false);
													}
												}
											}
										}
									}
								}
						));
					} else {
						ide.clearHoldState();
						projectile.markErased(false);
					}
				}
			}
			case BOUNCE -> {
				if (projectile instanceof ItemDanmakuEntity ide) {
					var result = dev.xkmc.youkaishomecoming.content.spell.physics.DanmakuBounceResolver.resolve(
							hitCtx.hitPosition(), hitCtx.incomingVelocity(), hitCtx.hitNormal(),
							hitCtx.bounceConfig(),
							ide.currentBounces, target());
					if (result.erased()) {
						ide.clearHoldState();
						switch (ide.hitBehaviorBlock) {
							case CONTINUE -> {
								ide.applyContinueState(hitCtx.movementEnd(), hitCtx.incomingVelocity());
								return;
							}
							case EXPIRE -> {
								if (afterExpiry != null) afterExpiry.execute(PreviewCardHolder.this, hitCtx.hitPosition(), hitCtx.incomingVelocity());
								projectile.markErased(false);
								return;
							}
							case DISCARD -> {
								projectile.markErased(false);
								return;
							}
						}
						projectile.markErased(false);
						return;
					}
					ide.applyBounceState(result.newPos(), result.newVel(), result.updatedBounces());
				} else {
					Vec3 v = hitCtx.incomingVelocity();
					Vec3 normal = hitCtx.hitNormal();
					if (normal.lengthSqr() > 1e-4) {
						double dot = v.dot(normal);
						projectile.snapMotionAndRotation(v.subtract(normal.scale(2 * dot)));
						projectile.setPos(hitCtx.hitPosition().add(normal.scale(0.08)));
					} else {
						projectile.snapMotionAndRotation(new Vec3(-v.x, v.y, -v.z));
					}
				}
			}
			case UNRESOLVED -> projectile.markErased(false);
		}
	}

	private record PreviewHit(PreviewTarget.HitType type, Vec3 position, double distanceSqr, Vec3 normal) {
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
			int beforeTick = shooter.tickCount;
			shooter.tick();
			if (shooter.tickCount == beforeTick) {
				++shooter.tickCount;
			}
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
		entityHitProjectiles.clear();
		blockContactStates.clear();
		safetyTripped = false;
		targetVelocity = Vec3.ZERO;
		clearPreviewSpellCircle();
	}

	public void setTargetDistance(float distance) {
		Vec3 dir = forward();
		fakeTarget.setPos(center().add(dir.scale(distance)));
	}

	public void setTargetPos(Vec3 pos) {
		Vec3 prev = fakeTarget.position();
		fakeTarget.setPos(pos);
		// Maintain velocity by finite difference unless explicitly set the same tick by pilot
		this.targetVelocity = pos.subtract(prev);
	}

	/** Set position and velocity together (pilot path — avoids double-diff). */
	public void setTargetPosAndVelocity(Vec3 pos, Vec3 velocity) {
		fakeTarget.setPos(pos);
		this.targetVelocity = velocity == null ? Vec3.ZERO : velocity;
	}

	public Vec3 getTargetPos() {
		return fakeTarget.position();
	}

	public AABB getEntityTargetCollisionBox() {
		return fakeTarget.getBoundingBox().inflate(0.3);
	}

	public AABB getBlockTargetCollisionBox() {
		return PreviewTarget.boxAt(blockTargetPos, targetBoxSize);
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

	public void setPreviewSpellCircle(ResourceLocation circle, float size) {
		previewSpellCircleOverride = true;
		previewSpellCircleVisible = true;
		previewSpellCircle = circle;
		previewSpellCircleSize = sanitizePreviewSpellCircleSize(size);
	}

	public void hidePreviewSpellCircle() {
		previewSpellCircleOverride = true;
		previewSpellCircleVisible = false;
		previewSpellCircle = null;
		previewSpellCircleSize = 1.0f;
	}

	public void clearPreviewSpellCircle() {
		previewSpellCircleOverride = false;
		previewSpellCircleVisible = false;
		previewSpellCircle = null;
		previewSpellCircleSize = 1.0f;
	}

	private boolean shouldShowPreviewSpellCircle() {
		return previewSpellCircleOverride && previewSpellCircleVisible && previewSpellCircle != null;
	}

	@Nullable
	private ResourceLocation getPreviewSpellCircle() {
		return shouldShowPreviewSpellCircle() ? previewSpellCircle : null;
	}

	private float getPreviewSpellCircleSize() {
		return shouldShowPreviewSpellCircle() ? previewSpellCircleSize : 0.0f;
	}

	private static float sanitizePreviewSpellCircleSize(float size) {
		if (!Float.isFinite(size)) {
			return 1.0f;
		}
		return Math.max(0.0f, Math.min(64.0f, size));
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

	public void setCasterPower(double power) { casterPower = Mth.clamp(power, 0, GrazeHelper.getMaximumPowerLevel()); }
	@Override public double casterPower() { return casterPower; }

	public void setBlockTargetPos(Vec3 pos) {
		blockTargetPos = pos == null ? Vec3.ZERO : pos;
		PreviewTarget.rememberBoxPos(blockTargetPos);
		blockContactStates.clear();
	}
	public Vec3 getBlockTargetPos() { return blockTargetPos; }

	public void setTargetBoxSize(Vec3 size) {
		targetBoxSize = PreviewTarget.sanitizeSize(size);
		PreviewTarget.rememberBoxSize(targetBoxSize);
		blockContactStates.clear();
	}
	public Vec3 getTargetBoxSize() { return targetBoxSize; }

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
	static class FakeCasterEntity extends ArmorStand implements CardHolder, SpellCircleHolder {

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
		public ItemDanmakuEntity prepareDanmaku(int life, Vec3 vec, YHDanmaku.Bullet type, DanmakuColor color) {
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
		public double casterPower() {
			return holder.casterPower();
		}

		@Override
		public boolean shouldShowSpellCircle() {
			return holder.shouldShowPreviewSpellCircle();
		}

		@Nullable
		@Override
		public ResourceLocation getSpellCircle() {
			return holder.getPreviewSpellCircle();
		}

		@Override
		public float getCircleSize(float pTick) {
			return holder.getPreviewSpellCircleSize();
		}

		@Override
		public DamageSource getDanmakuDamageSource(IYHDanmaku danmaku) {
			return holder.getDanmakuDamageSource(danmaku);
		}

		@Nullable
		@Override
		public Vec3 targetVelocity() {
			return holder.targetVelocity();
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
