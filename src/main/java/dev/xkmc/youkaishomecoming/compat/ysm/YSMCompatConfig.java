package dev.xkmc.youkaishomecoming.compat.ysm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YSMCompatConfig {

	private static final String RESOURCE_DIR = "yhysm";
	private static final Map<String, ModelRule> MODEL_RULES = new LinkedHashMap<>();
	private static final Map<String, List<String>> DEFAULT_EXPRESSIONS = Map.of(
			"angry", List.of("angry", "combat", "extra10", "attack", "attacked", "idle"),
			"cast", List.of("cast", "swing_hand", "extra10"),
			"charge", List.of("charge", "extra10", "extra11"),
			"special", List.of("special", "extra11", "extra12", "extra13")
	);

	private YSMCompatConfig() {
	}

	public static void reload(ResourceManager manager) {
		MODEL_RULES.clear();
		for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(RESOURCE_DIR, id -> id.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation id = entry.getKey();
			try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("models") && root.get("models").isJsonObject()) {
					for (Map.Entry<String, JsonElement> modelEntry : root.getAsJsonObject("models").entrySet()) {
						if (modelEntry.getValue().isJsonObject()) {
							loadRule(id, modelEntry.getKey(), modelEntry.getValue().getAsJsonObject());
						}
					}
				} else if (root.has("model")) {
					loadRule(id, root.get("model").getAsString(), root);
				}
			} catch (Exception ex) {
				YoukaisHomecoming.LOGGER.warn("Failed to load YH/YSM compat config {}", id, ex);
			}
		}
	}

	public static String expressionToken(String modelId, String expression) {
		List<String> candidates = expressionAnimations(modelId, expression);
		if (candidates.isEmpty()) {
			return expression;
		}
		return expression + "=" + String.join("+", candidates);
	}

	public static String debugExpressionMapping(String modelId, String expression) {
		List<String> candidates = expressionAnimations(modelId, expression);
		return candidates.isEmpty() ? expression + " -> (none)" : expression + " -> " + String.join(", ", candidates);
	}

	private static List<String> expressionAnimations(String modelId, String expression) {
		ModelRule rule = MODEL_RULES.get(modelId);
		if (rule != null) {
			List<String> configured = rule.expressions().get(expression);
			if (configured != null) {
				return configured;
			}
		}
		return DEFAULT_EXPRESSIONS.getOrDefault(expression, List.of(expression));
	}

	private static void loadRule(ResourceLocation source, String modelId, JsonObject object) {
		if (StringUtils.isBlank(modelId)) {
			YoukaisHomecoming.LOGGER.warn("Ignoring YH/YSM compat config {} with blank model id", source);
			return;
		}
		Map<String, List<String>> expressions = new LinkedHashMap<>();
		if (object.has("expressions") && object.get("expressions").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("expressions").entrySet()) {
				List<String> animations = parseAnimationList(entry.getValue());
				if (!animations.isEmpty()) {
					expressions.put(entry.getKey(), animations);
				}
			}
		}
		MODEL_RULES.put(modelId, new ModelRule(expressions));
		YoukaisHomecoming.LOGGER.debug("Loaded YH/YSM compat config {} for model {}", source, modelId);
	}

	private static List<String> parseAnimationList(JsonElement element) {
		List<String> result = new ArrayList<>();
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (JsonElement item : array) {
				addAnimation(result, item);
			}
		} else {
			addAnimation(result, element);
		}
		return result;
	}

	private static void addAnimation(List<String> result, JsonElement element) {
		if (!element.isJsonPrimitive()) {
			return;
		}
		String value = element.getAsString();
		if (StringUtils.isBlank(value) || value.contains("+") || StringUtils.containsWhitespace(value)) {
			return;
		}
		result.add(value);
	}

	private record ModelRule(Map<String, List<String>> expressions) {
	}
}
