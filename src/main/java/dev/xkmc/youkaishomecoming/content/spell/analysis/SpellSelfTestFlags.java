package dev.xkmc.youkaishomecoming.content.spell.analysis;

/**
 * Strict boolean parsing for the headless self-test switches: a flag is enabled
 * only when the value is exactly "1" or Boolean.parseBoolean ("true",
 * case-insensitive). Values like "false"/"0"/"yes"/empty never enable it
 * (acceptance review B).
 */
public final class SpellSelfTestFlags {

	private SpellSelfTestFlags() {
	}

	public static boolean enabled(String property, String env) {
		String value = System.getProperty(property);
		if (value == null) value = System.getenv(env);
		return "1".equals(value) || Boolean.parseBoolean(value);
	}
}
