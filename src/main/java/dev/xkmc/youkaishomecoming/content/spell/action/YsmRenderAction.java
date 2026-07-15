package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.compat.ysm.YsmRenderOverrideTarget;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

/**
 * Data-driven YSM render override for spell phases.
 * <p>
 * Blank model/texture keeps the current YH/YSM binding. Blank animation only switches
 * model/texture. Positive duration expires automatically; zero or negative duration
 * stays active until a clear action runs.
 * <p>
 * JSON: {@code {"type":"ysm_render","animation":"special","duration":40}}
 */
public record YsmRenderAction(String model, String texture, String animation, int duration,
							  boolean clear, String clearTarget) implements SpellAction {

	public static final Codec<YsmRenderAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("model", "").forGetter(YsmRenderAction::model),
			Codec.STRING.optionalFieldOf("texture", "").forGetter(YsmRenderAction::texture),
			Codec.STRING.optionalFieldOf("animation", "").forGetter(YsmRenderAction::animation),
			Codec.INT.optionalFieldOf("duration", 0).forGetter(YsmRenderAction::duration),
			Codec.BOOL.optionalFieldOf("clear", false).forGetter(YsmRenderAction::clear),
			Codec.STRING.optionalFieldOf("clear_target", "changed").forGetter(YsmRenderAction::clearTarget)
	).apply(i, YsmRenderAction::new));

	public YsmRenderAction(String model, String texture, String animation, int duration, boolean clear) {
		this(model, texture, animation, duration, clear, clear ? "all" : "changed");
	}

	public YsmRenderAction {
		model = normalize(model);
		texture = normalize(texture);
		animation = normalize(animation);
		clearTarget = normalize(clearTarget);
	}

	@Override
	public void execute(SpellContext ctx) {
		YsmRenderOverrideTarget target = null;
		if (ctx.self() instanceof YsmRenderOverrideTarget selfTarget) {
			target = selfTarget;
		} else if (ctx.holder() instanceof YsmRenderOverrideTarget holderTarget) {
			target = holderTarget;
		}
		if (target != null) {
			if (clear) {
				target.clearYsmRenderOverride(clearTarget.isBlank() || "changed".equals(clearTarget) ? "all" : clearTarget);
			} else {
				target.setYsmRenderOverride(model, texture, animation, duration, clearTarget);
			}
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
