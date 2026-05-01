package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

public class SpellPhaseChangeEvent extends Event {

	private final SpellRuntime runtime;
	private final LivingEntity caster;
	@Nullable
	private final LivingEntity target;
	private final ResourceLocation spellId;
	@Nullable
	private final ResourceLocation oldPhaseId;
	@Nullable
	private final ResourceLocation newPhaseId;

	public SpellPhaseChangeEvent(SpellRuntime runtime, LivingEntity caster, @Nullable LivingEntity target,
								 ResourceLocation spellId, @Nullable ResourceLocation oldPhaseId,
								 @Nullable ResourceLocation newPhaseId) {
		this.runtime = runtime;
		this.caster = caster;
		this.target = target;
		this.spellId = spellId;
		this.oldPhaseId = oldPhaseId;
		this.newPhaseId = newPhaseId;
	}

	public SpellRuntime getRuntime() {
		return runtime;
	}

	public LivingEntity getCaster() {
		return caster;
	}

	@Nullable
	public LivingEntity getTarget() {
		return target;
	}

	public ResourceLocation getSpellId() {
		return spellId;
	}

	@Nullable
	public ResourceLocation getOldPhaseId() {
		return oldPhaseId;
	}

	@Nullable
	public ResourceLocation getNewPhaseId() {
		return newPhaseId;
	}
}
