package dev.xkmc.youkaishomecoming.compat.ysm;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Spell hint grammar shared by external boss rendering and native player animation. */
public final class YsmAnimationHints {

	private YsmAnimationHints() { }

	public static String normalize(String animation, Function<String, String> semanticToken) {
		List<String> normalized = new ArrayList<>();
		for (String token : tokens(animation)) {
			String key = key(token);
			if (key.isBlank() || token.contains("=") || isMovement(key)) {
				normalized.add(token);
				continue;
			}
			String semantic = semanticToken.apply(key);
			if (List.of("angry", "cast", "charge", "special").contains(key)) {
				normalized.add(semantic);
			} else {
				int equals = semantic.indexOf('=');
				normalized.add("special=" + (equals >= 0 ? semantic.substring(equals + 1) : key));
			}
		}
		return String.join(" ", normalized);
	}

	public static boolean overridesPassiveExpression(String animation) {
		return tokens(animation).stream().map(YsmAnimationHints::key)
				.anyMatch(key -> !key.isBlank() && !List.of("fly", "walk", "calm").contains(key));
	}

	/** Matches ExternalLivingEntity's cap group priority and first-existing '+' fallback. */
	@Nullable
	public static String resolve(String animation, Predicate<String> exists) {
		List<String> tokens = tokens(animation);
		for (String group : List.of("angry", "cast", "charge", "special")) {
			if (tokens.stream().noneMatch(token -> key(token).equals(group))) continue;
			String candidates = tokens.stream().filter(token -> token.startsWith(group + "="))
					.map(token -> token.substring(group.length() + 1)).filter(value -> !value.isBlank())
					.findFirst().orElse(switch (group) {
						case "angry" -> "angry+combat+extra10+attack+attacked+idle";
						case "cast" -> "cast+swing_hand+extra10";
						case "charge" -> "charge+extra10+extra11";
						default -> "special+extra11+extra12+extra13";
					});
			return firstExisting(candidates, exists);
		}
		// Movement-only hints must also work on the native player cap controller.
		for (String token : tokens) {
			String candidates = switch (key(token)) {
				case "fly" -> "fly+elytra_fly+jump+swim+idle";
				case "walk" -> "walk+run+idle";
				case "calm" -> "idle+idle_longtime+calm+gui";
				case "climbing" -> "climbing+climb+sleep+idle";
				case "climb" -> "climb+climbing+idle";
				default -> "";
			};
			String resolved = firstExisting(candidates, exists);
			if (resolved != null) return resolved;
		}
		return null;
	}

	@Nullable
	private static String firstExisting(String candidates, Predicate<String> exists) {
		for (String candidate : candidates.split("\\+")) {
			if (!candidate.isBlank() && exists.test(candidate)) return candidate;
		}
		return null;
	}

	private static boolean isMovement(String key) {
		return List.of("fly", "walk", "calm", "climb", "climbing").contains(key);
	}

	private static String key(String token) {
		int equals = token.indexOf('=');
		return (equals >= 0 ? token.substring(0, equals) : token).trim();
	}

	private static List<String> tokens(String animation) {
		if (animation == null || animation.isBlank()) return List.of();
		return java.util.Arrays.stream(animation.split("[,;|\\s]+"))
				.filter(token -> !token.isBlank()).toList();
	}
}
