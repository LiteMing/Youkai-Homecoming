package dev.xkmc.youkaishomecoming.compat.ysm;

import java.util.Objects;
import java.util.List;

/** Main-entry alias compatibility for profiles and owner-scoped presentation requests. No OYSM dependency. */
public final class YsmModelPaths {
	private YsmModelPaths() { }

	public static String mainAlias(String model) {
		return model.endsWith("/main") ? model.substring(0, model.length() - 5) : model + "/main";
	}

	/** Both forms are needed when a legacy pack ID itself ends in /main. */
	public static List<String> mainAliases(String model) {
		return model.endsWith("/main") ? List.of(model.substring(0, model.length() - 5), model + "/main")
				: List.of(model + "/main");
	}

	public static boolean same(String first, String second) {
		return Objects.equals(first, second) || first != null && second != null
				&& (first.equals(second + "/main") || second.equals(first + "/main"));
	}
}
