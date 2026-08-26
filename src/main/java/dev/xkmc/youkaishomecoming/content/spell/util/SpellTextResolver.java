package dev.xkmc.youkaishomecoming.content.spell.util;

import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves placeholders in data-driven spell text strings against the current {@link SpellContext}.
 * <p>
 * Supported placeholders:
 * <ul>
 *   <li>{@code {spell_id}} — the spell card definition's resource location id.</li>
 *   <li>{@code {caster_name}} — the caster entity's display name.</li>
 *   <li>{@code {<variable>}} — any spell runtime variable (read via {@link SpellContext#getVariable}).
 *       Zero if the variable is unset.</li>
 * </ul>
 * <p>
 * This is the single shared mechanism so {@code run_command}, {@code show_spell_title} and
 * {@code fire_text_danmaku} all interpolate runtime values consistently (e.g. a live score).
 */
public final class SpellTextResolver {

	private SpellTextResolver() {
	}

	/** Matches a placeholder like {spell_id}, {caster_name} or a runtime variable key. */
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_.:-]*)}");

	public static String resolve(String text, SpellContext ctx) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		Matcher m = PLACEHOLDER.matcher(text);
		StringBuilder sb = new StringBuilder(text.length());
		while (m.find()) {
			m.appendReplacement(sb, Matcher.quoteReplacement(resolveKey(m.group(1), ctx)));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static String resolveKey(String key, SpellContext ctx) {
		if (ctx.definition() != null && key.equals("spell_id")) {
			return ctx.definition().id.toString();
		}
		if (key.equals("caster_name")) {
			return ctx.holder().self().getName().getString();
		}
		// Any other key is a runtime variable; getVariable returns 0 when unset.
		return format(ctx.getVariable(key));
	}

	private static String format(double value) {
		if (value == Math.rint(value) && !Double.isInfinite(value)) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}
}