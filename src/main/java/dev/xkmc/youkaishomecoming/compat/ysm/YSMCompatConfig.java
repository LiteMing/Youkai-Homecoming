package dev.xkmc.youkaishomecoming.compat.ysm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class YSMCompatConfig {

	private static final String RESOURCE_DIR = "yhysm";
	private static final String DEFAULT_TEXTURE = "default";
	private static final Path EXTERNAL_CONFIG = FMLPaths.CONFIGDIR.get().resolve(YoukaisHomecoming.MODID).resolve("ysm_defaults.json");
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** Native player predicates also read model rules from OpenYSM's animation worker. */
	private static final Map<String, ModelRule> MODEL_RULES = new java.util.concurrent.ConcurrentHashMap<>();
	private static final Map<ResourceLocation, RenderBinding> DEFAULT_BINDINGS = new ConcurrentHashMap<>();
	private static volatile ResourceManager resourceManager;
	private static final Map<String, List<String>> DEFAULT_EXPRESSIONS = Map.of(
			"angry", List.of("angry", "combat", "extra10", "attack", "attacked", "idle"),
			"cast", List.of("cast", "swing_hand", "extra10"),
			"charge", List.of("charge", "extra10", "extra11"),
			"special", List.of("special", "extra11", "extra12", "extra13")
	);

	private YSMCompatConfig() {
	}

	public static void reload(ResourceManager manager) {
		resourceManager = manager;
		MODEL_RULES.clear();
		DEFAULT_BINDINGS.clear();
		for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(RESOURCE_DIR, id -> id.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation id = entry.getKey();
			try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
				loadDocument(id.toString(), JsonParser.parseReader(reader).getAsJsonObject());
			} catch (Exception ex) {
				YoukaisHomecoming.LOGGER.warn("Failed to load YH/YSM compat config {}", id, ex);
			}
		}
		loadExternalConfig();
	}

	public static Map<ResourceLocation, RenderBinding> defaultBindings() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(DEFAULT_BINDINGS));
	}

	public static RenderBinding defaultBinding(ResourceLocation entityId) {
		return DEFAULT_BINDINGS.get(entityId);
	}

	/**
	 * Persists a type-level binding in the client-wide defaults file. A null binding removes the
	 * external entry and reveals the packaged/resource-pack default again after reload.
	 */
	public static synchronized void saveExternalBinding(ResourceLocation entityId, RenderBinding binding) {
		if (entityId == null) throw new IllegalArgumentException("Invalid entity type");
		JsonObject root = readExternalRoot();
		JsonObject entities = root.has("entities") && root.get("entities").isJsonObject()
				? root.getAsJsonObject("entities") : new JsonObject();
		if (binding == null) {
			entities.remove(entityId.toString());
		} else {
			entities.add(entityId.toString(), bindingToJson(binding));
		}
		if (entities.entrySet().isEmpty()) root.remove("entities");
		else root.add("entities", entities);
		writeExternalRoot(root);
		ResourceManager manager = resourceManager;
		if (manager != null) reload(manager);
		else if (binding == null) DEFAULT_BINDINGS.remove(entityId);
		else DEFAULT_BINDINGS.put(entityId, binding);
	}

	/** Model IDs declared by built-in/resource-pack or external default mappings. */
	public static List<String> configuredModelIds() {
		TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		ids.addAll(MODEL_RULES.keySet());
		DEFAULT_BINDINGS.values().stream().filter(RenderBinding::enabled).map(RenderBinding::modelId).forEach(ids::add);
		return List.copyOf(ids);
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

	private static void loadExternalConfig() {
		if (!Files.isRegularFile(EXTERNAL_CONFIG)) return;
		try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(EXTERNAL_CONFIG), StandardCharsets.UTF_8)) {
			loadDocument(EXTERNAL_CONFIG.toString(), JsonParser.parseReader(reader).getAsJsonObject());
		} catch (Exception ex) {
			YoukaisHomecoming.LOGGER.warn("Failed to load external YH/YSM default config {}", EXTERNAL_CONFIG, ex);
		}
	}

	private static JsonObject readExternalRoot() {
		if (!Files.isRegularFile(EXTERNAL_CONFIG)) return new JsonObject();
		try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(EXTERNAL_CONFIG), StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) throw new IllegalArgumentException("profile_storage: YSM defaults root must be an object");
			return parsed.getAsJsonObject();
		} catch (IOException | RuntimeException ex) {
			if (ex instanceof IllegalArgumentException illegal && illegal.getMessage() != null
					&& illegal.getMessage().startsWith("profile_storage:")) throw illegal;
			throw new IllegalArgumentException("profile_storage: Invalid YSM defaults JSON: " + ex.getMessage(), ex);
		}
	}

	private static JsonObject bindingToJson(RenderBinding binding) {
		JsonObject object = new JsonObject();
		object.addProperty("model", binding.modelId());
		object.addProperty("texture", binding.textureName());
		object.addProperty("enabled", binding.enabled());
		if (!binding.parameters().isEmpty()) {
			JsonObject parameters = new JsonObject();
			binding.parameters().forEach(parameters::addProperty);
			object.add("parameters", parameters);
		}
		return object;
	}

	private static void writeExternalRoot(JsonObject root) {
		Path temporary = null;
		try {
			Files.createDirectories(EXTERNAL_CONFIG.getParent());
			temporary = Files.createTempFile(EXTERNAL_CONFIG.getParent(), "ysm_defaults-", ".tmp");
			Files.writeString(temporary, JSON.toJson(root) + "\n", StandardCharsets.UTF_8);
			try {
				Files.move(temporary, EXTERNAL_CONFIG, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, EXTERNAL_CONFIG, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ex) {
			throw new IllegalArgumentException("profile_storage: " + EXTERNAL_CONFIG + ": " + ex.getMessage(), ex);
		} finally {
			if (temporary != null) {
				try { Files.deleteIfExists(temporary); }
				catch (IOException ex) { YoukaisHomecoming.LOGGER.warn("Could not remove YSM defaults temporary file {}", temporary, ex); }
			}
		}
	}

	private static void loadDocument(String source, JsonObject root) {
		if (root.has("entities") && root.get("entities").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entityEntry : root.getAsJsonObject("entities").entrySet()) {
				if (entityEntry.getValue().isJsonObject()) {
					loadBinding(source, entityEntry.getKey(), entityEntry.getValue().getAsJsonObject());
				}
			}
		}
		if (root.has("models") && root.get("models").isJsonObject()) {
			for (Map.Entry<String, JsonElement> modelEntry : root.getAsJsonObject("models").entrySet()) {
				if (modelEntry.getValue().isJsonObject()) {
					loadRule(source, modelEntry.getKey(), modelEntry.getValue().getAsJsonObject());
				}
			}
		} else if (root.has("model")) {
			loadRule(source, root.get("model").getAsString(), root);
		}
		if (root.has("entity") && root.has("model")) {
			loadBinding(source, root.get("entity").getAsString(), root);
		}
	}

	private static void loadBinding(String source, String entityIdText, JsonObject object) {
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
		DEFAULT_BINDINGS.put(entityId, RenderBinding.enabled(modelId, texture, parseParameters(object)));
		YoukaisHomecoming.LOGGER.debug("Loaded YH/YSM binding {} -> {} / {} from {}", entityId, modelId, texture, source);
	}

	private static Map<String, Float> parseParameters(JsonObject object) {
		if (!object.has("parameters") || !object.get("parameters").isJsonObject()) return Map.of();
		Map<String, Float> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("parameters").entrySet()) {
			if (result.size() >= YsmPresentationState.WIRE_MAX_PARAMETERS || !entry.getValue().isJsonPrimitive()
					|| !entry.getValue().getAsJsonPrimitive().isNumber()) continue;
			float value;
			try { value = entry.getValue().getAsFloat(); }
			catch (RuntimeException ignored) { continue; }
			if (!Float.isFinite(value) || Math.abs(value) > YsmPresentationState.WIRE_MAX_PARAMETER_VALUE) continue;
			try { result.put(YsmPresentationState.normalizeParameter(entry.getKey()), value); }
			catch (IllegalArgumentException ignored) { }
		}
		return result;
	}

	private static void loadRule(String source, String modelId, JsonObject object) {
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

	public record RenderBinding(String modelId, String textureName, boolean enabled, Map<String, Float> parameters) {

		public RenderBinding {
			// Binding appearance uses the same bounded numeric literals as presets, but never expires.
			parameters = new YsmModelProfile.Preset("", "", 0, parameters).parameters();
		}

		public RenderBinding(String modelId, String textureName, boolean enabled) {
			this(modelId, textureName, enabled, Map.of());
		}

		public static RenderBinding enabled(String modelId, String textureName) {
			return new RenderBinding(modelId, textureName, true);
		}

		public static RenderBinding enabled(String modelId, String textureName, Map<String, Float> parameters) {
			return new RenderBinding(modelId, textureName, true, parameters);
		}

		public static RenderBinding disabled() {
			return new RenderBinding("", "", false);
		}
	}
}
