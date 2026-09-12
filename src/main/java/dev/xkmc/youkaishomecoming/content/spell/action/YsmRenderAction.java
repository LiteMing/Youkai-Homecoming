package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.compat.ysm.YsmRenderConfig;
import dev.xkmc.youkaishomecoming.compat.ysm.YsmSpellHints;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

/**
 * Lightweight spell-side YSM context hint.
 *
 * <p>Model selection, presets, exact clips and parameters belong to the
 * shared /yhysm editor. This node remains only as an escape hatch for a spell
 * author who needs to feed the legacy YSM animation-hint context for a
 * bounded spell interval. Shooter uses the same {@link YsmRenderConfig}
 * override route.
 */
public record YsmRenderAction(String hint, int duration) implements SpellAction {

	public static final Codec<YsmRenderAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("hint", "").forGetter(YsmRenderAction::hint),
			Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("duration", 0).forGetter(YsmRenderAction::duration)
	).apply(i, YsmRenderAction::new));

	public static YsmRenderAction empty() {
		return new YsmRenderAction("", 0);
	}

	public YsmRenderAction {
		hint = hint == null ? "" : hint.trim();
		duration = Math.max(0, duration);
	}

	@Override
	public void execute(SpellContext ctx) {
		if (duration > dev.xkmc.youkaishomecoming.init.data.YHModConfig.COMMON.modelPresentationMaxTicks.get()) {
			throw new IllegalArgumentException("YSM hint duration exceeds configured maximum");
		}
		YsmSpellHints.apply(ctx, hint, duration);
	}

	public YsmRenderAction withHint(String value) { return new YsmRenderAction(value, duration); }
	public YsmRenderAction withDuration(int value) { return new YsmRenderAction(hint, value); }
}
