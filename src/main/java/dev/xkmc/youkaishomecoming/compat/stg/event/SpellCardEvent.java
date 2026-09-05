package dev.xkmc.youkaishomecoming.compat.stg.event;

import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntime;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRuntimeHost;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Public lifecycle notification for an operator spell-health segment.
 *
 * <p>The event is posted before an embedded force-phase/force-spell action is
 * executed, so listeners can observe the old phase and the final danmaku count
 * for that segment. It is informational and cannot cancel the transition.</p>
 */
public final class SpellCardEvent extends LivingEvent {

	public enum Outcome {
		BROKEN,
		TIMEOUT
	}

	private final LivingEntity caster;
	@Nullable
	private final LivingEntity opponent;
	private final ResourceLocation spellId;
	private final ResourceLocation phaseId;
	private final Outcome outcome;
	private final int battleDurationTicks;
	private final int activeDanmakuCount;

	private SpellCardEvent(LivingEntity caster, @Nullable LivingEntity opponent,
			ResourceLocation spellId, ResourceLocation phaseId, Outcome outcome,
			int battleDurationTicks, int activeDanmakuCount) {
		super(caster);
		this.caster = caster;
		this.opponent = opponent;
		this.spellId = spellId;
		this.phaseId = phaseId;
		this.outcome = outcome;
		this.battleDurationTicks = Math.max(0, battleDurationTicks);
		this.activeDanmakuCount = Math.max(0, activeDanmakuCount);
	}

	public LivingEntity getCaster() {
		return caster;
	}

	@Nullable
	public LivingEntity getOpponent() {
		return opponent;
	}

	public ResourceLocation getSpellId() {
		return spellId;
	}

	public ResourceLocation getPhaseId() {
		return phaseId;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public int getBattleDurationTicks() {
		return battleDurationTicks;
	}

	public int getActiveDanmakuCount() {
		return activeDanmakuCount;
	}

	public boolean isBroken() {
		return outcome == Outcome.BROKEN;
	}

	public boolean isTimeout() {
		return outcome == Outcome.TIMEOUT;
	}

	/** Posts a server-side event for a runtime health segment. */
	public static void post(CardHolder holder, SpellRuntime runtime, Outcome outcome,
			@Nullable DamageSource source) {
		LivingEntity caster = holder instanceof SpellRuntimeHost host && host.owner() != null
				? host.owner() : holder.self();
		if (caster.level().isClientSide()) return;
		LivingEntity opponent = null;
		if (source != null && source.getEntity() instanceof LivingEntity living) {
			opponent = living;
		} else {
			opponent = holder.targetEntity();
		}
		int active = holder instanceof SpellRuntimeHost host ? host.activeDanmakuCount() : 0;
		MinecraftForge.EVENT_BUS.post(new SpellCardEvent(caster, opponent,
				runtime.getDefinition().id, runtime.getCurrentPhaseId(), outcome,
				runtime.getBattleElapsedTicks(), active));
	}
}
