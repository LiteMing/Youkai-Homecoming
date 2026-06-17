package dev.xkmc.youkaishomecoming.content.entity.youkai;

import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.game.TouhouSpellCards;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

@SerialClass
public class GeneralYoukaiEntity extends YoukaiEntity {

	private static final ResourceLocation SPELL = YoukaisHomecoming.loc("ex_rumia");

	private static <T> EntityDataAccessor<T> defineId(EntityDataSerializer<T> ser) {
		return SynchedEntityData.defineId(GeneralYoukaiEntity.class, ser);
	}

	protected static final SyncedData SPELL_DATA = new SyncedData(GeneralYoukaiEntity::defineId, YOUKAI_DATA);

	private static final EntityDataAccessor<String> SPELL_MODEL = SPELL_DATA.define(SyncedData.STRING, "", "modelId");
	private static final EntityDataAccessor<String> YSM_MODEL_OVERRIDE = SPELL_DATA.define(SyncedData.STRING, "", "ysmModelOverride");
	private static final EntityDataAccessor<String> YSM_TEXTURE_OVERRIDE = SPELL_DATA.define(SyncedData.STRING, "", "ysmTextureOverride");
	private static final EntityDataAccessor<String> YSM_ANIMATION_OVERRIDE = SPELL_DATA.define(SyncedData.STRING, "", "ysmAnimationOverride");
	private static final EntityDataAccessor<Integer> YSM_OVERRIDE_UNTIL = SPELL_DATA.define(SyncedData.INT, 0, "ysmOverrideUntil");
	private static final EntityDataAccessor<Integer> YSM_MODEL_OVERRIDE_UNTIL = SPELL_DATA.define(SyncedData.INT, 0, "ysmModelOverrideUntil");
	private static final EntityDataAccessor<Integer> YSM_TEXTURE_OVERRIDE_UNTIL = SPELL_DATA.define(SyncedData.INT, 0, "ysmTextureOverrideUntil");
	private static final EntityDataAccessor<Integer> YSM_ANIMATION_OVERRIDE_UNTIL = SPELL_DATA.define(SyncedData.INT, 0, "ysmAnimationOverrideUntil");
	private static final int YSM_CLEAR_MODEL = 1;
	private static final int YSM_CLEAR_TEXTURE = 2;
	private static final int YSM_CLEAR_ANIMATION = 4;
	private static final int YSM_CLEAR_ALL = YSM_CLEAR_MODEL | YSM_CLEAR_TEXTURE | YSM_CLEAR_ANIMATION;

	private int tickAggressive;

	public GeneralYoukaiEntity(EntityType<? extends GeneralYoukaiEntity> pEntityType, Level pLevel) {
		super(pEntityType, pLevel);
	}

	@Override
	protected SyncedData data() {
		return SPELL_DATA;
	}

	public String getModelId() {
		String ans = entityData.get(SPELL_MODEL);
		if (ans.isEmpty()) return "";
		return ans;
	}

	public void syncModel() {
		String model = null;
		if (spellCard != null) model = spellCard.getModelId();
		if (model == null) model = "";
		entityData.set(SPELL_MODEL, model);
	}

	public void setYsmRenderOverride(String modelId, String textureName, String animationHint, int duration, String clearTarget) {
		String model = normalizeYsmOverride(modelId);
		String texture = normalizeYsmOverride(textureName);
		String animation = normalizeYsmOverride(animationHint);
		if (!model.isBlank()) {
			entityData.set(YSM_MODEL_OVERRIDE, model);
		}
		if (!texture.isBlank()) {
			entityData.set(YSM_TEXTURE_OVERRIDE, texture);
		}
		if (!animation.isBlank()) {
			entityData.set(YSM_ANIMATION_OVERRIDE, animation);
		}
		updateYsmFieldExpirations(changedMask(model, texture, animation), clearMask(clearTarget, changedMask(model, texture, animation)), duration);
	}

	public void clearYsmRenderOverride() {
		clearYsmRenderOverride("all");
	}

	public void clearYsmRenderOverride(String target) {
		clearYsmRenderOverride(clearMask(target, YSM_CLEAR_ALL));
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
		if (!hasYsmRenderOverride()) {
			entityData.set(YSM_OVERRIDE_UNTIL, 0);
		} else {
			entityData.set(YSM_OVERRIDE_UNTIL, getMaxYsmOverrideUntil());
		}
	}

