package dev.xkmc.youkaishomecoming.compat.stg;

import java.util.List;
import java.util.Locale;

public enum StgCombatMode {

	NOVICE_AUTO_BOMB("novice", true),
	CLASSIC_MANUAL_BOMB("classic", false);

	private static final List<String> COMMAND_NAMES = List.of("novice", "classic");

	private final String commandName;
	private final boolean autoBombOnHit;

	StgCombatMode(String commandName, boolean autoBombOnHit) {
		this.commandName = commandName;
		this.autoBombOnHit = autoBombOnHit;
	}

	public String commandName() {
		return commandName;
	}

	public boolean autoBombOnHit() {
		return autoBombOnHit;
	}

	public static List<String> commandNames() {
		return COMMAND_NAMES;
	}

	public static StgCombatMode fromName(String name) {
		if (name == null) {
			throw new IllegalArgumentException("STG mode cannot be null");
		}
		return switch (name.toLowerCase(Locale.ROOT).replace('-', '_')) {
			case "novice", "auto", "auto_bomb", "novice_auto_bomb" -> NOVICE_AUTO_BOMB;
			case "classic", "manual", "manual_bomb", "classic_manual_bomb" -> CLASSIC_MANUAL_BOMB;
			default -> throw new IllegalArgumentException("Unknown STG mode: " + name);
		};
	}

	public static StgCombatMode fromSerialized(String name) {
		try {
			return fromName(name);
		} catch (IllegalArgumentException ignored) {
			return NOVICE_AUTO_BOMB;
		}
	}

}
