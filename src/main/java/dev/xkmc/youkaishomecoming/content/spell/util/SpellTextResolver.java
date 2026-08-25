package dev.xkmc.youkaishomecoming.content.spell.util;

import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.Map;

/**
 * Resolves placeholders in data-driven spell text strings against the current {@link SpellContext}.
 * <p>
 * Supported placeholders:
 * <ul>
 *   <li>{@code {spell_id}} — the spell card definition's resource location id.</li>
 *   <li>{@code {caster_name}} — the caster entity's display name.</li>
 *   <li>{@code {<variable>}} — any spell runtime variable (read via {@link SpellContext#getVariable}).
 *       Zero if the variable is unset; the placeholder is left untouched shape-wise but renders as the number.</li>
 * </ul>
 * <p>
 * This is the single shared mechanism so {@code run_command}, {@code show_spell_title} and
 * {@code fire_text_danmaku} all interpolate runtime values consistently (e.g. a live score).
 */
public final class SpellTextResolver {

	private SpellTextResolver() {
	}

	public static String resolve(String text, SpellContext ctx) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		String result = text;
		if (ctx.definition() != null) {
			result = result.replace("{spell_id}", ctx.definition().id.toString());
		}
		result = result.replace("{caster_name}", ctx.holder().self().getName().getString());
		for (Map.Entry<String, Double> e : ctx.variables().entrySet()) {
			result = result.replace("{" + e.getKey() + "}", format(e.getValue()));
		}
		return result;
	}

	private static String format(double value) {
		if (value == Math.rint(value) && !Double.isInfinite(value)) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}
}