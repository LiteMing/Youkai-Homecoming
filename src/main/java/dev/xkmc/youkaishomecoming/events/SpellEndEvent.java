package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

public class SpellEndEvent extends Event {

	public enum Reason {
		NATURAL,
		REPLACED,
		TIMED_OUT,
		OWNER_LOST
	}

	private final SpellRuntime runtime;
	private final LivingEntity caster;
	@Nullable
	private final LivingEntity target;
	private final ResourceLocation spellId;
	@Nullable
	private final ResourceLocation phaseId;
	private final Reason reason;

	public SpellEndEvent(SpellRuntime runtime, LivingEntity caster, @Nullable LivingEntity target,
						 ResourceLocation spellId, @Nullable ResourceLocation phaseId, Reason reason) {
		this.runtime = runtime;
		this.caster = caster;
		this.target = target;
		this.spellId = spellId;
		this.phaseId = phaseId;
		this.reason = reason;
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
	public ResourceLocation getPhaseId() {
		return phaseId;
	}

	public Reason getReason() {
		return reason;
	}
}
