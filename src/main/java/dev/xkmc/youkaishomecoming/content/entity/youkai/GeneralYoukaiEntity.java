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

	public void setYsmRenderOverride(String modelId, String textureName, String animationHint, int duration) {
		entityData.set(YSM_MODEL_OVERRIDE, normalizeYsmOverride(modelId));
		entityData.set(YSM_TEXTURE_OVERRIDE, normalizeYsmOverride(textureName));
		entityData.set(YSM_ANIMATION_OVERRIDE, normalizeYsmOverride(animationHint));
		entityData.set(YSM_OVERRIDE_UNTIL, duration > 0 ? tickCount + duration : 0);
	}

	public void clearYsmRenderOverride() {
		entityData.set(YSM_MODEL_OVERRIDE, "");
		entityData.set(YSM_TEXTURE_OVERRIDE, "");
		entityData.set(YSM_ANIMATION_OVERRIDE, "");
		entityData.set(YSM_OVERRIDE_UNTIL, 0);
	}

	public boolean hasYsmRenderOverride() {
		if (isYsmRenderOverrideExpired()) {
			return false;
		}
		return !entityData.get(YSM_MODEL_OVERRIDE).isBlank() ||
				!entityData.get(YSM_TEXTURE_OVERRIDE).isBlank() ||
				!entityData.get(YSM_ANIMATION_OVERRIDE).isBlank();
	}

	public String getYsmModelOverride() {
		return hasYsmRenderOverride() ? entityData.get(YSM_MODEL_OVERRIDE) : "";
	}

	public String getYsmTextureOverride() {
		return hasYsmRenderOverride() ? entityData.get(YSM_TEXTURE_OVERRIDE) : "";
	}

	public String getYsmAnimationOverride() {
		return hasYsmRenderOverride() ? entityData.get(YSM_ANIMATION_OVERRIDE) : "";
	}

	public int getYsmOverrideTicksRemaining() {
		int until = entityData.get(YSM_OVERRIDE_UNTIL);
		return until <= 0 || isYsmRenderOverrideExpired() ? 0 : until - tickCount;
	}

	public String describeYsmRenderOverride() {
		if (!hasYsmRenderOverride()) {
			return "none";
		}
		int remaining = getYsmOverrideTicksRemaining();
		return "model=" + displayYsmOverride(entityData.get(YSM_MODEL_OVERRIDE)) +
				", texture=" + displayYsmOverride(entityData.get(YSM_TEXTURE_OVERRIDE)) +
				", animation=" + displayYsmOverride(entityData.get(YSM_ANIMATION_OVERRIDE)) +
				", duration=" + (remaining > 0 ? remaining + "t" : "until clear");
	}

	private boolean isYsmRenderOverrideExpired() {
		int until = entityData.get(YSM_OVERRIDE_UNTIL);
		return until > 0 && tickCount >= until;
	}

	private static String normalizeYsmOverride(String value) {
		return value == null ? "" : value.trim();
	}

	private static String displayYsmOverride(String value) {
		return value.isBlank() ? "(keep)" : value;
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
		if (!level().isClientSide() && isYsmRenderOverrideExpired()) {
			clearYsmRenderOverride();
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
