package dev.xkmc.youkaishomecoming.compat.ysm;

import java.util.Objects;
import java.util.List;

/** Validation for complete OYSM model-entry paths. */
public final class YsmModelPaths {
	private YsmModelPaths() { }

	public static String modelId(String value) {
		String id = value == null ? "" : value.trim();
		if (id.length() > 1 && id.startsWith("\"") && id.endsWith("\"")) id = id.substring(1, id.length() - 1).replace("\\\"", "\"");
		if (id.isEmpty() || id.length() > 256 || id.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid model ID");
		return id;
	}

	public static boolean same(String first, String second) {
		return Objects.equals(first, second);
	}
}
