package dev.xkmc.youkaishomecoming.compat.ysm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registers YH's resource-declared presentation presets in OYSM's registry. */
public final class YsmPresetRegistration {
	private static final String RESOURCE = "assets/youkaishomecoming/models/ysm/defaults.json";
	private static boolean attempted;

	private YsmPresetRegistration() { }

	public static void register() {
		if (attempted || !ModList.get().isLoaded("yes_steve_model") || !YsmOpenYsmSupport.available()) return;
		attempted = true;
		try {
			var modFile = ModList.get().getModFileById(YoukaisHomecoming.MODID);
			if (modFile == null) return;
			Path file = modFile.getFile().getSecureJar().getPath("/" + RESOURCE);
			if (!Files.isRegularFile(file)) return;
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			Class<?> api = Class.forName("rip.ysm.api.ExternalPresetAPI", false, YsmPresetRegistration.class.getClassLoader());
			api.getMethod("registerBindingKeys", Collection.class).invoke(null,
					java.util.Arrays.stream(YsmPresentationSignals.Trigger.values()).map(YsmPresentationSignals.Trigger::id).toList());
			Class<?> presetType = Class.forName("rip.ysm.api.ExternalPresetAPI$Definition", false, YsmPresetRegistration.class.getClassLoader());
			var ctor = presetType.getConstructor(String.class, String.class, String.class, int.class, Map.class);
			Method register = api.getMethod("registerDefaults", String.class, String.class, Collection.class, Map.class);
			if (!root.has("presets") || !root.get("presets").isJsonObject()) return;
			for (var model : root.getAsJsonObject("presets").entrySet()) {
				if (!model.getValue().isJsonArray()) continue;
				Map<String, String> bindings = new LinkedHashMap<>();
				if (root.has("bindings") && root.getAsJsonObject("bindings").has(model.getKey())) {
					for (var binding : root.getAsJsonObject("bindings").getAsJsonObject(model.getKey()).entrySet()) bindings.put(binding.getKey(), binding.getValue().getAsString());
				}
				ArrayList<Object> presets = new ArrayList<>();
				for (JsonElement value : model.getValue().getAsJsonArray()) {
					if (!value.isJsonObject()) continue;
					JsonObject item = value.getAsJsonObject();
					Map<String, Float> parameters = new LinkedHashMap<>();
					if (item.has("parameters") && item.get("parameters").isJsonObject()) {
						for (var parameter : item.getAsJsonObject("parameters").entrySet()) parameters.put(parameter.getKey(), parameter.getValue().getAsFloat());
					}
					presets.add(ctor.newInstance(item.get("id").getAsString(),
							item.has("description") ? item.get("description").getAsString() : "",
							item.has("clip") ? item.get("clip").getAsString() : "",
							item.has("ticks") ? item.get("ticks").getAsInt() : 0, parameters));
				}
				register.invoke(null, YoukaisHomecoming.MODID, model.getKey(), presets, bindings);
			}
			YoukaisHomecoming.LOGGER.info("Registered resource-declared YH presets with OYSM");
		} catch (ClassNotFoundException | NoSuchMethodException ignored) {
			// OYSM is optional; there is no second YH preset implementation.
		} catch (ReflectiveOperationException | java.io.IOException | RuntimeException ex) {
			YoukaisHomecoming.LOGGER.warn("Failed to register YH presets with OYSM", ex);
		}
	}
}
