package dev.xkmc.youkaishomecoming.events;

import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

public class SpellHitEvent extends Event {

	private final SpellRuntime runtime;
	private final LivingEntity caster;
	@Nullable
	private final LivingEntity target;
	private final ResourceLocation spellId;
	private final DamageSource source;
	private final float amount;
	private final int hitCount;

	public SpellHitEvent(SpellRuntime runtime, LivingEntity caster, @Nullable LivingEntity target,
						 ResourceLocation spellId, DamageSource source, float amount, int hitCount) {
		this.runtime = runtime;
		this.caster = caster;
		this.target = target;
		this.spellId = spellId;
		this.source = source;
		this.amount = amount;
		this.hitCount = hitCount;
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

	public DamageSource getSource() {
		return source;
	}

	public float getAmount() {
		return amount;
	}

	public int getHitCount() {
		return hitCount;
	}
}
