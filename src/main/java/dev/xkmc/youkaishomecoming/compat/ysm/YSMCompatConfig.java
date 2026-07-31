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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YSMCompatConfig {

	private static final String RESOURCE_DIR = "yhysm";
	private static final String DEFAULT_TEXTURE = "default";
	private static final Map<String, ModelRule> MODEL_RULES = new LinkedHashMap<>();
	private static final Map<ResourceLocation, RenderBinding> DEFAULT_BINDINGS = new LinkedHashMap<>();
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
		DEFAULT_BINDINGS.clear();
		loadBuiltinDefaults();
		for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(RESOURCE_DIR, id -> id.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation id = entry.getKey();
			try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("entities") && root.get("entities").isJsonObject()) {
					for (Map.Entry<String, JsonElement> entityEntry : root.getAsJsonObject("entities").entrySet()) {
						if (entityEntry.getValue().isJsonObject()) {
							loadBinding(id, entityEntry.getKey(), entityEntry.getValue().getAsJsonObject());
						}
					}
				}
				if (root.has("models") && root.get("models").isJsonObject()) {
					for (Map.Entry<String, JsonElement> modelEntry : root.getAsJsonObject("models").entrySet()) {
						if (modelEntry.getValue().isJsonObject()) {
							loadRule(id, modelEntry.getKey(), modelEntry.getValue().getAsJsonObject());
						}
					}
				} else if (root.has("model")) {
					loadRule(id, root.get("model").getAsString(), root);
				}
				if (root.has("entity") && root.has("model")) {
					loadBinding(id, root.get("entity").getAsString(), root);
				}
			} catch (Exception ex) {
				YoukaisHomecoming.LOGGER.warn("Failed to load YH/YSM compat config {}", id, ex);
			}
		}
	}

	public static Map<ResourceLocation, RenderBinding> defaultBindings() {
		return Collections.unmodifiableMap(DEFAULT_BINDINGS);
	}

	public static RenderBinding defaultBinding(ResourceLocation entityId) {
		return DEFAULT_BINDINGS.get(entityId);
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

	private static void loadBuiltinDefaults() {
		DEFAULT_BINDINGS.put(YoukaisHomecoming.loc("remilia_scarlet"), RenderBinding.enabled("YH内置/remilia", DEFAULT_TEXTURE));
	}

	private static void loadBinding(ResourceLocation source, String entityIdText, JsonObject object) {
		ResourceLocation entityId = ResourceLocation.tryParse(entityIdText);
		if (entityId == null) {
			YoukaisHomecoming.LOGGER.warn("Ignoring YH/YSM compat config {} with invalid entity id {}", source, entityIdText);
			return;
		}
		boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
		if (!enabled) {
			DEFAULT_BINDINGS.put(entityId, RenderBinding.disabled());
			YoukaisHomecoming.LOGGER.debug("Loaded disabled YH/YSM binding {} from {}", entityId, source);
			return;
		}
		String modelId = object.has("model") ? object.get("model").getAsString().trim() : "";
		if (StringUtils.isBlank(modelId)) {
			YoukaisHomecoming.LOGGER.warn("Ignoring YH/YSM compat config {} binding {} with blank model id", source, entityId);
			return;
		}
		String texture = object.has("texture") ? object.get("texture").getAsString().trim() : DEFAULT_TEXTURE;
		if (StringUtils.isBlank(texture)) {
			texture = DEFAULT_TEXTURE;
		}
		DEFAULT_BINDINGS.put(entityId, RenderBinding.enabled(modelId, texture));
		YoukaisHomecoming.LOGGER.debug("Loaded YH/YSM binding {} -> {} / {} from {}", entityId, modelId, texture, source);
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

	public record RenderBinding(String modelId, String textureName, boolean enabled) {

		public static RenderBinding enabled(String modelId, String textureName) {
			return new RenderBinding(modelId, textureName, true);
		}

		public static RenderBinding disabled() {
			return new RenderBinding("", "", false);
		}
	}
}
