package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.fastprojectileapi.spellcircle.EntitySpellCircleManager;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface SpellRuntimeHost extends LivingCardHolder {

	@Nullable
	LivingEntity owner();

	@Nullable
	SpellRuntime getSpellRuntime();

	void setSpellRuntime(@Nullable SpellRuntime runtime);

	void eraseDanmaku(@Nullable Player player);

	void syncSpellState();

	/** Number of live danmaku currently owned by this spell host. */
	default int activeDanmakuCount() {
		return 0;
	}

	/** Completes the normal host defeat path when a timed health segment has no transition. */
	default void settleSpellHealthTimeout() {
	}

	/** Certification trials fail on the first segment timeout, even when a boss timeout target exists. */
	default boolean spellHealthTimeoutEndsFight() {
		return false;
	}

	boolean isBossHost();

	default boolean isPlayerHost() {
		return !isBossHost();
	}

	default boolean isOwnedBy(@Nullable Player player) {
		LivingEntity owner = owner();
		return player != null && owner != null && owner.getUUID().equals(player.getUUID());
	}

	@Nullable
	default ResourceLocation getSpellDefinitionId() {
		SpellRuntime runtime = getSpellRuntime();
		return runtime == null ? null : runtime.getDefinition().id;
	}

	default boolean hasSpell(ResourceLocation spellId) {
		return spellId.equals(getSpellDefinitionId());
	}

	default boolean restrictsManualMovement() {
		SpellRuntime runtime = getSpellRuntime();
		return runtime != null && runtime.getMovementDirective().restrictsManualMovement();
	}

	default LivingEntity spellCircleDisplayEntity() {
		LivingEntity owner = owner();
		return owner == null ? self() : owner;
	}

	default Entity spellCircleSourceEntity() {
		return this instanceof Entity entity ? entity : self();
	}

	default void clearTemporarySpellCircle() {
		EntitySpellCircleManager.clearTemporaryOverrides(spellCircleSourceEntity());
	}

	/** Applies the current action-selected displacement after the spell tick. */
	default void applySpellMovement() {
		SpellRuntime runtime = getSpellRuntime();
		if (runtime == null) return;
		SpellMovementDirective directive = runtime.getMovementDirective();
		LivingEntity caster = movementCaster();
		if (directive.mode() == SpellMovementDirective.Mode.RANDOM) {
			return;
		}
		caster.setDeltaMovement(Vec3.ZERO);
		if (directive.mode() == SpellMovementDirective.Mode.NONE) {
			return;
		}
		Vec3 delta = clampMovement(directive.displacement());
		if (delta.lengthSqr() > 0) {
			caster.move(MoverType.SELF, delta);
		}
	}

	private LivingEntity movementCaster() {
		LivingEntity owner = owner();
		return !isBossHost() && owner != null ? owner : self();
	}

	private static Vec3 clampMovement(Vec3 movement) {
		if (!Double.isFinite(movement.x) || !Double.isFinite(movement.y)
				|| !Double.isFinite(movement.z)) {
			return Vec3.ZERO;
		}
		double max = dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON
				.certificationMaxDisplacementPerTick.get();
		return movement.lengthSqr() > max * max ? movement.normalize().scale(max) : movement;
	}

	default void switchSpellDefinition(SpellDefinition definition, boolean clearScreen) {
		switchSpellRuntime(new SpellRuntime(definition), clearScreen);
	}

	default void switchSpellRuntime(SpellRuntime runtime, boolean clearScreen) {
		if (clearScreen) {
			eraseDanmaku(null);
		}
		clearTemporarySpellCircle();
		setSpellRuntime(runtime);
	}

}
