package dev.xkmc.youkaishomecoming.content.spell.preview;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.compat.ysm.YSMCompatConfig.RenderBinding;
import dev.xkmc.youkaishomecoming.compat.ysm.YsmModelProfile;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Editor export: a portable profile plus the optional UUID/type binding being edited.
 * Only the profile is sent to the shared-profile API; binding data uses its existing packet/store. */
public record YsmEditorDocument(YsmModelProfile profile, @Nullable Binding binding) {

	public static final int MAX_JSON_LENGTH = YsmModelProfile.MAX_JSON_LENGTH * 2;

	public record Binding(boolean typeTarget, String target, String model, String texture, Map<String, Float> parameters) {
		public Binding {
			if (target == null || target.length() > 256 || model == null || model.length() > 256
					|| texture == null || texture.length() > 256) throw new IllegalArgumentException("Invalid binding fields");
			parameters = RenderBinding.enabled(model, texture, parameters).parameters();
		}
	}

	public String toJson() {
		JsonObject root = JsonParser.parseString(profile.toJson()).getAsJsonObject();
		if (binding != null) {
			JsonObject value = new JsonObject();
			value.addProperty("scope", binding.typeTarget() ? "type" : "uuid");
			value.addProperty("target", binding.target());
			value.addProperty("model", binding.model());
			value.addProperty("texture", binding.texture());
			JsonObject parameters = new JsonObject();
			binding.parameters().forEach(parameters::addProperty);
			value.add("parameters", parameters);
			root.add("binding", value);
		}
		return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
	}

	public static YsmEditorDocument fromJson(String json) {
		if (json == null || json.length() > MAX_JSON_LENGTH) throw new IllegalArgumentException("Profile JSON is too large");
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			var bindingJson = root.remove("binding");
			YsmModelProfile profile = YsmModelProfile.fromJson(root.toString());
			Binding binding = null;
			if (bindingJson != null) {
				JsonObject value = bindingJson.getAsJsonObject();
				var keys = Set.of("scope", "target", "model", "texture", "parameters");
				for (String key : value.keySet()) if (!keys.contains(key)) throw new IllegalArgumentException("Unknown binding field: " + key);
				String scope = string(value, "scope");
				if (!scope.equals("uuid") && !scope.equals("type")) throw new IllegalArgumentException("Invalid binding scope");
				Map<String, Float> parameters = new LinkedHashMap<>();
				JsonObject parameterJson = value.has("parameters") ? value.getAsJsonObject("parameters") : new JsonObject();
				for (var entry : parameterJson.entrySet()) {
					if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber())
						throw new IllegalArgumentException("Parameters must be numeric literals");
					parameters.put(entry.getKey(), entry.getValue().getAsFloat());
				}
				binding = new Binding(scope.equals("type"), string(value, "target"), string(value, "model"), string(value, "texture"), parameters);
			}
			return new YsmEditorDocument(profile, binding);
		} catch (RuntimeException ex) {
			throw new IllegalArgumentException("Invalid YH editor profile: " + ex.getMessage(), ex);
		}
	}

	private static String string(JsonObject object, String key) {
		var value = object.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw new IllegalArgumentException("Expected string: " + key);
		return value.getAsString();
	}
}
