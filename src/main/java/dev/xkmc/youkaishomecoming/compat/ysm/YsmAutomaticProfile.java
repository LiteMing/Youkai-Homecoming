package dev.xkmc.youkaishomecoming.compat.ysm;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.xkmc.youkaishomecoming.compat.ysm.YsmModelProfile.Trigger;

/**
 * Client-only, read-only defaults for standard YSM clips.
 *
 * The server cannot inspect model assets, so these routes are deliberately
 * derived from the observer's loaded catalog and never persisted or synced.
 */
final class YsmAutomaticProfile {
	private static final String PREFIX = "__yh_auto_";
	private static final Map<String, Cached> CACHE = new LinkedHashMap<>();
	private static final List<Rule> RULES = List.of(
			new Rule(Trigger.IDLE, List.of("idle"), 0),
			new Rule(Trigger.WALK, List.of("walk", "run"), 0),
			new Rule(Trigger.FLY, List.of("fly", "elytra_fly"), 0),
			new Rule(Trigger.SIT, List.of("sit", "ride", "riding"), 0),
			new Rule(Trigger.SWIM, List.of("swim", "swimming", "swimming_up"), 0),
			new Rule(Trigger.HURT, List.of("attacked", "hurt", "damage"), 10)
	);

	private YsmAutomaticProfile() { }

	/**
	 * Merge only routes absent from the explicit profile. A blank explicit clip
	 * is still authoritative because it may intentionally preserve native YSM
	 * behavior while changing parameters.
	 */
	@Nullable
	static YsmModelProfile merge(String model, @Nullable YsmModelProfile explicit, YsmModelCatalog catalog) {
		Cached cached = CACHE.get(model);
		if (cached != null && Objects.equals(cached.explicit(), explicit) && cached.catalog().equals(catalog)) return cached.profile();
		if (catalog.status() != YsmModelCatalog.Status.READY) return explicit;
		if (explicit != null && !explicit.model().equals(model)) explicit = null;
		Map<String, YsmModelProfile.Preset> presets = new LinkedHashMap<>();
		Map<Trigger, String> routes = new LinkedHashMap<>();
		if (explicit != null) {
			presets.putAll(explicit.presets());
			routes.putAll(explicit.triggers());
		}
		for (Rule rule : RULES) {
			if (routes.containsKey(rule.trigger())) continue;
			String clip = firstPresent(catalog.animations(), rule.candidates());
			if (clip == null) continue;
			if (presets.size() >= YsmModelProfile.MAX_PRESETS) break;
			String id = uniqueId(PREFIX + rule.trigger().id(), presets);
			presets.put(id, new YsmModelProfile.Preset("YH automatic " + rule.trigger().id(), clip, rule.ticks(), Map.of()));
			routes.put(rule.trigger(), id);
		}
		YsmModelProfile result;
		if (explicit != null && presets.equals(explicit.presets()) && routes.equals(explicit.triggers())) result = explicit;
		else if (presets.isEmpty()) result = explicit;
		else result = new YsmModelProfile(model, presets, routes);
		CACHE.put(model, new Cached(explicit, catalog, result));
		return result;
	}

	static void clear() { CACHE.clear(); }

	@Nullable
	static String firstPresent(List<String> animations, List<String> candidates) {
		for (String candidate : candidates) if (animations.contains(candidate)) return candidate;
		return null;
	}

	private static String uniqueId(String base, Map<String, YsmModelProfile.Preset> presets) {
		if (!presets.containsKey(base)) return base;
		for (int i = 1; ; i++) {
			String candidate = base + "_" + i;
			if (!presets.containsKey(candidate)) return candidate;
		}
	}

	private record Rule(Trigger trigger, List<String> candidates, int ticks) { }
	private record Cached(@Nullable YsmModelProfile explicit, YsmModelCatalog catalog, @Nullable YsmModelProfile profile) { }
}