	public boolean hasYsmRenderOverride() {
		return hasActiveYsmField(YSM_MODEL_OVERRIDE, YSM_MODEL_OVERRIDE_UNTIL) ||
				hasActiveYsmField(YSM_TEXTURE_OVERRIDE, YSM_TEXTURE_OVERRIDE_UNTIL) ||
				hasActiveYsmField(YSM_ANIMATION_OVERRIDE, YSM_ANIMATION_OVERRIDE_UNTIL);
	}

	public String getYsmModelOverride() {
		return hasActiveYsmField(YSM_MODEL_OVERRIDE, YSM_MODEL_OVERRIDE_UNTIL) ? entityData.get(YSM_MODEL_OVERRIDE) : "";
	}

	public String getYsmTextureOverride() {
		return hasActiveYsmField(YSM_TEXTURE_OVERRIDE, YSM_TEXTURE_OVERRIDE_UNTIL) ? entityData.get(YSM_TEXTURE_OVERRIDE) : "";
	}

	public String getYsmAnimationOverride() {
		return hasActiveYsmField(YSM_ANIMATION_OVERRIDE, YSM_ANIMATION_OVERRIDE_UNTIL) ? entityData.get(YSM_ANIMATION_OVERRIDE) : "";
	}

	public int getYsmOverrideTicksRemaining() {
		int remaining = 0;
		remaining = mergeYsmRemaining(remaining, entityData.get(YSM_MODEL_OVERRIDE_UNTIL));
		remaining = mergeYsmRemaining(remaining, entityData.get(YSM_TEXTURE_OVERRIDE_UNTIL));
		remaining = mergeYsmRemaining(remaining, entityData.get(YSM_ANIMATION_OVERRIDE_UNTIL));
		return remaining;
	}

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
		int until = entityData.get(untilField);
		return until > 0 && tickCount >= until;
	}

	private static String normalizeYsmOverride(String value) {
		return value == null ? "" : value.trim();
	}

	private static String displayYsmOverride(String value) {
		return value.isBlank() ? "(keep)" : value;
	}

	private String displayYsmOverride(EntityDataAccessor<String> field, EntityDataAccessor<Integer> untilField) {
		String value = entityData.get(field);
		if (value.isBlank()) {
			return "(keep)";
		}
		int until = entityData.get(untilField);
		return until > 0 && tickCount < until ? value + " (" + (until - tickCount) + "t)" : value;
	}

	private int mergeYsmRemaining(int current, int until) {
		if (until <= tickCount) {
			return current;
		}
		int remaining = until - tickCount;
		return current <= 0 ? remaining : Math.min(current, remaining);
	}

	private static int changedMask(String model, String texture, String animation) {
		int mask = 0;
		if (!model.isBlank()) {
			mask |= YSM_CLEAR_MODEL;
		}
		if (!texture.isBlank()) {
			mask |= YSM_CLEAR_TEXTURE;
		}
		if (!animation.isBlank()) {
			mask |= YSM_CLEAR_ANIMATION;
		}
		return mask;
	}

	private static int clearMask(String target, int changedMask) {
		return switch (normalizeYsmOverride(target).toLowerCase(java.util.Locale.ROOT)) {
			case "", "changed" -> changedMask;
			case "model" -> YSM_CLEAR_MODEL;
			case "texture" -> YSM_CLEAR_TEXTURE;
			case "animation", "anim" -> YSM_CLEAR_ANIMATION;
			case "model_texture", "model+texture", "render" -> YSM_CLEAR_MODEL | YSM_CLEAR_TEXTURE;
			case "all" -> YSM_CLEAR_ALL;
			default -> changedMask;
		};
	}

	private void updateYsmFieldExpirations(int changedMask, int expireMask, int duration) {
		int until = duration > 0 ? tickCount + duration : 0;
		updateYsmFieldExpiration(YSM_CLEAR_MODEL, changedMask, expireMask, until, YSM_MODEL_OVERRIDE_UNTIL);
		updateYsmFieldExpiration(YSM_CLEAR_TEXTURE, changedMask, expireMask, until, YSM_TEXTURE_OVERRIDE_UNTIL);
		updateYsmFieldExpiration(YSM_CLEAR_ANIMATION, changedMask, expireMask, until, YSM_ANIMATION_OVERRIDE_UNTIL);
		entityData.set(YSM_OVERRIDE_UNTIL, getMaxYsmOverrideUntil());
	}

	private void updateYsmFieldExpiration(int bit, int changedMask, int expireMask, int until, EntityDataAccessor<Integer> untilField) {
		if ((changedMask & bit) != 0 || (expireMask & bit) != 0) {
			entityData.set(untilField, (expireMask & bit) != 0 ? until : 0);
		}
	}

	private int getMaxYsmOverrideUntil() {
		return Math.max(entityData.get(YSM_MODEL_OVERRIDE_UNTIL),
				Math.max(entityData.get(YSM_TEXTURE_OVERRIDE_UNTIL), entityData.get(YSM_ANIMATION_OVERRIDE_UNTIL)));
	}

	protected void registerGoals() {
		goalSelector.addGoal(4, new YoukaiAttackGoal<>(this));
		goalSelector.addGoal(6, new FloatGoal(this));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new MultiHurtByTargetGoal(this, GeneralYoukaiEntity.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, this::wouldAttack));
	}

	protected boolean wouldAttack(LivingEntity entity) {
		if (shouldIgnore(entity)) return false;
		return entity.hasEffect(YHEffects.YOUKAIFYING.get());
	}

	@Override
	public boolean shouldHurt(LivingEntity le) {
		if (shouldIgnore(le)) return false;
		return super.shouldHurt(le) || wouldAttack(le);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			int expiredMask = 0;
			if (isYsmFieldExpired(YSM_MODEL_OVERRIDE_UNTIL)) {
				expiredMask |= YSM_CLEAR_MODEL;
			}
			if (isYsmFieldExpired(YSM_TEXTURE_OVERRIDE_UNTIL)) {
				expiredMask |= YSM_CLEAR_TEXTURE;
			}
			if (isYsmFieldExpired(YSM_ANIMATION_OVERRIDE_UNTIL)) {
				expiredMask |= YSM_CLEAR_ANIMATION;
			}
			if (expiredMask != 0) {
				clearYsmRenderOverride(expiredMask);
			}
		}
		if (level().isClientSide()) {
			if (isAggressive()) {
				if (tickAggressive < 20)
					tickAggressive++;
			} else if (tickAggressive > 0) {
				tickAggressive--;
			}
		}
	}

	@Override
	public boolean shouldShowSpellCircle() {
		return level().isClientSide() ? isAggressive() : getTarget() != null;
	}

	@Override
	public @Nullable ResourceLocation getSpellCircle() {
		if (!shouldShowSpellCircle()) {
			return null;
		}
		return SPELL;
	}

	@Override
	public float getCircleSize(float pTick) {
		return tickAggressive == 0 ? 0 : Math.min(1, (tickAggressive + pTick) / 20f);
	}

	@Nullable
	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
		initSpellCard();
		return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData, pDataTag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("SpellRuntime") && spellRuntime == null) {
			var runtimeTag = tag.getCompound("SpellRuntime");
			var spellId = ResourceLocation.tryParse(runtimeTag.getString("DefinitionId"));
			if (spellId != null) {
				var def = SpellRegistry.get(spellId);
				if (def != null) {
					setSpellRuntime(new SpellRuntime(def));
				}
			}
		}
		String id = getModelId();
		// Reconstruct spell if:
		// 1. spellCard is completely null (legacy fallback), OR
		// 2. spellCard exists but card is null AND no spellRuntime (migrated data-driven spell lost on save/load)
		if (!id.isEmpty() && (spellCard == null || (spellCard.card == null && spellRuntime == null))) {
			TouhouSpellCards.setSpell(this, id);
		}
		// Restore SpellRuntime state AFTER reconstruction
		if (tag.contains("SpellRuntime") && spellRuntime != null) {
			spellRuntime.loadFromTag(tag.getCompound("SpellRuntime"));
		}
	}

	public void initSpellCard() {
	}

}
