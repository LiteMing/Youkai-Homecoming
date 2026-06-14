package dev.xkmc.youkaishomecoming.content.spell.runtime;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.LivingCardHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public interface SpellRuntimeHost extends LivingCardHolder {

	@Nullable
	LivingEntity owner();

	@Nullable
	SpellRuntime getSpellRuntime();

	void setSpellRuntime(@Nullable SpellRuntime runtime);

	void eraseDanmaku(@Nullable Player player);

	void syncSpellState();

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

	default void switchSpellDefinition(SpellDefinition definition, boolean clearScreen) {
		if (clearScreen) {
			eraseDanmaku(null);
		}
		setSpellRuntime(new SpellRuntime(definition));
	}

}
