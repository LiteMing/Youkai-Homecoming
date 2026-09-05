package dev.xkmc.youkaishomecoming.content.entity.boss;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.entity.movement.*;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.SpellProgressColor;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHDamageTypes;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@SerialClass
public class BossYoukaiEntity extends GeneralYoukaiEntity implements MovementControlled {

	public static AttributeSupplier.Builder createAttributes() {
		return YoukaiEntity.createAttributes()
				.add(Attributes.MAX_HEALTH, 200)
				.add(Attributes.ATTACK_DAMAGE, 10)
				.add(Attributes.FOLLOW_RANGE, 128);
	}

	private static final Logger BOSS_LOGGER = LoggerFactory.getLogger("YH-BossChunkForce");

	protected final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED,
			BossEvent.BossBarOverlay.NOTCHED_20);
	private boolean ticking = false;

	// 移动控制器
	private final CompositeMovementController movementController = new CompositeMovementController();
	private boolean movementInitialized = false;

	// NBT 键名常量
	private static final String NBT_DODGE = "enableDodge";
	private static final String NBT_CHASE_FLEE = "enableChaseFlee";
	private static final String NBT_STRAFE = "enableStrafe";

	// 缓存的标志状态 (用于检测变化)
	private boolean cachedDodge = false;
	private boolean cachedChaseFlee = false;
	private boolean cachedStrafe = false;

	@SerialClass.SerialField
	private ResourceLocation spawnDimension;

	// ========== 区块强加载 (防止战斗中 Boss 被卸载) ==========
	private ChunkPos forcedChunkPos = null; // 当前强加载的区块位置，null 表示未强加载
	/**
	 * 强加载宽限tick。Boss 目标超出范围后，短暂保持区块加载，
	 * 让脱战回血流程完成，避免在脱战瞬间被区块卸载。
	 */
	private static final int COMBAT_GRACE_TICKS = 60; // 3秒宽限期
	/**
	 * 独立的超距离计数器。因为 YoukaiTargetContainer 不检查距离，
	 * getTarget() 只要玩家存活就的返回非 null，所以 noTargetTime 永远为 0。
	 * 必须用这个独立计数器来追踪目标超出范围的时间。
	 */
	private int outOfRangeTicks = 0;

	public BossYoukaiEntity(EntityType<? extends BossYoukaiEntity> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
		setPersistenceRequired();
		// Boss bar only shows during combat (see customServerAiStep).
		bossEvent.setVisible(false);
	}

	// ========== 移动功能开关 (使用 PersistentData) ==========

	/**
	 * 获取闪避功能是否启用
	 */
	public boolean isDodgeEnabled() {
		return getPersistentData().getBoolean(NBT_DODGE);
	}

	/**
	 * 设置闪避功能开关
	 */
	public void setDodgeEnabled(boolean enabled) {
		getPersistentData().putBoolean(NBT_DODGE, enabled);
	}

	/**
	 * 获取追逐/逃跑功能是否启用
	 */
	public boolean isChaseFleeEnabled() {
		return getPersistentData().getBoolean(NBT_CHASE_FLEE);
	}

	/**
	 * 设置追逐/逃跑功能开关
	 */
	public void setChaseFleeEnabled(boolean enabled) {
		getPersistentData().putBoolean(NBT_CHASE_FLEE, enabled);
	}

	/**
	 * 获取斜向移动功能是否启用
	 */
	public boolean isStrafeEnabled() {
		return getPersistentData().getBoolean(NBT_STRAFE);
	}

	/**
	 * 设置斜向移动功能开关
	 */
	public void setStrafeEnabled(boolean enabled) {
		getPersistentData().putBoolean(NBT_STRAFE, enabled);
	}

	/**
	 * 一次性设置所有移动功能开关
	 */
	public void setAllMovementEnabled(boolean enabled) {
		setDodgeEnabled(enabled);
		setChaseFleeEnabled(enabled);
		setStrafeEnabled(enabled);
	}

	/**
	 * 刷新移动控制器 (当开关改变时调用)
	 */
	private void refreshMovementControllers() {
		movementController.clear();
		movementInitialized = false;
	}

	/**
	 * 检查移动标志位是否发生变化 (支持 /data merge 实时修改)
	 */
	private void checkMovementFlagsChange() {
		boolean currentDodge = isDodgeEnabled();
		boolean currentChaseFlee = isChaseFleeEnabled();
		boolean currentStrafe = isStrafeEnabled();

		if (currentDodge != cachedDodge ||
				currentChaseFlee != cachedChaseFlee ||
				currentStrafe != cachedStrafe) {
			cachedDodge = currentDodge;
			cachedChaseFlee = currentChaseFlee;
			cachedStrafe = currentStrafe;
			refreshMovementControllers();
		}
	}

	@Override
	public CompositeMovementController getMovementController() {
		// 检查标志位变化 (支持 /data merge 实时修改)
		checkMovementFlagsChange();
		if (!movementInitialized) {
			initMovementControllers();
			movementInitialized = true;
		}
		return movementController;
	}

	@Override
	public void initMovementControllers() {
		// 根据标志位开关添加控制器 (按优先级排序)

		// 1. 闪避控制器 - 最高优先级
		if (isDodgeEnabled()) {
			movementController.add(new DodgeController(this)
					.setScanRadius(10.0)
					.setDangerRadius(5.0)
					.setSpeedMultiplier(1.5));
		}

		// 2. 追逐/逃跑控制器
		if (isChaseFleeEnabled()) {
			movementController.add(new ChaseFleeController(this)
					.setIdealDistance(25.0)
					.setChaseThreshold(40.0)
					.setFleeThreshold(10.0)
					.setDiagonalFactor(0.4));
		}

		// 3. 斜向移动控制器
		if (isStrafeEnabled()) {
			movementController.add(new StrafeController(this)
					.setSpeedMultiplier(0.7)
					.setDirectionChangePeriod(80)
					.setVerticalWave(0.25, 0.08));
		}
	}

	@Override
	public boolean shouldHurt(LivingEntity le) {
		if (shouldIgnore(le))
			return false;
		return le instanceof Enemy || super.shouldHurt(le);
	}

	@Override
	public boolean canBeAffected(MobEffectInstance ins) {
		return ins.getEffect() == YHEffects.BEATEN.get() || ins.getEffect() == YHEffects.AUTO_DODGE.get();
	}

	@Override
	public void tick() {
		if (spawnDimension == null) {
			spawnDimension = level().dimension().location();
		} else if (!spawnDimension.equals(level().dimension().location())) {
			if (!YHModConfig.COMMON.canReimuTeleportToOtherDimension.get())
				discard();
		}
		ticking = true;
		double maxSpeed = 0.5;
		if (getDeltaMovement().length() > maxSpeed) {
			setDeltaMovement(getDeltaMovement().normalize().scale(maxSpeed));
		}
		validateData();
		super.tick();
		ticking = false;

		// 区块强加载逻辑：战斗状态下强制加载 Boss 所在区块
		if (!level().isClientSide()) {
			updateChunkForcing();
		}
	}

	@Override
	public void aiStep() {
		if (hurtCD < 1000)
			hurtCD++;
		super.aiStep();

		// Preserve the two YH effects that are explicitly allowed on bosses.
		if (!getActiveEffectsMap().isEmpty()) {
			var effectsToRemove = getActiveEffectsMap().keySet().stream()
					.filter(effect -> effect != YHEffects.BEATEN.get() && effect != YHEffects.AUTO_DODGE.get())
					.toList();

			for (var effect : effectsToRemove) {
				removeEffect(effect);
			}
		}
	}

	private int hurtCD = 0;
	private boolean hurtCall = false;
	private boolean chaotic = false;

	protected int getCD(DamageSource source) {
		if (!YHModConfig.COMMON.reimuExtraDamageCoolDown.get())
			return 10;
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
			return 10;
		if (source.getEntity() instanceof Player pl && pl.getAbilities().instabuild)
			return 10;
		if (source.is(YHDamageTypes.DANMAKU))
			return 20;
		if (source.is(DamageTypeTags.BYPASSES_COOLDOWN))
			return 30;
		return 50;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (ticking || source.getEntity() == this) {
			if (!source.is(DamageTypes.GENERIC_KILL))
				return false;
			else {
				var target = getTarget();
				if (target != null && amount >= getCombatProgress() && !isRemoved()) {
					trySummonReinforcementOnDeath(target);
					discard();
				}
			}
		}
		if (getTarget() instanceof Player && !(source.getEntity() instanceof Player))
			return false;
		if (source.getEntity() instanceof LivingEntity le) {
			setLastHurtByMob(le);
			targets.checkTarget();
		}
		if (!source.is(DamageTypes.GENERIC_KILL) || source.getEntity() != null) {
			if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) &&
					!(source.getEntity() instanceof LivingEntity))
				return false;
			if (source.getEntity() instanceof LivingEntity le) {
				if (shouldIgnore(le))
					return false;
			}
			int cd = getCD(source);
			if (hurtCD < cd) {
				return false;
			}
		}
		hurtCD = 0;
		hurtCall = true;
		boolean ans = super.hurt(source, amount);
		hurtCall = false;
		return ans;
	}

	@Override
	public boolean canSwimInFluidType(FluidType type) {
		return true;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	private float illegalDamage = 0;

	protected void notifyIllegalDamage(float amount, @Nullable Entity causer) {
		illegalDamage += amount;
		if (illegalDamage > 200) {
			setFlag(32, true);
		}
	}

	protected float clampDamage(DamageSource source, float amount) {
		if (!hurtCall) {
			notifyIllegalDamage(amount, source.getEntity());
			return 0;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (source.getEntity() instanceof LivingEntity le) {
				if (le instanceof ServerPlayer sp) {
					if (sp.isCreative()) {
						return amount;
					}
				}
			} else {
				if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
					if (amount > 4) {
						notifyIllegalDamage(amount - 4, source.getEntity());
					}
					return Math.min(4, amount);
				}
				if (source.is(DamageTypes.GENERIC_KILL)) {
					return amount;
				}
			}
		}
		int reduction = damageLimit();
		float ans = Math.min(getMaxHealth() / reduction, amount);
		if (YHModConfig.COMMON.reimuDamageReduction.get() && !source.is(YHDamageTypes.DANMAKU_TYPE))
			ans /= nonDanmakuReduction();
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			notifyIllegalDamage(amount - ans, source.getEntity());
		}
		return ans;
	}

	protected int damageLimit() {
		return 20;
	}

	protected int nonDanmakuReduction() {
		return 5;
	}

	@Override
	protected final void actuallyHurt(DamageSource source, float amount) {
		if (!hurtCall) {
			notifyIllegalDamage(amount, source.getEntity());
			return;
		}
		super.actuallyHurt(source, amount);
	}

	@Override
	protected void hurtFinal(DamageSource source, float amount) {
		if (!Float.isFinite(amount))
			return;
		amount = clampDamage(source, amount);
		if (amount <= 0)
			return;
		super.hurtFinal(source, amount);
	}

	@Override
	public void setHealth(float val) {
		if (!Float.isFinite(val))
			return;
		if (level().isClientSide()) {
			setCombatProgress(val);
		}
		float health = getCombatProgress();
		if (tickCount > 5 && val <= health) {
			notifyIllegalDamage(health - val, null);
			return;
		}
		setCombatProgress(val);
	}

	@Override
	public void setCombatProgress(float amount) {
		if (combatProgress != null) {
			float health = combatProgress.progress;
			if (health > getVanillaProgress()) {
				notifyIllegalDamage(health - getVanillaProgress(), null);
			}
			if (health > getCombatProgress()) {
				notifyIllegalDamage(health - getCombatProgress(), null);
				setFlag(32, true);
			}
		}
		super.setCombatProgress(amount);
	}

	public void heal(float original) {
		var heal = ForgeEventFactory.onLivingHeal(this, original);
		heal = Math.max(original, heal);
		if (heal <= 0)
			return;
		float f = getCombatProgress();
		if (f > 0) {
			setHealth(f + heal);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (hasCustomName()) {
			bossEvent.setName(getDisplayName());
		}

		// 读取移动开关 NBT 到 PersistentData
		// PersistentData 会自动从 ForgeData 标签读取，这里处理旧版本兼容
		if (tag.contains(NBT_DODGE)) {
			setDodgeEnabled(tag.getBoolean(NBT_DODGE));
		}
		if (tag.contains(NBT_CHASE_FLEE)) {
			setChaseFleeEnabled(tag.getBoolean(NBT_CHASE_FLEE));
		}
		if (tag.contains(NBT_STRAFE)) {
			setStrafeEnabled(tag.getBoolean(NBT_STRAFE));
		}

		// 刷新移动控制器
		refreshMovementControllers();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);

		// PersistentData 会自动保存到 ForgeData 标签
		// 这里我们仍然写入顶级 NBT 以便使用 /data merge
		tag.putBoolean(NBT_DODGE, isDodgeEnabled());
		tag.putBoolean(NBT_CHASE_FLEE, isChaseFleeEnabled());
		tag.putBoolean(NBT_STRAFE, isStrafeEnabled());
	}

	public void setCustomName(@Nullable Component pName) {
		super.setCustomName(pName);
		bossEvent.setName(getDisplayName());
	}

	@SerialClass.SerialField
	protected int noTargetTime;

	@Override
	protected void customServerAiStep() {
		if (isBeaten()) {
			bossEvent.setVisible(false);
			return;
		}
		super.customServerAiStep();
		if (combatProgress != null && getCombatProgress() != combatProgress.progress) {
			bossEvent.setProgress(random().nextFloat());
			return;
		}
		updateBossBarProjection();
		if (getTarget() == null || !getTarget().isAlive()) {
			chaotic = false;
			noTargetTime++;
			boolean doHeal = noTargetTime >= 20 && tickCount % 20 == 0;
			doHeal |= getCombatProgress() < getMaxHealth();
			if (doHeal && getLastHurtByMob() instanceof Player player && player.getAbilities().instabuild) {
				if (tickCount - getLastHurtByMobTimestamp() < 100) {
					doHeal = false;
				}
			}
			if (doHeal) {
				setHealth(getMaxHealth());
				if (getFlag(32)) {
					setFlag(32, false);
				}
			}
		} else {
			noTargetTime = 0;
		}
		// Boss bar only during combat: shown while a target exists, hidden
		// 40 ticks after losing it (mirrors MaidenEntity; moved here so all
		// bosses share the same non-combat visibility behavior).
		if (noTargetTime == 0) {
			bossEvent.setVisible(true);
		}
		if (noTargetTime > 40) {
			bossEvent.setVisible(false);
		}
	}

	private void updateBossBarProjection() {
		float progress = getCombatProgress();
		float maximum = combatProgress == null ? getMaxHealth()
				: combatProgress.getMaxProgress(getMaxHealth());
		Component name = getDisplayName();
		if (spellRuntime != null && spellRuntime.getSpellHealthTotal() > 0) {
			int total = spellRuntime.getSpellHealthTotal();
			int completed = Math.min(total, spellRuntime.getSpellHealthCompleted());
			int segmentMaximum = spellRuntime.getSpellMaxHealth();
			double remaining = total - completed;
			if (segmentMaximum > 0) {
				remaining = Math.max(0, remaining - segmentMaximum)
						+ Mth.clamp(progress, 0, segmentMaximum);
			}
			progress = (float) remaining;
			maximum = total;

			int duration = spellRuntime.getSpellDurationTicks();
			if (duration > 0) {
				int remainingTicks = Math.max(0, duration - spellRuntime.getSpellElapsedTicks());
				int remainingSeconds = (remainingTicks + 19) / 20;
				name = Component.empty().append(name).append(Component.literal(
						"  " + remainingSeconds + "s").withStyle(ChatFormatting.AQUA));
			}
		}
		bossEvent.setProgress(maximum <= 0 ? 0 : Mth.clamp(progress / maximum, 0, 1));
		bossEvent.setName(name);
		bossEvent.setColor(SpellProgressColor.bossBarColor(this, BossEvent.BossBarColor.RED));
	}

	@Override
	public void beginDanmakuDefeat() {
		super.beginDanmakuDefeat();
		bossEvent.setVisible(false);
	}

	public boolean isChaotic() {
		return getFlag(32) || chaotic || combatProgress != null && getCombatProgress() != combatProgress.progress;
	}

	public void startSeenByPlayer(ServerPlayer pPlayer) {
		super.startSeenByPlayer(pPlayer);
		this.bossEvent.addPlayer(pPlayer);
	}

	public void stopSeenByPlayer(ServerPlayer pPlayer) {
		super.stopSeenByPlayer(pPlayer);
		this.bossEvent.removePlayer(pPlayer);
	}

	@Override
	public boolean canChangeDimensions() {
		return YHModConfig.COMMON.canReimuTeleportToOtherDimension.get();
	}

	// ========== 区块强加载管理 ==========

	/**
	 * 判断是否应该保持区块强加载。
	 * 1. 有存活目标且在合理范围内 → 强加载（防止战斗中区块卸载）
	 * 2. 目标超出范围或无目标 + 宽限期内 → 短暂保持（让脱战回血流程完成）
	 * 宽限期结束后释放，允许 Boss 自然脱战。
	 *
	 * 注意：YoukaiTargetContainer 不检查距离，只要玩家存活 getTarget() 就返回非 null，
	 * 因此不能依赖 noTargetTime，必须用独立的 outOfRangeTicks 计数。
	 */
	private boolean shouldForceLoadChunk() {
		LivingEntity target = getTarget();
		double maxRange = getAttributeValue(Attributes.FOLLOW_RANGE) * 1.5; // 128 * 1.5 = 192
		boolean targetInRange = target != null && target.isAlive()
				&& distanceToSqr(target) <= maxRange * maxRange;

		if (targetInRange) {
			outOfRangeTicks = 0; // 目标在范围内，重置计数器
			return true;
		}

		// 目标不在范围内（超距离或无目标），递增计数器
		outOfRangeTicks++;

		// 已经在强加载中且宽限期未过 → 继续保持
		if (forcedChunkPos != null && outOfRangeTicks < COMBAT_GRACE_TICKS) {
			return true;
		}
		return false;
	}

	/**
	 * 更新区块强加载状态。
	 * - 满足强加载条件时，强制加载当前区块
	 * - 跨区块移动时，更新强加载的区块
	 * - 条件不再满足时，释放强加载
	 */
	private void updateChunkForcing() {
		if (!(level() instanceof ServerLevel serverLevel))
			return;

		if (shouldForceLoadChunk()) {
			ChunkPos currentChunk = new ChunkPos(blockPosition());
			if (forcedChunkPos == null) {
				// 进入战斗，开始强加载
				boolean success = forceChunk(serverLevel, currentChunk, true);
				forcedChunkPos = currentChunk;
				BOSS_LOGGER.info("Boss {} 开始强加载区块 [{}, {}], 成功={}",
						getUUID().toString().substring(0, 8), currentChunk.x, currentChunk.z, success);
			} else if (!forcedChunkPos.equals(currentChunk)) {
				// 跨区块移动，更新强加载
				forceChunk(serverLevel, forcedChunkPos, false);
				boolean success = forceChunk(serverLevel, currentChunk, true);
				BOSS_LOGGER.debug("Boss {} 跨区块: [{}, {}] -> [{}, {}], 成功={}",
						getUUID().toString().substring(0, 8),
						forcedChunkPos.x, forcedChunkPos.z,
						currentChunk.x, currentChunk.z, success);
				forcedChunkPos = currentChunk;
			}
		} else {
			// 条件不再满足，释放强加载
			if (forcedChunkPos != null) {
				BOSS_LOGGER.info("Boss {} 释放区块强加载 [{}, {}], noTargetTime={}",
						getUUID().toString().substring(0, 8),
						forcedChunkPos.x, forcedChunkPos.z, noTargetTime);
			}
			releaseForcing();
		}
	}

	/**
	 * 释放当前强加载的区块
	 */
	private void releaseForcing() {
		if (forcedChunkPos != null && level() instanceof ServerLevel serverLevel) {
			forceChunk(serverLevel, forcedChunkPos, false);
			forcedChunkPos = null;
		}
	}

	/**
	 * 执行区块强加载/取消强加载
	 * 
	 * @param level 服务端世界
	 * @param chunk 目标区块坐标
	 * @param add   true=强加载, false=取消
	 * @return 操作是否成功
	 */
	private boolean forceChunk(ServerLevel level, ChunkPos chunk, boolean add) {
		return ForgeChunkManager.forceChunk(level, YoukaisHomecoming.MODID, this, chunk.x, chunk.z, add, true);
	}

	@Override
	public void checkDespawn() {
		// Boss 永远不应被原版远距离清除机制移除
		this.noActionTime = 0;
	}

	@Override
	public void remove(RemovalReason reason) {
		// 实体被移除时（死亡、discard、维度切换等），确保释放强加载
		if (forcedChunkPos != null) {
			BOSS_LOGGER.info("Boss {} 被移除({}), 释放区块 [{}, {}]",
					getUUID().toString().substring(0, 8), reason,
					forcedChunkPos.x, forcedChunkPos.z);
		}
		releaseForcing();
		super.remove(reason);
	}

}
